package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.repositories.BlockedDomainRepository;
import de.marsg.wishlist.jpa.repositories.LinkHostCheckRepository;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Judges link hosts: shop allowlist, shorteners, admin domain blocks, local blocklist, then the
 * stored per-host Web Risk verdict, and a fresh parallel lookup only for hosts never seen before.
 */
@Service
public class LinkSafetyService {

    private static final Logger log = LoggerFactory.getLogger(LinkSafetyService.class);
    private static final long WEB_RISK_DEADLINE_SECONDS = 5;
    private static final int WEB_RISK_PARALLELISM = 4;

    /** Caps concurrent lookups across requests so they cannot drain the DB connection pool. */
    private final Semaphore webRiskPermits = new Semaphore(WEB_RISK_PARALLELISM);

    private final List<String> allowedDomains;
    private final List<String> shortenerDomains;
    private final LinkBlocklist blocklist;
    private final boolean blocklistEnabled;
    private final BlockedDomainRepository blockedDomains;
    private final LinkHostCheckRepository hostChecks;
    private final WebRiskClient webRisk;
    private final WishLinkRepository linkRepository;

    public LinkSafetyService(
            @Value("${wishlist.link-safety.allowed-domains}") List<String> allowedDomains,
            @Value("${wishlist.link-safety.shortener-domains}") List<String> shortenerDomains,
            LinkBlocklist blocklist,
            @Value("${wishlist.link-safety.blocklist.enabled}") boolean blocklistEnabled,
            BlockedDomainRepository blockedDomains, LinkHostCheckRepository hostChecks, WebRiskClient webRisk,
            WishLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
        this.allowedDomains = normalizeAll(allowedDomains);
        this.shortenerDomains = normalizeAll(shortenerDomains);
        this.blocklist = blocklist;
        this.blocklistEnabled = blocklistEnabled;
        this.blockedDomains = blockedDomains;
        this.hostChecks = hostChecks;
        this.webRisk = webRisk;
    }

    /**
     * Problems per link index for already normalized URLs (a null entry is skipped); each exception
     * carries its link_index. Web Risk runs in parallel for unseen hosts only and fails open.
     */
    public Map<Integer, ApiException> findProblems(List<String> urls) {
        Map<Integer, ApiException> problems = new TreeMap<>();
        Map<String, List<Integer>> unseen = new LinkedHashMap<>();
        Map<String, Boolean> unsafeByHost = new HashMap<>();
        List<String> candidates = urls.stream().filter(Objects::nonNull).distinct().toList();
        Set<String> adminBlockedUrls = candidates.isEmpty() ? Set.of()
                : Set.copyOf(linkRepository.findAdminBlockedUrls(candidates));
        for (int i = 0; i < urls.size(); i++) {
            String host = urls.get(i) == null ? null : URI.create(urls.get(i)).getHost();
            if (host == null) {
                continue;
            }
            if (adminBlockedUrls.contains(urls.get(i))) {
                problems.put(i, problem(LinkBlockSource.ADMIN, i));
                continue;
            }
            LinkBlockSource source = adminDomainBlocked(host) ? LinkBlockSource.ADMIN_DOMAIN : null;
            if (source == null && isAllowlisted(host)) {
                continue;
            }
            if (source == null) {
                source = listSource(host);
            }
            if (source == null) {
                Boolean cached = unsafeByHost.containsKey(host) ? unsafeByHost.get(host) : cachedUnsafe(host);
                unsafeByHost.put(host, cached);
                if (cached == null) {
                    unseen.computeIfAbsent(host, h -> new ArrayList<>()).add(i);
                } else if (cached) {
                    source = LinkBlockSource.WEB_RISK;
                }
            }
            if (source != null) {
                problems.put(i, problem(source, i));
            }
        }
        lookUp(unseen.keySet()).forEach((host, unsafe) -> {
            if (unsafe) {
                unseen.get(host).forEach(i -> problems.put(i, problem(LinkBlockSource.WEB_RISK, i)));
            }
        });
        return problems;
    }

