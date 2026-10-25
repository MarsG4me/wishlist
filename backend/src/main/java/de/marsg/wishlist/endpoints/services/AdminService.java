package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminStatsDTO;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminUserDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
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
    private final UserService userService;
    private final UserExportService exportService;
    private final AdminAuditService auditService;

    public AdminService(UserRepository userRepository, WishlistRepository wishlistRepository,
            WishRepository wishRepository, UserService userService, UserExportService exportService,
            AdminAuditService auditService) {
        this.auditService = auditService;
        this.userService = userService;
        this.exportService = exportService;
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
    @Transactional
    public ReturnListDTO<ReturnAdminUserDTO> searchByEmail(UUID adminId, String email,
            boolean includeDeleted, Integer limit, Integer offset) {
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.ASC, "email"));
        Page<UserEntity> page = includeDeleted
                ? userRepository.searchByEmailIncludingDeleted(
                        "%" + email.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", pageable)
                : userRepository.findByEmailContainingIgnoreCase(email, pageable);
        ReturnListDTO<ReturnAdminUserDTO> result = ReturnListDTO.of(page, ReturnAdminUserDTO::from);
        // the raw term is personal data and stays out of the log
        log.info("Admin {} searched accounts (term length {}, include_deleted {}): {} match(es)",
                adminId, email.length(), includeDeleted, result.total());
        auditService.record(adminId, AdminAuditAction.USER_SEARCH, null, Map.of(
                "term_length", email.length(), "include_deleted", includeDeleted, "matches", result.total()));
        return result;
    }

    /** Export of any account, soft-deleted ones included; the access is audited. */
    @Transactional
    public ReturnUserExportDTO exportUser(UUID adminId, UUID targetId) {
        log.info("Admin {} exported data of user {}", adminId, targetId);
        ReturnUserExportDTO export = exportService.export(targetId);
        auditService.record(adminId, AdminAuditAction.USER_EXPORT, targetId, null);
        return export;
    }

    /** Soft delete like the self-service one, or immediate erasure; UserService audits inside its transactions. */
    public void deleteUser(UUID adminId, UUID targetId, boolean immediate) {
        log.info("Admin {} deletes user {} (immediate {})", adminId, targetId, immediate);
        if (immediate) {
            userService.eraseUserNow(adminId, targetId);
        } else {
            userService.softDeleteUserAudited(adminId, targetId);
        }
    }
}
