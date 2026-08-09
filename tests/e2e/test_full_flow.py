"""Browser end-to-end flow: wishlist -> wish -> group -> invite -> join -> share
-> claim -> owner-invisibility. Runs against the real stack (Keycloak login included),
so it catches anything the API-level tests cannot see: routing, auth redirects,
CORS, JSON naming, UI wiring.
"""

import time

from playwright.sync_api import Page, expect

UNIQUE = str(int(time.time()))
LIST_NAME = f"E2E List {UNIQUE}"
WISH_NAME = f"E2E Wish {UNIQUE}"
GROUP_NAME = f"E2E Group {UNIQUE}"


def test_full_flow(as_user):
    alice: Page = as_user("alice")

    # --- alice creates a wishlist with one wish ---
    alice.get_by_role("button", name="New wishlist").click()
    alice.fill("#list-name", LIST_NAME)
    alice.get_by_role("button", name="Save").click()
    card = alice.locator(".p-card", has_text=LIST_NAME)
    expect(card).to_be_visible()
    card.get_by_role("button", name="Open").click()

    alice.get_by_role("button", name="New wish").click()
    alice.fill("#wish-name", WISH_NAME)
    alice.get_by_role("button", name="Save").click()
    expect(alice.locator(".p-card", has_text=WISH_NAME)).to_be_visible()

    # --- alice creates a group and an invite ---
    alice.goto("http://localhost:5173/groups")
    alice.get_by_role("button", name="New group").click()
    alice.fill("#group-name", GROUP_NAME)
    alice.get_by_role("button", name="Create").click()
    group_card = alice.locator(".p-card", has_text=GROUP_NAME)
    expect(group_card).to_be_visible()
    group_card.get_by_role("button", name="Open").click()

    alice.get_by_role("tab", name="Invites").click()
    alice.get_by_role("button", name="New invite").click()
    invite_link = alice.locator(".invite-code-box code").inner_text(timeout=10_000)
    assert "/join/" in invite_link
    alice.keyboard.press("Escape")

    # --- alice shares the wishlist into the group ---
    alice.get_by_role("tab", name="Shared wishlists").click()
    alice.get_by_role("button", name="Share one of my wishlists").click()
    alice.get_by_role("option", name=LIST_NAME).click()
    alice.get_by_role("button", name="Share", exact=True).click()
    expect(alice.locator(".p-card", has_text=LIST_NAME)).to_be_visible()

    # --- bob joins via the invite link and claims the wish ---
    bob: Page = as_user("bob")
    bob.goto(invite_link)
    expect(bob.get_by_role("tab", name="Shared wishlists")).to_be_visible(timeout=15_000)
    shared_card = bob.locator(".p-card", has_text=LIST_NAME)
    expect(shared_card).to_be_visible()
    shared_card.get_by_role("button", name="Open", exact=True).click()

    wish_card = bob.locator(".p-card", has_text=WISH_NAME)
    expect(wish_card).to_be_visible()
    wish_card.get_by_role("button", name="Claim").click()
    expect(wish_card.locator(".p-chip", has_text="Bob")).to_be_visible()

    # --- bob sees his claim in the overview ---
    bob.goto("http://localhost:5173/claims")
    expect(bob.locator("tr", has_text=WISH_NAME)).to_be_visible()

    # --- the owner must never see the claim ---
    alice.goto("http://localhost:5173/")
    alice.locator(".p-card", has_text=LIST_NAME).get_by_role("button", name="Open").click()
    owner_card = alice.locator(".p-card", has_text=WISH_NAME)
    expect(owner_card).to_be_visible()
    expect(owner_card.locator(".p-chip")).to_have_count(0)
    assert "Bob" not in owner_card.inner_text()
