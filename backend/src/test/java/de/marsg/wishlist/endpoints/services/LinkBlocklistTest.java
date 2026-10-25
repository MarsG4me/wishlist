package de.marsg.wishlist.endpoints.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LinkBlocklistTest {

    private static LinkBlocklist listOf(String... domains) {
        long[] hashes = Arrays.stream(domains).mapToLong(LinkBlocklist::hash).sorted().toArray();
        LinkBlocklist list = new LinkBlocklist();
        list.swap(hashes);
        return list;
    }

    @Test
    void matchesHostAndParentDomains() {
        LinkBlocklist list = listOf("evil.com", "bad.example.org");
        assertTrue(list.contains("evil.com"));
        assertTrue(list.contains("A.B.Evil.com"));
        assertTrue(list.contains("bad.example.org"));
        assertFalse(list.contains("example.org"));
        assertFalse(list.contains("notevil.com"));
        assertFalse(list.contains("evil.com.good.net"));
    }

    @Test
    void parsesHostsAndDomainLines() {
        assertEquals("evil.com", LinkBlocklist.parseLine("0.0.0.0 Evil.com"));
        assertEquals("evil.com", LinkBlocklist.parseLine("127.0.0.1\tevil.com # note"));
        assertEquals("evil.com", LinkBlocklist.parseLine("evil.com"));
        assertNull(LinkBlocklist.parseLine("# comment"));
        assertNull(LinkBlocklist.parseLine("   "));
        assertNull(LinkBlocklist.parseLine("127.0.0.1 localhost"));
    }
}
