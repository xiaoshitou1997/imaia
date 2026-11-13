package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Where a catalog model came from: gateway sync or manual entry. */
public enum ModelSource {
    SYNC, MANUAL;

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
    public static ModelSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ModelSource 值不能为空。");
        }
        try {
            return ModelSource.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ModelSource 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ModelSource, String> {

        @Override
        public String convertToDatabaseColumn(ModelSource attribute) {
            return attribute == null ? SYNC.value() : attribute.value();
        }

        @Override
        public ModelSource convertToEntityAttribute(String dbData) {
            return ModelSource.fromValue(dbData);
        }
    }
}
