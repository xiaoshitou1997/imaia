import { ApiError } from '@/lib/api'
import { toast } from 'sonner'

export function handleServerError(error: unknown) {
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.log(error)
  }

  const errMsg =
    error instanceof ApiError ? error.message : '出错了，请稍后再试'

  toast.error(errMsg)
}
