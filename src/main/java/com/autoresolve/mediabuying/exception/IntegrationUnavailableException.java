package com.autoresolve.mediabuying.exception;

public class IntegrationUnavailableException extends MediaBuyingException {

    public IntegrationUnavailableException(String message) {
        super(message);
    }

    public IntegrationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
