package com.imaia.common.error;

import org.springframework.http.HttpStatus;

/**
 * 无业务归属的公共错误码：任何模块都可能遇到、不属于具体领域语义的失败场景。
 *
 * <p>业务模块自己的失败场景放各自的 {@code XxxErrorCodeEnum}，不往这里塞业务码。
 */
public enum CommonErrorCodeEnum implements ErrorCode {

    /** 未捕获异常兜底；响应不带堆栈。 */
    COMMON_INTERNAL_ERROR("common.internal.error", HttpStatus.INTERNAL_SERVER_ERROR),

    /** 入口参数校验失败（Bean Validation 或等价入口校验）。 */
    COMMON_PARAM_INVALID("common.param.invalid", HttpStatus.BAD_REQUEST),

    /** 未认证：无会话/令牌、令牌无效或伪造。 */
    COMMON_UNAUTHORIZED("common.unauthorized", HttpStatus.UNAUTHORIZED),

    /** 会话或令牌过期；前端据此引导重新认证。 */
    COMMON_SESSION_EXPIRED("common.session.expired", HttpStatus.UNAUTHORIZED),

    /** 登录失败；不区分账号不存在与凭据错误，防账号枚举。 */
    COMMON_LOGIN_FAILED("common.login.failed", HttpStatus.UNAUTHORIZED),

    /** 已认证但无所需权限点。 */
    COMMON_FORBIDDEN("common.forbidden", HttpStatus.FORBIDDEN);

    private final String code;
    private final HttpStatus status;

    CommonErrorCodeEnum(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
