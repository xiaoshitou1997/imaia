package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import java.util.Locale;

/**
 * Normalized message content category. Enum end-to-end: adapters map their native
 * message types into it, core logic switches on it, and the archived column stores
 * the lowercase value (matching the V10 backfill) through {@link Converter}.
 */
public enum MessageCategory {
    TEXT, IMAGE, VOICE, VIDEO, STICKER, FILE, RICH, OTHER;

    /** The stored/API representation (lowercase). */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value();
    }

    /** Maps a stored/API value; unknown or absent values degrade to OTHER. */
    public static MessageCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return MessageCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            return OTHER;
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<MessageCategory, String> {

        @Override
        public String convertToDatabaseColumn(MessageCategory attribute) {
            return attribute == null ? OTHER.value() : attribute.value();
        }

        @Override
        public MessageCategory convertToEntityAttribute(String dbData) {
            return fromValue(dbData);
        }
    }
}
