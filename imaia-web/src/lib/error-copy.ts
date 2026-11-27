/**
 * 后端错误码 → 前端通用文案映射。
 *
 * 后端 `Result.code` 是字符串契约：前端按 code 给出稳定描述，不依赖后端 message
 * 的措辞（message 会随文案与 locale 变化，code 不会）。未收录的 code 走统一兜底
 * 并显示原始码，便于排查而不是静默吞掉。
 *
 * 键与后端 ErrorCode 实现一一对应：`common.*` 来自公共错误码，
 * 其余为 imaia 业务码。
 */
export const ERROR_CODE_COPY: Record<string, string> = {
  // 公共错误码
  'common.internal.error': '服务器内部错误，请稍后重试。',
  'common.param.invalid': '请求参数不合法。',
  'common.unauthorized': '未认证或凭据无效。',
  'common.session.expired': '登录状态已过期，请重新登录。',
  'common.login.failed': '用户名或密码错误。',
  'common.forbidden': '没有访问该资源的权限。',

  // 业务错误码：参数与任务
  'param.invalid': '请求参数不合法。',
  'param.malformed-json': '请求体格式有误。',
  'param.time-format': '时间格式不正确。',
  'param.self-command.alias-too-long': '自指令别名过长。',
  'param.history-sync.unsupported': '该平台不支持拉取历史消息。',
  'task.schedule-conflict': '定时设置冲突，请只填一种。',
  'task.content-required': '任务内容不能为空。',
  'task.cron-invalid': '定时表达式无效。',
  'task.state-not-allowed': '当前状态不允许此操作。',

  // 业务错误码：资源与认证
  'resource.not-found': '要访问的内容不存在。',
  'data.conflict': '数据冲突，请刷新后重试。',
  'auth.not-configured': '登录未启用，请先在服务端配置管理员凭据。',
  'auth.invalid-credentials': '用户名或密码错误。',
  'upstream.unavailable': '上游服务暂时不可用。',
}

/** 兜底文案：不暴露内部细节，但保留原始码方便排查。 */
export function copyForCode(code: string | null | undefined): string {
  if (!code) return '操作失败，请稍后重试。'
  return ERROR_CODE_COPY[code] ?? `操作失败（错误码 ${code}），请稍后重试。`
}
