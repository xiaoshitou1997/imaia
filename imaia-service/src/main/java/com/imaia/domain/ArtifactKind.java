package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Media kind of an AI-generated artifact. */
public enum ArtifactKind {
    IMAGE, VIDEO;

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
    public static ArtifactKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ArtifactKind 值不能为空。");
        }
        try {
            return ArtifactKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ArtifactKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ArtifactKind, String> {

        @Override
        public String convertToDatabaseColumn(ArtifactKind attribute) {
            return attribute == null ? IMAGE.value() : attribute.value();
        }

        @Override
        public ArtifactKind convertToEntityAttribute(String dbData) {
            return ArtifactKind.fromValue(dbData);
        }
    }
}
