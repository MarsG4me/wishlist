package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceJoinTest {

    @Mock
    private GroupMembershipRepository membershipRepository;
    @Mock
    private GroupInviteRepository inviteRepository;
    @Mock
    private GroupWishlistShareRepository shareRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupService groupService;
    @Mock
    private AccessService accessService;

    private MembershipService membershipService;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID inviteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(membershipRepository, inviteRepository,
                shareRepository, userRepository, groupService, accessService);
    }

    private GroupInviteEntity mockInvite(boolean revoked, boolean expired) {
        GroupInviteEntity invite = mock(GroupInviteEntity.class);
        when(invite.isRevoked()).thenReturn(revoked);
        if (!revoked) {
            when(invite.isExpired()).thenReturn(expired);
        }
        when(inviteRepository.findByCode("code")).thenReturn(Optional.of(invite));
        return invite;
    }

    @Test
    void unknownCodeRejected() {
        when(inviteRepository.findByCode("code")).thenReturn(Optional.empty());
        assertJoinFails(ErrorCode.INVITE_NOT_FOUND);
    }

    @Test
    void revokedInviteRejected() {
        mockInvite(true, false);
        assertJoinFails(ErrorCode.INVITE_REVOKED);
    }

    @Test
    void expiredInviteRejected() {
        mockInvite(false, true);
        assertJoinFails(ErrorCode.INVITE_EXPIRED);
    }

    @Test
    void existingMemberRejected() {
        GroupInviteEntity invite = mockInvite(false, false);
        when(invite.getGroupsId()).thenReturn(groupId);
        GroupEntity group = mock(GroupEntity.class);
        when(group.getId()).thenReturn(groupId);
        when(groupService.requireGroup(groupId)).thenReturn(group);
        when(membershipRepository.existsByGroupsIdAndUsersId(groupId, userId)).thenReturn(true);
        assertJoinFails(ErrorCode.ALREADY_MEMBER);
    }

    @Test
    void exhaustedInviteRejectedByAtomicConsume() {
        GroupInviteEntity invite = mockInvite(false, false);
        when(invite.getGroupsId()).thenReturn(groupId);
        when(invite.getId()).thenReturn(inviteId);
        GroupEntity group = mock(GroupEntity.class);
        when(group.getId()).thenReturn(groupId);
        when(groupService.requireGroup(groupId)).thenReturn(group);
        when(membershipRepository.existsByGroupsIdAndUsersId(groupId, userId)).thenReturn(false);
        when(inviteRepository.consumeUse(inviteId)).thenReturn(0);
        assertJoinFails(ErrorCode.INVITE_EXHAUSTED);
    }

    @Test
    void inviteEntityStateChecks() {
        GroupInviteEntity fresh = new GroupInviteEntity(groupId, userId, "c",
                Instant.now().plusSeconds(60), 2);
        assertThat(fresh.isExpired()).isFalse();
        assertThat(fresh.isRevoked()).isFalse();
        assertThat(fresh.isExhausted()).isFalse();

        GroupInviteEntity expired = new GroupInviteEntity(groupId, userId, "c",
                Instant.now().minusSeconds(60), null);
        assertThat(expired.isExpired()).isTrue();
        assertThat(expired.isExhausted()).isFalse();

        fresh.revoke();
        assertThat(fresh.isRevoked()).isTrue();
    }

    private void assertJoinFails(ErrorCode expected) {
        assertThatThrownBy(() -> membershipService.join(userId, "code"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
