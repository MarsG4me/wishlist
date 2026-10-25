package de.marsg.wishlist.endpoints.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.repositories.BlockedDomainRepository;
import de.marsg.wishlist.jpa.repositories.LinkHostCheckRepository;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Order of the link checks, the host verdict cache, the Web Risk budget fail-open and the 5s deadline. */
class LinkSafetyServiceTest {

    private final BlockedDomainRepository blockedDomains = mock(BlockedDomainRepository.class);
    private final LinkHostCheckRepository hostChecks = mock(LinkHostCheckRepository.class);
    private final WebRiskClient webRisk = mock(WebRiskClient.class);
    private final WishLinkRepository links = mock(WishLinkRepository.class);
    private final LinkBlocklist blocklist = new LinkBlocklist();
    private LinkSafetyService service;

    @BeforeEach
    void setUp() {
        blocklist.swap(Arrays.stream(new String[] {"listed.example"}).mapToLong(LinkBlocklist::hash).sorted().toArray());
        service = new LinkSafetyService(List.of("shop.example", "amzn.to"), List.of("bit.ly", "amzn.to"), blocklist,
                true, blockedDomains, hostChecks, webRisk, links);
        when(links.findAdminBlockedUrls(any())).thenReturn(List.of());
        when(blockedDomains.findBlockedAmong(any())).thenReturn(List.of());
        when(hostChecks.findVerdict(anyString())).thenReturn(Optional.empty());
        when(webRisk.enabled()).thenReturn(true);
    }

    private static void assertProblem(Map<Integer, ApiException> problems, int index, ErrorCode code) {
        assertThat(problems).containsKey(index);
        assertThat(problems.get(index).getErrorCode()).isEqualTo(code);
        assertThat(problems.get(index).getLinkIndex()).isEqualTo(index);
    }

