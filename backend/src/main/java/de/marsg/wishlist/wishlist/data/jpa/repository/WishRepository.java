package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;
import jakarta.transaction.Transactional;

@Repository
public interface WishRepository extends JpaRepository<Wish, UUID> {

    List<Wish> findAllByWishlist(Wishlist wishlist);

    long countAllByWishlist(Wishlist wishlist);

    @Query(value = "SELECT w.* FROM wishes w WHERE w.claimer_id = :claimerId LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Wish> findAllByClaimerId(@Param("claimerId") UUID claimerId, @Param("limit") int limit,
            @Param("offset") int offset);

    default List<Wish> findAllByClaimerId(@Param("claimerId") UUID claimerId, @Param("offset") int offset) {
        return findAllByClaimerId(claimerId, 50, offset);
    }

    @Query(value = "SELECT COUNT(w.*) FROM wishes w WHERE w.claimer_id = :claimerId", nativeQuery = true)
    long countAllByClaimerId(@Param("claimerId") UUID claimerId);

    @Query(value = "SELECT w.* FROM wishes w WHERE w.claimer_id = :claimerId AND w.is_bought_by_claimer = :bought LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Wish> findAllByIsBoughtAndClaimer(@Param("bought") boolean bought, @Param("claimerId") UUID claimerId,
            @Param("limit") int limit, @Param("offset") int offset);

    default List<Wish> findAllByIsBoughtAndClaimer(@Param("bought") boolean bought, @Param("claimerId") UUID claimerId,
            @Param("offset") int offset) {
        return findAllByIsBoughtAndClaimer(bought, claimerId, 50, offset);
    }

    @Query(value = "SELECT COUNT(w.*) FROM wishes w WHERE w.claimer_id = :claimerId AND w.is_bought_by_claimer = :bought", nativeQuery = true)
    long countAllByIsBoughtAndClaimer(@Param("bought") boolean bought, @Param("claimerId") UUID claimerId);

    @Modifying
    @Transactional
    void deleteByWishlist(Wishlist wishlist);

}
