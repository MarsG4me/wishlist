package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Language changes go through our backend (the Account API answered 401 for the app's
 * own token). Our DB owns the value; the Keycloak mirror is best effort and must never
 * fail the request.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceLocaleTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupMembershipRepository membershipRepository;
    @Mock
    private GroupWishlistShareRepository shareRepository;
    @Mock
    private KeycloakAccountClient keycloakAccountClient;
    @Mock
    private ClaimService claimService;
    @Mock
    private MembershipService membershipService;

    private UserService userService;

    private final UUID userId = UUID.randomUUID();
    private final UUID keycloakId = UUID.randomUUID();
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, membershipRepository, shareRepository,
                keycloakAccountClient, claimService, membershipService,
                mock(WishlistRepository.class), mock(WishRepository.class), mock(AdminAuditService.class),
                mock(TransactionTemplate.class));
        user = new UserEntity(keycloakId, "Alice", "alice@test.local", UserLocale.EN);
    }

    @Test
    void localeIsStoredAndMirroredIntoKeycloak() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ReturnOwnUserDTO result = userService.updateLocale(userId, null, UserLocale.DE);

        assertThat(result.locale()).isEqualTo(UserLocale.DE);
        assertThat(user.getLocale()).isEqualTo(UserLocale.DE);
        verify(keycloakAccountClient).updateLocale(keycloakId, "de");
    }

    @Test
    void keycloakOutageStillChangesTheLanguage() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED))
                .when(keycloakAccountClient).updateLocale(keycloakId, "de");

        ReturnOwnUserDTO result = userService.updateLocale(userId, null, UserLocale.DE);

        assertThat(result.locale()).isEqualTo(UserLocale.DE);
        assertThat(user.getLocale()).isEqualTo(UserLocale.DE);
    }

    @Test
    void externalProviderAccountsMayChangeTheirLanguage() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ReturnOwnUserDTO result = userService.updateLocale(userId, "google", UserLocale.DE);

        assertThat(result.identityProvider()).isEqualTo("google");
        assertThat(result.locale()).isEqualTo(UserLocale.DE);
    }

    @Test
    void unknownUserIsRejectedBeforeTouchingKeycloak() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateLocale(userId, null, UserLocale.DE))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(keycloakAccountClient, never()).updateLocale(any(), any());
    }
}
