import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { QueryState } from './query-state'

/**
 * 三态切换是本组件的核心契约：错误优先于空与加载（失败不能被误读成「无数据」），
 * 重试必须触发 onRetry。
 */
describe('QueryState', () => {
  it('renders error state with retry and takes precedence over empty', async () => {
    const onRetry = vi.fn()
    const { getByRole, getByText } = await render(
      <QueryState isLoading isError isEmpty onRetry={onRetry}>
        <p>内容不应出现</p>
      </QueryState>,
    )

    await expect.element(getByRole('alert')).toBeInTheDocument()
    await expect.element(getByText('加载失败，请稍后重试。')).toBeInTheDocument()
    await expect.element(getByText('暂无数据。')).not.toBeInTheDocument()
    await expect.element(getByText('内容不应出现')).not.toBeInTheDocument()
  })

  it('clicking retry calls onRetry', async () => {
    const onRetry = vi.fn()
    const { getByRole } = await render(
      <QueryState isLoading isError onRetry={onRetry}>
        <p>内容</p>
      </QueryState>,
    )

    await userEvent.click(getByRole('button', { name: '重试' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('renders loading state without content', async () => {
    const { getByRole, getByText } = await render(
      <QueryState isLoading isError={false}>
        <p>内容不应出现</p>
      </QueryState>,
    )

    await expect.element(getByRole('status')).toBeInTheDocument()
    await expect.element(getByText('加载中…')).toBeInTheDocument()
    await expect.element(getByText('内容不应出现')).not.toBeInTheDocument()
  })

  it('renders empty state after a successful empty response', async () => {
    const { getByText } = await render(
      <QueryState isLoading={false} isError={false} isEmpty>
        <p>内容不应出现</p>
      </QueryState>,
    )

    await expect.element(getByText('暂无数据。')).toBeInTheDocument()
    await expect.element(getByText('内容不应出现')).not.toBeInTheDocument()
  })

  it('renders children when loaded with data', async () => {
    const { getByText, getByRole } = await render(
      <QueryState isLoading={false} isError={false}>
        <p>会话列表内容</p>
      </QueryState>,
    )

    await expect.element(getByText('会话列表内容')).toBeInTheDocument()
    await expect.element(getByRole('alert')).not.toBeInTheDocument()
    await expect.element(getByRole('status')).not.toBeInTheDocument()
  })
})
