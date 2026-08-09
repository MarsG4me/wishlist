package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminStatsDTO;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminUserDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * App-admin functions: aggregate counts and account lookup for tier management.
 * Deliberately NO access to wishlist/wish/group content — counts and account data only.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final WishRepository wishRepository;

    public AdminService(UserRepository userRepository, WishlistRepository wishlistRepository,
            WishRepository wishRepository) {
        this.userRepository = userRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishRepository = wishRepository;
    }

    /** Counts exclude soft-deleted rows (soft-delete restriction applies to count queries). */
    @Transactional(readOnly = true)
    public ReturnAdminStatsDTO stats() {
        return new ReturnAdminStatsDTO(
                userRepository.count(),
                wishlistRepository.count(),
                wishRepository.count());
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnAdminUserDTO> searchByEmail(String email, Integer limit, Integer offset) {
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.ASC, "email"));
        return ReturnListDTO.of(userRepository.findByEmailContainingIgnoreCase(email, pageable),
                ReturnAdminUserDTO::from);
    }
}
