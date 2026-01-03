package de.marsg.wishlist.wishlist.request.dto.groups;

import java.util.UUID;

public record ReturnGroupDTO(
        UUID id,
        String name,
        String description,
        long memberCount) {

}
