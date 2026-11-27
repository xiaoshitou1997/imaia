package com.imaia.web;

import org.springframework.http.ResponseEntity;

/** The console API response envelope: {@code {code, message, data}}. */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> ResponseEntity<Result<T>> error(int httpStatus, int code, String message) {
        return ResponseEntity.status(httpStatus).body(new Result<>(code, message, null));
    }
}
