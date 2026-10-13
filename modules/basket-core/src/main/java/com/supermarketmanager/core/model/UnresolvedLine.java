package com.supermarketmanager.core.model;

/**
 * A line that ended up without a purchase.
 * UNFULFILLED: no acceptable candidate at all. LEFT_OUT: candidates exist, but not within the store limit.
 */
public record UnresolvedLine(String lineId, String label, Status status, String reason) {
    public enum Status { UNFULFILLED, LEFT_OUT }
}
