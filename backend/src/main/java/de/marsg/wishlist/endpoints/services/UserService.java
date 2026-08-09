package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;
    private final KeycloakAccountClient keycloakAccountClient;

    public UserService(UserRepository userRepository, GroupMembershipRepository membershipRepository,
            KeycloakAccountClient keycloakAccountClient) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.keycloakAccountClient = keycloakAccountClient;
    }

    @Transactional(readOnly = true)
    public ReturnOwnUserDTO getOwnUser(UUID userId) {
        return ReturnOwnUserDTO.from(requireUser(userId));
    }

    @Transactional
    public ReturnOwnUserDTO updateDisplayname(UUID userId, String displayname) {
        UserEntity user = requireUser(userId);
        user.setDisplayname(displayname);
        return ReturnOwnUserDTO.from(user);
    }

    /** Keycloak first; the DB copy is only written when Keycloak accepted the change. */
    @Transactional
    public ReturnOwnUserDTO updateEmail(UUID userId, String bearerToken, String newEmail) {
        UserEntity user = requireUser(userId);
        keycloakAccountClient.updateEmail(bearerToken, newEmail);
        user.setEmail(newEmail);
        log.info("Updated email of user {}", userId);
        return ReturnOwnUserDTO.from(user);
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
        log.info("Soft-deleted user {}", userId);
    }

    /** Other users are only visible when both share at least one group. */
    @Transactional(readOnly = true)
    public ReturnSimpleUserDTO getOtherUser(UUID requesterId, UUID targetId) {
        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!requesterId.equals(targetId)
                && !membershipRepository.existsGroupWithBothUsers(requesterId, targetId)) {
            throw new ApiException(ErrorCode.USER_HAS_NO_COMMON_GROUP);
        }
        return ReturnSimpleUserDTO.from(target);
    }

    @Transactional
    public ReturnOwnUserDTO setTier(UUID targetId, UserTier tier) {
        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        target.setTier(tier);
        log.info("Set tier of user {} to {}", targetId, tier);
        return ReturnOwnUserDTO.from(target);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
