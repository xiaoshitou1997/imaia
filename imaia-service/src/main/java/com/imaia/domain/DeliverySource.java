package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** What triggered an outbound delivery. */
public enum DeliverySource {
    AI_REPLY,
    SELF_COMMAND,
    TASK,
    MANUAL,
    API;

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
    public static DeliverySource fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeliverySource 不能为空。");
        }
        try {
            return DeliverySource.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 DeliverySource 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<DeliverySource, String> {

        @Override
        public String convertToDatabaseColumn(DeliverySource attribute) {
            return attribute == null ? MANUAL.value() : attribute.value();
        }

        @Override
        public DeliverySource convertToEntityAttribute(String dbData) {
            return DeliverySource.fromValue(dbData);
        }
    }
}
