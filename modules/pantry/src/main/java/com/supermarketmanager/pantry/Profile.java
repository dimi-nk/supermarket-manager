package com.supermarketmanager.pantry;

/** A named target pantry ("Regular", "Low-carb from November"). Exactly one may be active. */
public record Profile(String id, String name, String description, boolean active, String createdAt) {
}
