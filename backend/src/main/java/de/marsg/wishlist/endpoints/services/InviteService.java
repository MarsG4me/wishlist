package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.invites.ReturnInviteDTO;
import de.marsg.wishlist.endpoints.dto.invites.ReturnInvitePreviewDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite codes: 128-bit random, base64url (22 chars) — unguessable but short enough
 * for links and QR codes. Valid 24h, optional use limit. Invites are join history:
 * never deleted, revocation only sets revoked_at.
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);
    private static final Duration VALIDITY = Duration.ofHours(24);
    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GroupInviteRepository inviteRepository;
    private final GroupService groupService;
    private final AccessService accessService;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public InviteService(GroupInviteRepository inviteRepository, GroupService groupService,
            AccessService accessService, GroupMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.groupService = groupService;
        this.accessService = accessService;
    }

    @Transactional
    public ReturnInviteDTO create(UUID userId, UUID groupId, Integer maxUses) {
        GroupEntity group = groupService.requireGroup(groupId);
        accessService.assertGroupAdminOrHide(userId, group);
        GroupInviteEntity invite = inviteRepository.save(new GroupInviteEntity(
                groupId, userId, generateCode(), Instant.now().plus(VALIDITY), maxUses));
        log.info("User {} created invite {} for group {}", userId, invite.getId(), groupId);
        return ReturnInviteDTO.from(invite);
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnInviteDTO> list(UUID userId, UUID groupId, Integer limit, Integer offset) {
        GroupEntity group = groupService.requireGroup(groupId);
        accessService.assertGroupAdminOrHide(userId, group);
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id"));
        return ReturnListDTO.of(inviteRepository.findByGroupsId(groupId, pageable), ReturnInviteDTO::from);
    }

    @Transactional
    public void revoke(UUID userId, UUID groupId, UUID inviteId) {
        GroupEntity group = groupService.requireGroup(groupId);
        accessService.assertGroupAdminOrHide(userId, group);
        GroupInviteEntity invite = inviteRepository.findById(inviteId)
                .filter(found -> found.getGroupsId().equals(groupId))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        invite.revoke();
        log.info("User {} revoked invite {}", userId, inviteId);
    }

    /** Unknown, malformed or deleted-group invites are all INVITE_NOT_FOUND; keycloakId may be null. */
    @Transactional(readOnly = true)
    public ReturnInvitePreviewDTO preview(String code, UUID keycloakId) {
        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            throw new ApiException(ErrorCode.INVITE_NOT_FOUND);
        }
        GroupInviteEntity invite = inviteRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        GroupEntity group = groupService.findGroup(invite.getGroupsId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        String status = statusOf(invite);
        if (!"valid".equals(status)) {
            return new ReturnInvitePreviewDTO(null, null, null, null, status, null, null);
        }
        String creator = Optional.ofNullable(invite.getUsersId()).flatMap(userRepository::findById)
                .map(UserEntity::getDisplayname).orElse(null);
        Boolean member = null;
        UUID groupId = null;
        if (keycloakId != null) {
            Optional<UserEntity> viewer = userRepository.findByKeycloakId(keycloakId);
            member = viewer.isPresent()
                    && membershipRepository.existsByGroupsIdAndUsersId(group.getId(), viewer.get().getId());
            groupId = member ? group.getId() : null;
        }
        return new ReturnInvitePreviewDTO(group.getName(), creator,
                membershipRepository.countByGroupsId(group.getId()), invite.getExpiresAt(),
                status, member, groupId);
    }

    private static String statusOf(GroupInviteEntity invite) {
        if (invite.isRevoked()) {
            return "revoked";
        }
        if (invite.isExpired()) {
            return "expired";
        }
        return invite.isExhausted() ? "exhausted" : "valid";
    }

    private String generateCode() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
