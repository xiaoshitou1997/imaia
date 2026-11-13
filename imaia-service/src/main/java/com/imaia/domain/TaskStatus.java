package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Scheduled task lifecycle state. */
public enum TaskStatus {
    PENDING, DONE, CANCELLED, FAILED;

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
    public static TaskStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskStatus 值不能为空。");
        }
        try {
            return TaskStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 TaskStatus 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<TaskStatus, String> {

        @Override
        public String convertToDatabaseColumn(TaskStatus attribute) {
            return attribute == null ? PENDING.value() : attribute.value();
        }

        @Override
        public TaskStatus convertToEntityAttribute(String dbData) {
            return TaskStatus.fromValue(dbData);
        }
    }
}
