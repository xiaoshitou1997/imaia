package com.imaia.common.error;

import org.springframework.http.HttpStatus;

/**
 * 错误码契约：一个独立业务失败场景对应唯一常量，不复用码扩展语义。
 *
 * <p>{@link #code()} 是对外契约（字符串，前端按码映射通用文案），
 * {@link #status()} 表达协议层结果，两者解耦——同一个 HTTP 状态可对应多个业务码，
 * 同一个业务码也只映射一个 HTTP 状态。HTTP 状态用标准枚举承载，不写数字。
 *
 * <p>{@link #messageKey()} 由接口按 {@code error.} + code 统一派生，各枚举不存文案键；
 * 文案在输出时刻按请求 locale 经 MessageSource 渲染，异常不携带成品文案。
 */
public interface ErrorCode {

    /** 契约错误码，对外稳定不可变。 */
    String code();

    /** 对应的 HTTP 状态。 */
    HttpStatus status();

    /**
     * 文案键：固定由 code 派生，保证「一个码一套译文」且各枚举无需重复声明。
     * 语言资源文件里的键名必须与此一致（如 code 为 {@code common.param.invalid}
     * 则键为 {@code error.common.param.invalid}）。
     */
    default String messageKey() {
        return "error." + code();
    }
}
