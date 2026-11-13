package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Who really sent the message: the human owner or the assistant itself. */
public enum MessageOrigin {
    HUMAN, AI;

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
    public static MessageOrigin fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageOrigin 值不能为空。");
        }
        try {
            return MessageOrigin.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 MessageOrigin 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<MessageOrigin, String> {

        @Override
        public String convertToDatabaseColumn(MessageOrigin attribute) {
            return attribute == null ? HUMAN.value() : attribute.value();
        }

        @Override
        public MessageOrigin convertToEntityAttribute(String dbData) {
            return MessageOrigin.fromValue(dbData);
        }
    }
}
