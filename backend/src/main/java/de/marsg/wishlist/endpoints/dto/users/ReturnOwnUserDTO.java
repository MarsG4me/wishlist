package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.entities.UserTier;
import java.util.UUID;

/**
 * @param locale           interface language of this account; the frontend renders in it
 *                         instead of guessing from the browser.
 * @param identityProvider external login this account uses (Google, Apple, …), or
 *                         {@code null} for a local account. When set, email and
 *                         password are managed at the provider and the app hides
 *                         those forms.
 */
public record ReturnOwnUserDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("displayname") String displayname,
        @JsonProperty("email") String email,
        @JsonProperty("tier") UserTier tier,
        @JsonProperty("locale") UserLocale locale,
        @JsonProperty("identity_provider") String identityProvider) {

    public static ReturnOwnUserDTO from(UserEntity user, String identityProvider) {
        return new ReturnOwnUserDTO(user.getId(), user.getDisplayname(), user.getEmail(), user.getTier(),
                user.getLocale(), identityProvider);
    }
}
