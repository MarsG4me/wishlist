package de.marsg.wishlist.helpers;

import de.marsg.wishlist.endpoints.services.LinkRescanService;
import de.marsg.wishlist.endpoints.services.LinkSafetyService;
import de.marsg.wishlist.endpoints.services.WebRiskClient;
import de.marsg.wishlist.jpa.repositories.LinkHostCheckRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Last day of the month, 02:00: spends the Web Risk budget left in the month on rechecking the
 * hosts with the oldest verdict. A changed verdict flags or unflags the host's links.
 */
@Component
public class LinkHostRecheckJob {

    private static final Logger log = LoggerFactory.getLogger(LinkHostRecheckJob.class);
    private static final int BATCH = 500;

    private final WebRiskClient webRisk;
    private final LinkHostCheckRepository hostChecks;
    private final LinkSafetyService linkSafety;
    private final LinkRescanService rescanService;

    public LinkHostRecheckJob(WebRiskClient webRisk, LinkHostCheckRepository hostChecks,
            LinkSafetyService linkSafety, LinkRescanService rescanService) {
        this.webRisk = webRisk;
        this.hostChecks = hostChecks;
        this.linkSafety = linkSafety;
        this.rescanService = rescanService;
    }

    @Scheduled(cron = "0 0 2 L * *")
    public void recheck() {
        if (!webRisk.enabled()) {
            return;
        }
        Instant runStart = Instant.now();
        int rechecked = 0;
        int budget = webRisk.remainingBudget();
        while (budget > 0) {
            List<String> hosts = hostChecks.findOldestHostsUsedByLinks(runStart, Math.min(BATCH, budget));
            if (hosts.isEmpty()) {
                break;
            }
            for (String host : hosts) {
                if (linkSafety.adminDomainBlocked(host) || linkSafety.isAllowlisted(host)
                        || linkSafety.listSource(host) != null) {
                    hostChecks.deleteByHost(host);
                    continue;
                }
                Boolean unsafe = webRisk.isUnsafe(host);
                if (unsafe == null) {
                    log.info("Web Risk recheck stopped after {} hosts (budget spent or lookup failed)", rechecked);
                    return;
                }
                hostChecks.upsertVerdict(host, unsafe ? "UNSAFE" : "SAFE");
                rescanService.rescanHost(host);
                rechecked++;
                budget--;
            }
        }
        log.info("Web Risk recheck done: {} hosts", rechecked);
    }
}
