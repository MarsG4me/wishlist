package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminStatsDTO;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminUserDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

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

    /** Substring search reaches every account, so who looked for what is audited. */
    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnAdminUserDTO> searchByEmail(UUID adminId, String email, Integer limit,
            Integer offset) {
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.ASC, "email"));
        ReturnListDTO<ReturnAdminUserDTO> result = ReturnListDTO.of(
                userRepository.findByEmailContainingIgnoreCase(email, pageable), ReturnAdminUserDTO::from);
        log.info("Admin {} searched accounts for \"{}\": {} match(es)", adminId, email, result.total());
        return result;
    }
}
