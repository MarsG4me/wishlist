"""Admin console: stats visible, email search, tier switching, and the guarantee that
normal users neither see nor reach the admin page.
"""

import re

from playwright.sync_api import Page, expect


def test_admin_console(as_user):
    admin: Page = as_user("admin")

    expect(admin.get_by_role("menuitem", name="Admin")).to_be_visible()
    admin.get_by_role("menuitem", name="Admin").click()

    # counts render as plain numbers, no links into anyone's content
    for card in ("Users", "Wishlists", "Wishes"):
        stat = admin.locator(".p-card", has_text=card).locator(".stat-number")
        expect(stat).to_have_text(re.compile(r"^\d+$"))
        expect(stat.locator("a")).to_have_count(0)

    # search by email and flip the tier
    admin.get_by_placeholder("email…").fill("carol@test.local")
    admin.get_by_role("button", name="Search").click()
    row = admin.locator("tr", has_text="carol@test.local").first
    expect(row).to_be_visible()

    def pick_tier(tier: str) -> None:
        # the success toast sits bottom-right and can cover the table, so let it fade first
        expect(admin.locator(".p-toast-message")).to_have_count(0)
        row.locator(".p-dropdown").click()
        # PrimeReact keeps a hidden native <select> around, so target the overlay list
        admin.locator(".p-dropdown-panel .p-dropdown-item", has_text=tier).click()
        expect(row.locator(".p-dropdown-label")).to_have_text(tier)

    pick_tier("PREMIUM")
    # switch back so repeated runs stay idempotent
    pick_tier("FREE")


def test_normal_user_has_no_admin_access(as_user):
    bob: Page = as_user("bob")
    expect(bob.get_by_role("menuitem", name="Admin")).to_have_count(0)

    # direct navigation renders the page shell but the API refuses the data
    bob.goto("http://localhost:5173/admin")
    expect(bob.locator(".p-toast", has_text="not allowed")).to_be_visible(timeout=10_000)
