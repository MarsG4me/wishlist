package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private WishRepository wishRepository;

    private LimitService limitService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        limitService = new LimitService(groupRepository, wishlistRepository, wishRepository);
    }

    @Test
    void freeUserBlockedAtTenGroups() {
        when(groupRepository.countByUsersId(userId)).thenReturn(10L);
        assertThatThrownBy(() -> limitService.assertCanCreateGroup(userId, UserTier.FREE))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GROUP_LIMIT_REACHED));
    }

    @Test
    void premiumUserAllowedAtTenGroups() {
        when(groupRepository.countByUsersId(userId)).thenReturn(10L);
        assertThatCode(() -> limitService.assertCanCreateGroup(userId, UserTier.PREMIUM))
                .doesNotThrowAnyException();
    }

    @Test
    void premiumUserBlockedAtTwentyGroups() {
        when(groupRepository.countByUsersId(userId)).thenReturn(20L);
        assertThatThrownBy(() -> limitService.assertCanCreateGroup(userId, UserTier.PREMIUM))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void freeUserBlockedAtTenWishlists() {
        when(wishlistRepository.countByUsersId(userId)).thenReturn(10L);
        assertThatThrownBy(() -> limitService.assertCanCreateWishlist(userId, UserTier.FREE))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WISHLIST_LIMIT_REACHED));
    }

    @Test
    void freeUserBlockedAtHundredWishes() {
        UUID wishlistId = UUID.randomUUID();
        when(wishRepository.countByWishlistsId(wishlistId)).thenReturn(100L);
        assertThatThrownBy(() -> limitService.assertCanAddWish(wishlistId, UserTier.FREE))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WISH_LIMIT_REACHED));
    }

    @Test
    void belowLimitAlwaysAllowed() {
        when(wishlistRepository.countByUsersId(userId)).thenReturn(9L);
        assertThatCode(() -> limitService.assertCanCreateWishlist(userId, UserTier.FREE))
                .doesNotThrowAnyException();
    }
}
