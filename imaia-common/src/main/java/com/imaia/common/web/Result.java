package com.imaia.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 控制台 API 响应包络：{@code {code, message, data}}。
 *
 * <p>{@code code} 是字符串契约（成功固定 {@link #SUCCESS_CODE}），
 * 前端按 code 映射通用文案；{@code message} 是本次按请求 locale 渲染的即时描述。
 */
public record Result<T>(String code, String message, T data) {

    /** 成功码：前端据此区分成功与业务失败。 */
    public static final String SUCCESS_CODE = "ok";

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data);
    }

    public static <T> ResponseEntity<Result<T>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Result<>(code, message, null));
    }
}
