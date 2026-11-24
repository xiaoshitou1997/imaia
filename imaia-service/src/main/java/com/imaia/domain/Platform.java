package com.imaia.domain;

import jakarta.persistence.AttributeConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 平台代码：hub 当前支持的 IM 平台封闭值域。存储（platform_code 列，CHECK 锁值）、
 * wire（@JsonValue 小写）、入参校验（fromValue）与判断全链路用它承载，不以 String 中转；
 * 事件通道内部的标识传递仍走 value() 字符串（adapter 契约 platform()）。
 */
public enum Platform {
    WECHAT,
    FEISHU;

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
    public static Platform fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("platform 不能为空，必须是 wechat / feishu 之一。");
        }
        try {
            return Platform.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("未知平台：" + value + "，必须是 wechat / feishu 之一。");
        }
    }

    /** JPA converter: enum -> lowercase VARCHAR; NULL passes through (nullable scope columns). */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<Platform, String> {

        @Override
        public String convertToDatabaseColumn(Platform attribute) {
            return attribute == null ? null : attribute.value();
        }

        @Override
        public Platform convertToEntityAttribute(String dbData) {
            return dbData == null ? null : Platform.fromValue(dbData);
        }
    }
}
