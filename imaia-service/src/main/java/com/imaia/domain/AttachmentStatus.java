package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Download state of a message attachment. */
public enum AttachmentStatus {
    PENDING,
    DOWNLOADED,
    FAILED,
    SKIPPED;

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
    public static AttachmentStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AttachmentStatus 不能为空。");
        }
        try {
            return AttachmentStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 AttachmentStatus 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<AttachmentStatus, String> {

        @Override
        public String convertToDatabaseColumn(AttachmentStatus attribute) {
            return attribute == null ? PENDING.value() : attribute.value();
        }

        @Override
        public AttachmentStatus convertToEntityAttribute(String dbData) {
            return AttachmentStatus.fromValue(dbData);
        }
    }
}
