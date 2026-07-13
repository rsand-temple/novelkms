package com.richardsand.novelkms.service;

/**
 * A precondition failure in a review-network operation that the resource layer
 * translates into a specific HTTP response — e.g. the author has not claimed a
 * handle yet (409 {@code profile_required}), the chapter has no text to publish
 * (400 {@code empty_chapter}), or a lifecycle move is not legal from the current
 * status (409 {@code invalid_transition}).
 *
 * <p>Modeled on {@link TrashException}: the service decides <em>what</em> went
 * wrong and what it means in HTTP terms; the resource only forwards it, so the
 * rule and its status code stay in the same place.
 */
public class ReviewException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int    status;
    private final String code;

    public ReviewException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
