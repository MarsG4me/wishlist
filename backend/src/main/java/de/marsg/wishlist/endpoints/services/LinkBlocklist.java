package de.marsg.wishlist.endpoints.services;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * In-memory domain blocklist stored as sorted 64-bit hashes (millions of entries stay
 * in tens of MB). A host matches when it or any parent domain is listed; swap is atomic.
 */
@Component
public class LinkBlocklist {

    private volatile long[] hashes = new long[0];

    public int size() {
        return hashes.length;
    }

    /** Replaces the whole set; the array must be sorted and deduplicated. */
    void swap(long[] sortedHashes) {
        this.hashes = sortedHashes;
    }

    /** True when the host or one of its parent domains (never the bare TLD) is listed. */
    public boolean contains(String host) {
        long[] snapshot = hashes;
        if (host == null || snapshot.length == 0) {
            return false;
        }
        String candidate = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (Arrays.binarySearch(snapshot, hash(candidate)) >= 0) {
                return true;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) {
                return false;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    /** Extracts the domain from a hosts-file ("0.0.0.0 d") or domain-only line; null if none. */
    static String parseLine(String line) {
        int hash = line.indexOf('#');
        String content = (hash >= 0 ? line.substring(0, hash) : line).trim();
        if (content.isEmpty()) {
            return null;
        }
        String[] tokens = content.split("\\s+");
        String domain = tokens[tokens.length - 1].toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (domain.isEmpty() || domain.equals("localhost") || domain.indexOf('/') >= 0
                || domain.indexOf(':') >= 0) {
            return null;
        }
        return domain;
    }

    /** FNV-1a 64 with a murmur finalizer; collisions are negligible at this set size. */
    static long hash(String domain) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < domain.length(); i++) {
            h = (h ^ domain.charAt(i)) * 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }
}
