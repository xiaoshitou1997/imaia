/**
 * 日期时间展示：统一按用户 locale 与时区格式化。
 *
 * 后端时间一律是 ISO 时间戳（UTC 或带时区）。界面不能用字符串截断
 * （`.replace('T',' ').slice(0,19)`）——那等于把 UTC 原样显示，整体偏移 8 小时，
 * 而且换个 locale 就不对。全部走 Intl。
 */

/** 用户 locale；本项目控制台为中文，仍在调用点显式传入以便将来切换。 */
export const DEFAULT_LOCALE = 'zh-CN'

const DATE_TIME: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}

/** `2026-01-01T00:00:00Z` → `2026/01/01 08:00`（按浏览器本地时区）。 */
export function formatDateTime(
  iso: string | null | undefined,
  locale: string = DEFAULT_LOCALE
): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(locale, DATE_TIME).format(date)
}

/** 只到日期：`2026/01/01`。 */
export function formatDate(
  iso: string | null | undefined,
  locale: string = DEFAULT_LOCALE
): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

/**
 * `<input type="datetime-local">` 的本地墙钟值转提交用的 ISO 时间戳。
 * 后端按显式 timezone 解析，所以这里必须给出带时区的绝对值，不能给裸字符串。
 */
export function localInputToIso(localValue: string): string | null {
  if (!localValue) return null
  const date = new Date(localValue)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString()
}
