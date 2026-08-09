"""Interface language: the profile dropdown writes through our own backend
(PATCH /v1/users/me/locale) and the choice survives a reload. Previously this went
straight to the Keycloak Account API, which answered 401 for the app token.
"""

from playwright.sync_api import Page, expect

PROFILE_URL = "http://localhost:5173/profile"


def pick_language(page: Page, label: str) -> None:
    card = page.locator(".p-card", has_text="Language")
    card.locator(".p-dropdown").click()
    # PrimeReact renders the options in an overlay panel, not in the native select
    page.locator(".p-dropdown-panel .p-dropdown-item", has_text=label).click()
    expect(card.locator(".p-dropdown-label")).to_have_text(label)


def test_language_change_is_saved_by_our_backend(as_user):
    carol: Page = as_user("carol")
    carol.goto(PROFILE_URL)

    with carol.expect_response(
        lambda r: "/v1/users/me/locale" in r.url and r.request.method == "PATCH"
    ) as saved:
        pick_language(carol, "Deutsch")
    response = saved.value
    assert response.status == 200, response.text()
    assert response.json()["locale"] == "DE"

    # success toast, not the error one the 401 used to produce
    expect(carol.locator(".p-toast-message", has_text="Language saved")).to_be_visible()
    expect(carol.locator(".p-toast-message-error")).to_have_count(0)

    # the account setting, not a browser guess: it is still there after a reload
    carol.reload()
    expect(carol.locator(".p-card", has_text="Language").locator(".p-dropdown-label")).to_have_text(
        "Deutsch"
    )

    # back to English so repeated runs stay idempotent
    expect(carol.locator(".p-toast-message")).to_have_count(0)
    pick_language(carol, "English")


def test_language_is_settable_without_keycloak_account_permissions(as_user):
    """The old flow needed the `account` audience in the user's token; ours needs
    nothing but a valid session, so a plain user can switch languages."""
    bob: Page = as_user("bob")
    bob.goto(PROFILE_URL)

    pick_language(bob, "Deutsch")
    expect(bob.locator(".p-toast-message-error")).to_have_count(0)

    bob.goto("http://localhost:5173/")
    bob.goto(PROFILE_URL)
    expect(bob.locator(".p-card", has_text="Language").locator(".p-dropdown-label")).to_have_text(
        "Deutsch"
    )

    expect(bob.locator(".p-toast-message")).to_have_count(0)
    pick_language(bob, "English")
