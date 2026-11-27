package com.imaia.web;

/**
 * 带错误码的业务异常：抛出方声明「哪个业务失败场景」，渲染交给全局异常处理器。
 *
 * <p>不用 {@link IllegalArgumentException} 之流裸抛，是因为那类异常没有码——
 * 全局处理器只能按 HTTP 状态给一个通用码，前端无法按码分支，
 * 也就违反「一场景一码」。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
