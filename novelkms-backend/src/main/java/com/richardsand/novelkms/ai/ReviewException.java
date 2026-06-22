package com.richardsand.novelkms.ai;

/**
 * A configuration or precondition failure during review orchestration that the
 * resource layer should translate into a specific HTTP response (e.g. no
 * credential configured, non-manuscript chapter, empty chapter). Provider call
 * failures are NOT represented here — those are recorded on a FAILED review
 * artifact instead.
 */
public class ReviewException extends RuntimeException {
    private static final long serialVersionUID = -7924295589556151928L;
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
