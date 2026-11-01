import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * MessageBubble 用例（docs/15 版本）：
 * - 工具条目：RUNNING 呼吸灰 / OK 淡灰 / FAIL 红
 * - 确认卡片：标题用后端给的 title（不是 toolName 映射表）
 * - 三态：PENDING → EXECUTED / REJECTED / FAILED
 * - 多张卡片各自绑自己的 token
 */
const { default: MessageBubble } = await import('@/components/assistant/MessageBubble.vue')

const mkMsg = (over = {}) => ({
  id: 1,
  role: 'assistant',
  name: '小灯',
  text: '准备执行',
  at: Date.now(),
  ...over,
})

const mkPending = (token, over = {}) => mkMsg({
  tools: [
    { name: 'search_songs', status: 'OK', summary: '共 1 首' },
    { name: 'update_song_mv', status: 'RUNNING', summary: '正在修改…' },
  ],
  pending: {
    token,
    toolName: 'update_song_mv',
    title: '《迷星叫》 的 MV 地址',
    arguments: { songId: 1, mvUrl: 'https://x' },
    preview: '《迷星叫》 的 MV 地址 → mvUrl=https://x，songId=1',
    state: 'PENDING',
    result: '',
  },
  ...over,
})

const mountBubble = (msg) => mount(MessageBubble, { props: { msg, confirming: false } })

describe('MessageBubble（v2）', () => {
  it('工具条目渲染：RUNNING 呼吸 / OK 常规 / FAIL 红', () => {
    const w = mountBubble(mkMsg({
      tools: [
        { name: 'search_songs', status: 'OK', summary: '共 1 首' },
        { name: 'list_events', status: 'RUNNING', summary: '正在查演出…' },
        { name: 'delete_event', status: 'FAIL', summary: '无权限' },
      ],
    }))
    const lines = w.findAll('p').map((p) => p.text()).filter((t) => t.includes('🔧'))
    expect(lines[0]).toContain('search_songs')
    expect(w.findAll('p').some((p) => p.classes().includes('animate-pulse'))).toBe(true)
    expect(w.findAll('p').some((p) => p.classes().includes('text-red-500'))).toBe(true)
  })

  it('确认卡片标题用后端 title，参数表可读', () => {
    const w = mountBubble(mkPending('tok-a'))
    expect(w.text()).toContain('⚠️ 待确认：《迷星叫》 的 MV 地址')   // 不是 update_song_mv
    expect(w.text()).toContain('songId')
    expect(w.text()).toContain('https://x')
    expect(w.text()).not.toContain('待确认：update_song_mv')
  })

  it('点确认执行 → emit confirm(pending, true)；点取消 → false', async () => {
    const w = mountBubble(mkPending('tok-a'))
    await w.findAll('button').find((b) => b.text() === '确认执行').trigger('click')
    expect(w.emitted('confirm')[0][1]).toBe(true)
    expect(w.emitted('confirm')[0][0].token).toBe('tok-a')

    const w2 = mountBubble(mkPending('tok-b'))
    await w2.findAll('button').find((b) => b.text() === '取消').trigger('click')
    expect(w2.emitted('confirm')[0][1]).toBe(false)
  })

  it('三态文案与配色：EXECUTED 绿 / REJECTED 灰 / FAILED 红', async () => {
    const w = mountBubble(mkPending('tok-a'))
    await w.setProps({ msg: mkPending('tok-a', { pending: { ...mkPending('tok-a').pending, state: 'EXECUTED', result: '已更新 MV 地址' } }) })
    expect(w.text()).toContain('✅ 已执行')
    expect(w.text()).toContain('已更新 MV 地址')

    await w.setProps({ msg: mkPending('tok-a', { pending: { ...mkPending('tok-a').pending, state: 'REJECTED' } }) })
    expect(w.text()).toContain('🚫 已取消')

    await w.setProps({ msg: mkPending('tok-a', { pending: { ...mkPending('tok-a').pending, state: 'FAILED', result: '该演出存在待支付或已支付订单' } }) })
    expect(w.text()).toContain('❌ 执行失败')
    expect(w.text()).toContain('该演出存在待支付或已支付订单')   // 原样展示
  })

  it('两张卡片各自绑自己的 token（不串台）', async () => {
    const a = mountBubble(mkPending('token-a'))
    const b = mountBubble(mkPending('token-b'))
    await a.findAll('button').find((x) => x.text() === '确认执行').trigger('click')
    await b.findAll('button').find((x) => x.text() === '确认执行').trigger('click')
    expect([a.emitted('confirm')[0][0].token, b.emitted('confirm')[0][0].token]).toEqual(['token-a', 'token-b'])
  })

  it('流式文本 + 长文折叠', async () => {
    const shortMsg = mkMsg({ text: '你好', streaming: true })
    const w = mountBubble(shortMsg)
    expect(w.text()).toContain('你好')

    const long = mountBubble(mkMsg({ text: 'a'.repeat(600) }))
    expect(long.text()).toContain('展开全文')
  })
})
