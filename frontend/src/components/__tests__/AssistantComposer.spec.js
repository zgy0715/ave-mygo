import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * AssistantComposer 用例（docs/12 §九）：
 * - 字数 500 上限：超限禁用发送 + 红色计数
 * - Enter 发送 / Shift+Enter 换行
 * - 快捷提问按模式切换（chat vs ops）
 */
const { default: AssistantComposer } = await import('@/components/assistant/AssistantComposer.vue')

const mountC = (props = {}) =>
  mount(AssistantComposer, {
    props: { modelValue: '', loading: false, mode: 'chat', personaChip: '小灯', ...props },
  })

describe('AssistantComposer', () => {
  it('Enter 触发 send（带当前文本）；Shift+Enter 不触发', async () => {
    const w = mountC({ modelValue: '你好' })
    await w.find('textarea').trigger('keydown', { key: 'Enter' })
    expect(w.emitted('send')).toHaveLength(1)

    await w.find('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(w.emitted('send')).toHaveLength(1)
  })

  it('500 字上限：超限红色计数且发送禁用', async () => {
    const long = 'a'.repeat(501)
    const w = mountC({ modelValue: long })
    expect(w.text()).toContain('501/500')
    const sendBtn = w.findAll('button').find((b) => b.attributes('aria-label') === '发送')
    expect(sendBtn.attributes('disabled')).toBeDefined()
  })

  it('快捷提问按模式切换：chat 推荐演出 / ops 列出所有演出', async () => {
    const chat = mountC({ mode: 'chat' })
    expect(chat.text()).toContain('推荐演出')
    expect(chat.text()).not.toContain('列出所有演出')

    const ops = mountC({ mode: 'ops' })
    expect(ops.text()).toContain('列出所有演出')
    expect(ops.text()).toContain('⚙ 运营中')
  })

  it('点快捷提问 → update:modelValue 回填文本', async () => {
    const w = mountC({ mode: 'chat' })
    await w.findAll('button').find((b) => b.text() === '推荐演出').trigger('click')
    expect(w.emitted('update:modelValue')[0]).toEqual(['推荐演出'])
  })

  it('loading 且 chat 模式 → 显示停止按钮；ops 模式不显示（不可停止）', async () => {
    const chat = mountC({ loading: true, mode: 'chat' })
    expect(chat.text()).toContain('停止')

    const ops = mountC({ loading: true, mode: 'ops' })
    expect(ops.text()).not.toContain('停止')
  })
})
