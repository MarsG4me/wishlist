package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.endpoints.dto.linkreports.DecideLinkReportDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.DecideLinkReportDTO.Action;
import de.marsg.wishlist.endpoints.services.LinkReportAdminService.AuthorityReport;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.UuidV7Factory;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.LinkReportEntity;
import de.marsg.wishlist.jpa.entities.LinkReportReason;
import de.marsg.wishlist.jpa.entities.LinkReportStatus;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import de.marsg.wishlist.jpa.repositories.BlockedDomainRepository;
import de.marsg.wishlist.jpa.repositories.LinkReportRepository;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** Decision flows, the escalation rules and the authority export (incl. HTML escaping). */
class LinkReportAdminServiceTest {

    private final LinkReportRepository reports = mock(LinkReportRepository.class);
    private final WishLinkRepository links = mock(WishLinkRepository.class);
    private final BlockedDomainRepository blockedDomains = mock(BlockedDomainRepository.class);
    private final LinkRescanService rescan = mock(LinkRescanService.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private LinkReportAdminService service;

    private final UUID admin = UUID.randomUUID();
    private final UUID reporter = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();
    private LinkReportEntity report;
    private UUID reportId;

    @BeforeEach
    void setUp() {
        service = new LinkReportAdminService(reports, links, blockedDomains, rescan, audit, JsonMapper.builder().build());
        report = new LinkReportEntity(linkId, reporter, LinkReportReason.SCAM, "fake shop");
        reportId = UuidV7Factory.next();
        ReflectionTestUtils.setField(report, "id", reportId);
        when(reports.findById(reportId)).thenReturn(Optional.of(report));
        when(reports.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        WishLinkEntity link = new WishLinkEntity("https://evil.example/p", null, (short) 0, "evil.example");
        ReflectionTestUtils.setField(link, "id", linkId);
        when(links.findById(linkId)).thenReturn(Optional.of(link));
        when(reports.findItem(reportId)).thenAnswer(inv -> List.<Object[]>of(new Object[] {reportId,
                report.getStatus().name(), "SCAM", "fake shop", linkId, "https://evil.example/p", "evil.example", "ADMIN",
                UUID.randomUUID(), "Bricks", "Alice", "Bob", Timestamp.from(Instant.now())}));
        when(reports.findEvidenceSource(reportId)).thenReturn(List.<Object[]>of(new Object[] {linkId,
                "https://evil.example/p", "evil.example", UUID.randomUUID(), "Bricks <b>", UUID.randomUUID(), "List",
                UUID.randomUUID(), "alice@it.local", "<script>alert(1)</script>"}));
    }

    private static DecideLinkReportDTO decision(Action action, boolean escalate) {
        return new DecideLinkReportDTO(action, escalate);
    }

    @Test
    void dismissOnlyClosesTheReport() {
        var item = service.decide(admin, reportId, decision(Action.DISMISS, false));

        assertThat(report.getStatus()).isEqualTo(LinkReportStatus.DISMISSED);
        assertThat(item.id()).isEqualTo(reportId);
        verify(links, never()).flagAsAdmin(any());
        verify(audit).record(eq(admin), eq(AdminAuditAction.LINK_REPORT_DECISION), eq(reportId), any());
    }

    @Test
    void escalatingNeedsABlockAction() {
        assertThatThrownBy(() -> service.decide(admin, reportId, decision(Action.DISMISS, true)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(report.getStatus()).isEqualTo(LinkReportStatus.OPEN);
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void blockLinkFlagsOnlyThatLink() {
        service.decide(admin, reportId, decision(Action.BLOCK_LINK, false));

        assertThat(report.getStatus()).isEqualTo(LinkReportStatus.ACTIONED);
        assertThat(report.getEvidence()).isNull();
        verify(links).flagAsAdmin(linkId);
        verify(blockedDomains, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void blockDomainStoresTheDomainAndRescansIt() {
        service.decide(admin, reportId, decision(Action.BLOCK_DOMAIN, false));

        assertThat(report.getStatus()).isEqualTo(LinkReportStatus.ACTIONED);
        verify(links, never()).flagAsAdmin(any());
        var order = org.mockito.Mockito.inOrder(rescan, blockedDomains);
        order.verify(rescan).lockDomain("evil.example");
        order.verify(blockedDomains).insertIfAbsent("evil.example", admin, reportId);
        order.verify(rescan).rescanDomain("evil.example", false);
    }

    @Test
    void decisionsLoadTheReportWithARowLock() {
        service.decide(admin, reportId, decision(Action.DISMISS, false));

        verify(reports).findByIdForUpdate(reportId);
    }

    @Test
    void escalationSnapshotsTheEvidence() {
        service.decide(admin, reportId, decision(Action.BLOCK_LINK, true));

        assertThat(report.getStatus()).isEqualTo(LinkReportStatus.ESCALATED);
        assertThat(report.getEvidence()).contains("evil.example", "alice@it.local", reporter.toString(), "SCAM");
        assertThat(report.getAdminUsersId()).isEqualTo(admin);
        assertThat(report.getDecidedAt()).isNotNull();
    }

    @Test
    void aDecidedReportCannotBeDecidedAgain() {
        service.decide(admin, reportId, decision(Action.DISMISS, false));

        assertThatThrownBy(() -> service.decide(admin, reportId, decision(Action.BLOCK_LINK, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_REPORT_ALREADY_DECIDED));
    }

    @Test
    void blockingNeedsTheLinkToStillExist() {
        when(links.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(admin, reportId, decision(Action.BLOCK_LINK, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_NOT_FOUND));
    }

    @Test
    void unknownReportIsNotFound() {
        assertThatThrownBy(() -> service.decide(admin, UUID.randomUUID(), decision(Action.DISMISS, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_REPORT_NOT_FOUND));
    }

    @Test
    void authorityReportNeedsAnEscalatedReport() {
        assertThatThrownBy(() -> service.authorityReport(admin, reportId, "json"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_ESCALATED));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void authorityHtmlEscapesEveryValue() {
        service.decide(admin, reportId, decision(Action.BLOCK_LINK, true));

        AuthorityReport html = service.authorityReport(admin, reportId, "html");
        String page = new String(html.body(), StandardCharsets.UTF_8);

        assertThat(html.contentType()).startsWith("text/html");
        assertThat(html.filename()).endsWith(".html");
        assertThat(page).doesNotContain("<script>alert(1)</script>").doesNotContain("Bricks <b>");
        assertThat(page).contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("Bricks &lt;b&gt;");
        verify(audit).record(eq(admin), eq(AdminAuditAction.LINK_REPORT_EXPORT), eq(reportId), any());
    }

    @Test
    void authorityJsonCarriesTheEvidence() {
        service.decide(admin, reportId, decision(Action.BLOCK_LINK, true));

        AuthorityReport json = service.authorityReport(admin, reportId, "json");

        assertThat(json.contentType()).isEqualTo("application/json");
        assertThat(new String(json.body(), StandardCharsets.UTF_8)).contains("\"evidence\"", "evil.example");
    }

    @Test
    void unblockingADomainRemovesItAndLiftsAdminFlags() {
        UUID id = UUID.randomUUID();
        when(blockedDomains.findHostById(id)).thenReturn(Optional.of("evil.example"));

        service.unblockDomain(admin, id);

        var order = org.mockito.Mockito.inOrder(rescan, blockedDomains);
        order.verify(rescan).lockDomain("evil.example");
        order.verify(blockedDomains).deleteBlockedById(id);
        order.verify(rescan).rescanDomain("evil.example", true);
        verify(audit).record(eq(admin), eq(AdminAuditAction.DOMAIN_UNBLOCK), eq(id), any());
        assertThatThrownBy(() -> service.unblockDomain(admin, UUID.randomUUID())).isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BLOCKED_DOMAIN_NOT_FOUND));
    }

    @Test
    void unblockingALinkClearsItsAdminFlagAndRescansTheHost() {
        service.unblockLink(admin, linkId);

        verify(links).clearAdminFlag(linkId);
        verify(rescan).rescanHost("evil.example");
        verify(audit).record(admin, AdminAuditAction.LINK_UNBLOCK, linkId, null);
    }
}
