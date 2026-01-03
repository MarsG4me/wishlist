package de.marsg.wishlist.wishlist.request.dto.groups;

import java.util.List;

public record ReturnGroupListDTO(
    long totalGroups,
    int offset,
    int returnedGroups,
    List<ReturnSimpleGroupDTO> groups
) {

}
