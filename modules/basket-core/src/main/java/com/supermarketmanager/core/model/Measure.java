package com.supermarketmanager.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Every quantity is kept in one of three base units: kilograms, litres or pieces. */
public enum Measure {
    KG("kg"), L("l"), EACH("each");

    private final String code;

    Measure(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Measure of(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (Measure m : values()) {
            if (m.code.equals(v) || m.name().equalsIgnoreCase(v)) return m;
        }
        throw new IllegalArgumentException("Unknown measure '" + value + "' (expected kg, l or each)");
    }
}
