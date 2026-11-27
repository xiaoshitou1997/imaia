package com.imaia.common.web;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.imaia.common.error.CommonErrorCodeEnum;
import com.imaia.common.error.ErrorCode;
import com.imaia.common.error.ServiceException;
import com.imaia.common.i18n.I18nConfig;

/**
 * 统一异常映射：把失败场景落到错误码契约与包络。
 *
 * <p>HTTP 状态取 {@code status()}，包络 {@code code} 取 {@code code()}，
 * {@code message} 按 {@code messageKey()} 经 MessageSource 以请求 locale 渲染。
 * 未捕获异常只回通用码，堆栈只进日志，不向客户端泄漏。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> service(ServiceException failure) {
        ErrorCode code = failure.errorCode();
        return respond(code, failure.args());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<FieldViolation>>> invalidBody(MethodArgumentNotValidException failure) {
        // 字段级明细放 data，每项含字段名与按 locale 渲染的提示；不在处理器拼成品句。
        List<FieldViolation> violations = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        String message = render(CommonErrorCodeEnum.COMMON_PARAM_INVALID);
        return ResponseEntity.status(CommonErrorCodeEnum.COMMON_PARAM_INVALID.status())
                .body(new Result<>(CommonErrorCodeEnum.COMMON_PARAM_INVALID.code(), message, violations));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> notFound(NoSuchElementException failure) {
        log.debug("Resource not found: {}", failure.getMessage());
        return respond(CommonErrorCodeEnum.COMMON_PARAM_INVALID, new Object[0]);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> conflict(DataIntegrityViolationException failure) {
        log.warn("Data integrity violation", failure);
        return respond(CommonErrorCodeEnum.COMMON_INTERNAL_ERROR, new Object[0]);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Result<Void>> badTime(DateTimeParseException failure) {
        return respond(CommonErrorCodeEnum.COMMON_PARAM_INVALID, new Object[0]);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> badJson(HttpMessageNotReadableException failure) {
        return respond(CommonErrorCodeEnum.COMMON_PARAM_INVALID, new Object[0]);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> internal(Exception failure) {
        log.error("Unhandled API failure", failure);
        return respond(CommonErrorCodeEnum.COMMON_INTERNAL_ERROR, new Object[0]);
    }

    private ResponseEntity<Result<Void>> respond(ErrorCode code, Object[] args) {
        return Result.error(code.status(), code.code(), render(code, args));
    }

    private String render(ErrorCode code) {
        return render(code, new Object[0]);
    }

    private String render(ErrorCode code, Object[] args) {
        return I18nConfig.render(messageSource, code, args, LocaleContextHolder.getLocale());
    }

    /** 字段级校验明细：放包络 data，供前端标在字段旁。 */
    public record FieldViolation(String field, String message) {
    }
}
