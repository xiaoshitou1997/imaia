package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Report period granularity. */
public enum ReportPeriod {
    WEEK, MONTH, YEAR;

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
    public static ReportPeriod fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ReportPeriod 值不能为空。");
        }
        try {
            return ReportPeriod.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 ReportPeriod 值：" + value + "。");
        }
    }

    /** JPA converter: enum ↔ lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<ReportPeriod, String> {

        @Override
        public String convertToDatabaseColumn(ReportPeriod attribute) {
            return attribute == null ? WEEK.value() : attribute.value();
        }

        @Override
        public ReportPeriod convertToEntityAttribute(String dbData) {
            return ReportPeriod.fromValue(dbData);
        }
    }
}
