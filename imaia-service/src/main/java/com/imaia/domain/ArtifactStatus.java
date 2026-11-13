package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Delivery state of an AI-generated artifact. */
public enum ArtifactStatus {
    GENERATED, DELIVERED, FAILED;

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
    public static ArtifactStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ArtifactStatus 值不能为空。");
        }
        try {
            return ArtifactStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ArtifactStatus 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ArtifactStatus, String> {

        @Override
        public String convertToDatabaseColumn(ArtifactStatus attribute) {
            return attribute == null ? GENERATED.value() : attribute.value();
        }

        @Override
        public ArtifactStatus convertToEntityAttribute(String dbData) {
            return ArtifactStatus.fromValue(dbData);
        }
    }
}
