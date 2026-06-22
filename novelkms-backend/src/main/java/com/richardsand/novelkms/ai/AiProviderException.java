package com.richardsand.novelkms.ai;

/**
 * Raised when an AI provider call fails — network error, authentication failure,
 * an error payload returned by the provider, or an unparseable response. The
 * message is suitable for surfacing to the user (it is recorded on the FAILED
 * review artifact).
 */
public class AiProviderException extends Exception {
    private static final long serialVersionUID = 444532874849301662L;

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
