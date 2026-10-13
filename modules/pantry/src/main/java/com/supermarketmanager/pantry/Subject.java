package com.supermarketmanager.pantry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What a target or stock entry refers to: a generic category or a concrete catalog product. Serialised as "category:yoghurt". */
public record Subject(Type type, String id) {

    public enum Type {
        CATEGORY, PRODUCT;

        public String code() {
            return name().toLowerCase();
        }

        public static Type of(String s) {
            return Type.valueOf(s.trim().toUpperCase());
        }
    }

    public Subject {
        if (type == null || id == null || id.isBlank()) throw new IllegalArgumentException("Subject needs a type and an id");
    }

    public static Subject category(String id) {
        return new Subject(Type.CATEGORY, id);
    }

    public static Subject product(String id) {
        return new Subject(Type.PRODUCT, id);
    }

    @JsonCreator
    public static Subject parse(String key) {
        int i = key == null ? -1 : key.indexOf(':');
        if (i <= 0) throw new IllegalArgumentException("Expected 'category:<id>' or 'product:<id>', got '" + key + "'");
        return new Subject(Type.of(key.substring(0, i)), key.substring(i + 1));
    }

    public static boolean isKey(String key) {
        return key != null && (key.startsWith("category:") || key.startsWith("product:"));
    }

    @JsonValue
    public String key() {
        return type.code() + ":" + id;
    }

    @Override
    public String toString() {
        return key();
    }
}
