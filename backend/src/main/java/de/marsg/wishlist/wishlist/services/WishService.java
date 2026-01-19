package de.marsg.wishlist.wishlist.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistSharingRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;

public class WishService {

    private final LogMgr log;
    private final WishRepository wishRepository;
    private final WishlistSharingRepository wishlistSharingRepository;

    public WishService(LogMgr log, WishRepository wishRepository,
        WishlistSharingRepository wishlistSharingRepository
    ) {
        this.log = log;
        this.wishRepository = wishRepository;
        this.wishlistSharingRepository = wishlistSharingRepository;
    }



    /*
        Helper functions
    */

    public boolean canUserSeeWish(UUID userId, UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }

        return wishlistSharingRepository.isUserInSharedWishlistGroup(wish.get().getWishlist().getId(), userId)
        || wishlistSharingRepository.isUserOwnerOfWishlist(wish.get().getWishlist().getId(), userId);
        
    }

    public boolean canWishBeClaimed(UUID wishId){
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }

        return !wish.get().isClaimed() || wish.get().isUnlimited();
    }

    public boolean isWishClaimed(UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }
        return wish.get().isClaimed();
    }

    public User getAllClaimersOfWish(UUID wishId){
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return null;
        }
        return wish.get().getClaimer();
    }

    public long countWishesInWishlist(Wishlist wishlist){
        return wishRepository.countAllByWishlist(wishlist);
    }

    public List<Wish> getAllWishesInWishlist(Wishlist wishlist){
        return wishRepository.findAllByWishlist(wishlist);
    }

    public boolean canDelete(Wish wish){
        return !wish.isClaimed();
    }

    public boolean delete(Wish wish){
        if (canDelete(wish)) {
            wishRepository.delete(wish);
            return true;
        }
        return false;
    }
}
