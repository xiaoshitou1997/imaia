import { createFileRoute } from '@tanstack/react-router'
import { AssistantSettings } from '@/features/settings/assistant'

export const Route = createFileRoute('/_authenticated/settings/')({
  component: AssistantSettings,
})
