package com.supermarketmanager.pantry;

public record StockEvent(long id, String at, String kind, Subject subject, double delta, double resulting,
                         Double price, String retailer, String reference, String note) {
}
