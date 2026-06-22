package com.richardsand.novelkms.service;

/**
 * A precondition failure during a trash operation that the resource layer
 * translates into a specific HTTP response — e.g. the batch is not found (404)
 * or a restore is blocked because the parent is itself in the trash or has been
 * permanently removed (409).
 */
public class TrashException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int    status;
    private final String code;

    public TrashException(int status, String code, String message) {
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
