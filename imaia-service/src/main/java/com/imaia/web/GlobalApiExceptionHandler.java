package com.imaia.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 把已知失败场景映射到统一包络与错误码枚举。
 *
 * <p>每个失败场景落到一个 {@link ErrorCode}：code 是契约，前端按 code 分支；
 * HTTP 状态由枚举携带，不在这里各写一个数字。未预期异常只回通用码与
 * requestId 级提示，堆栈只进日志。
 */
@RestControllerAdvice(basePackages = "com.imaia.web")
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException failure) {
        ErrorCode code = failure.errorCode();
        return Result.error(code.httpStatus(), code.code(), failure.getMessage());
    }

    /** Bean Validation 失败：回落到字段级信息，便于前端定位。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalidBody(MethodArgumentNotValidException failure) {
        String detail = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return Result.error(code.httpStatus(), code.code(),
                detail.isBlank() ? code.defaultMessage() : detail);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException failure) {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        return Result.error(code.httpStatus(), code.code(),
                failure.getMessage() == null ? code.defaultMessage() : failure.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> conflict(DataIntegrityViolationException failure) {
        ErrorCode code = ErrorCode.DATA_CONFLICT;
        return Result.error(code.httpStatus(), code.code(), code.defaultMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Result<Void>> badTime(DateTimeParseException failure) {
        ErrorCode code = ErrorCode.INVALID_TIME_FORMAT;
        return Result.error(code.httpStatus(), code.code(), code.defaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> badJson(HttpMessageNotReadableException failure) {
        ErrorCode code = ErrorCode.MALFORMED_JSON;
        return Result.error(code.httpStatus(), code.code(), code.defaultMessage());
    }

    /**
     * 兼容仍在抛 IllegalArgumentException 的入口：归到通用参数码，不再借用
     * HTTP 状态本身当 code。新代码请抛 {@link BusinessException}。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> badRequest(IllegalArgumentException failure) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return Result.error(code.httpStatus(), code.code(), failure.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> internal(Exception failure) {
        log.error("Unhandled API failure", failure);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return Result.error(code.httpStatus(), code.code(), code.defaultMessage());
    }
}
