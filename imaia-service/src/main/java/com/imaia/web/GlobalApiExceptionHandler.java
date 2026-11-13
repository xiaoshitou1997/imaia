package com.imaia.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Maps expected failures to the envelope with clear client-facing messages. */
@RestControllerAdvice(basePackages = "com.imaia.web")
public class GlobalApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException failure) {
        return ApiEnvelope.error(400, 400, failure.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException failure) {
        return ApiEnvelope.error(404, 404, failure.getMessage() == null ? "资源不存在。" : failure.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException failure) {
        return ApiEnvelope.error(409, 409, "数据冲突：唯一键重复或外键约束不满足。");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> badTime(DateTimeParseException failure) {
        return ApiEnvelope.error(400, 400, "时间格式应为 yyyy-MM-dd HH:mm。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException failure) {
        return ApiEnvelope.error(400, 400, "请求体不是合法 JSON：" + failure.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception failure) {
        return ApiEnvelope.error(500, 500, failure.getMessage() == null ? failure.toString() : failure.getMessage());
    }
}
