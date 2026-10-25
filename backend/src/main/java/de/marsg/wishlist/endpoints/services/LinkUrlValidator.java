package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Syntax gate for wish links: public http(s) URLs with a DNS host, nothing else. */
@Component
public class LinkUrlValidator {

    static final int MAX_LENGTH = 2048;
    private static final Set<String> PRIVATE_TLDS =
            Set.of("localhost", "local", "internal", "localdomain", "lan", "intranet", "arpa");

    /** Returns the trimmed URL with scheme and host normalized; throws INVALID_LINK_URL otherwise. */
    public String normalize(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String url = raw.trim();
        if (url.isEmpty() || url.length() > MAX_LENGTH || hasForbiddenChar(url)) {
            throw invalid();
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String authority = uri.getRawAuthority();
        if (!uri.isAbsolute() || uri.isOpaque() || authority == null
                || !(scheme.equals("http") || scheme.equals("https")) || authority.contains("@")) {
            throw invalid();
        }
        String hostPart = authority;
        String port = "";
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            hostPart = authority.substring(0, colon);
            port = authority.substring(colon + 1);
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65535) {
                throw invalid();
            }
        }
        String host = asciiHost(hostPart);
        StringBuilder out = new StringBuilder(scheme).append("://").append(host);
        if (!port.isEmpty()) {
            out.append(':').append(port);
        }
        out.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            out.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            out.append('#').append(uri.getRawFragment());
        }
        if (out.length() > MAX_LENGTH) {
            throw invalid();
        }
        return out.toString();
    }

    private static boolean hasForbiddenChar(String url) {
        return url.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)
                || Character.isSpaceChar(c)
                || Character.getType(c) == Character.FORMAT);
    }

    private String asciiHost(String hostPart) {
        if (hostPart.isEmpty() || hostPart.startsWith("[")) {
            throw invalid();
        }
        String host;
        try {
            host = IDN.toASCII(hostPart).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty() || host.length() > 253 || !host.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+")) {
            throw invalid();
        }
        String tld = host.substring(host.lastIndexOf('.') + 1);
        if (tld.matches("[0-9]+|0x[0-9a-f]*") || PRIVATE_TLDS.contains(tld)) {
            throw invalid();
        }
        return host;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_LINK_URL);
    }
}
