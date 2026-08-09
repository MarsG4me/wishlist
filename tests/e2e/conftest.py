"""Shared fixtures for the browser end-to-end tests.

The tests expect a running dev stack:
  docker compose up -d          (Postgres 18 + Keycloak with the wishlist-dev realm)
  backend on   http://localhost:6543
  frontend on  http://localhost:5173

CI (.github/workflows/ci.yml) brings all of that up before running pytest.
"""

import pytest
from playwright.sync_api import Browser, Page

APP_URL = "http://localhost:5173"

USERS = {
    "alice": ("alice@test.local", "test"),
    "bob": ("bob@test.local", "test"),
    "carol": ("carol@test.local", "test"),
    "admin": ("admin@test.local", "test"),  # has the `admin` realm role
}


def login(page: Page, user: str) -> None:
    """Opens the app and walks through the Keycloak login form."""
    username, password = USERS[user]
    page.goto(APP_URL)
    page.wait_for_url("**/realms/wishlist-dev/**", timeout=15_000)
    page.fill("#username", username)
    page.fill("#password", password)
    page.click("#kc-login")
    page.wait_for_url(f"{APP_URL}/**", timeout=15_000)
    # shell renders once /v1/users/me answered
    page.get_by_role("menubar").wait_for(timeout=15_000)


@pytest.fixture()
def as_user(browser: Browser):
    """Returns a factory creating an isolated logged-in page per user."""
    contexts = []

    def _make(user: str) -> Page:
        context = browser.new_context()
        contexts.append(context)
        page = context.new_page()
        login(page, user)
        return page

    yield _make
    for context in contexts:
        context.close()
