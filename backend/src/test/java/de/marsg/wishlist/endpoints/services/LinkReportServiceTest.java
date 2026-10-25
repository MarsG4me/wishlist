package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.endpoints.dto.linkreports.CreateLinkReportDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.entities.LinkReportEntity;
import de.marsg.wishlist.jpa.entities.LinkReportReason;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.LinkReportRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Who may report which link: viewers only, never the owner, once per link. */
class LinkReportServiceTest {

    private final LinkReportRepository reports = mock(LinkReportRepository.class);
    private final WishRepository wishes = mock(WishRepository.class);
    private final WishlistService wishlistService = mock(WishlistService.class);
    private final GroupWishlistShareRepository shares = mock(GroupWishlistShareRepository.class);
    private LinkReportService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID wishlistId = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();
    private final CreateLinkReportDTO body = new CreateLinkReportDTO(LinkReportReason.SCAM, "looks fake");
    private WishLinkEntity link;

    @BeforeEach
    void setUp() {
        service = new LinkReportService(reports, wishes, wishlistService,
                new AccessService(mock(GroupMembershipRepository.class), shares));
        WishlistEntity wishlist = mock(WishlistEntity.class);
        when(wishlist.getId()).thenReturn(wishlistId);
        when(wishlist.getUsersId()).thenReturn(owner);
        when(wishlistService.requireWishlist(wishlistId)).thenReturn(wishlist);
        link = new WishLinkEntity("https://x.example/", null, (short) 0, "x.example");
        ReflectionTestUtils.setField(link, "id", linkId);
        WishEntity wish = new WishEntity(wishlistId, "w", null, 1);
        wish.replaceLinks(List.of(link));
        when(wishes.findByLinkId(linkId)).thenReturn(Optional.of(wish));
        when(shares.existsShareGivingUserAccess(viewer, wishlistId)).thenReturn(true);
    }

    private void assertCode(UUID user, ErrorCode expected) {
        assertThatThrownBy(() -> service.report(user, linkId, body)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void viewerCanReportOnce() {
        assertThatCode(() -> service.report(viewer, linkId, body)).doesNotThrowAnyException();
        verify(reports).saveAndFlush(any(LinkReportEntity.class));

        when(reports.existsByWishLinksIdAndUsersId(linkId, viewer)).thenReturn(true);
        assertCode(viewer, ErrorCode.LINK_ALREADY_REPORTED);
    }

    @Test
    void aRaceOnTheUniqueConstraintAlsoMeansAlreadyReported() {
        when(reports.saveAndFlush(any(LinkReportEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertCode(viewer, ErrorCode.LINK_ALREADY_REPORTED);
    }

    @Test
    void ownerCannotReportOwnLink() {
        assertCode(owner, ErrorCode.CANNOT_REPORT_OWN_LINK);
        verify(reports, never()).saveAndFlush(any(LinkReportEntity.class));
    }

    @Test
    void strangerLearnsNothing() {
        assertCode(stranger, ErrorCode.LINK_NOT_FOUND);
    }

    @Test
    void unknownLinkIsNotFound() {
        assertThatThrownBy(() -> service.report(viewer, UUID.randomUUID(), body)).isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_NOT_FOUND));
    }

    @Test
    void blockedLinksAreInvisibleToViewers() {
        ReflectionTestUtils.setField(link, "blockedBy", LinkBlockSource.ADMIN);

        assertCode(viewer, ErrorCode.LINK_NOT_FOUND);
    }
}
