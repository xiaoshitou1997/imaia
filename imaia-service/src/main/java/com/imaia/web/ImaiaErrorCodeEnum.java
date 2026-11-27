package com.imaia.web;

import org.springframework.http.HttpStatus;

import com.imaia.common.error.ErrorCode;

/**
 * imaia 业务错误码：一个独立失败场景一个常量，不复用码扩展语义。
 *
 * <p>{@code code} 是对外契约（字符串），{@code status} 是协议层结果，两者解耦。
 * 文案不在此声明——由 {@code messageKey()} 派生键名后经语言资源渲染，
 * 确保同一码在不同 locale 下呈现对应语言。
 */
public enum ImaiaErrorCodeEnum implements ErrorCode {

    /** 未归类的入参非法；新场景请定义自己的码，不要复用。 */
    PARAM_INVALID("param.invalid", HttpStatus.BAD_REQUEST),

    /** 请求体不是合法 JSON。 */
    PARAM_MALFORMED_JSON("param.malformed-json", HttpStatus.BAD_REQUEST),

    /** 时间格式不符合约定。 */
    PARAM_TIME_FORMAT("param.time-format", HttpStatus.BAD_REQUEST),

    /** 自指令别名超长。 */
    SELF_COMMAND_ALIAS_TOO_LONG("param.self-command.alias-too-long", HttpStatus.BAD_REQUEST),

    /** 只有微信连接支持拉取历史消息。 */
    HISTORY_SYNC_UNSUPPORTED("param.history-sync.unsupported", HttpStatus.BAD_REQUEST),

    /** 定时任务的 fireAt 与 cron 同时缺省或同时给出。 */
    TASK_SCHEDULE_CONFLICT("task.schedule-conflict", HttpStatus.BAD_REQUEST),

    /** 定时任务缺少 content 与 prompt。 */
    TASK_CONTENT_REQUIRED("task.content-required", HttpStatus.BAD_REQUEST),

    /** cron 表达式无法解析。 */
    TASK_CRON_INVALID("task.cron-invalid", HttpStatus.BAD_REQUEST),

    /** 任务当前状态不允许该操作。 */
    TASK_STATE_NOT_ALLOWED("task.state-not-allowed", HttpStatus.BAD_REQUEST),

    /** 目标资源不存在。 */
    RESOURCE_NOT_FOUND("resource.not-found", HttpStatus.NOT_FOUND),

    /** 唯一键或外键约束冲突。 */
    DATA_CONFLICT("data.conflict", HttpStatus.CONFLICT),

    /** 控制台登录凭据未配置，登录端点整体禁用。 */
    AUTH_NOT_CONFIGURED("auth.not-configured", HttpStatus.SERVICE_UNAVAILABLE),

    /** 登录失败；不区分用户不存在与密码错误，防账号枚举。 */
    AUTH_INVALID_CREDENTIALS("auth.invalid-credentials", HttpStatus.UNAUTHORIZED),

    /** 连接外部平台失败。 */
    UPSTREAM_UNAVAILABLE("upstream.unavailable", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final HttpStatus status;

    ImaiaErrorCodeEnum(String code, HttpStatus status) {
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
