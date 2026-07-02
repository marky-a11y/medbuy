package com.autoresolve.mediabuying.exception;

public class MediaBuyingException extends RuntimeException {

    public MediaBuyingException(String message) {
        super(message);
    }

    public MediaBuyingException(String message, Throwable cause) {
        super(message, cause);
    }
}
