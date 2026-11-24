package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Chat-directory slot resolved to exactly one catalog model (chat core; tools are separate config). */
public enum ModelSlot {
    CHAT,
    VISION;

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
    public static ModelSlot fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ModelSlot 不能为空。");
        }
        try {
            return ModelSlot.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ModelSlot 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ModelSlot, String> {

        @Override
        public String convertToDatabaseColumn(ModelSlot attribute) {
            return attribute == null ? CHAT.value() : attribute.value();
        }

        @Override
        public ModelSlot convertToEntityAttribute(String dbData) {
            return ModelSlot.fromValue(dbData);
        }
    }
}
