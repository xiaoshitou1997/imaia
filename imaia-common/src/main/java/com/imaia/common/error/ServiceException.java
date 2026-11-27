package com.imaia.common.error;

/**
 * 业务失败统一异常：携带错误码与占位符参数，文案在输出时刻按 locale 渲染。
 *
 * <p>构造只接受 {@link ErrorCode} 与 args，<b>不提供裸文案构造</b>——
 * 异常若携带成品文案，就会绕过国际化：同一个异常在不同 locale 下只能是同一种语言。
 * args 只装数据值（名称、单号、数量一类），不装整句。
 */
public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public ServiceException(ErrorCode errorCode, Object... args) {
        // message 仅供日志定位使用，不进入响应体；响应文案由处理器按 messageKey 渲染。
        super(errorCode.messageKey());
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public ServiceException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.messageKey(), cause);
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 占位符参数副本；避免调用方修改内部数组。 */
    public Object[] args() {
        return args.clone();
    }
}
