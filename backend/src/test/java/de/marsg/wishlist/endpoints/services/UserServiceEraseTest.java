package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Immediate erasure runs soft delete, Keycloak and row delete as separate steps; later failures keep the earlier ones. */
class UserServiceEraseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final KeycloakAccountClient keycloak = mock(KeycloakAccountClient.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final WishRepository wishRepository = mock(WishRepository.class);
    private final GroupMembershipRepository membershipRepository = mock(GroupMembershipRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private UserService service;
    private UserEntity user;
    private final UUID userId = UUID.randomUUID();
    private final UUID keycloakId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, membershipRepository, mock(GroupWishlistShareRepository.class),
                keycloak, mock(ClaimService.class), membershipService, wishlistRepository, wishRepository, audit,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        user = new UserEntity(keycloakId, "Alice", "alice@test.local", UserLocale.EN);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findByIdIncludingDeleted(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void allStepsRunInOrder() {
        service.eraseUserNow(adminId, userId);
        verify(audit).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, Map.of("step", "soft"));
        verify(audit).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, null);

        assertThat(user.isDeleted()).isTrue();
        var order = org.mockito.Mockito.inOrder(membershipService, keycloak, userRepository);
        order.verify(membershipService).removeAllMembershipsOf(userId);
        order.verify(keycloak).deleteUser(keycloakId);
        order.verify(userRepository).deleteHardById(userId);
    }

    @Test
    void keycloakFailureKeepsSoftDeleteAndRow() {
        doThrow(new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED)).when(keycloak).deleteUser(keycloakId);

        assertThatThrownBy(() -> service.eraseUserNow(adminId, userId)).isInstanceOf(ApiException.class);

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository, never()).deleteHardById(any());
        verify(audit).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, Map.of("step", "soft"));
        verify(audit, never()).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, null);
    }

    @Test
    void rowDeleteFailureHappensAfterKeycloak() {
        when(userRepository.deleteHardById(userId)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.eraseUserNow(adminId, userId)).isInstanceOf(IllegalStateException.class);

        verify(audit).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, Map.of("step", "soft"));
        verify(keycloak).deleteUser(keycloakId);
        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    void alreadyDeletedUserOnlyReleasesTiesAgain() {
        user.markDeleted();
        var deletedAt = user.getDeletedAt();

        service.eraseUserNow(adminId, userId);

        assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
        verify(membershipService).removeAllMembershipsOf(userId);
        verify(userRepository).deleteHardById(userId);
        verify(audit, never()).record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, Map.of("step", "soft"));
    }

    @Test
    void unknownUserIsNotFound() {
        UUID other = UUID.randomUUID();
        when(userRepository.findByIdIncludingDeleted(other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eraseUserNow(adminId, other)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(keycloak, never()).deleteUser(any());
    }

    @Test
    void adminSoftDeleteIsAuditedAndUnknownUserIsNot() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.softDeleteUserAudited(adminId, userId);

        verify(audit).record(adminId, AdminAuditAction.USER_DELETE, userId, null);
        UUID other = UUID.randomUUID();
        when(userRepository.findById(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeleteUserAudited(adminId, other)).isInstanceOf(ApiException.class);
        verify(audit, never()).record(adminId, AdminAuditAction.USER_DELETE, other, null);
    }

    @Test
    void tierChangeIsAudited() {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.setTier(adminId, userId, UserTier.PREMIUM);

        verify(audit).record(adminId, AdminAuditAction.TIER_CHANGE, userId, Map.of("tier", "PREMIUM"));
    }

    @Test
    void onboardingCountsComeFromTheRepositories() {
        when(wishlistRepository.countByUsersId(userId)).thenReturn(2L);
        when(wishRepository.countInWishlistsOf(userId)).thenReturn(5L);
        when(membershipRepository.countInLiveGroups(userId)).thenReturn(1L);

        var dto = service.getOnboarding(userId);

        assertThat(dto.wishlistTotal()).isEqualTo(2);
        assertThat(dto.wishTotal()).isEqualTo(5);
        assertThat(dto.groupTotal()).isEqualTo(1);
    }
}
