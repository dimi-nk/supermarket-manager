package com.supermarketmanager.core;

/** Thrown when an operation conflicts with current state (e.g. applying a basket twice); mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
