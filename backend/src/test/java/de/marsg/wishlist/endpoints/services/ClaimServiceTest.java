package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.ClaimEntity;
import de.marsg.wishlist.jpa.entities.ClaimParticipantEntity;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.ClaimParticipantRepository;
import de.marsg.wishlist.jpa.repositories.ClaimRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimParticipantRepository participantRepository;
    @Mock
    private WishRepository wishRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessService accessService;

    private ClaimService claimService;

    private final UUID requester = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID wishId = UUID.randomUUID();
    private final UUID wishlistId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(claimRepository, participantRepository, wishRepository,
                wishlistRepository, userRepository, accessService);
    }

    private WishEntity mockWish(Integer quantity) {
        WishEntity wish = mock(WishEntity.class);
        when(wish.getWishlistsId()).thenReturn(wishlistId);
        if (quantity != null) {
            when(wish.getQuantity()).thenReturn(quantity);
        }
        when(wishRepository.findByIdForUpdate(wishId)).thenReturn(Optional.of(wish));
        return wish;
    }

    private WishlistEntity mockWishlist(UUID owner) {
        WishlistEntity wishlist = mock(WishlistEntity.class);
        when(wishlist.getUsersId()).thenReturn(owner);
        when(wishlistRepository.findById(wishlistId)).thenReturn(Optional.of(wishlist));
        return wishlist;
    }

    @Test
    void ownerCannotClaimOwnWish() {
        mockWish(null);
        mockWishlist(requester);
        assertThatThrownBy(() -> claimService.create(requester, wishId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANNOT_CLAIM_OWN_WISH));
    }

    @Test
    void unsharedWishIsHidden() {
        mockWish(null);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(false);
        assertThatThrownBy(() -> claimService.create(requester, wishId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WISH_NOT_FOUND));
    }

    @Test
    void secondParticipationOnSameWishBlocked() {
        mockWish(null);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(true);
        when(participantRepository.existsByWishesIdAndIdUsersId(wishId, requester)).thenReturn(true);
        assertThatThrownBy(() -> claimService.create(requester, wishId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CLAIMED));
    }

    @Test
    void capacityFullBlocksNewClaim() {
        mockWish(1);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(true);
        when(participantRepository.existsByWishesIdAndIdUsersId(wishId, requester)).thenReturn(false);
        when(claimRepository.countByWishesId(wishId)).thenReturn(1L);
        assertThatThrownBy(() -> claimService.create(requester, wishId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WISH_FULLY_CLAIMED));
        verify(claimRepository, never()).save(any());
    }

    @Test
    void successfulClaimSavesClaimAndParticipant() {
        mockWish(2);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(true);
        when(participantRepository.existsByWishesIdAndIdUsersId(wishId, requester)).thenReturn(false);
        when(claimRepository.countByWishesId(wishId)).thenReturn(1L);
        ClaimEntity saved = mock(ClaimEntity.class);
        UUID claimId = UUID.randomUUID();
        when(saved.getId()).thenReturn(claimId);
        when(claimRepository.save(any())).thenReturn(saved);
        when(participantRepository.findByIdClaimsId(claimId)).thenReturn(List.of());
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        claimService.create(requester, wishId);

        verify(claimRepository).save(any(ClaimEntity.class));
        verify(participantRepository).save(any(ClaimParticipantEntity.class));
    }

    @Test
    void joiningAPrivateClaimIsBlocked() {
        UUID claimId = UUID.randomUUID();
        ClaimEntity claim = new ClaimEntity(wishId, ownerId);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        mockWish(null);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(true);
        when(participantRepository.existsByWishesIdAndIdUsersId(wishId, requester)).thenReturn(false);

        assertThatThrownBy(() -> claimService.join(requester, claimId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_NOT_SHARED));
        verify(participantRepository, never()).save(any());
    }

    @Test
    void joiningASharedClaimAddsTheParticipant() {
        UUID claimId = UUID.randomUUID();
        ClaimEntity claim = new ClaimEntity(wishId, ownerId);
        claim.setShared(true);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        mockWish(null);
        WishlistEntity wishlist = mockWishlist(ownerId);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(accessService.canViewAsSharedWishlist(requester, wishlistId)).thenReturn(true);
        when(participantRepository.existsByWishesIdAndIdUsersId(wishId, requester)).thenReturn(false);
        when(participantRepository.findByIdClaimsId(claim.getId())).thenReturn(List.of());
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        claimService.join(requester, claimId);

        verify(participantRepository).save(any(ClaimParticipantEntity.class));
    }

    @Test
    void onlyTheClaimCreatorMayShareIt() {
        UUID claimId = UUID.randomUUID();
        ClaimEntity claim = new ClaimEntity(wishId, ownerId);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.setShared(requester, claimId, true))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_CLAIM_OWNER));
        assertThat(claim.isShared()).isFalse();
    }

    @Test
    void creatorTogglesSharing() {
        UUID claimId = UUID.randomUUID();
        ClaimEntity claim = new ClaimEntity(wishId, requester);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(participantRepository.findByIdClaimsId(claim.getId())).thenReturn(List.of());
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        assertThat(claimService.setShared(requester, claimId, true).isShared()).isTrue();
        assertThat(claim.isShared()).isTrue();
    }

    @Test
    void leavingAsLastParticipantDeletesClaim() {
        UUID claimId = UUID.randomUUID();
        ClaimParticipantEntity participant = new ClaimParticipantEntity(claimId, requester, wishId);
        when(participantRepository.findByIdClaimsId(claimId)).thenReturn(List.of(participant));
        when(participantRepository.countByIdClaimsId(claimId)).thenReturn(0L);

        claimService.leave(requester, claimId);

        verify(participantRepository).delete(participant);
        verify(claimRepository).deleteById(claimId);
    }

    @Test
    void leavingWithRemainingParticipantsKeepsClaim() {
        UUID claimId = UUID.randomUUID();
        ClaimParticipantEntity participant = new ClaimParticipantEntity(claimId, requester, wishId);
        when(participantRepository.findByIdClaimsId(claimId)).thenReturn(List.of(participant));
        when(participantRepository.countByIdClaimsId(claimId)).thenReturn(1L);

        claimService.leave(requester, claimId);

        verify(claimRepository, never()).deleteById(claimId);
    }
}
