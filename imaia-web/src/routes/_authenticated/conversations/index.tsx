import { createFileRoute } from '@tanstack/react-router'
import { Conversations } from '@/features/conversations'

export const Route = createFileRoute('/_authenticated/conversations/')({
  component: Conversations,
})
