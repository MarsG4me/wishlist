package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The cheap pre-checks run before link safety: owner passes, a viewer is told why, a stranger learns nothing. */
class WishServiceAccessTest {

    private final WishRepository wishRepository = mock(WishRepository.class);
    private final WishlistService wishlistService = mock(WishlistService.class);
    private final GroupWishlistShareRepository shareRepository = mock(GroupWishlistShareRepository.class);
    private WishService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID wishlistId = UUID.randomUUID();
    private final UUID wishId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AccessService access = new AccessService(mock(GroupMembershipRepository.class), shareRepository);
        service = new WishService(wishRepository, null, null, wishlistService, access, null, null, null);
        WishlistEntity wishlist = mock(WishlistEntity.class);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(wishlist.getUsersId()).thenReturn(owner);
        when(wishlistService.requireWishlist(wishlistId)).thenReturn(wishlist);
        WishEntity wish = mock(WishEntity.class);
        when(wish.getWishlistsId()).thenReturn(wishlistId);
        when(wishRepository.findById(wishId)).thenReturn(Optional.of(wish));
        when(shareRepository.existsShareGivingUserAccess(viewer, wishlistId)).thenReturn(true);
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void ownerMayCreateAndEdit() {
        assertThatCode(() -> service.assertMayCreate(owner, wishlistId)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertMayEdit(owner, wishId)).doesNotThrowAnyException();
    }

    @Test
    void sharedViewerIsToldTheyAreNotTheOwner() {
        assertCode(() -> service.assertMayCreate(viewer, wishlistId), ErrorCode.NOT_WISHLIST_OWNER);
        assertCode(() -> service.assertMayEdit(viewer, wishId), ErrorCode.NOT_WISHLIST_OWNER);
    }

    @Test
    void strangerGetsNotFound() {
        assertCode(() -> service.assertMayCreate(stranger, wishlistId), ErrorCode.WISHLIST_NOT_FOUND);
        assertCode(() -> service.assertMayEdit(stranger, wishId), ErrorCode.WISH_NOT_FOUND);
    }
}
