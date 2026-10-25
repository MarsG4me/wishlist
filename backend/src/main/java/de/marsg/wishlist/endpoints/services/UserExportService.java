package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportClaim;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportFederatedIdentity;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportGroup;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportInvite;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportKeycloak;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportLink;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportLinkReport;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportMembership;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportShare;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportUser;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportWish;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO.ExportWishlist;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.UuidV7Factory;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.UserExportRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the GDPR data export of one account (also for soft-deleted ones). */
@Service
public class UserExportService {

    private final UserRepository userRepository;
    private final UserExportRepository exportRepository;
    private final KeycloakAccountClient keycloakAccountClient;

    public UserExportService(UserRepository userRepository, UserExportRepository exportRepository,
            KeycloakAccountClient keycloakAccountClient) {
        this.userRepository = userRepository;
        this.exportRepository = exportRepository;
        this.keycloakAccountClient = keycloakAccountClient;
    }

    @Transactional(readOnly = true)
    public ReturnUserExportDTO export(UUID userId) {
        UserEntity user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return new ReturnUserExportDTO(Instant.now(),
                new ExportUser(user.getId(), user.getDisplayname(), user.getEmail(), user.getLocale(),
                        user.getTier(), UuidV7Factory.timestampOf(user.getId())),
                keycloakData(user.getKeycloakId()),
                wishlists(userId),
                memberships(userId),
                exportRepository.findGroupRowsCreatedBy(userId).stream()
                        .map(row -> new ExportGroup((UUID) row[0], (String) row[1], (String) row[2],
                                instantOf(row[3]))).toList(),
                exportRepository.findInvitesCreatedBy(userId).stream()
                        .map(i -> new ExportInvite(i.getCode(), i.getExpiresAt(), i.getRevokedAt(),
                                i.getMaxUses(), i.getUseCount())).toList(),
                exportRepository.findShareRows(userId).stream()
                        .map(row -> new ExportShare((UUID) row[0], (UUID) row[1])).toList(),
                exportRepository.findClaimRows(userId).stream()
                        .map(row -> new ExportClaim((UUID) row[0], (String) row[1], (String) row[2],
                                (Boolean) row[3], userId.equals(row[4]))).toList(),
                exportRepository.findLinkReportRows(userId).stream()
                        .map(row -> new ExportLinkReport((UUID) row[0], (String) row[1], (String) row[2],
                                (String) row[3], (String) row[4], UuidV7Factory.timestampOf((UUID) row[0])))
                        .toList());
    }

    /** Null when Keycloak cannot be reached: the export of our own data must not depend on it. */
    private ExportKeycloak keycloakData(UUID keycloakId) {
        try {
            Map<?, ?> kc = keycloakAccountClient.fetchUser(keycloakId);
            List<ExportFederatedIdentity> identities = keycloakAccountClient.fetchFederatedIdentities(keycloakId)
                    .stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .map(i -> new ExportFederatedIdentity(text(i.get("identityProvider")), text(i.get("userName"))))
                    .toList();
            return new ExportKeycloak(text(kc.get("username")), text(kc.get("email")),
                    kc.get("emailVerified") instanceof Boolean verified ? verified : null,
                    text(kc.get("firstName")), text(kc.get("lastName")),
                    kc.get("createdTimestamp") instanceof Number created ? created.longValue() : null, identities);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<ExportWishlist> wishlists(UUID userId) {
        Map<UUID, List<ExportLink>> linksByWish = exportRepository.findLinkRows(userId).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0], Collectors.mapping(
                        row -> new ExportLink((String) row[1], (String) row[2], ((Number) row[3]).intValue()),
                        Collectors.toList())));
        Map<UUID, List<ExportWish>> wishesByList = exportRepository.findWishRows(userId).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[1], Collectors.mapping(
                        row -> new ExportWish((UUID) row[0], (String) row[2], (String) row[3],
                                row[4] == null ? null : ((Number) row[4]).intValue(), instantOf(row[5]),
                                linksByWish.getOrDefault((UUID) row[0], List.of())),
                        Collectors.toList())));
        return exportRepository.findWishlistRows(userId).stream()
                .map(row -> new ExportWishlist((UUID) row[0], (String) row[1], instantOf(row[2]),
                        wishesByList.getOrDefault((UUID) row[0], List.of())))
                .toList();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** Native timestamptz columns arrive as Instant, OffsetDateTime or Timestamp depending on the driver path. */
    static Instant instantOf(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
        };
    }

    private List<ExportMembership> memberships(UUID userId) {
        return exportRepository.findMembershipsWithGroups(userId).stream().map(row -> {
            return new ExportMembership((UUID) row[0], (String) row[1], (Boolean) row[2],
                    userId.equals(row[3]), UuidV7Factory.timestampOf((UUID) row[4]));
        }).toList();
    }
}
