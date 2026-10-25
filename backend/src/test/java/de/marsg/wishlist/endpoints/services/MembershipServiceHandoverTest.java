package de.marsg.wishlist.endpoints.services;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Account deletion hands creator and admin on: another admin first, else the oldest member. */
@ExtendWith(MockitoExtension.class)
class MembershipServiceHandoverTest {

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
    @Mock
    private ClaimService claimService;

    private MembershipService membershipService;

    private final UUID leaver = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private GroupEntity group;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(membershipRepository, inviteRepository,
                shareRepository, userRepository, groupService, accessService, claimService);
        group = mock(GroupEntity.class);
        lenient().when(group.getId()).thenReturn(groupId);
        lenient().when(group.getUsersId()).thenReturn(leaver);
        lenient().when(membershipRepository.findGroupForUpdate(groupId)).thenReturn(Optional.of(group));
    }

    private GroupMembershipEntity member(UUID id, UUID userId, boolean admin) {
        GroupMembershipEntity m = mock(GroupMembershipEntity.class);
        lenient().when(m.getId()).thenReturn(id);
        lenient().when(m.getGroupsId()).thenReturn(groupId);
        lenient().when(m.getUsersId()).thenReturn(userId);
        lenient().when(m.isAdmin()).thenReturn(admin);
        return m;
    }

    private UUID idAt(long millis) {
        return new UUID(millis << 16, 1L);
    }

    @Test
    void creatorHandsOverToAnotherAdminEvenIfOlderMemberExists() {
        UUID oldUser = UUID.randomUUID();
        UUID adminUser = UUID.randomUUID();
        GroupMembershipEntity own = member(idAt(1), leaver, true);
        GroupMembershipEntity oldest = member(idAt(2), oldUser, false);
        GroupMembershipEntity admin = member(idAt(3), adminUser, true);
        org.mockito.Mockito.when(membershipRepository.findByUsersId(leaver)).thenReturn(List.of(own));
        org.mockito.Mockito.when(membershipRepository.findByGroupsId(groupId))
                .thenReturn(List.of(own, oldest, admin));

        membershipService.removeAllMembershipsOf(leaver);

        verify(group).transferCreatorTo(adminUser);
        verify(oldest, never()).setAdmin(true);
        verify(membershipRepository).delete(own);
    }

    @Test
    void lastAdminCreatorHandsOverToOldestMember() {
        UUID oldUser = UUID.randomUUID();
        UUID youngUser = UUID.randomUUID();
        GroupMembershipEntity own = member(idAt(1), leaver, true);
        GroupMembershipEntity oldest = member(idAt(2), oldUser, false);
        GroupMembershipEntity youngest = member(idAt(9), youngUser, false);
        org.mockito.Mockito.when(membershipRepository.findByUsersId(leaver)).thenReturn(List.of(own));
        org.mockito.Mockito.when(membershipRepository.findByGroupsId(groupId))
                .thenReturn(List.of(youngest, own, oldest));

        membershipService.removeAllMembershipsOf(leaver);

        verify(oldest).setAdmin(true);
        verify(youngest, never()).setAdmin(true);
        verify(group).transferCreatorTo(oldUser);
    }

    @Test
    void lastAdminWhoIsNotCreatorPromotesButKeepsCreator() {
        UUID creator = UUID.randomUUID();
        lenient().when(group.getUsersId()).thenReturn(creator);
        GroupMembershipEntity own = member(idAt(5), leaver, true);
        GroupMembershipEntity other = member(idAt(2), creator, false);
        org.mockito.Mockito.when(membershipRepository.findByUsersId(leaver)).thenReturn(List.of(own));
        org.mockito.Mockito.when(membershipRepository.findByGroupsId(groupId))
                .thenReturn(List.of(own, other));

        membershipService.removeAllMembershipsOf(leaver);

        verify(other).setAdmin(true);
        verify(group, never()).transferCreatorTo(creator);
    }

    @Test
    void groupWithoutRemainingMembersIsSoftDeleted() {
        GroupMembershipEntity own = member(idAt(1), leaver, true);
        org.mockito.Mockito.when(membershipRepository.findByUsersId(leaver)).thenReturn(List.of(own));
        org.mockito.Mockito.when(membershipRepository.findByGroupsId(groupId)).thenReturn(List.of(own));

        membershipService.removeAllMembershipsOf(leaver);

        verify(group).markDeleted();
        verify(membershipRepository).delete(own);
    }
}
