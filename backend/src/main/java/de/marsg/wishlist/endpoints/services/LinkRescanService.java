package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-judges saved links when the lists change. Automatic flags are set and cleared here;
 * ADMIN and ADMIN_DOMAIN flags are never touched by an automatic rescan; only unblocking lifts them.
 */
@Service
public class LinkRescanService {

    private static final Logger log = LoggerFactory.getLogger(LinkRescanService.class);
    private static final int BATCH = 500;

    private final WishLinkRepository linkRepository;
    private final LinkSafetyService linkSafety;

    private final TransactionTemplate transactionTemplate;

    public LinkRescanService(WishLinkRepository linkRepository, LinkSafetyService linkSafety,
            TransactionTemplate transactionTemplate) {
        this.linkRepository = linkRepository;
        this.linkSafety = linkSafety;
        this.transactionTemplate = transactionTemplate;
    }

    /** Rescan after the lists were reloaded; BLOCKLIST flags only drop when every source loaded. */
    @EventListener
    void onListsChanged(LinkListsChangedEvent event) {
        rescanAll(event.blocklistComplete());
    }

    public void rescanAll() {
        rescanAll(true);
    }

    /** Backfills missing hosts, then rescans every host; a failure is logged, the next trigger retries. */
    void rescanAll(boolean blocklistComplete) {
        try {
            backfillHosts();
            List<String> hosts = linkRepository.findDistinctHosts();
            hosts.forEach(host -> rescanHost(host, blocklistComplete));
            log.info("Rescanned saved links of {} hosts", hosts.size());
        } catch (RuntimeException e) {
            log.warn("Link rescan failed ({})", e.getClass().getSimpleName());
        }
    }

    /** Applies the host's current verdict to its links; the stored Web Risk verdict counts, no new lookups. */
    public void rescanHost(String host) {
        rescanHost(host, true);
    }

    /** Under the host's advisory lock; with an incomplete blocklist, BLOCKLIST flags are kept. */
    void rescanHost(String host, boolean blocklistComplete) {
        transactionTemplate.executeWithoutResult(status -> {
            linkRepository.lockHost(host);
            LinkBlockSource source = linkSafety.sourceWithoutWebRisk(host);
            if (source == LinkBlockSource.ADMIN_DOMAIN) {
                linkRepository.flagHostAsAdminDomain(host);
            } else if (source != null) {
                linkRepository.flagHostAutomatically(host, source.name());
            } else if (blocklistComplete) {
                linkRepository.clearAutomaticFlags(host);
            } else {
                linkRepository.clearAutomaticFlagsKeepingBlocklist(host);
            }
        });
    }

    /** Locks the domain and every saved host under it in sorted order; call inside a transaction before changing the block. */
    public void lockDomain(String domain) {
        Set<String> hosts = new TreeSet<>(linkRepository.findHostsUnder(domain));
        hosts.add(domain);
        hosts.forEach(linkRepository::lockHost);
    }

    /** After a domain block or unblock: every link on the domain or a subdomain; unblocking lifts ADMIN_DOMAIN flags first. */
    public void rescanDomain(String domain, boolean liftDomainFlags) {
        transactionTemplate.executeWithoutResult(status -> {
            lockDomain(domain);
            for (String host : linkRepository.findHostsUnder(domain)) {
                if (liftDomainFlags) {
                    linkRepository.clearAdminDomainFlags(host);
                }
                rescanHost(host);
            }
        });
    }

    private void backfillHosts() {
        UUID after = new UUID(0, 0);
        while (true) {
            List<Object[]> rows = linkRepository.findWithoutHost(after, BATCH);
            if (rows.isEmpty()) {
                return;
            }
            for (Object[] row : rows) {
                after = (UUID) row[0];
                try {
                    String host = URI.create((String) row[1]).getHost();
                    if (host != null) {
                        linkRepository.setHost((UUID) row[0], host.toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Saved link {} has an unparseable URL, host left empty", row[0]);
                }
            }
        }
    }
}
