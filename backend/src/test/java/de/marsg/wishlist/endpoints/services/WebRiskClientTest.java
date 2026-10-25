package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.jpa.repositories.WebRiskUsageRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The budget gate in front of the HTTP call: no key, no budget, no call, no verdict. */
class WebRiskClientTest {

    private final WebRiskUsageRepository usage = mock(WebRiskUsageRepository.class);

    private WebRiskClient client(String key, int limit) {
        return new WebRiskClient(key, limit, usage, JsonMapper.builder().build());
    }

    @Test
    void withoutKeyNothingIsCountedOrAsked() {
        WebRiskClient client = client("", 100);

        assertThat(client.enabled()).isFalse();
        assertThat(client.isUnsafe("a.example")).isNull();
        verify(usage, never()).tryReserve(anyInt());
    }

    @Test
    void spentBudgetMeansNoVerdict() {
        when(usage.tryReserve(5)).thenReturn(0);

        assertThat(client("key", 5).isUnsafe("a.example")).isNull();
    }

    @Test
    void zeroLimitNeverReserves() {
        assertThat(client("key", 0).isUnsafe("a.example")).isNull();
        verify(usage, never()).tryReserve(anyInt());
    }

    @Test
    void remainingBudgetNeverGoesNegative() {
        when(usage.findCallsThisMonth()).thenReturn(Optional.of(120));

        assertThat(client("key", 100).remainingBudget()).isZero();
        when(usage.findCallsThisMonth()).thenReturn(Optional.empty());
        assertThat(client("key", 100).remainingBudget()).isEqualTo(100);
    }
}
