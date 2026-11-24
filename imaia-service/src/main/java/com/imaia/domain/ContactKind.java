package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Kind of an IM contact identity. */
public enum ContactKind {
    USER,
    BOT,
    SYSTEM;

    /** The stored/API representation (lowercase). */
    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value();
    }

    /** Maps a stored/API value; unknown values are a client error. */
    @JsonCreator
    public static ContactKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ContactKind 不能为空。");
        }
        try {
            return ContactKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ContactKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ContactKind, String> {

        @Override
        public String convertToDatabaseColumn(ContactKind attribute) {
            return attribute == null ? USER.value() : attribute.value();
        }

        @Override
        public ContactKind convertToEntityAttribute(String dbData) {
            return ContactKind.fromValue(dbData);
        }
    }
}
