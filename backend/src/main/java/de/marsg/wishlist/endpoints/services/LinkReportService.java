package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.linkreports.CreateLinkReportDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.LinkReportEntity;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.LinkReportRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets group members report a wish link of someone else's wishlist to the admins. */
@Service
public class LinkReportService {

    private static final Logger log = LoggerFactory.getLogger(LinkReportService.class);

    private final LinkReportRepository reportRepository;
    private final WishRepository wishRepository;
    private final WishlistService wishlistService;
    private final AccessService accessService;

    public LinkReportService(LinkReportRepository reportRepository, WishRepository wishRepository,
            WishlistService wishlistService, AccessService accessService) {
        this.reportRepository = reportRepository;
        this.wishRepository = wishRepository;
        this.wishlistService = wishlistService;
        this.accessService = accessService;
    }

    /**
     * The reporter must see the wish (else LINK_NOT_FOUND, also for links hidden as blocked)
     * and not own it; one report per user and link.
     */
    @Transactional
    public void report(UUID userId, UUID linkId, CreateLinkReportDTO dto) {
        WishEntity wish = wishRepository.findByLinkId(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_NOT_FOUND));
        WishlistEntity wishlist;
        try {
            wishlist = wishlistService.requireWishlist(wish.getWishlistsId());
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.LINK_NOT_FOUND);
        }
        WishLinkEntity link = wish.getLinks().stream().filter(l -> l.getId().equals(linkId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_NOT_FOUND));
        if (accessService.isWishlistOwner(userId, wishlist)) {
            throw new ApiException(ErrorCode.CANNOT_REPORT_OWN_LINK);
        }
        if (link.isBlocked() || !accessService.canViewAsSharedWishlist(userId, wishlist.getId())) {
            throw new ApiException(ErrorCode.LINK_NOT_FOUND);
        }
        if (reportRepository.existsByWishLinksIdAndUsersId(linkId, userId)) {
            throw new ApiException(ErrorCode.LINK_ALREADY_REPORTED);
        }
        try {
            reportRepository.saveAndFlush(new LinkReportEntity(linkId, userId, dto.reason(), dto.comment()));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.LINK_ALREADY_REPORTED);
        }
        log.info("User {} reported link {} ({})", userId, linkId, dto.reason());
    }
}
