package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupWishlistShareRepository shareRepository;
    private final KeycloakAccountClient keycloakAccountClient;
    private final ClaimService claimService;

    public UserService(UserRepository userRepository, GroupMembershipRepository membershipRepository,
            GroupWishlistShareRepository shareRepository, KeycloakAccountClient keycloakAccountClient,
            ClaimService claimService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.shareRepository = shareRepository;
        this.keycloakAccountClient = keycloakAccountClient;
        this.claimService = claimService;
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

    /** Keycloak first; the DB copy is only written when Keycloak accepted the change. */
    @Transactional
    public ReturnOwnUserDTO updateEmail(UUID userId, String bearerToken, String newEmail) {
        UserEntity user = requireUser(userId);
        // checked up front so a taken address fails with a code instead of a constraint violation
        userRepository.findByEmailIncludingDeleted(newEmail)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
                });
        keycloakAccountClient.updateEmail(bearerToken, newEmail);
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

    /** Verifies the current password, then resets via Keycloak Admin API. Nothing stored in our DB. */
    public void updatePassword(UUID userId, String currentPassword, String newPassword) {
        UserEntity user = requireUser(userId);
        keycloakAccountClient.changePassword(user.getEmail(), currentPassword, user.getKeycloakId(), newPassword);
    }

    @Transactional
    public void softDeleteOwnUser(UUID userId) {
        UserEntity user = requireUser(userId);
        user.markDeleted();
        // stop sharing immediately: a deleted account's wishlists disappear from every group
        shareRepository.deleteAll(shareRepository.findSharesOfUser(userId));
        // and stop gifting: the claims would otherwise block wishes with an invisible claimer
        claimService.removeAllParticipationsOf(userId);
        log.info("Soft-deleted user {}", userId);
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
    public ReturnOwnUserDTO setTier(UUID targetId, UserTier tier) {
        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        target.setTier(tier);
        log.info("Set tier of user {} to {}", targetId, tier);
        // admin response: the target's login method is none of the admin's business
        return ReturnOwnUserDTO.from(target, null);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
