package de.marsg.wishlist.helpers;

import org.springframework.http.HttpStatus;

/**
 * Stable error catalogue. The code is what the frontend translates; the message is
 * English and aimed at direct API users. Codes must never be renamed once released.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MISSING_REQUEST_BODY(HttpStatus.BAD_REQUEST, "Request body is missing or malformed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to perform this action"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested route does not exist"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not supported for this route"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This content type is not supported"),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the data"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "An account with this email already exists. If it was deleted, sign in with the "
                    + "original login to reactivate it instead of creating a new account"),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN,
            "This account was deleted; sign in again to restore it"),
    USER_HAS_NO_COMMON_GROUP(HttpStatus.FORBIDDEN, "You share no group with this user"),

    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "Wishlist not found"),
    NOT_WISHLIST_OWNER(HttpStatus.FORBIDDEN, "You are not the owner of this wishlist"),
    WISHLIST_LIMIT_REACHED(HttpStatus.CONFLICT, "You reached the maximum number of wishlists"),
    WISHLIST_ALREADY_SHARED(HttpStatus.CONFLICT, "This wishlist is already shared into this group"),
    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "This wishlist is not shared into this group"),

    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "Wish not found"),
    WISH_LIMIT_REACHED(HttpStatus.CONFLICT, "This wishlist reached its maximum number of wishes"),
    WISH_HAS_CLAIMS(HttpStatus.CONFLICT,
            "This wish already has claims; deleting it requires confirmation"),

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Group not found"),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "You are not a member of this group"),
    NOT_GROUP_ADMIN(HttpStatus.FORBIDDEN, "You are not an admin of this group"),
    GROUP_LIMIT_REACHED(HttpStatus.CONFLICT, "You reached the maximum number of owned groups"),

    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "Membership not found"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "You are already a member of this group"),
    LAST_ADMIN_CANNOT_LEAVE(HttpStatus.CONFLICT,
            "The last admin cannot leave the group; promote another member first"),
    LAST_ADMIN_CANNOT_BE_DEMOTED(HttpStatus.CONFLICT,
            "The last admin cannot be demoted; promote another member first"),

    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "Invite not found"),
    INVITE_EXPIRED(HttpStatus.GONE, "This invite has expired"),
    INVITE_REVOKED(HttpStatus.GONE, "This invite has been revoked"),
    INVITE_EXHAUSTED(HttpStatus.GONE, "This invite has no uses left"),

    INVALID_LINK_URL(HttpStatus.BAD_REQUEST, "The link must be a public http or https URL"),
    LINK_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "This link was blocked by our safety checks"),

    CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "Claim not found"),
    CANNOT_CLAIM_OWN_WISH(HttpStatus.FORBIDDEN, "You cannot claim your own wish"),
    ALREADY_CLAIMED(HttpStatus.CONFLICT, "You already claim or participate in a claim of this wish"),
    WISH_FULLY_CLAIMED(HttpStatus.CONFLICT, "All units of this wish are already claimed"),
    CLAIM_NOT_SHARED(HttpStatus.FORBIDDEN,
            "This claim is not open for co-claimers; only its creator can open it up"),
    NOT_CLAIM_OWNER(HttpStatus.FORBIDDEN, "Only the creator of this claim can change it"),

    INVALID_CREDENTIALS(HttpStatus.FORBIDDEN, "The current password is incorrect"),
    ACCOUNT_MANAGED_EXTERNALLY(HttpStatus.CONFLICT,
            "This account signs in through an external provider; change email and password there"),
    KEYCLOAK_UPDATE_FAILED(HttpStatus.BAD_GATEWAY, "The account update was rejected by the identity provider"),
    KEYCLOAK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The identity provider is currently unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
