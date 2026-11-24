package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** One scheduled task execution state. */
public enum TaskLogStatus {
    RUNNING,
    SENT,
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
    public static TaskLogStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskLogStatus 不能为空。");
        }
        try {
            return TaskLogStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 TaskLogStatus 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<TaskLogStatus, String> {

        @Override
        public String convertToDatabaseColumn(TaskLogStatus attribute) {
            return attribute == null ? RUNNING.value() : attribute.value();
        }

        @Override
        public TaskLogStatus convertToEntityAttribute(String dbData) {
            return TaskLogStatus.fromValue(dbData);
        }
    }
}
