package com.imaia.web;

/**
 * 业务错误码：一个独立业务失败场景对应唯一常量，不复用码扩展语义。
 *
 * <p>与 HTTP 状态解耦：HTTP 状态表达协议层结果，{@code code} 是契约，
 * 前端按 code 分支映射文案；同码多义会让前端无法稳定判断。
 * 段位约定：1xxx 通用/参数，2xxx 资源，3xxx 认证授权，4xxx 外部依赖，
 * 9xxx 服务端未预期。
 */
public enum ErrorCode {

    /** 未归类的入参非法（历史通用入口，新场景不要复用）。 */
    INVALID_REQUEST(1400, 400, "请求参数不合法。"),

    /** 请求体不是合法 JSON。 */
    MALFORMED_JSON(1401, 400, "请求体不是合法 JSON。"),

    /** 时间格式不符合约定。 */
    INVALID_TIME_FORMAT(1402, 400, "时间格式应为 yyyy-MM-dd HH:mm。"),

    /** 字段校验失败（Bean Validation）。 */
    VALIDATION_FAILED(1403, 400, "字段校验失败。"),

    /** 自指令别名超长。 */
    SELF_COMMAND_ALIAS_TOO_LONG(1404, 400, "自指令别名超过长度上限。"),

    /** 只有微信连接支持拉取历史消息。 */
    HISTORY_SYNC_UNSUPPORTED_PLATFORM(1405, 400, "该平台不支持拉取历史消息。"),

    /** 定时任务的 fireAt 与 cron 同时缺省或同时给出。 */
    TASK_SCHEDULE_CONFLICT(1406, 400, "fireAt 与 cron 必须二选一。"),

    /** 定时任务缺少 content 与 prompt。 */
    TASK_CONTENT_REQUIRED(1407, 400, "任务需要 content 或 prompt。"),

    /** cron 表达式无法解析。 */
    TASK_CRON_INVALID(1408, 400, "cron 表达式无效。"),

    /** 任务当前状态不允许该操作。 */
    TASK_STATE_NOT_ALLOWED(1409, 400, "该任务当前状态不允许此操作。"),

    /** 目标资源不存在。 */
    RESOURCE_NOT_FOUND(2404, 404, "资源不存在。"),

    /** 唯一键或外键约束冲突。 */
    DATA_CONFLICT(2409, 409, "数据冲突：唯一键重复或外键约束不满足。"),

    /** 控制台登录凭据未配置，登录端点整体禁用。 */
    AUTH_NOT_CONFIGURED(3503, 503, "登录未启用：管理员凭据未配置。"),

    /** 登录失败；不区分用户不存在与密码错误，防账号枚举。 */
    AUTH_INVALID_CREDENTIALS(3401, 401, "用户名或密码错误。"),

    /** 连接外部平台失败。 */
    UPSTREAM_UNAVAILABLE(4502, 502, "上游服务不可用。"),

    /** 未预期异常。 */
    INTERNAL_ERROR(9500, 500, "服务器内部错误，详情见服务日志。");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /** 契约错误码。 */
    public int code() {
        return code;
    }

    /** 对应的 HTTP 状态。 */
    public int httpStatus() {
        return httpStatus;
    }

    /** 兜底文案；调用方可传入更具体的描述。 */
    public String defaultMessage() {
        return defaultMessage;
    }
}
