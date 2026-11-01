import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * AssistantComposer 用例（docs/15 版本，已无「模式」概念）：
 * - Enter 发送 / Shift+Enter 换行
 * - 500 字上限静默限制（不显示计数器）
 * - 快捷提问固定一组；点 chip 回填文本
 * - loading 时出现停止按钮（图标）
 * - 面板内不出现「运营」字样（docs/15 验收）
 */
const { default: AssistantComposer } = await import('@/components/assistant/AssistantComposer.vue')

const mountC = (props = {}) =>
  mount(AssistantComposer, { props: { modelValue: '', loading: false, personaChip: '小灯', ...props } })

describe('AssistantComposer（v2）', () => {
  it('Enter 触发 send；Shift+Enter 不触发', async () => {
    const w = mountC({ modelValue: '你好' })
    await w.find('textarea').trigger('keydown', { key: 'Enter' })
    expect(w.emitted('send')).toHaveLength(1)
    await w.find('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(w.emitted('send')).toHaveLength(1)
  })

  it('500 字上限：超限禁用发送且不显示计数器', async () => {
    const w = mountC({ modelValue: 'a'.repeat(501) })
    expect(w.text()).not.toContain('/500')
    const sendBtn = w.findAll('button').find((b) => b.attributes('aria-label') === '发送')
    expect(sendBtn.attributes('disabled')).toBeDefined()
  })

  it('快捷提问固定一组，点 chip 回填', async () => {
    const w = mountC()
    expect(w.text()).toContain('推荐演出')
    expect(w.text()).toContain('春日影是哪张专辑')
    expect(w.text()).toContain('MyGO 有哪些成员')
    await w.findAll('button').find((b) => b.text() === '推荐演出').trigger('click')
    expect(w.emitted('update:modelValue')[0]).toEqual(['推荐演出'])
  })

  it('停止按钮与发送键同风格（主色成套）', async () => {
    const loading = mountC({ loading: true })
    const stop = loading.find('button[aria-label="停止生成"]')
    expect(stop.classes()).toContain('btn-primary')          // 主色实心，不是白底黑块
  })

  it('发送键禁用态是「待激活」：primary-soft 底 + 主色图标，不是灰块', () => {
    const w = mountC()
    const sendBtn = w.findAll('button').find((b) => b.attributes('aria-label') === '发送')
    expect(sendBtn.classes().join(' ')).toContain('bg-primary-soft')
    expect(sendBtn.classes().join(' ')).not.toContain('bg-elevated')
  })

  it('停止按钮与发送键同风格（主色成套）', async () => {
    const loading = mountC({ loading: true })
    const stop = loading.find('button[aria-label="停止生成"]')
    expect(stop.classes()).toContain('btn-primary')          // 主色实心，不是白底黑块
  })

  it('发送键禁用态是「待激活」：primary-soft 底 + 主色图标，不是灰块', () => {
    const w = mountC()
    const sendBtn = w.findAll('button').find((b) => b.attributes('aria-label') === '发送')
    expect(sendBtn.classes().join(' ')).toContain('bg-primary-soft')
    expect(sendBtn.classes().join(' ')).not.toContain('bg-elevated')
  })

  it('loading → 停止图标按钮；否则发送键', async () => {
    const loading = mountC({ loading: true })
    expect(loading.find('button[aria-label="停止生成"]').exists()).toBe(true)
    expect(loading.find('button[aria-label="发送"]').exists()).toBe(false)

    const idle = mountC({ modelValue: 'x' })
    expect(idle.find('button[aria-label="发送"]').exists()).toBe(true)
  })

  it('不再出现「运营」字样与琥珀色边框（docs/15 验收）', () => {
    const w = mountC({ modelValue: 'hi' })
    expect(w.text()).not.toContain('运营')
    expect(w.html()).not.toContain('amber')
  })

  it('maxlength 与 MAX 一致（500），不再有 600 的漂移', () => {
    expect(mountC().find('textarea').attributes('maxlength')).toBe('500')
  })

  it('不再有无效的 pr-12（.input 的 padding 简写本来就会覆盖它）', () => {
    expect(mountC().find('textarea').classes()).not.toContain('pr-12')
  })

  it('自适应高度补 2px 边框，触顶改为内部滚动（修右侧竖条）', async () => {
    const w = mountC({ modelValue: 'a' })
    const ta = w.find('textarea')
    Object.defineProperty(ta.element, 'scrollHeight', { value: 100, configurable: true })
    await ta.setValue('a\nb')
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    expect(ta.element.style.height).toBe('102px')
    expect(ta.element.style.overflowY).toBe('hidden')

    Object.defineProperty(ta.element, 'scrollHeight', { value: 200, configurable: true })
    await ta.setValue('a\nb\nc')
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    expect(ta.element.style.height).toBe('120px')
    expect(ta.element.style.overflowY).toBe('auto')
  })
})
