package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Normalized message content category. */
public enum MessageKind {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    STICKER,
    FILE,
    RICH,
    OTHER;

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
    public static MessageKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageKind 不能为空。");
        }
        try {
            return MessageKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 MessageKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<MessageKind, String> {

        @Override
        public String convertToDatabaseColumn(MessageKind attribute) {
            return attribute == null ? OTHER.value() : attribute.value();
        }

        @Override
        public MessageKind convertToEntityAttribute(String dbData) {
            return MessageKind.fromValue(dbData);
        }
    }
}
