package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Kind of a message attachment. */
public enum AttachmentKind {
    IMAGE,
    VOICE,
    VIDEO,
    STICKER,
    FILE,
    PREVIEW;

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
    public static AttachmentKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AttachmentKind 不能为空。");
        }
        try {
            return AttachmentKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 AttachmentKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<AttachmentKind, String> {

        @Override
        public String convertToDatabaseColumn(AttachmentKind attribute) {
            return attribute == null ? IMAGE.value() : attribute.value();
        }

        @Override
        public AttachmentKind convertToEntityAttribute(String dbData) {
            return AttachmentKind.fromValue(dbData);
        }
    }
}
