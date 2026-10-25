package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private LimitService limitService;

    private final AccessService accessService = mock(AccessService.class);
    private WishlistService wishlistService;

    private final UUID requester = UUID.randomUUID();
    private final UUID wishlistId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(wishlistRepository, accessService, limitService);
    }

    @Test
    void renameByNonViewerAnswersLikeMissingWishlist() {
        WishlistEntity wishlist = mock(WishlistEntity.class);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.WISHLIST_NOT_FOUND))
                .when(accessService).assertCanViewWishlist(requester, wishlist);

        assertThatThrownBy(() -> wishlistService.rename(requester, wishlistId, "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WISHLIST_NOT_FOUND));
    }
}
