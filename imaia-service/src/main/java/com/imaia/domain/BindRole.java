package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Role of a member inside one conversation. */
public enum BindRole {
    MEMBER,
    ADMIN,
    OWNER,
    BOT;

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
    public static BindRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BindRole 不能为空。");
        }
        try {
            return BindRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知 BindRole 值：" + value + "。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<BindRole, String> {

        @Override
        public String convertToDatabaseColumn(BindRole attribute) {
            return attribute == null ? MEMBER.value() : attribute.value();
        }

        @Override
        public BindRole convertToEntityAttribute(String dbData) {
            return BindRole.fromValue(dbData);
        }
    }
}
