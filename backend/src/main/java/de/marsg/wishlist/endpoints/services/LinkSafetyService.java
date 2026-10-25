package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Rejects links to known-bad hosts: shop allowlist first, then local blocklist, then Web Risk. */
@Service
public class LinkSafetyService {

    private static final Logger log = LoggerFactory.getLogger(LinkSafetyService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String WEB_RISK = "https://webrisk.googleapis.com/v1/uris:search"
            + "?threatTypes=MALWARE&threatTypes=SOCIAL_ENGINEERING&threatTypes=UNWANTED_SOFTWARE"
            + "&threatTypes=SOCIAL_ENGINEERING_EXTENDED_COVERAGE";

    private final List<String> allowedDomains;
    private final LinkBlocklist blocklist;
    private final boolean blocklistEnabled;
    private final String webRiskKey;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public LinkSafetyService(
            @Value("${wishlist.link-safety.allowed-domains}") List<String> allowedDomains,
            LinkBlocklist blocklist,
            @Value("${wishlist.link-safety.blocklist.enabled}") boolean blocklistEnabled,
            @Value("${wishlist.link-safety.web-risk-api-key:}") String webRiskKey,
            ObjectMapper objectMapper) {
        this.allowedDomains = allowedDomains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList();
        this.blocklist = blocklist;
        this.blocklistEnabled = blocklistEnabled;
        this.webRiskKey = webRiskKey == null ? "" : webRiskKey.trim();
        this.objectMapper = objectMapper;
    }

    /** Expects a URL already normalized by {@link LinkUrlValidator}; throws LINK_BLOCKED. */
    public void assertSafe(String url) {
        String host = URI.create(url).getHost();
        if (host == null || isAllowlisted(host)) {
            return;
        }
        if ((blocklistEnabled && blocklist.contains(host)) || (!webRiskKey.isEmpty() && flaggedByWebRisk(url))) {
            throw new ApiException(ErrorCode.LINK_BLOCKED);
        }
    }

    boolean isAllowlisted(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return allowedDomains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d));
    }

    private boolean flaggedByWebRisk(String url) {
        try {
            URI uri = URI.create(WEB_RISK + "&uri=" + URLEncoder.encode(url, StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(webRiskKey, StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Web Risk lookup failed with HTTP {}, allowing link", response.statusCode());
                return false;
            }
            return objectMapper.readTree(response.body()).hasNonNull("threat");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Web Risk lookup failed ({}), allowing link", e.getClass().getSimpleName());
            return false;
        }
    }
}
