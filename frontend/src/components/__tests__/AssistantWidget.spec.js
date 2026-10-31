import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * AssistantWidget 用例（docs/12 §九）：
 * - 运营开关仅 ADMIN 可见
 * - 切模式不清空消息
 * - Esc 逐级退出（先最大化，再关闭）
 * - 管理员 ops 发送走 agentChat；普通用户走 chatStream
 */
const chatStream = vi.fn()
const chatWithAssistant = vi.fn()
const agentChat = vi.fn()
const agentConfirm = vi.fn()

vi.mock('@/api/assistant', () => ({
  chatStream: (...a) => chatStream(...a),
  chatWithAssistant: (...a) => chatWithAssistant(...a),
}))
vi.mock('@/api/agent', () => ({
  agentChat: (...a) => agentChat(...a),
  agentConfirm: (...a) => agentConfirm(...a),
}))
vi.mock('@/stores/player', () => ({ usePlayerStore: () => ({ current: null, barCollapsed: false }) }))

const { default: AssistantWidget } = await import('@/components/AssistantWidget.vue')
const { useAuthStore } = await import('@/stores/auth')

/** 挂载：auth 角色可指定；绕过持久化干扰（sessionStorage 由 jsdom 提供） */
async function mountWidget(role = 'ADMIN') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 1, username: 'admin', nickname: '站长', role }
  auth.token = 't'
  const { mount } = await import('@vue/test-utils')
  // 组件与测试必须共享同一个 pinia，否则组件里读到的 user 为空
  return mount(AssistantWidget, { global: { plugins: [pinia] } })
}

const openPanel = async (w) => {
  await w.find('button[aria-label*="AI 助手"]').trigger('click')
  await w.vm.$nextTick()
  return w.find('[role="dialog"]')
}

describe('AssistantWidget（v2 融合版）', () => {
  beforeEach(() => {
    sessionStorage.clear()
    chatStream.mockReset()
    chatWithAssistant.mockReset()
    agentChat.mockReset()
    agentConfirm.mockReset()
  })

  it('管理员：面板有「运营」开关；普通用户：没有', async () => {
    const admin = await mountWidget('ADMIN')
    const panel = await openPanel(admin)
    expect(panel.find('button[aria-pressed]').exists()).toBe(true)

    const guest = await mountWidget('USER')
    const panel2 = await openPanel(guest)
    expect(panel2.find('button[aria-pressed]').exists()).toBe(false)
  })

  it('管理员开运营模式：输入框 placeholder 变「下达运营指令…」+ 面板琥珀边框', async () => {
    const w = await mountWidget('ADMIN')
    const panel = await openPanel(w)
    await panel.find('button[aria-pressed]').trigger('click')

    expect(panel.find('textarea').attributes('placeholder')).toContain('下达运营指令')
    expect(panel.classes().join(' ')).toContain('amber')
  })

  it('切模式不清空消息：先聊一条，开运营后消息还在', async () => {
    chatStream.mockImplementation(({ onDelta }) => {
      onDelta('流式回答')
      return Promise.resolve({ personaName: '小灯' })
    })
    const w = await mountWidget('ADMIN')
    const panel = await openPanel(w)
    await panel.find('textarea').setValue('你好')
    await panel.find('textarea').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(w.text()).toContain('流式回答')

    await panel.find('button[aria-pressed]').trigger('click')   // 开运营
    expect(w.text()).toContain('流式回答')                       // 消息未被清空
  })

  it('运营模式发送 → 走 agentChat（非流式）；普通用户发消息 → 走 chatStream', async () => {
    agentChat.mockResolvedValue({ type: 'reply', content: '运营回复', toolCalls: [] })
    const w = await mountWidget('ADMIN')
    let panel = await openPanel(w)
    await panel.find('button[aria-pressed]').trigger('click')
    await panel.find('textarea').setValue('列出所有演出')
    await panel.find('textarea').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(agentChat).toHaveBeenCalledTimes(1)
    expect(chatStream).not.toHaveBeenCalled()
    await new Promise((r) => setTimeout(r, 600))
    const persisted = JSON.parse(sessionStorage.getItem('assistant_thread:xiaodeng') || '{"messages":[]}')
    console.log('[DEBUG] 持久化消息:', JSON.stringify(persisted.messages.map((m) => ({ role: m.role, mode: m.mode, text: (m.text || '').slice(0, 30), tools: m.tools?.length, pending: !!m.pending }))))
    console.log('[DEBUG] panel text:', w.text().slice(0, 500))
    expect(w.text()).toContain('运营回复')

    // 普通用户
    chatStream.mockImplementation(({ onDelta }) => { onDelta('游客答案'); return Promise.resolve({}) })
    const g = await mountWidget('USER')
    panel = await openPanel(g)
    await panel.find('textarea').setValue('推荐演出')
    await panel.find('textarea').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(chatStream).toHaveBeenCalledTimes(1)
    expect(agentChat).toHaveBeenCalledTimes(1)   // 仍是 1 次（游客没走 agent）
  })

  it('切人格可用：点 Anon 后展示愛音问候（回归 persona=undefined 导致线程丢失）', async () => {
    const w = await mountWidget('USER')
    const panel = await openPanel(w)
    expect(w.text()).toContain('我是小灯')

    await panel.findAll('button').find((b) => b.text() === 'Anon').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('千早愛音')          // 该人格的问候语已渲染

    await panel.findAll('button').find((b) => b.text() === '小祥').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('豊川祥子')

    await panel.findAll('button').find((b) => b.text() === '睦子米').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('睦')
  })

  it('Esc 逐级退出：最大化→还原→关闭', async () => {
    const w = await mountWidget('ADMIN')
    const ball = w.find('button[aria-label*="AI 助手"]')
    await ball.trigger('click')
    await w.find('[role="dialog"]').getByRole?.('button') // noop
    await w.find('[role="dialog"] button[aria-label="最大化"]').trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"]').classes().join(' ')).toContain('90vh')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(w.find('[role="dialog"]').classes().join(' ')).not.toContain('90vh')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(w.find('[role="dialog"]').exists()).toBe(false)
  })
})
