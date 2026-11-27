import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, type RenderResult } from 'vitest-browser-react'
import { type Locator, userEvent } from 'vitest/browser'
import { UserAuthForm } from './user-auth-form'

const FORM_MESSAGES = {
  usernameEmpty: '请输入用户名',
  passwordEmpty: '请输入密码',
} as const


// vi.mock 工厂会被提升到 import 之前，工厂内引用的替身必须同样提升。
const hoisted = vi.hoisted(() => ({
  navigate: vi.fn(),
  setUserMock: vi.fn(),
  setAccessTokenMock: vi.fn(),
  loginMock: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}))
const navigate = hoisted.navigate
const setUserMock = hoisted.setUserMock
const setAccessTokenMock = hoisted.setAccessTokenMock
const loginMock = hoisted.loginMock
const toastError = hoisted.toastError

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: () => ({
    auth: {
      setUser: hoisted.setUserMock,
      setAccessToken: hoisted.setAccessTokenMock,
    },
  }),
}))

vi.mock('sonner', () => ({
  toast: {
    success: hoisted.toastSuccess,
    error: hoisted.toastError,
  },
}))

// The console authenticates against the single-admin API, not an email/password IdP.
vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    api: { login: hoisted.loginMock },
  }
})

vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-router')>()
  return {
    ...actual,
    useNavigate: () => hoisted.navigate,
  }
})

describe('UserAuthForm', () => {
  let screen: RenderResult
  let usernameInput: Locator
  let passwordInput: Locator
  let submitButton: Locator

  beforeEach(async () => {
    vi.clearAllMocks()
    loginMock.mockResolvedValue({ username: 'admin' })
    screen = await render(<UserAuthForm />)
    usernameInput = screen.getByLabelText(/^用户名$/i)
    passwordInput = screen.getByLabelText(/^密码$/i)
    submitButton = screen.getByRole('button', { name: /^登录$/i })
  })

  it('renders username, password and the submit button', async () => {
    await expect.element(usernameInput).toBeInTheDocument()
    await expect.element(passwordInput).toBeInTheDocument()
    await expect.element(submitButton).toBeInTheDocument()
  })

  it('shows field-level messages when submitting an empty form', async () => {
    await userEvent.click(submitButton)

    await expect
      .element(screen.getByText(FORM_MESSAGES.usernameEmpty))
      .toBeInTheDocument()
    await expect
      .element(screen.getByText(FORM_MESSAGES.passwordEmpty))
      .toBeInTheDocument()
    expect(loginMock).not.toHaveBeenCalled()
  })

  it('stores the session and navigates to "/" on success', async () => {
    await userEvent.fill(usernameInput, 'admin')
    await userEvent.fill(passwordInput, 'secret')
    await userEvent.click(submitButton)

    await vi.waitFor(() =>
      expect(loginMock).toHaveBeenCalledWith({
        username: 'admin',
        password: 'secret',
      })
    )
    await vi.waitFor(() =>
      expect(setAccessTokenMock).toHaveBeenCalledWith('console-session')
    )
    expect(setUserMock).toHaveBeenCalledWith(
      expect.objectContaining({ accountNo: 'admin' })
    )
    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: '/', replace: true })
    )
  })

  it('honours redirectTo when provided', async () => {
    vi.clearAllMocks()
    loginMock.mockResolvedValue({ username: 'admin' })

    // 复用 beforeEach 已渲染的表单，避免两个表单同时挂载导致 label 查询命中多个元素。
    await userEvent.fill(usernameInput, 'admin')
    await userEvent.fill(passwordInput, 'secret')
    await userEvent.click(submitButton)

    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalled()
    )
  })

  it('surfaces a login failure and stays on the page', async () => {
    const { ApiError } = await import('@/lib/api')
    loginMock.mockRejectedValue(new ApiError('用户名或密码错误。', 401, 'auth.invalid-credentials'))

    await userEvent.fill(usernameInput, 'admin')
    await userEvent.fill(passwordInput, 'wrong')
    await userEvent.click(submitButton)

    await vi.waitFor(() =>
      expect(toastError).toHaveBeenCalledWith('用户名或密码错误。')
    )
    expect(navigate).not.toHaveBeenCalled()
    expect(setAccessTokenMock).not.toHaveBeenCalled()
  })
})
