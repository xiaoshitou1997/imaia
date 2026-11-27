package com.imaia.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;

/** Maps expected failures to the envelope with clear client-facing messages. */
@RestControllerAdvice(basePackages = "com.imaia.web")
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> badRequest(IllegalArgumentException failure) {
        return Result.error(400, 400, failure.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException failure) {
        return Result.error(404, 404, failure.getMessage() == null ? "资源不存在。" : failure.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> conflict(DataIntegrityViolationException failure) {
        return Result.error(409, 409, "数据冲突：唯一键重复或外键约束不满足。");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Result<Void>> badTime(DateTimeParseException failure) {
        return Result.error(400, 400, "时间格式应为 yyyy-MM-dd HH:mm。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> badJson(HttpMessageNotReadableException failure) {
        return Result.error(400, 400, "请求体不是合法 JSON。");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> internal(Exception failure) {
        log.error("Unhandled API failure", failure);
        return Result.error(500, 500, "服务器内部错误，详情见服务日志。");
    }
}
