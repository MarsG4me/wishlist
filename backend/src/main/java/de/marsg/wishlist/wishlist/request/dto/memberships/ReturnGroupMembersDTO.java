package de.marsg.wishlist.wishlist.request.dto.memberships;

import java.util.List;

import de.marsg.wishlist.wishlist.request.dto.users.ReturnGroupUserDTO;


public record ReturnGroupMembersDTO(
    long total,
    int offset,
    int count,
    List<ReturnGroupUserDTO> members
) {

}
