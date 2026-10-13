package com.supermarketmanager.pantry;

public record StockEntry(Subject subject, String name, String measure, double amount, String updatedAt) {
}
