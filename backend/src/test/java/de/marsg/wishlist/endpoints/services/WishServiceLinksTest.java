package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.endpoints.dto.common.ErrorResponseDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnOwnWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnSharedWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.WishLinkDTO;
import de.marsg.wishlist.endpoints.handler.GlobalExceptionHandler;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** link_index of the first failing link, and the blocked/hidden handling in owner and viewer DTOs. */
class WishServiceLinksTest {

    private final LinkSafetyService safety = mock(LinkSafetyService.class);
    private final WishService service =
            new WishService(null, null, null, null, null, null, new LinkUrlValidator(), safety);

    private static WishLinkDTO link(String url) {
        return new WishLinkDTO(url, null);
    }

    @Test
    void invalidUrlReportsItsIndexEvenWhenALaterLinkIsBlocked() {
        when(safety.findProblems(anyList())).thenReturn(Map.of(2, new ApiException(ErrorCode.LINK_BLOCKED).atLink(2)));

        assertThatThrownBy(() -> service.prepareLinks(
                List.of(link("https://ok.example/"), link("ftp://nope"), link("https://bad.example/"))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_LINK_URL);
                    assertThat(e.getLinkIndex()).isEqualTo(1);
                });
    }

    @Test
    void firstSafetyProblemWinsByIndex() {
        when(safety.findProblems(anyList())).thenReturn(Map.of(
                1, new ApiException(ErrorCode.LINK_SHORTENER_NOT_ALLOWED).atLink(1),
                2, new ApiException(ErrorCode.LINK_BLOCKED).atLink(2)));

        assertThatThrownBy(() -> service.prepareLinks(
                List.of(link("https://ok.example/"), link("https://bit.ly/x"), link("https://bad.example/"))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_SHORTENER_NOT_ALLOWED);
                    assertThat(e.getLinkIndex()).isEqualTo(1);
                });
    }

    @Test
    void cleanLinksBecomeEntitiesWithHostAndPosition() {
        when(safety.findProblems(anyList())).thenReturn(Map.of());

        List<WishLinkEntity> entities = service.prepareLinks(List.of(link(" https://Ok.Example/a "), link("https://b.example/")));

        assertThat(entities).extracting(WishLinkEntity::getHost).containsExactly("ok.example", "b.example");
        assertThat(entities).extracting(WishLinkEntity::getPosition).containsExactly((short) 0, (short) 1);
    }

    @Test
    void errorBodyCarriesLinkIndexOnlyWhenSet() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ErrorResponseDTO withIndex = handler.handleApiException(new ApiException(ErrorCode.LINK_BLOCKED).atLink(3)).getBody();
        ErrorResponseDTO without = handler.handleApiException(new ApiException(ErrorCode.WISH_NOT_FOUND)).getBody();

        assertThat(withIndex.errorCode()).isEqualTo("LINK_BLOCKED");
        assertThat(withIndex.linkIndex()).isEqualTo(3);
        assertThat(without.linkIndex()).isNull();
    }

    private static WishEntity wishWithLinks() {
        WishLinkEntity clean = new WishLinkEntity("https://ok.example/", "ok", (short) 0, "ok.example");
        WishLinkEntity blocked = new WishLinkEntity("https://bad.example/", "bad", (short) 1, "bad.example");
        ReflectionTestUtils.setField(clean, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(blocked, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(blocked, "blockedBy", LinkBlockSource.ADMIN);
        WishEntity wish = new WishEntity(UUID.randomUUID(), "Bricks", null, 1);
        wish.replaceLinks(List.of(clean, blocked));
        return wish;
    }

    @Test
    void ownerSeesBlockedLinksWithTheirSource() {
        ReturnOwnWishDTO dto = ReturnOwnWishDTO.from(wishWithLinks());

        assertThat(dto.links()).hasSize(2);
        assertThat(dto.links().get(0).blocked()).isFalse();
        assertThat(dto.links().get(1).blocked()).isTrue();
        assertThat(dto.links().get(1).blockedBy()).isEqualTo(LinkBlockSource.ADMIN);
    }

    @Test
    void viewersNeverSeeBlockedLinks() {
        ReturnSharedWishDTO dto = ReturnSharedWishDTO.from(wishWithLinks(), List.of());

        assertThat(dto.links()).extracting(l -> l.url()).containsExactly("https://ok.example/");
    }
}
