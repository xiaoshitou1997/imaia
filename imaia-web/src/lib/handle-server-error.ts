import { ApiError } from '@/lib/api'
import { copyForCode } from '@/lib/error-copy'
import { toast } from 'sonner'

/**
 * 统一错误提示：后端 `code` 是契约，优先按 code 映射通用描述；
 * 后端给出的 `message` 是本方系统生成的即时原因，作为更具体的补充。
 *
 * 顺序说明：code 文案稳定、可统一维护；message 可能随文案调整变化，
 * 所以先按 code 兜底，message 存在且与 code 文案不同时优先展示 message
 * （它往往更精确，例如「historyLimit 取值范围 2-200」）。
 */
export function handleServerError(error: unknown) {
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.log(error)
  }

  if (error instanceof ApiError) {
    // 后端 message 更具体时用它，否则回落到 code 通用描述。
    toast.error(error.message || copyForCode(error.code))
    return
  }

  toast.error(copyForCode(null))
}
