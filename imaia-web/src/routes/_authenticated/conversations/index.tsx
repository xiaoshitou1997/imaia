import { createFileRoute } from '@tanstack/react-router'
import { Conversations } from '@/features/conversations'

type ConversationsSearch = {
  q?: string
  page?: number
}

/** 搜索词与页码进 URL：刷新与分享不丢；q 非敏感可公开。 */
export const Route = createFileRoute('/_authenticated/conversations/')({
  validateSearch: (search: Record<string, unknown>): ConversationsSearch => {
    const pageRaw = Number(search.page)
    return {
      q: typeof search.q === 'string' && search.q.length > 0 ? search.q : undefined,
      page: Number.isInteger(pageRaw) && pageRaw > 1 ? pageRaw : undefined,
    }
  },
  component: Conversations,
})
