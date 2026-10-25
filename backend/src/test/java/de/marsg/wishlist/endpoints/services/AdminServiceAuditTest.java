package de.marsg.wishlist.endpoints.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Every admin access to account data leaves an audit row, and the search row never holds the term. */
class AdminServiceAuditTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final UserExportService exportService = mock(UserExportService.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private AdminService service;
    private final UUID admin = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminService(userRepository, mock(WishlistRepository.class), mock(WishRepository.class),
                userService, exportService, audit);
    }

    @Test
    void searchRecordsTermLengthButNotTheTerm() {
        when(userRepository.findByEmailContainingIgnoreCase(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.searchByEmail(admin, "alice@x", false, null, null);

        verify(audit).record(admin, AdminAuditAction.USER_SEARCH, null,
                Map.of("term_length", 7, "include_deleted", false, "matches", 0L));
    }

    @Test
    void exportIsRecorded() {
        service.exportUser(admin, target);
        verify(audit).record(admin, AdminAuditAction.USER_EXPORT, target, null);
    }

    @Test
    void failedExportLeavesNoAuditRow() {
        when(exportService.export(target)).thenThrow(new IllegalStateException("boom"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.exportUser(admin, target))
                .isInstanceOf(IllegalStateException.class);
        verify(audit, org.mockito.Mockito.never()).record(any(), any(), any(), any());
    }

    @Test
    void deleteDelegatesToTheAuditedUserServicePaths() {
        service.deleteUser(admin, target, false);
        service.deleteUser(admin, target, true);
        verify(userService).softDeleteUserAudited(admin, target);
        verify(userService).eraseUserNow(admin, target);
    }
}
