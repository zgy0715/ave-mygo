import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'

/**
 * AgentConsole 用例（docs/11 验收清单·测试项）：
 * - mock agentChat 返回 pending_confirmation → 确认卡片渲染
 * - 点「确认执行」→ 调用 agentConfirm(token, true)；成功 → 卡片变「已执行」
 * - 取消 → agentConfirm(token, false) → 卡片变「已取消」
 * - 50301 → 灰色系统提示，不整页报错
 */
const agentChat = vi.fn()
const agentConfirm = vi.fn()
vi.mock('@/api/agent', () => ({
  agentChat: (...a) => agentChat(...a),
  agentConfirm: (...a) => agentConfirm(...a),
}))

const { default: AgentConsole } = await import('@/components/AgentConsole.vue')

const mountConsole = () =>
  mount(AgentConsole, { global: { plugins: [createPinia()] } })

const PENDING = {
  type: 'pending_confirmation',
  content: '准备执行：修改歌曲 MV',
  token: 'tok-1',
  toolName: 'update_song_mv',
  arguments: { songId: 1, mvUrl: 'https://www.bilibili.com/video/BV1GJ411x7h7' },
  toolCalls: [{ name: 'search_songs', status: 'EXECUTED', summary: '共 1 首' }],
}

const typeAndSend = async (w, text) => {
  await w.find('textarea').setValue(text)
  await w.find('textarea').trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

describe('AgentConsole', () => {
  beforeEach(() => {
    agentChat.mockReset()
    agentConfirm.mockReset()
  })

  it('pending_confirmation 渲染确认卡片（中文动作 + 参数表 + 工具条目）', async () => {
    agentChat.mockResolvedValue(PENDING)
    const w = mountConsole()
    await typeAndSend(w, '把迷星叫的 MV 换成 https://x')

    expect(w.text()).toContain('⚠️ 待确认：修改歌曲 MV')      // toolName → 中文映射
    expect(w.text()).toContain('songId')
    expect(w.text()).toContain('1')
    expect(w.text()).toContain('🔧')                          // 工具条目
    expect(w.text()).toContain('search_songs')
    expect(agentConfirm).not.toHaveBeenCalled()               // 此时数据库未动
  })

  it('点「确认执行」→ agentConfirm(token, true) → 卡片变「已执行」', async () => {
    agentChat.mockResolvedValue(PENDING)
    agentConfirm.mockResolvedValue({ status: 'EXECUTED', result: '已更新 MV 地址' })
    const w = mountConsole()
    await typeAndSend(w, '换 MV')

    await w.find('button.btn-primary').trigger('click')       // 红色确认键
    await flushPromises()

    expect(agentConfirm).toHaveBeenCalledWith('tok-1', true)
    expect(w.text()).toContain('✅ 已执行')
    expect(w.text()).toContain('已更新 MV 地址')
  })

  it('点「取消」→ agentConfirm(token, false) → 卡片变「已取消」', async () => {
    agentChat.mockResolvedValue(PENDING)
    agentConfirm.mockResolvedValue({ status: 'REJECTED', result: '' })
    const w = mountConsole()
    await typeAndSend(w, '换 MV')

    await w.findAll('button').find((b) => b.text() === '取消').trigger('click')
    await flushPromises()

    expect(agentConfirm).toHaveBeenCalledWith('tok-1', false)
    expect(w.text()).toContain('🚫 已取消')
  })

  it('确认失败（FAILED）→ 红色卡片展示后端原文', async () => {
    agentChat.mockResolvedValue(PENDING)
    agentConfirm.mockResolvedValue({ status: 'FAILED', result: '该演出存在待支付或已支付订单' })
    const w = mountConsole()
    await typeAndSend(w, '删掉它')

    await w.find('button.btn-primary').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('❌ 执行失败')
    expect(w.text()).toContain('该演出存在待支付或已支付订单')  // 保留原文不改写
  })

  it('50301 LLM 不可用 → 灰色系统提示引导只读问答，不整页报错', async () => {
    agentChat.mockRejectedValue(new Error('Agent 需要 LLM 才能工作'))
    const w = mountConsole()
    await typeAndSend(w, '列出所有演出')

    expect(w.text()).toContain('小灯暂时离线')
    expect(w.text()).toContain('智能问答')
    expect(w.find('.text-red-500').exists()).toBe(false)
  })
})
