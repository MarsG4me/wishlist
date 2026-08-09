package de.marsg.wishlist.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7FactoryTest {

    @Test
    void producesVersion7Variant2() {
        UUID id = UuidV7Factory.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentTimestamp() {
        Instant before = Instant.now().minusMillis(1);
        UUID id = UuidV7Factory.next();
        Instant embedded = UuidV7Factory.timestampOf(id);
        assertThat(embedded).isBetween(before, Instant.now().plusMillis(1));
    }

    @Test
    void idsFromDifferentMillisAreTimeOrdered() throws InterruptedException {
        UUID first = UuidV7Factory.next();
        Thread.sleep(Duration.ofMillis(2));
        UUID second = UuidV7Factory.next();
        assertThat(first.toString()).isLessThan(second.toString());
    }

    @Test
    void generatesUniqueIds() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7Factory.next())).isTrue();
        }
    }

    @Test
    void timestampOfRejectsNonV7() {
        assertThatThrownBy(() -> UuidV7Factory.timestampOf(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
