package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import de.marsg.wishlist.wishlist.data.jpa.entity.Group;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    
    List<Group> findAllByOwner(User owner);

}
