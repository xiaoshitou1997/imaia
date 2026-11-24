import { createFileRoute } from '@tanstack/react-router'
import { ImageSettings } from '@/features/settings/image'

export const Route = createFileRoute('/_authenticated/settings/image')({
  component: ImageSettings,
})
