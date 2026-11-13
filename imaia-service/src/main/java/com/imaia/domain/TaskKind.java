package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Scheduled task kind: one-shot reminder, recurring cron, or periodic report. */
public enum TaskKind {
    REMINDER, CRON, REPORT;

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
    public static TaskKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskKind 值不能为空。");
        }
        try {
            return TaskKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 TaskKind 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<TaskKind, String> {

        @Override
        public String convertToDatabaseColumn(TaskKind attribute) {
            return attribute == null ? REMINDER.value() : attribute.value();
        }

        @Override
        public TaskKind convertToEntityAttribute(String dbData) {
            return TaskKind.fromValue(dbData);
        }
    }
}
