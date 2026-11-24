import { createFileRoute } from '@tanstack/react-router'
import { VideoSettings } from '@/features/settings/video'

export const Route = createFileRoute('/_authenticated/settings/video')({
  component: VideoSettings,
})
