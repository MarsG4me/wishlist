package de.marsg.wishlist.endpoints.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Downloads the blocklist sources at startup (off-thread) and daily; failures keep the old set. */
@Component
public class LinkBlocklistUpdater {

    private static final Logger log = LoggerFactory.getLogger(LinkBlocklistUpdater.class);
    private static final int MAX_ENTRIES = 10_000_000;

    private final LinkBlocklist blocklist;
    private final boolean enabled;
    private final List<String> urls;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public LinkBlocklistUpdater(LinkBlocklist blocklist,
            @Value("${wishlist.link-safety.blocklist.enabled}") boolean enabled,
            @Value("${wishlist.link-safety.blocklist.urls}") List<String> urls) {
        this.blocklist = blocklist;
        this.enabled = enabled;
        this.urls = urls.stream().map(String::trim).filter(u -> !u.isEmpty()).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadAtStartup() {
        if (enabled) {
            Thread.startVirtualThread(this::refresh);
        }
    }

    @Scheduled(cron = "0 30 4 * * *")
    void refreshDaily() {
        if (enabled) {
            refresh();
        }
    }

    /** Aborts and keeps the current set on any failure, unless the set is still empty. */
    void refresh() {
        long[] buffer = new long[1 << 16];
        int count = 0;
        boolean failed = false;
        for (String url : urls) {
            try {
                long[] filled = buffer;
                int[] n = {count};
                try (Stream<String> lines = fetch(url)) {
                    for (var it = lines.iterator(); it.hasNext() && n[0] < MAX_ENTRIES;) {
                        String domain = LinkBlocklist.parseLine(it.next());
                        if (domain == null) {
                            continue;
                        }
                        if (n[0] == filled.length) {
                            filled = Arrays.copyOf(filled, filled.length * 2);
                        }
                        filled[n[0]++] = LinkBlocklist.hash(domain);
                    }
                }
                buffer = filled;
                count = n[0];
            } catch (Exception e) {
                failed = true;
                log.warn("Blocklist source failed ({})", e.getClass().getSimpleName());
            }
        }
        if (failed && blocklist.size() > 0) {
            log.warn("Blocklist refresh incomplete, keeping previous set of {} entries", blocklist.size());
            return;
        }
        long[] sorted = Arrays.copyOf(buffer, count);
        Arrays.sort(sorted);
        int unique = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[unique++] = sorted[i];
            }
        }
        blocklist.swap(Arrays.copyOf(sorted, unique));
        log.info("Blocklist loaded: {} domains from {} sources", unique, urls.size());
    }

    private Stream<String> fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).build();
        HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}
