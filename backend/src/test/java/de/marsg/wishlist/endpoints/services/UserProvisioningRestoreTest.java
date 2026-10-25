package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.SoftDeletePurgeJob;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

/** A soft-deleted account is only restored by a login that happened after the deletion. */
@ExtendWith(MockitoExtension.class)
class UserProvisioningRestoreTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAccountClient keycloakAccountClient;

    private UserProvisioningService service;
    private final UUID keycloakId = UUID.randomUUID();
    private UserEntity deleted;
    private Instant deletedAt;

    @BeforeEach
    void setUp() {
        service = new UserProvisioningService(userRepository, keycloakAccountClient);
        deleted = new UserEntity(keycloakId, "Alice", "alice@test.local", UserLocale.EN);
        deleted.markDeleted();
        deletedAt = deleted.getDeletedAt();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
    }

    private void withDeletedRow() {
        when(userRepository.findByKeycloakIdIncludingDeleted(keycloakId)).thenReturn(Optional.of(deleted));
    }

    private Jwt jwt(Instant authTime) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(keycloakId.toString());
        when(jwt.hasClaim("auth_time")).thenReturn(authTime != null);
        if (authTime != null) {
            when(jwt.getClaimAsInstant("auth_time")).thenReturn(authTime);
        }
        return jwt;
    }

    @Test
    void tokenWithoutAuthTimeIsRejected() {
        withDeletedRow();
        assertThatThrownBy(() -> service.provision(jwt(null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DELETED));
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void refreshedOldSessionIsRejectedViaAuthTime() {
        withDeletedRow();
        assertThatThrownBy(() -> service.provision(jwt(deletedAt.minusSeconds(60))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DELETED));
    }

    @Test
    void freshLoginRestoresTheAccount() {
        withDeletedRow();
        when(userRepository.save(deleted)).thenReturn(deleted);

        UserEntity result = service.provision(jwt(deletedAt.plusSeconds(60)));

        assertThat(result.isDeleted()).isFalse();
        verify(userRepository).save(deleted);
    }

    @Test
    void freshLoginAfterTheRetentionWindowIsRejected() {
        ReflectionTestUtils.setField(deleted, "deletedAt",
                Instant.now().minus(SoftDeletePurgeJob.RETENTION).minusSeconds(60));
        withDeletedRow();

        assertThatThrownBy(() -> service.provision(jwt(Instant.now())))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DELETED));
        assertThat(deleted.isDeleted()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void erasedKeycloakUserIsNotProvisionedAgain() {
        when(userRepository.findByKeycloakIdIncludingDeleted(keycloakId)).thenReturn(Optional.empty());
        when(keycloakAccountClient.userExists(keycloakId)).thenReturn(false);

        Jwt token = mock(Jwt.class);
        when(token.getSubject()).thenReturn(keycloakId.toString());

        assertThatThrownBy(() -> service.provision(token))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DELETED));
        verify(userRepository, never()).save(any());
    }
}
