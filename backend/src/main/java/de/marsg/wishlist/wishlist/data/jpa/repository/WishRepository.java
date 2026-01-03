package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;

@Repository
public interface WishRepository extends JpaRepository<Wish, UUID> {

    List<Wish> findAllByWishlist(Wishlist wishlist);

}
