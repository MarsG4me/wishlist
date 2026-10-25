package de.marsg.wishlist.endpoints.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUrlValidatorTest {

    private final LinkUrlValidator validator = new LinkUrlValidator();

    @Test
    void normalizesSchemeAndHost() {
        assertEquals("https://example.com/Path?q=1#f", validator.normalize("  HTTPS://Example.COM/Path?q=1#f "));
        assertEquals("http://xn--bcher-kva.example:8080/", validator.normalize("http://bücher.example:8080/"));
    }

    @Test
    void rejectsSupplementaryPlaneFormatChars() {
        assertThrows(ApiException.class, () -> validator.normalize("http://example.com/a\uDB40\uDC20b"));
    }

    @Test
    void rejectsUnsafeUrls() {
        List<String> bad = List.of("", "javascript:alert(1)", "data:text/html,x", "ftp://example.com",
                "//example.com", "http://user:pw@example.com", "http://127.0.0.1/", "http://[::1]/",
                "http://2130706433/", "http://0x7f.1/", "http://localhost/", "http://a.localhost/",
                "http://printer.local/", "http://db.internal/", "http://intranet/", "http://exa mple.com",
                "http://example.com/a\nb", "http://example.com/a\u200Bb", "http://exa\u00ADmple.com/",
                "http://example.com:99999/", "http://-bad.example.com/",
                "http://example.com/" + "a".repeat(2048));
        for (String url : bad) {
            ApiException e = assertThrows(ApiException.class, () -> validator.normalize(url), url);
            assertEquals(ErrorCode.INVALID_LINK_URL, e.getErrorCode());
        }
    }
}
