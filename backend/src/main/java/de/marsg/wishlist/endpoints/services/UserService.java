package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.users.ReturnOnboardingDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.GroupWishlistShareEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupWishlistShareRepository shareRepository;
    private final KeycloakAccountClient keycloakAccountClient;
    private final ClaimService claimService;
    private final MembershipService membershipService;
    private final WishlistRepository wishlistRepository;
    private final WishRepository wishRepository;
    private final AdminAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public UserService(UserRepository userRepository, GroupMembershipRepository membershipRepository,
            GroupWishlistShareRepository shareRepository, KeycloakAccountClient keycloakAccountClient,
            ClaimService claimService, MembershipService membershipService,
            WishlistRepository wishlistRepository, WishRepository wishRepository,
            AdminAuditService auditService, TransactionTemplate transactionTemplate) {
        this.wishlistRepository = wishlistRepository;
        this.wishRepository = wishRepository;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.shareRepository = shareRepository;
        this.keycloakAccountClient = keycloakAccountClient;
        this.claimService = claimService;
        this.membershipService = membershipService;
    }

    @Transactional(readOnly = true)
    public ReturnOwnUserDTO getOwnUser(UUID userId, String identityProvider) {
        return ReturnOwnUserDTO.from(requireUser(userId), identityProvider);
    }

    @Transactional
    public ReturnOwnUserDTO updateDisplayname(UUID userId, String identityProvider, String displayname) {
        UserEntity user = requireUser(userId);
        user.setDisplayname(displayname);
        return ReturnOwnUserDTO.from(user, identityProvider);
    }

    /** Password check, then Keycloak; the DB copy is only written when Keycloak accepted the change. */
    @Transactional
    public ReturnOwnUserDTO updateEmail(UUID userId, String currentPassword, String newEmail) {
        UserEntity user = requireUser(userId);
        keycloakAccountClient.verifyCurrentPassword(user.getEmail(), currentPassword);
        // checked up front so a taken address fails with a code instead of a constraint violation
        userRepository.findByEmailIncludingDeleted(newEmail)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
                });
        keycloakAccountClient.updateEmail(user.getKeycloakId(), newEmail);
        user.setEmail(newEmail);
        try {
            // flushed here on purpose: the check above is not atomic, and a violation
            // surfacing at commit time would leave the caller with a bare 500
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        log.info("Updated email of user {}", userId);
        return ReturnOwnUserDTO.from(user, null);
    }

    /**
     * The interface language belongs to this app, so our DB is the source of truth and the
     * change always succeeds. Keycloak only gets the value mirrored for its own login pages
     * and emails — a failure there is logged, never surfaced to the user.
     */
    @Transactional
    public ReturnOwnUserDTO updateLocale(UUID userId, String identityProvider, UserLocale locale) {
        UserEntity user = requireUser(userId);
        user.setLocale(locale);
        try {
            keycloakAccountClient.updateLocale(user.getKeycloakId(), locale.tag());
        } catch (ApiException e) {
            log.warn("Locale of user {} stored but not mirrored into Keycloak", userId);
        }
        log.info("Set locale of user {} to {}", userId, locale);
        return ReturnOwnUserDTO.from(user, identityProvider);
    }

    /** Verifies the current password, resets via Keycloak Admin API and ends the other sessions. Nothing stored in our DB. */
    public void updatePassword(UUID userId, String keepSessionId, String currentPassword, String newPassword) {
        UserEntity user = requireUser(userId);
        keycloakAccountClient.changePassword(user.getEmail(), currentPassword, user.getKeycloakId(), newPassword,
                keepSessionId);
    }

    /** Counts for the onboarding card; soft-deleted wishlists, wishes and groups do not count. */
    @Transactional(readOnly = true)
    public ReturnOnboardingDTO getOnboarding(UUID userId) {
        return new ReturnOnboardingDTO(wishlistRepository.countByUsersId(userId),
                wishRepository.countInWishlistsOf(userId), membershipRepository.countInLiveGroups(userId));
    }

    @Transactional
    public void softDeleteUser(UUID userId) {
        softDelete(requireUser(userId));
    }

    /** Admin soft delete; the audit row commits with the soft delete or not at all. */
    @Transactional
    public void softDeleteUserAudited(UUID adminId, UUID userId) {
        softDelete(requireUser(userId));
        auditService.record(adminId, AdminAuditAction.USER_DELETE, userId, null);
    }

    /**
     * Immediate erasure in three steps, deliberately not one transaction: the soft delete
     * commits first, then Keycloak, then the row. A failure leaves a soft-deleted user
     * and a retry (an already deleted user, Keycloak 404 = success) finishes the job.
     */
    public void eraseUserNow(UUID adminId, UUID userId) {
        UUID keycloakId = transactionTemplate.execute(status -> {
            UserEntity user = userRepository.findByIdIncludingDeleted(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
            if (user.isDeleted()) {
                releaseTies(userId);
            } else {
                softDelete(user);
                auditService.record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, Map.of("step", "soft"));
            }
            return user.getKeycloakId();
        });
        keycloakAccountClient.deleteUser(keycloakId);
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.deleteHardById(userId);
            auditService.record(adminId, AdminAuditAction.USER_DELETE_IMMEDIATE, userId, null);
        });
        log.info("Erased user {}", userId);
    }

    private void softDelete(UserEntity user) {
        UUID userId = user.getId();
        user.markDeleted();
        releaseTies(userId);
        log.info("Soft-deleted user {}", userId);
    }

    /** Leaves all groups, drops shares and claims of the account; idempotent, also for already deleted ones. */
    @Transactional
    public void releaseTies(UUID userId) {
        // leave every group first: shares, claims and creator/admin handover as for a manual leave
        membershipService.removeAllMembershipsOf(userId);
        // stop sharing immediately: a deleted account's wishlists disappear from every group
        List<GroupWishlistShareEntity> shares = shareRepository.findSharesOfUser(userId);
        List<UUID> ownWishlists = shares.stream().map(GroupWishlistShareEntity::getWishlistsId)
                .distinct().toList();
        shareRepository.deleteAll(shares);
        shareRepository.flush();
        // other members' claims on those wishlists lose their basis too
        claimService.purgeParticipationsWithoutAccess(ownWishlists);
        // and stop gifting: the claims would otherwise block wishes with an invisible claimer
        claimService.removeAllParticipationsOf(userId);
    }

    /**
     * Other users are only visible when both share at least one group. An id without a
     * common group is answered exactly like an unknown one, so the endpoint cannot be
     * used to test which user ids exist.
     */
    @Transactional(readOnly = true)
    public ReturnSimpleUserDTO getOtherUser(UUID requesterId, UUID targetId) {
        UserEntity target = userRepository.findById(targetId)
                .filter(found -> requesterId.equals(targetId)
                        || membershipRepository.existsGroupWithBothUsers(requesterId, targetId))
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return ReturnSimpleUserDTO.from(target);
    }

    @Transactional
    public ReturnOwnUserDTO setTier(UUID adminId, UUID targetId, UserTier tier) {
        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        target.setTier(tier);
        log.info("Set tier of user {} to {}", targetId, tier);
        auditService.record(adminId, AdminAuditAction.TIER_CHANGE, targetId, Map.of("tier", tier.name()));
        // admin response: the target's login method is none of the admin's business
        return ReturnOwnUserDTO.from(target, null);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
