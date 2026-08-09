package de.marsg.wishlist.jpa.entities;

import java.util.Locale;

/** Mirrors the native Postgres enum type {@code user_locale}; one value per shipped UI language. */
public enum UserLocale {
    DE,
    EN;

    /** Maps a JWT/Keycloak locale claim ("de", "de-DE", …) to a shipped language, defaulting to EN. */
    public static UserLocale fromTag(String languageTag) {
        if (languageTag == null || languageTag.length() < 2) {
            return EN;
        }
        String prefix = languageTag.substring(0, 2).toUpperCase(Locale.ROOT);
        for (UserLocale locale : values()) {
            if (locale.name().equals(prefix)) {
                return locale;
            }
        }
        return EN;
    }

    /** Lowercase language tag as Keycloak and the frontend catalogues expect it. */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
