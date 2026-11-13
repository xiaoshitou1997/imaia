package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Lifecycle state of a long-term memory (only active exists in v1). */
public enum MemoryStatus {
    ACTIVE;

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
    public static MemoryStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MemoryStatus 值不能为空。");
        }
        try {
            return MemoryStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 MemoryStatus 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<MemoryStatus, String> {

        @Override
        public String convertToDatabaseColumn(MemoryStatus attribute) {
            return attribute == null ? ACTIVE.value() : attribute.value();
        }

        @Override
        public MemoryStatus convertToEntityAttribute(String dbData) {
            return MemoryStatus.fromValue(dbData);
        }
    }
}
