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

  it('助手气泡不再依赖 .card：16px 圆角生效、无 hover 上浮', () => {
    const w = mountBubble(mkMsg({ text: '你好' }))
    const b = w.find('[data-bubble]')
    expect(b.classes()).not.toContain('card')            // .card 的 6px / translateY(-4px) 不再介入
    expect(b.classes()).toContain('rounded-2xl')
    expect(b.classes()).toContain('rounded-bl-sm')
    expect(b.classes()).toContain('bg-white')
  })

  it('hover 操作条不再被 hidden 吞掉（回归 group-hover/none 拼写错误）', () => {
    const w = mountBubble(mkMsg({ text: '你好' }))
    const bar = w.find('[data-msg-actions]')
    expect(bar.classes()).not.toContain('hidden')
    expect(bar.classes().join(' ')).toContain('group-hover/msg:opacity-100')
    expect(w.html()).toContain('group/msg')
    expect(w.html()).not.toContain('group-hover/none')
  })

  it('工具条目默认单行截断，点击展开完整摘要', async () => {
    const w = mountBubble(mkMsg({ tools: [{ name: 'list_events', status: 'OK', summary: '共 6 场演出资料' }] }))
    const line = w.findAll('p').find((p) => p.text().includes('🔧'))
    const sum = line.find('[data-tool-summary]')
    expect(sum.classes()).toContain('truncate')
    expect(sum.classes()).not.toContain('break-all')

    await line.trigger('click')
    expect(sum.classes()).not.toContain('truncate')
    expect(sum.classes()).toContain('break-words')
    expect(line.text()).toContain('共 6 场演出资料')
  })

  it('meta 行合并名字与时间，气泡下方不再重复时间', () => {
    const w = mountBubble(mkMsg({ text: '你好' }))
    const meta = w.find('[data-msg-meta]')
    expect(meta.text()).toContain('小灯')
    expect(meta.text()).toMatch(/\d{2}:\d{2}/)
    // 时间只出现一次（meta 内），不再在气泡外多一行
    expect((w.text().match(/\d{2}:\d{2}/g) || []).length).toBe(1)
  })

  it('轻排版：**粗体** 渲染为 <strong>，不残留星号', () => {
    const w = mountBubble(mkMsg({ text: '结论：**今天有 6 场**。' }))
    expect(w.find('strong').text()).toBe('今天有 6 场')
    expect(w.text()).not.toContain('**')
  })

  it('轻排版：编号列表按 marker 渲染，不吞文字', () => {
    const w = mountBubble(mkMsg({ text: '成员：\n1. 高松燈\n2. 千早愛音' }))
    expect(w.text()).toContain('1.')
    expect(w.text()).toContain('高松燈')
    expect(w.text()).toContain('千早愛音')
  })

  it('禁止 HTML 注入：<img onerror> 不产生真实元素', () => {
    const w = mountBubble(mkMsg({ text: '<img src=x onerror=alert(1)>' }))
    expect(w.find('img').exists()).toBe(false)
    expect(w.text()).toContain('<img src=x onerror=alert(1)>')
  })
})
