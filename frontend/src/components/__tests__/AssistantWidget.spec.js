import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * AssistantWidget v2 用例（docs/15）：
 * - 面板里没有「运营」开关/字样
 * - v2 事件流：tool 条目、delta 文本、confirm 卡片、error 提示
 * - 确认卡片调用 agentConfirm(token, true)，成功后变「已执行」
 * - 切人格仍可用；Esc 逐级退出
 */
const chatV2 = vi.fn()
const chatWithAssistant = vi.fn()
const agentConfirm = vi.fn()

vi.mock('@/api/assistant', () => ({
  chatV2: (...a) => chatV2(...a),
  chatWithAssistant: (...a) => chatWithAssistant(...a),
}))
vi.mock('@/api/agent', () => ({ agentConfirm: (...a) => agentConfirm(...a) }))
vi.mock('@/stores/player', () => ({ usePlayerStore: () => ({ current: null, barCollapsed: false }) }))

const { default: AssistantWidget } = await import('@/components/AssistantWidget.vue')
const { useAuthStore } = await import('@/stores/auth')

async function mountWidget(role = 'USER') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 1, username: 'u', nickname: '用户', role }
  auth.token = 't'
  return mount(AssistantWidget, { global: { plugins: [pinia] } })
}

const openPanel = async (w) => {
  await w.find('button[aria-label*="AI 助手"]').trigger('click')
  await w.vm.$nextTick()
  return w.find('[role="dialog"]')
}

const sendMsg = async (panel, text) => {
  await panel.find('textarea').setValue(text)
  await panel.find('textarea').trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

/** 用固定事件序列模拟后端 v2 流 */
const fakeStream = (events) => {
  chatV2.mockImplementation(async ({ onEvent }) => {
    events.forEach((e) => onEvent(e))
    return events.find((e) => e.type === 'done') || {}
  })
}

describe('AssistantWidget（v2 单链路）', () => {
  beforeEach(() => {
    sessionStorage.clear()
    chatV2.mockReset()
    chatWithAssistant.mockReset()
    agentConfirm.mockReset()
  })

  it('面板没有「运营」开关和字样', async () => {
    const w = await mountWidget('ADMIN')
    const panel = await openPanel(w)
    expect(panel.find('button[aria-pressed]').exists()).toBe(false)
    expect(panel.text()).not.toContain('运营')
    expect(panel.classes().join(' ')).not.toContain('amber')
  })

  it('工具条目 + 逐字文本 + done 收尾', async () => {
    fakeStream([
      { type: 'status', phase: 'thinking' },
      { type: 'tool', name: 'list_events', status: 'RUNNING', summary: '正在查演出…' },
      { type: 'tool', name: 'list_events', status: 'OK', summary: '共 6 场' },
      { type: 'delta', text: '站内目前共' },
      { type: 'delta', text: ' 6 场演出。' },
      { type: 'done', usedTools: 1, elapsedMs: 1200 },
    ])
    const w = await mountWidget()
    const panel = await openPanel(w)
    await sendMsg(panel, '站里现在有几场演出')

    expect(w.text()).toContain('🔧')
    expect(w.text()).toContain('list_events')
    expect(w.text()).toContain('共 6 场')
    expect(w.text()).toContain('站内目前共 6 场演出。')
    // RUNNING → OK 只保留一条，不重复
    expect(w.text().split('list_events').length - 1).toBe(1)
  })

  it('confirm 事件出卡片（标题用 title）→ 确认执行调 agentConfirm 并变绿', async () => {
    fakeStream([
      { type: 'confirm', token: 'tok-1', toolName: 'update_song_mv', title: '《迷星叫》 的 MV 地址', arguments: { songId: 1, mvUrl: 'https://x' }, preview: '…' },
      { type: 'done', usedTools: 1, pending: true },
    ])
    agentConfirm.mockResolvedValue({ status: 'EXECUTED', result: '已更新《迷星叫》的 MV 地址' })

    const w = await mountWidget('ADMIN')
    const panel = await openPanel(w)
    await sendMsg(panel, '把迷星叫的 MV 换成 https://x')

    expect(w.text()).toContain('⚠️ 待确认：《迷星叫》 的 MV 地址')
    expect(agentConfirm).not.toHaveBeenCalled()       // 未点确认前不执行

    await panel.findAll('button').find((b) => b.text() === '确认执行').trigger('click')
    await flushPromises()
    expect(agentConfirm).toHaveBeenCalledWith('tok-1', true)
    expect(w.text()).toContain('✅ 已执行')
    expect(w.text()).toContain('已更新《迷星叫》的 MV 地址')
  })

  it('error 事件（50301）→ 提示小灯暂时离线，不白屏', async () => {
    fakeStream([
      { type: 'error', code: 50301, message: 'LLM 未就绪' },
      { type: 'done' },
    ])
    const w = await mountWidget()
    const panel = await openPanel(w)
    await sendMsg(panel, '你好')

    expect(w.text()).toContain('小灯暂时离线')
  })

  it('切人格可用（回归 persona=undefined）', async () => {
    const w = await mountWidget()
    const panel = await openPanel(w)
    expect(w.text()).toContain('我是小灯')
    await panel.findAll('button').find((b) => b.text() === 'Anon').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('千早愛音')
    await panel.findAll('button').find((b) => b.text() === '小祥').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('豊川祥子')
  })

  it('Esc 逐级退出：最大化 → 还原 → 关闭', async () => {
    const w = await mountWidget()
    await w.find('button[aria-label*="AI 助手"]').trigger('click')
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
