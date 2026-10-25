package de.marsg.wishlist.endpoints.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.repositories.WishLinkRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A rescan sets and clears automatic flags only; ADMIN flags are lifted by the unblock endpoints alone. */
class LinkRescanServiceTest {

    private final WishLinkRepository links = mock(WishLinkRepository.class);
    private final LinkSafetyService safety = mock(LinkSafetyService.class);
    private final LinkRescanService service = new LinkRescanService(links, safety,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void automaticSourceFlagsTheHostWithoutTouchingAdminFlags() {
        when(safety.sourceWithoutWebRisk("evil.example")).thenReturn(LinkBlockSource.BLOCKLIST);

        service.rescanHost("evil.example");

        verify(links).flagHostAutomatically("evil.example", "BLOCKLIST");
        verify(links, never()).clearAdminDomainFlags(anyString());
        verify(links, never()).clearAutomaticFlags(anyString());
    }

    @Test
    void cleanHostClearsOnlyAutomaticFlags() {
        when(safety.sourceWithoutWebRisk("fine.example")).thenReturn(null);

        service.rescanHost("fine.example");

        verify(links).clearAutomaticFlags("fine.example");
        verify(links, never()).clearAdminDomainFlags(anyString());
    }

    @Test
    void domainBlockFlagsAsAdmin() {
        when(safety.sourceWithoutWebRisk("blocked.example")).thenReturn(LinkBlockSource.ADMIN_DOMAIN);

        service.rescanHost("blocked.example");

        verify(links).flagHostAsAdminDomain("blocked.example");
    }

    @Test
    void unblockingADomainLiftsAdminFlagsOfItsSubdomainsBeforeRejudging() {
        when(links.findHostsUnder("d.example")).thenReturn(List.of("d.example", "www.d.example"));

        service.rescanDomain("d.example", true);

        verify(links).clearAdminDomainFlags("d.example");
        verify(links).clearAdminDomainFlags("www.d.example");
        verify(links).clearAutomaticFlags("www.d.example");
    }

    @Test
    void incompleteBlocklistKeepsBlocklistFlagsOfCleanHosts() {
        when(safety.sourceWithoutWebRisk("fine.example")).thenReturn(null);

        service.rescanHost("fine.example", false);

        verify(links).clearAutomaticFlagsKeepingBlocklist("fine.example");
        verify(links, never()).clearAutomaticFlags(anyString());
    }

    @Test
    void incompleteBlocklistEventStillBackfillsHostsButKeepsBlocklistFlags() {
        UUID id = UUID.randomUUID();
        when(links.findWithoutHost(any(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[] {id, "https://shop.example/x"}))
                .thenReturn(List.of());
        when(links.findDistinctHosts()).thenReturn(List.of("shop.example"));

        service.onListsChanged(new LinkListsChangedEvent(false));

        verify(links).setHost(id, "shop.example");
        verify(links).clearAutomaticFlagsKeepingBlocklist("shop.example");
        verify(links, never()).clearAutomaticFlags(anyString());
    }

    @Test
    void completeBlocklistEventClearsAutomaticFlags() {
        when(links.findWithoutHost(any(), anyInt())).thenReturn(List.of());
        when(links.findDistinctHosts()).thenReturn(List.of("shop.example"));

        service.onListsChanged(new LinkListsChangedEvent(true));

        verify(links).clearAutomaticFlags("shop.example");
    }

    @Test
    void hostIsLockedBeforeItsFlagsChange() {
        service.rescanHost("fine.example");

        InOrder order = Mockito.inOrder(links);
        order.verify(links).lockHost("fine.example");
        order.verify(links).clearAutomaticFlags("fine.example");
    }

    @Test
    void domainRescanLocksTheDomainAndItsHostsInSortedOrderFirst() {
        when(links.findHostsUnder("d.example")).thenReturn(List.of("www.d.example", "a.d.example"));

        service.rescanDomain("d.example", false);

        InOrder order = Mockito.inOrder(links);
        order.verify(links).lockHost("a.d.example");
        order.verify(links).lockHost("d.example");
        order.verify(links).lockHost("www.d.example");
        order.verify(links).clearAutomaticFlags("www.d.example");
    }

    @Test
    void rescanAllBackfillsMissingHostsAndSurvivesFailures() {
        UUID id = UUID.randomUUID();
        when(links.findWithoutHost(any(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[] {id, "https://Shop.Example/x"}))
                .thenReturn(List.of());
        when(links.findDistinctHosts()).thenThrow(new IllegalStateException("db down"));

        service.rescanAll();

        verify(links).setHost(id, "shop.example");
    }
}
