import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { handleServerError } from './handle-server-error'

const toastError = vi.hoisted(() => vi.fn())

vi.mock('sonner', () => ({
  toast: {
    error: toastError,
  },
}))

beforeEach(() => {
  vi.mocked(toastError).mockClear()
})

describe('handleServerError', () => {
  it('passes the ApiError message through to the toast', () => {
    handleServerError(new ApiError('会话不存在。', 404, 404))

    expect(toastError).toHaveBeenCalledWith('会话不存在。')
  })

  it('falls back to a generic Chinese message for unknown errors', () => {
    handleServerError(new Error('network'))

    expect(toastError).toHaveBeenCalledWith('出错了，请稍后再试')
  })

  it('falls back for non-Error values as well', () => {
    handleServerError('boom')

    expect(toastError).toHaveBeenCalledWith('出错了，请稍后再试')
  })

  it('logs the error to the console in development', () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const err = new Error('logged')

    handleServerError(err)

    expect(log).toHaveBeenCalledWith(err)
    log.mockRestore()
  })

  it('does not log to the console in production', () => {
    vi.stubEnv('DEV', false)
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})

    handleServerError(new Error('not logged'))

    expect(log).not.toHaveBeenCalled()
    log.mockRestore()
    vi.unstubAllEnvs()
  })
})
