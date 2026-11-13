package com.imaia.web;

import org.springframework.http.ResponseEntity;

/** The console API response envelope: {@code {code, message, data}}. */
public record ApiEnvelope<T>(int code, String message, T data) {

    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(0, "ok", data);
    }

    public static <T> ResponseEntity<ApiEnvelope<T>> error(int httpStatus, int code, String message) {
        return ResponseEntity.status(httpStatus).body(new ApiEnvelope<>(code, message, null));
    }
}
