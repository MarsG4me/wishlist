package de.marsg.wishlist.endpoints.services;

/** Published when the blocklist was (re)loaded at startup or daily; saved links get rescanned. */
public record LinkListsChangedEvent(boolean blocklistComplete) {
}
