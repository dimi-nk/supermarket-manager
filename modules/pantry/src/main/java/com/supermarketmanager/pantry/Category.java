package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Measure;

/** A generic kind of thing ("Strained yoghurt"). Categories nest; stock below a category counts towards it. */
public record Category(String id, String name, String parentId, Measure measure, String searchQuery, String notes) {
}
