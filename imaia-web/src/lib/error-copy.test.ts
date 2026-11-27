import { describe, expect, it } from 'vitest'
import { copyForCode, ERROR_CODE_COPY } from './error-copy'

describe('copyForCode', () => {
  it('maps a known backend code to its stable copy', () => {
    expect(copyForCode('resource.not-found')).toBe('要访问的内容不存在。')
    expect(copyForCode('auth.invalid-credentials')).toBe('用户名或密码错误。')
  })

  it('mentions the raw code for an unmapped code so it is diagnosable', () => {
    const text = copyForCode('brand.new.code')
    expect(text).toContain('brand.new.code')
  })

  it('returns a generic message when no code is available', () => {
    expect(copyForCode(null)).toBe('操作失败，请稍后重试。')
    expect(copyForCode(undefined)).toBe('操作失败，请稍后重试。')
  })

  it('has no empty entries', () => {
    for (const [code, text] of Object.entries(ERROR_CODE_COPY)) {
      expect(text, `code ${code} 的文案不能为空`).not.toBe('')
    }
  })
})
