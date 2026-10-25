package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.DecideLinkReportDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.ReturnBlockedDomainDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.ReturnLinkReportDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.helpers.UuidV7Factory;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.entities.LinkReportEntity;
import de.marsg.wishlist.jpa.entities.LinkReportReason;
import de.marsg.wishlist.jpa.entities.LinkReportStatus;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import de.marsg.wishlist.jpa.repositories.BlockedDomainRepository;
import de.marsg.wishlist.jpa.repositories.LinkReportRepository;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin side of link reports: queue, decisions, authority export and domain/link unblocking.
 * Every state change writes its audit row inside the same transaction.
 */
@Service
public class LinkReportAdminService {

    private static final Logger log = LoggerFactory.getLogger(LinkReportAdminService.class);

    /** A downloadable authority report. */
    public record AuthorityReport(String filename, String contentType, byte[] body) {
    }

    private final LinkReportRepository reportRepository;
    private final WishLinkRepository linkRepository;
    private final BlockedDomainRepository blockedDomains;
    private final LinkRescanService rescanService;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    public LinkReportAdminService(LinkReportRepository reportRepository, WishLinkRepository linkRepository,
            BlockedDomainRepository blockedDomains, LinkRescanService rescanService,
            AdminAuditService auditService, ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.linkRepository = linkRepository;
        this.blockedDomains = blockedDomains;
        this.rescanService = rescanService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnLinkReportDTO> list(LinkReportStatus status, Integer limit, Integer offset) {
        Page<Object[]> page = reportRepository.findItems(status == null ? null : status.name(),
                PageParams.of(limit, offset, Sort.unsorted()));
        return ReturnListDTO.of(page, LinkReportAdminService::toItem);
    }

    /**
     * Dismisses, or blocks the link or its whole domain; escalating (block actions only) also
     * stores the evidence snapshot. A report is decided once; a second one is LINK_REPORT_ALREADY_DECIDED.
     */
    @Transactional
    public ReturnLinkReportDTO decide(UUID adminId, UUID reportId, DecideLinkReportDTO dto) {
        LinkReportEntity report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_REPORT_NOT_FOUND));
        boolean blocking = dto.action() != DecideLinkReportDTO.Action.DISMISS;
        if (dto.escalate() && !blocking) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "escalate is only possible with a block action");
        }
        if (report.getStatus() != LinkReportStatus.OPEN) {
            throw new ApiException(ErrorCode.LINK_REPORT_ALREADY_DECIDED);
        }
        Instant now = Instant.now();
        String evidence = null;
        if (blocking) {
            WishLinkEntity link = report.getWishLinksId() == null ? null
                    : linkRepository.findById(report.getWishLinksId()).orElse(null);
            if (link == null) {
                throw new ApiException(ErrorCode.LINK_NOT_FOUND);
            }
            if (dto.escalate()) {
                evidence = evidenceJson(report, dto.action(), adminId, now);
            }
            if (dto.action() == DecideLinkReportDTO.Action.BLOCK_LINK) {
                linkRepository.flagAsAdmin(link.getId());
            } else {
                String host = link.getHost() != null ? link.getHost() : URI.create(link.getUrl()).getHost();
                rescanService.lockDomain(host);
                blockedDomains.insertIfAbsent(host, adminId, reportId);
                rescanService.rescanDomain(host, false);
            }
        }
        LinkReportStatus status = !blocking ? LinkReportStatus.DISMISSED
                : dto.escalate() ? LinkReportStatus.ESCALATED : LinkReportStatus.ACTIONED;
        report.decide(status, adminId, now, evidence);
        reportRepository.flush();
        auditService.record(adminId, AdminAuditAction.LINK_REPORT_DECISION, reportId,
                Map.of("action", dto.action().name(), "escalate", dto.escalate(), "status", status.name()));
        log.info("Admin {} decided link report {}: {} (escalate {})", adminId, reportId, dto.action(),
                dto.escalate());
        return toItem(reportRepository.findItem(reportId).get(0));
    }

    /** Printable download of an escalated report, built from its stored evidence; the export is audited. */
    @Transactional
    public AuthorityReport authorityReport(UUID adminId, UUID reportId, String format) {
        LinkReportEntity report = requireReport(reportId);
        if (report.getStatus() != LinkReportStatus.ESCALATED || report.getEvidence() == null) {
            throw new ApiException(ErrorCode.REPORT_NOT_ESCALATED);
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("report_id", reportId.toString());
        document.put("status", report.getStatus().name());
        document.put("reported_at", UuidV7Factory.timestampOf(reportId).toString());
        document.put("decided_at", report.getDecidedAt() == null ? null : report.getDecidedAt().toString());
        document.put("evidence", objectMapper.readValue(report.getEvidence(), Map.class));
        boolean html = "html".equalsIgnoreCase(format);
        AuthorityReport result = html
                ? new AuthorityReport("link-report-" + reportId + ".html", "text/html;charset=UTF-8",
                        renderHtml(document).getBytes(StandardCharsets.UTF_8))
                : new AuthorityReport("link-report-" + reportId + ".json", "application/json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document));
        auditService.record(adminId, AdminAuditAction.LINK_REPORT_EXPORT, reportId,
                Map.of("format", html ? "html" : "json"));
        return result;
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnBlockedDomainDTO> listBlockedDomains(Integer limit, Integer offset) {
        Page<Object[]> page = blockedDomains.findPage(PageParams.of(limit, offset, Sort.unsorted()));
        return ReturnListDTO.of(page, row -> new ReturnBlockedDomainDTO((UUID) row[0], (String) row[1],
                UuidV7Factory.timestampOf((UUID) row[0]), (UUID) row[2]));
    }

    /** Removes the block, lifts ADMIN_DOMAIN flags on the domain's links and lets the automatic checks re-judge them. */
    @Transactional
    public void unblockDomain(UUID adminId, UUID blockedDomainId) {
        String host = blockedDomains.findHostById(blockedDomainId)
                .orElseThrow(() -> new ApiException(ErrorCode.BLOCKED_DOMAIN_NOT_FOUND));
        rescanService.lockDomain(host);
        blockedDomains.deleteBlockedById(blockedDomainId);
        rescanService.rescanDomain(host, true);
        auditService.record(adminId, AdminAuditAction.DOMAIN_UNBLOCK, blockedDomainId, Map.of("host", host));
        log.info("Admin {} unblocked domain {}", adminId, host);
    }

    /** Clears the ADMIN flag of one link; the host is re-judged, so a listed host flags it again. */
    @Transactional
    public void unblockLink(UUID adminId, UUID linkId) {
        WishLinkEntity link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_NOT_FOUND));
        linkRepository.clearAdminFlag(linkId);
        if (link.getHost() != null) {
            rescanService.rescanHost(link.getHost());
        }
        auditService.record(adminId, AdminAuditAction.LINK_UNBLOCK, linkId, null);
        log.info("Admin {} unblocked link {}", adminId, linkId);
    }

    private LinkReportEntity requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_REPORT_NOT_FOUND));
    }

    private static ReturnLinkReportDTO toItem(Object[] row) {
        UUID id = (UUID) row[0];
        return new ReturnLinkReportDTO(id, LinkReportStatus.valueOf((String) row[1]),
                LinkReportReason.valueOf((String) row[2]), (String) row[3], UuidV7Factory.timestampOf(id),
                (UUID) row[4], (String) row[5], (String) row[6],
                row[7] == null ? null : LinkBlockSource.valueOf((String) row[7]),
                (UUID) row[8], (String) row[9], (String) row[10], (String) row[11],
                UserExportService.instantOf(row[12]));
    }

    /** Snapshot of everything an authority needs; kept after link, wish and accounts are purged. */
    private String evidenceJson(LinkReportEntity report, DecideLinkReportDTO.Action action, UUID adminId,
            Instant decidedAt) {
        List<Object[]> rows = reportRepository.findEvidenceSource(report.getId());
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.LINK_NOT_FOUND);
        }
        Object[] r = rows.get(0);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("link_id", text(r[0]));
        evidence.put("url", r[1]);
        evidence.put("host", r[2]);
        evidence.put("wish_id", text(r[3]));
        evidence.put("wish_title", r[4]);
        evidence.put("wishlist_id", text(r[5]));
        evidence.put("wishlist_title", r[6]);
        evidence.put("owner_id", text(r[7]));
        evidence.put("owner_email", r[8]);
        evidence.put("owner_displayname", r[9]);
        evidence.put("reporter_id", text(report.getUsersId()));
        evidence.put("reason", report.getReason().name());
        evidence.put("comment", report.getComment());
        evidence.put("reported_at", UuidV7Factory.timestampOf(report.getId()).toString());
        evidence.put("decision", action.name());
        evidence.put("decided_by", adminId.toString());
        evidence.put("decided_at", decidedAt.toString());
        return objectMapper.writeValueAsString(evidence);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String renderHtml(Map<String, Object> document) {
        StringBuilder out = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Link report</title><style>body{font-family:sans-serif;margin:2rem;color:#000}")
                .append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #888;padding:.4rem;")
                .append("text-align:left;vertical-align:top;word-break:break-all}th{width:28%}")
                .append("@media print{body{margin:0}}</style></head><body><h1>Link report</h1><table>");
        appendRows(out, "", document);
        return out.append("</table></body></html>").toString();
    }

    private static void appendRows(StringBuilder out, String prefix, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                appendRows(out, key + ".", nested);
            } else {
                out.append("<tr><th>").append(HtmlUtils.htmlEscape(key)).append("</th><td>")
                        .append(entry.getValue() == null ? "-" : HtmlUtils.htmlEscape(entry.getValue().toString()))
                        .append("</td></tr>");
            }
        }
    }
}
