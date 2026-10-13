package com.supermarketmanager.core;

/** Thrown by any module when a referenced entity does not exist; mapped to HTTP 404 by the app. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
