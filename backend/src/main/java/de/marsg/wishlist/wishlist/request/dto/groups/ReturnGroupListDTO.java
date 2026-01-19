package de.marsg.wishlist.wishlist.request.dto.groups;

import java.util.List;

public record ReturnGroupListDTO(
    long total,
    int offset,
    int count,
    List<ReturnSimpleGroupDTO> groups
) {

}
