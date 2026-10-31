import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * MessageBubble 用例（docs/12 §九）：
 * - 工具条目渲染与状态色
 * - 确认卡片三态（PENDING→确认→EXECUTED / REJECTED / FAILED）
 * - 多张卡片各自确认各自的 token（回归 v1 lastPending 串台 bug）
 */
const { default: MessageBubble } = await import('@/components/assistant/MessageBubble.vue')

const mkPending = (token) => ({
  id: 1, role: 'assistant', mode: 'ops', name: '小灯', text: '准备执行', at: Date.now(),
  tools: [{ name: 'search_songs', status: 'EXECUTED', summary: '共 1 首' }],
  pending: { token, toolName: 'update_song_mv', arguments: { songId: 1, mvUrl: 'https://x' }, state: 'PENDING', result: '' },
})

const mountBubble = (msg) => mount(MessageBubble, { props: { msg, confirming: false } })

describe('MessageBubble', () => {
  it('渲染工具条目与状态', () => {
    const w = mountBubble(mkPending('tok-a'))
    expect(w.text()).toContain('🔧')
    expect(w.text()).toContain('search_songs')
    expect(w.text()).toContain('EXECUTED')
    expect(w.text()).toContain('共 1 首')
  })

  it('PENDING 卡片显示中文动作与参数，确认→EXECUTED', async () => {
    const w = mountBubble(mkPending('tok-a'))
    expect(w.text()).toContain('⚠️ 待确认：修改歌曲 MV')
    expect(w.text()).toContain('https://x')

    await w.findAll('button').find((b) => b.text() === '确认执行').trigger('click')
    expect(w.emitted('confirm')[0]).toEqual([mkPending('tok-a').pending, true])

    // 模拟父层回写状态（与 widget confirm 行为一致）
    w.setProps({ msg: { ...mkPending('tok-a'), pending: { ...mkPending('tok-a').pending, state: 'EXECUTED', result: '已更新' } } })
    await w.vm.$nextTick()
    expect(w.text()).toContain('✅ 已执行')
  })

  it('REJECTED / FAILED 三态配色文案', async () => {
    const w = mountBubble(mkPending('tok-a'))
    await w.findAll('button').find((b) => b.text() === '取消').trigger('click')
    expect(w.emitted('confirm')[0][1]).toBe(false)

    await w.setProps({ msg: { ...mkPending('tok-a'), pending: { ...mkPending('tok-a').pending, state: 'REJECTED' } } })
    expect(w.text()).toContain('🚫 已取消')

    await w.setProps({ msg: { ...mkPending('tok-a'), pending: { ...mkPending('tok-a').pending, state: 'FAILED', result: '该演出存在待支付或已支付订单' } } })
    expect(w.text()).toContain('❌ 执行失败')
    expect(w.text()).toContain('该演出存在待支付或已支付订单')   // 原样展示
  })

  it('两张卡片各自绑自己的 token（回归 v1 串台 bug）', async () => {
    const a = mkPending('token-a')
    const b = mkPending('token-b')
    const w1 = mountBubble(a)
    const w2 = mountBubble(b)

    await w1.findAll('button').find((x) => x.text() === '确认执行').trigger('click')
    await w2.findAll('button').find((x) => x.text() === '确认执行').trigger('click')

    const calls = w1.emitted('confirm').concat(w2.emitted('confirm'))
    expect(calls.map((c) => c[0].token)).toEqual(['token-a', 'token-b'])   // 不串台
  })

  it('FAILED + 40902 → 提示重新下达指令', async () => {
    const w = mountBubble(mkPending('tok-old'))
    await w.setProps({ msg: { ...mkPending('tok-old'), pending: { ...mkPending('tok-old').pending, state: 'FAILED', result: '这次确认已失效，请重新下达指令' } } })
    expect(w.text()).toContain('请重新下达指令')
  })
})
