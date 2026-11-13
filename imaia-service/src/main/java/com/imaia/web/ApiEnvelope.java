package com.imaia.web;

import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** The console API response envelope: {@code {code, message, data}}. */
public final class ApiEnvelope {

    private ApiEnvelope() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("message", "ok");
        envelope.put("data", data);
        return envelope;
    }

    public static ResponseEntity<Map<String, Object>> error(int httpStatus, int code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", code);
        envelope.put("message", message);
        return ResponseEntity.status(httpStatus).body(envelope);
    }
}
