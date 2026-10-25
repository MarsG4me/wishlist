package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Session revoke after a password change against a tiny fake Keycloak: keeps the current sid, never throws. */
class KeycloakAccountClientSessionsTest {

    private final UUID keycloakId = UUID.randomUUID();
    private final List<String> deleted = new CopyOnWriteArrayList<>();
    private volatile int sessionsStatus = 200;
    private volatile int consentsStatus = 200;
    private HttpServer server;
    private KeycloakAccountClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = new KeycloakAccountClient("http://127.0.0.1:" + server.getAddress().getPort(), "r", "admin",
                "secret", "cli");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        int status = 200;
        String body = "{}";
        if (path.endsWith("/token")) {
            body = "{\"access_token\":\"t\"}";
        } else if (path.endsWith("/consents") && ex.getRequestMethod().equals("GET")) {
            status = consentsStatus;
            body = "[{\"clientId\":\"app-a\",\"additionalGrants\":[{\"client\":\"app-a\",\"key\":\"Offline Token\"}]},"
                    + "{\"clientId\":\"app-b\",\"grantedClientScopes\":[\"profile\"]},"
                    + "{\"clientId\":\"app-c\",\"additionalGrants\":[{\"client\":\"app-c\",\"key\":\"Offline Token\"}]}]";
        } else if (path.endsWith("/sessions") && ex.getRequestMethod().equals("GET")) {
            status = sessionsStatus;
            body = "[{\"id\":\"keep\"},{\"id\":\"other1\"},{\"id\":\"other2\"}]";
        } else if (ex.getRequestMethod().equals("DELETE")) {
            deleted.add(path.substring(path.lastIndexOf('/') + 1));
            status = 204;
            body = "";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    @Test
    void endsAllSessionsButTheCurrentOne() {
        client.logoutOtherSessions(keycloakId, "keep");
        assertThat(deleted).containsExactlyInAnyOrder("other1", "other2");
    }

    @Test
    void missingSidEndsNothing() {
        client.logoutOtherSessions(keycloakId, null);
        assertThat(deleted).isEmpty();
    }

    @Test
    void keycloakFailureIsSwallowed() {
        sessionsStatus = 500;
        client.logoutOtherSessions(keycloakId, "keep");
        assertThat(deleted).isEmpty();
    }

    @Test
    void revokesOfflineTokensOfClientsThatHoldOne() {
        client.revokeOfflineTokens(keycloakId);
        assertThat(deleted).containsExactlyInAnyOrder("app-a", "app-c");
    }

    @Test
    void offlineRevokeFailureIsSwallowed() {
        consentsStatus = 500;
        client.revokeOfflineTokens(keycloakId);
        assertThat(deleted).isEmpty();
    }

    @Test
    void passwordChangeEndsOtherSessionsAndRevokesOfflineTokens() {
        client.changePassword("a@test.local", "old", keycloakId, "new", "keep");
        assertThat(deleted).containsExactlyInAnyOrder("other1", "other2", "app-a", "app-c");
    }

    @Test
    void passwordChangeSucceedsEvenWhenTheOfflineRevokeFails() {
        consentsStatus = 500;
        client.changePassword("a@test.local", "old", keycloakId, "new", "keep");
        assertThat(deleted).containsExactlyInAnyOrder("other1", "other2");
    }
}
