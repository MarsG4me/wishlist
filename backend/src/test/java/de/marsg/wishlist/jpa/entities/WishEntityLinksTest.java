package de.marsg.wishlist.jpa.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Editing a wish keeps the row and id of every link whose URL is resubmitted. */
class WishEntityLinksTest {

    private static WishLinkEntity link(String url, String label, int position, boolean persisted) {
        WishLinkEntity l = new WishLinkEntity(url, label, (short) position, "x.example");
        if (persisted) {
            ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
        }
        return l;
    }

    @Test
    void unchangedUrlsKeepTheirIdAndTakeNewLabelAndPosition() {
        WishLinkEntity a = link("https://x.example/a", "A", 0, true);
        WishLinkEntity b = link("https://x.example/b", "B", 1, true);
        WishEntity wish = new WishEntity(UUID.randomUUID(), "w", null, 1);
        wish.replaceLinks(List.of(a, b));

        wish.replaceLinks(List.of(link("https://x.example/b", "B2", 0, false), link("https://x.example/a", "A2", 1, false)));

        assertThat(wish.getLinks()).containsExactly(b, a);
        assertThat(b.getLabel()).isEqualTo("B2");
        assertThat(a.getPosition()).isEqualTo((short) 1);
    }

    @Test
    void removedLinksGoAndNewOnesAreAdded() {
        WishLinkEntity a = link("https://x.example/a", "A", 0, true);
        WishLinkEntity b = link("https://x.example/b", "B", 1, true);
        WishEntity wish = new WishEntity(UUID.randomUUID(), "w", null, 1);
        wish.replaceLinks(List.of(a, b));
        WishLinkEntity fresh = link("https://x.example/c", "C", 1, false);

        wish.replaceLinks(List.of(link("https://x.example/a", "A", 0, false), fresh));

        assertThat(wish.getLinks()).containsExactly(a, fresh);
    }

    @Test
    void duplicateUrlsPairUpInOrder() {
        WishLinkEntity first = link("https://x.example/a", "1", 0, true);
        WishLinkEntity second = link("https://x.example/a", "2", 1, true);
        WishEntity wish = new WishEntity(UUID.randomUUID(), "w", null, 1);
        wish.replaceLinks(List.of(first, second));

        wish.replaceLinks(List.of(link("https://x.example/a", "x", 0, false), link("https://x.example/a", "y", 1, false),
                link("https://x.example/a", "z", 2, false)));

        assertThat(wish.getLinks()).hasSize(3);
        assertThat(wish.getLinks().get(0)).isSameAs(first);
        assertThat(wish.getLinks().get(1)).isSameAs(second);
        assertThat(first.getLabel()).isEqualTo("x");
        assertThat(second.getLabel()).isEqualTo("y");
    }
}
