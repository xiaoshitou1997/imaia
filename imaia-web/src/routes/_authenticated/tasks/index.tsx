import { createFileRoute } from '@tanstack/react-router'
import { Tasks } from '@/features/tasks'
import { taskStatusOptions } from '@/features/tasks/data/status'

/** 筛选与页码进 URL：刷新与分享不丢。status 只接受契约值域，非法值回落 all。 */
function validateStatus(raw: unknown): string | undefined {
  return typeof raw === 'string' && taskStatusOptions.some((s) => s.value === raw)
    ? raw
    : undefined
}

type TasksSearch = {
  status?: string
  page?: number
}

export const Route = createFileRoute('/_authenticated/tasks/')({
  validateSearch: (search: Record<string, unknown>): TasksSearch => {
    const pageRaw = Number(search.page)
    return {
      status: validateStatus(search.status),
      page: Number.isInteger(pageRaw) && pageRaw > 1 ? pageRaw : undefined,
    }
  },
  component: Tasks,
})
