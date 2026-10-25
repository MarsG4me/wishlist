"""Link safety: a group member reports a link, the admin blocks it, the owner sees the
blocked badge and other members no longer see the link.
"""

import time

from playwright.sync_api import Page, expect

UNIQUE = str(int(time.time()))
LIST_NAME = f"E2E Report List {UNIQUE}"
WISH_NAME = f"E2E Report Wish {UNIQUE}"
GROUP_NAME = f"E2E Report Group {UNIQUE}"
LINK_URL = "https://example.com/x"


def test_report_block_flow(as_user):
    alice: Page = as_user("alice")

    # --- alice: wishlist with one wish that carries a link ---
    alice.get_by_role("button", name="New wishlist").click()
    alice.fill("#list-name", LIST_NAME)
    alice.get_by_role("button", name="Save").click()
    alice.locator(".p-card", has_text=LIST_NAME).get_by_role("link", name=LIST_NAME).click()

    alice.get_by_role("button", name="New wish").click()
    alice.fill("#wish-name", WISH_NAME)
    alice.get_by_role("button", name="Add link").click()
    alice.get_by_placeholder("https://…").fill(LINK_URL)
    alice.get_by_role("button", name="Save").click()
    expect(alice.locator(".p-card", has_text=WISH_NAME).get_by_role("link", name="example.com")).to_be_visible()

    # --- alice: group, invite, share the wishlist ---
    alice.goto("http://localhost:5173/groups")
    alice.get_by_role("button", name="New group").click()
    alice.fill("#group-name", GROUP_NAME)
    alice.get_by_role("button", name="Create").click()
    alice.locator(".p-card", has_text=GROUP_NAME).get_by_role("link", name=GROUP_NAME).click()

    alice.get_by_role("tab", name="Invites").click()
    alice.get_by_role("button", name="New invite").first.click()
    invite_link = alice.locator(".invite-code-box code").inner_text(timeout=10_000)
    alice.keyboard.press("Escape")

    alice.get_by_role("tab", name="Shared wishlists").click()
    alice.get_by_role("button", name="Share wishlist").first.click()
    alice.get_by_role("button", name="Select one of my wishlists").click()
    alice.get_by_role("option", name=LIST_NAME).click()
    alice.get_by_role("button", name="Add", exact=True).click()
    expect(alice.locator(".p-card", has_text=LIST_NAME)).to_be_visible()

    # --- bob joins and reports the link; the owner never gets a report button ---
    bob: Page = as_user("bob")
    bob.goto(invite_link)
    bob.get_by_role("button", name="Join group").click()
    expect(bob.get_by_role("tab", name="Shared wishlists")).to_be_visible(timeout=15_000)
    bob.locator(".p-card", has_text=LIST_NAME).get_by_role("link", name=LIST_NAME).click()
    bob_card = bob.locator(".p-card", has_text=WISH_NAME)
    expect(bob_card.get_by_role("link", name="example.com")).to_be_visible()
    bob_card.get_by_role("button", name="Report").click()
    bob.locator(".p-dialog .p-dropdown").click()
    bob.locator(".p-dropdown-panel .p-dropdown-item", has_text="Scam").click()
    bob.get_by_role("button", name="Send report").click()
    expect(bob.locator(".p-toast", has_text="reported")).to_be_visible(timeout=10_000)

    alice.goto("http://localhost:5173/")
    alice.locator(".p-card", has_text=LIST_NAME).get_by_role("link", name=LIST_NAME).click()
    expect(alice.locator(".p-card", has_text=WISH_NAME)).to_be_visible()
    expect(alice.get_by_role("button", name="Report")).to_have_count(0)

    # --- admin blocks the link ---
    admin: Page = as_user("admin")
    admin.get_by_role("menuitem", name="Admin").click()
    row = admin.locator("#admin-link-reports tr", has_text=WISH_NAME)
    expect(row).to_be_visible(timeout=10_000)
    row.get_by_role("button", name="Decide").click()
    admin.get_by_label("Block this link").check()
    admin.get_by_role("button", name="Apply").click()
    expect(admin.locator(".p-toast", has_text="Report decided")).to_be_visible(timeout=10_000)

    # --- alice sees the badge, bob no longer sees the link ---
    alice.reload()
    owner_card = alice.locator(".p-card", has_text=WISH_NAME)
    expect(owner_card.get_by_text("Hidden from others")).to_be_visible(timeout=10_000)

    bob.reload()
    expect(bob.locator(".p-card", has_text=WISH_NAME)).to_be_visible()
    expect(bob.get_by_role("link", name="example.com")).to_have_count(0)
