package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Role of one message in the model context stream. */
public enum ChatRole {
    USER, ASSISTANT, TOOL;

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
    public static ChatRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ChatRole 值不能为空。");
        }
        try {
            return ChatRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ChatRole 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ChatRole, String> {

        @Override
        public String convertToDatabaseColumn(ChatRole attribute) {
            return attribute == null ? USER.value() : attribute.value();
        }

        @Override
        public ChatRole convertToEntityAttribute(String dbData) {
            return ChatRole.fromValue(dbData);
        }
    }
}
