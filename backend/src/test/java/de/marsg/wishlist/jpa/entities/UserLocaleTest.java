package de.marsg.wishlist.jpa.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserLocaleTest {

    @ParameterizedTest
    @CsvSource({"de,DE", "de-DE,DE", "DE_at,DE", "en,EN", "en-GB,EN"})
    void languageTagsMapToShippedLanguages(String tag, UserLocale expected) {
        assertThat(UserLocale.fromTag(tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"d", "fr", "zh-Hant"})
    void unknownOrMissingTagsFallBackToEnglish(String tag) {
        assertThat(UserLocale.fromTag(tag)).isEqualTo(UserLocale.EN);
    }

    @Test
    void tagIsTheLowercaseFormKeycloakAndTheFrontendUse() {
        assertThat(UserLocale.DE.tag()).isEqualTo("de");
        assertThat(UserLocale.EN.tag()).isEqualTo("en");
    }
}
