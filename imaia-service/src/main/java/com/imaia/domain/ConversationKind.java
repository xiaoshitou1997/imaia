package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Conversation line shape: direct chat or group chat. */
public enum ConversationKind {
    DIRECT, GROUP;

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
    public static ConversationKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConversationKind 值不能为空。");
        }
        try {
            return ConversationKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ConversationKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ConversationKind, String> {

        @Override
        public String convertToDatabaseColumn(ConversationKind attribute) {
            return attribute == null ? DIRECT.value() : attribute.value();
        }

        @Override
        public ConversationKind convertToEntityAttribute(String dbData) {
            return ConversationKind.fromValue(dbData);
        }
    }
}