    @Test
    void shortenerIsRejectedUnlessAllowlisted() {
        Map<Integer, ApiException> problems = service.findProblems(
                List.of("https://shop.example/a", "https://bit.ly/x", "https://amzn.to/y"));

        assertThat(problems).hasSize(1);
        assertProblem(problems, 1, ErrorCode.LINK_SHORTENER_NOT_ALLOWED);
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void adminBlockedDomainRejectsItsSubdomainsToo() {
        when(blockedDomains.findBlockedAmong(any())).thenAnswer(inv -> {
            java.util.Collection<String> hosts = inv.getArgument(0);
            return hosts.contains("bad.example") ? List.of("bad.example") : List.of();
        });

        Map<Integer, ApiException> problems = service.findProblems(List.of("https://www.bad.example/x"));

        assertProblem(problems, 0, ErrorCode.LINK_BLOCKED);
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void adminDomainBlockWinsOverTheAllowlist() {
        when(blockedDomains.findBlockedAmong(any())).thenAnswer(inv -> {
            java.util.Collection<String> hosts = inv.getArgument(0);
            return hosts.contains("shop.example") ? List.of("shop.example") : List.of();
        });

        assertProblem(service.findProblems(List.of("https://www.shop.example/x")), 0, ErrorCode.LINK_BLOCKED);
        assertThat(service.sourceWithoutWebRisk("www.shop.example")).isEqualTo(LinkBlockSource.ADMIN_DOMAIN);
    }

    @Test
    void hashBlocklistRejectsAndSkipsWebRisk() {
        assertProblem(service.findProblems(List.of("https://listed.example/")), 0, ErrorCode.LINK_BLOCKED);
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void allowlistedHostSkipsEveryOtherCheck() {
        blocklist.swap(Arrays.stream(new String[] {"shop.example"}).mapToLong(LinkBlocklist::hash).sorted().toArray());

        assertThat(service.findProblems(List.of("https://shop.example/"))).isEmpty();
        verify(hostChecks, never()).findVerdict(anyString());
    }

    @Test
    void storedVerdictSkipsWebRisk() {
        when(hostChecks.findVerdict("seen-safe.example")).thenReturn(Optional.of("SAFE"));
        when(hostChecks.findVerdict("seen-bad.example")).thenReturn(Optional.of("UNSAFE"));

        Map<Integer, ApiException> problems = service.findProblems(
                List.of("https://seen-safe.example/", "https://seen-bad.example/"));

        assertThat(problems).hasSize(1);
        assertProblem(problems, 1, ErrorCode.LINK_BLOCKED);
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void unseenHostsAreLookedUpOnceAndTheVerdictStored() {
        when(webRisk.isUnsafe("new-bad.example")).thenReturn(true);
        when(webRisk.isUnsafe("new-ok.example")).thenReturn(false);

        Map<Integer, ApiException> problems = service.findProblems(List.of("https://new-ok.example/a",
                "https://new-bad.example/b", "https://new-bad.example/c"));

        assertThat(problems).containsOnlyKeys(1, 2);
        assertProblem(problems, 1, ErrorCode.LINK_BLOCKED);
        verify(webRisk).isUnsafe("new-bad.example");
        verify(hostChecks).upsertVerdict("new-bad.example", "UNSAFE");
        verify(hostChecks).upsertVerdict("new-ok.example", "SAFE");
    }

    @Test
    void exhaustedBudgetOrLookupErrorFailsOpenAndStoresNothing() {
        when(webRisk.isUnsafe(anyString())).thenReturn(null);

        assertThat(service.findProblems(List.of("https://unknown.example/"))).isEmpty();
        verify(hostChecks, never()).upsertVerdict(anyString(), anyString());
    }

    @Test
    void withoutApiKeyNoLookupHappens() {
        when(webRisk.enabled()).thenReturn(false);

        assertThat(service.findProblems(List.of("https://unknown.example/"))).isEmpty();
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void slowLookupsAreCutOffAfterTheOverallDeadline() {
        when(webRisk.isUnsafe(anyString())).thenAnswer(inv -> {
            Thread.sleep(30_000);
            return true;
        });
        long start = System.nanoTime();

        Map<Integer, ApiException> problems = service.findProblems(List.of("https://slow1.example/", "https://slow2.example/"));

        assertThat(problems).isEmpty();
        assertThat((System.nanoTime() - start) / 1_000_000).isBetween(4_500L, 8_000L);
    }

    @Test
    void oneFailingLookupDoesNotDropTheVerdictsOfTheOthers() {
        when(webRisk.isUnsafe("bad.example")).thenReturn(true);
        when(webRisk.isUnsafe("broken.example")).thenThrow(new IllegalStateException("boom"));
        when(webRisk.isUnsafe("ok.example")).thenReturn(false);

        Map<Integer, ApiException> problems = service.findProblems(
                List.of("https://bad.example/", "https://broken.example/", "https://ok.example/"));

        assertThat(problems).containsOnlyKeys(0);
        assertProblem(problems, 0, ErrorCode.LINK_BLOCKED);
    }

    @Test
    void atMostFourLookupsRunInParallel() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(webRisk.isUnsafe(anyString())).thenAnswer(inv -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(100);
            running.decrementAndGet();
            return false;
        });

        service.findProblems(List.of("https://h1.example/", "https://h2.example/", "https://h3.example/",
                "https://h4.example/", "https://h5.example/", "https://h6.example/", "https://h7.example/",
                "https://h8.example/"));

        assertThat(peak.get()).isBetween(2, 4);
        verify(webRisk, org.mockito.Mockito.times(8)).isUnsafe(anyString());
    }

    @Test
    void anUrlAdminBlockedInAnyWishIsRejectedWithoutFurtherChecks() {
        when(links.findAdminBlockedUrls(any())).thenReturn(List.of("https://shop.example/blocked"));

        Map<Integer, ApiException> problems = service.findProblems(
                List.of("https://shop.example/fine", "https://shop.example/blocked"));

        assertThat(problems).containsOnlyKeys(1);
        assertProblem(problems, 1, ErrorCode.LINK_BLOCKED);
    }

    @Test
    void nullEntriesAreSkippedAndIndicesStayAligned() {
        Map<Integer, ApiException> problems = service.findProblems(java.util.Arrays.asList(null, "https://bit.ly/x"));

        assertThat(problems).containsOnlyKeys(1);
    }

    @Test
    void sourceWithoutWebRiskUsesTheStoredVerdictOnly() {
        when(hostChecks.findVerdict("seen-bad.example")).thenReturn(Optional.of("UNSAFE"));

        assertThat(service.sourceWithoutWebRisk("seen-bad.example")).isEqualTo(LinkBlockSource.WEB_RISK);
        assertThat(service.sourceWithoutWebRisk("bit.ly")).isEqualTo(LinkBlockSource.SHORTENER);
        assertThat(service.sourceWithoutWebRisk("listed.example")).isEqualTo(LinkBlockSource.BLOCKLIST);
        assertThat(service.sourceWithoutWebRisk("shop.example")).isNull();
        assertThat(service.sourceWithoutWebRisk("never-seen.example")).isNull();
        verify(webRisk, never()).isUnsafe(anyString());
    }

    @Test
    void parentDomainsNeverIncludeTheBareTld() {
        assertThat(LinkSafetyService.withParents("a.b.example.com"))
                .containsExactly("a.b.example.com", "b.example.com", "example.com");
    }
}
