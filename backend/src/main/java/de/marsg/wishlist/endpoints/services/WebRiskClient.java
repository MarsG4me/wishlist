package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.jpa.repositories.WebRiskUsageRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Google Web Risk host lookup under a monthly DB-counted budget; every failure means "no verdict". */
@Component
public class WebRiskClient {

    private static final Logger log = LoggerFactory.getLogger(WebRiskClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final String ENDPOINT = "https://webrisk.googleapis.com/v1/uris:search"
            + "?threatTypes=MALWARE&threatTypes=SOCIAL_ENGINEERING&threatTypes=UNWANTED_SOFTWARE"
            + "&threatTypes=SOCIAL_ENGINEERING_EXTENDED_COVERAGE";

    private final String key;
    private final int monthlyLimit;
    private final WebRiskUsageRepository usage;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public WebRiskClient(@Value("${wishlist.link-safety.web-risk-api-key:}") String key,
            @Value("${wishlist.link-safety.web-risk-monthly-limit}") int monthlyLimit,
            WebRiskUsageRepository usage, ObjectMapper objectMapper) {
        this.key = key == null ? "" : key.trim();
        this.monthlyLimit = monthlyLimit;
        this.usage = usage;
        this.objectMapper = objectMapper;
    }

    /** An empty API key switches Web Risk off entirely. */
    public boolean enabled() {
        return !key.isEmpty();
    }

    /** Calls left in the current UTC month. */
    public int remainingBudget() {
        return Math.max(0, monthlyLimit - usage.findCallsThisMonth().orElse(0));
    }

    /** TRUE unsafe, FALSE safe, null when there is no verdict (no key, budget spent, HTTP or parse error). */
    public Boolean isUnsafe(String host) {
        if (!enabled() || monthlyLimit <= 0 || usage.tryReserve(monthlyLimit) == 0) {
            return null;
        }
        try {
            URI uri = URI.create(ENDPOINT + "&uri=" + URLEncoder.encode("https://" + host + "/", StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Web Risk lookup failed with HTTP {}, allowing host", response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body()).hasNonNull("threat");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Web Risk lookup failed ({}), allowing host", e.getClass().getSimpleName());
            return null;
        }
    }
}
