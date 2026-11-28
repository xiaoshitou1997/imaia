/**
 * 任务与运行记录的状态值域：与后端契约字段一一对应
 * （imaia-service TaskStatus / TaskLogStatus 的小写 wire 值）。
 * 取值全集来自后端枚举，前端只做展示映射，不另造值。
 */

export interface StatusOption {
  value: string
  label: string
}

/** 任务状态（TaskStatus wire 值）；`all` 由筛选器单独提供，不在契约值域内。 */
export const taskStatusOptions: StatusOption[] = [
  { value: 'active', label: '活动' },
  { value: 'paused', label: '暂停' },
  { value: 'done', label: '已完成' },
  { value: 'failed', label: '失败' },
  { value: 'cancelled', label: '已取消' },
]

const taskStatusLabels: Record<string, string> = Object.fromEntries(
  taskStatusOptions.map((s) => [s.value, s.label]),
)

export function taskStatusLabel(status: string): string {
  return taskStatusLabels[status] ?? status
}

const runStatusLabels: Record<string, string> = {
  running: '执行中',
  sent: '已发送',
  failed: '失败',
  skipped: '已跳过',
}

export function runStatusLabel(status: string): string {
  return runStatusLabels[status] ?? status
}
