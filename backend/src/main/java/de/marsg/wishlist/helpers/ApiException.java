package de.marsg.wishlist.helpers;

/** Business error carrying a stable {@link ErrorCode}; rendered by the GlobalExceptionHandler. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Integer linkIndex;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public ApiException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public ApiException(ErrorCode errorCode, String detail, Integer linkIndex) {
        super(detail);
        this.errorCode = errorCode;
        this.linkIndex = linkIndex;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 0-based index of the offending entry in the request's links, or null. */
    public Integer getLinkIndex() {
        return linkIndex;
    }

    public ApiException atLink(int index) {
        return new ApiException(errorCode, getMessage(), index);
    }
}
