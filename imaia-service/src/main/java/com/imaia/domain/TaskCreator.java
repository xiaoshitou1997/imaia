package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Who created the scheduled task. */
public enum TaskCreator {
    AI, CONSOLE;

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
    public static TaskCreator fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskCreator 值不能为空。");
        }
        try {
            return TaskCreator.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 TaskCreator 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<TaskCreator, String> {

        @Override
        public String convertToDatabaseColumn(TaskCreator attribute) {
            return attribute == null ? AI.value() : attribute.value();
        }

        @Override
        public TaskCreator convertToEntityAttribute(String dbData) {
            return TaskCreator.fromValue(dbData);
        }
    }
}
