/**
 * 文案资源层。用户可见文案集中在此，组件不硬编码中英文句子。
 *
 * 关于语言数量：本项目按 ADR 0001 仅提供 zh-CN（单管理员自用控制台），
 * 但「文案集中维护」是独立要求——散落在 JSX 里的字面量无法审校、
 * 改词要动代码、也无法按 key 做完整性检查。这里用扁平 key 表承载，
 * 将来接 en-US 只需补一份同构的 map。
 */
export const copy = {
  // 通用操作
  'action.save': '保存',
  'action.cancel': '取消',
  'action.retry': '重试',
  'action.back': '返回上一页',
  'action.home': '回到首页',

  // 列表与状态
  'state.loading': '加载中…',
  'state.loadFailed': '加载失败，请稍后重试。',
  'state.empty': '暂无数据。',
  'state.noChanges': '没有需要保存的修改',

  // 会话
  'conversation.search': '搜索会话',
  'conversation.empty': '没有匹配的会话。',
  'conversation.messages.empty': '这个会话还没有归档消息。',
  'conversation.edit.title': '编辑会话',
  'conversation.unknown': '未知会话',

  // 设置
  'settings.assistant.loadFailed':
    '加载助手设置失败。为避免用默认值覆盖服务器上的真实配置，保存已禁用。',
  'settings.tools.loadFailed':
    '加载工具端点配置失败。为避免用默认值覆盖服务器上的真实配置，保存已禁用。',
  'settings.saved': '设置已保存',
  'settings.saveFailed': '保存失败',

  // 产品名
  'product.name': 'imaia 中枢',
} as const

export type CopyKey = keyof typeof copy

/** 取文案；key 未收录时返回 key 本身，便于在界面上一眼发现缺漏。 */
export function t(key: CopyKey): string {
  return copy[key] ?? key
}
