import { createFileRoute } from '@tanstack/react-router'
import { SearchSettings } from '@/features/settings/search'

export const Route = createFileRoute('/_authenticated/settings/search')({
  component: SearchSettings,
})