    /** Why a host is blocked without any Web Risk call (stored verdict only); null when clean. */
    public LinkBlockSource sourceWithoutWebRisk(String host) {
        if (adminDomainBlocked(host)) {
            return LinkBlockSource.ADMIN_DOMAIN;
        }
        if (isAllowlisted(host)) {
            return null;
        }
        LinkBlockSource source = listSource(host);
        if (source != null) {
            return source;
        }
        return Boolean.TRUE.equals(cachedUnsafe(host)) ? LinkBlockSource.WEB_RISK : null;
    }

    /** True for allowlisted shops and their subdomains; they skip every other check. */
    public boolean isAllowlisted(String host) {
        return matches(allowedDomains, host);
    }

    /** Admin domain block on the host or a parent domain; it wins over the allowlist. */
    public boolean adminDomainBlocked(String host) {
        return !blockedDomains.findBlockedAmong(withParents(host)).isEmpty();
    }

    /** Shortener or local blocklist, in that order; null when none applies. */
    public LinkBlockSource listSource(String host) {
        if (matches(shortenerDomains, host)) {
            return LinkBlockSource.SHORTENER;
        }
        return blocklistEnabled && blocklist.contains(host) ? LinkBlockSource.BLOCKLIST : null;
    }

    /** True when the stored Web Risk verdict is UNSAFE, false when SAFE, null when never checked. */
    Boolean cachedUnsafe(String host) {
        return hostChecks.findVerdict(host).map("UNSAFE"::equals).orElse(null);
    }

    /** Hosts the lists cover, as the host itself plus its parent domains (never the bare TLD). */
    static List<String> withParents(String host) {
        List<String> candidates = new ArrayList<>();
        String candidate = host.toLowerCase(Locale.ROOT);
        candidates.add(candidate);
        int dot = candidate.indexOf('.');
        while (dot >= 0 && candidate.indexOf('.', dot + 1) >= 0) {
            candidate = candidate.substring(dot + 1);
            candidates.add(candidate);
            dot = candidate.indexOf('.');
        }
        return candidates;
    }

    private Map<String, Boolean> lookUp(java.util.Collection<String> hosts) {
        Map<String, Boolean> unsafeByHost = new HashMap<>();
        if (hosts.isEmpty() || !webRisk.enabled()) {
            return unsafeByHost;
        }
        List<String> ordered = List.copyOf(hosts);
        List<Callable<Boolean>> tasks = ordered.stream().<Callable<Boolean>>map(host -> () -> {
            webRiskPermits.acquire();
            try {
                Boolean unsafe = webRisk.isUnsafe(host);
                if (unsafe != null) {
                    hostChecks.upsertVerdict(host, unsafe ? "UNSAFE" : "SAFE");
                }
                return unsafe;
            } finally {
                webRiskPermits.release();
            }
        }).toList();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = pool.invokeAll(tasks, WEB_RISK_DEADLINE_SECONDS, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                Future<Boolean> future = futures.get(i);
                if (future.isCancelled()) {
                    continue;
                }
                try {
                    Boolean unsafe = future.get();
                    if (unsafe != null) {
                        unsafeByHost.put(ordered.get(i), unsafe);
                    }
                } catch (ExecutionException e) {
                    log.warn("Web Risk lookup failed ({}), allowing link", e.getCause().getClass().getSimpleName());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Web Risk lookups failed ({}), allowing links", e.getClass().getSimpleName());
        }
        return unsafeByHost;
    }

    private static ApiException problem(LinkBlockSource source, int index) {
        return new ApiException(source == LinkBlockSource.SHORTENER
                ? ErrorCode.LINK_SHORTENER_NOT_ALLOWED : ErrorCode.LINK_BLOCKED).atLink(index);
    }

    private static boolean matches(List<String> domains, String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return domains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d));
    }

    private static List<String> normalizeAll(List<String> domains) {
        return domains.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList();
    }
}
