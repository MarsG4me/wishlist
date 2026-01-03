package de.marsg.wishlist.wishlist.request.dto.memberships;

import java.util.List;

import de.marsg.wishlist.wishlist.request.dto.users.GroupUserReturnDTO;


public record ReturnGroupMembersDTO(
    long totalGroups,
    int offset,
    int returnedGroups,
    List<GroupUserReturnDTO> members
) {

}
