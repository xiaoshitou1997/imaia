package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Message direction in the archive: incoming (peer) or outgoing (owner). */
public enum MessageDirection {
    INCOMING, OUTGOING;

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
    public static MessageDirection fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageDirection 值不能为空。");
        }
        try {
            return MessageDirection.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 MessageDirection 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<MessageDirection, String> {

        @Override
        public String convertToDatabaseColumn(MessageDirection attribute) {
            return attribute == null ? INCOMING.value() : attribute.value();
        }

        @Override
        public MessageDirection convertToEntityAttribute(String dbData) {
            return MessageDirection.fromValue(dbData);
        }
    }
}
