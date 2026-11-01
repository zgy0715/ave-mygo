import { describe, expect, it, vi } from 'vitest'
import { chatV2 } from '@/api/assistant'

/**
 * chatV2 解析单测（docs/15 §五）：
 * - 喂固定 SSE 文本，断言事件序列（status → tool×2 → delta×2 → done）
 * - 事件被 TCP 拆成两半也能拼回
 * - HTTP 非 2xx 抛错（供上层提示离线）
 * - 写操作：confirm 事件解析出 title / arguments / preview
 */
function sseResponse(parts) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    start(controller) {
      parts.forEach((p) => controller.enqueue(encoder.encode(p)))
      controller.close()
    },
  })
  return { ok: true, status: 200, body: stream }
}

describe('chatV2（事件流解析）', () => {
  it('按顺序回调 status/tool/delta/done', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse([
        'data:{"type":"status","phase":"thinking"}\n\n',
        'data:{"type":"tool","name":"search_songs","status":"RUNNING","summary":"正在查歌曲…"}\n\n',
        'data:{"type":"tool","name":"search_songs","status":"OK","summary":"共 1 首"}\n\n',
        'data:{"type":"delta","text":"《春日影》出自"}\n\n',
        'data:{"type":"delta","text":"专辑《迷跡波》。"}\n\n',
        'data:{"type":"done","usedTools":1,"conversationId":"v2-q1","elapsedMs":10203}\n\n',
      ]),
    )
    const seen = []
    let text = ''
    const done = await chatV2({
      message: '春日影是哪张专辑',
      conversationId: 'v2-q1',
      onEvent: (e) => {
        seen.push(e.type)
        if (e.type === 'delta') text += e.text
      },
    })

    expect(seen).toEqual(['status', 'tool', 'tool', 'delta', 'delta', 'done'])
    expect(text).toBe('《春日影》出自专辑《迷跡波》。')
    expect(done).toMatchObject({ usedTools: 1, elapsedMs: 10203 })
  })

  it('事件被 TCP 拆成两半也能解析', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse([
        'data:{"type":"del',
        'ta","text":"A"}\n\ndata:{"type":"delta","text":"B"}\n\n',
        'data:{"type":"done"}\n\n',
      ]),
    )
    let text = ''
    await chatV2({
      message: 'x',
      conversationId: 'c',
      onEvent: (e) => { if (e.type === 'delta') text += e.text },
    })
    expect(text).toBe('AB')
  })

  it('写操作：confirm 事件带 title / arguments / preview', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse([
        'data:{"type":"confirm","token":"8f3c","toolName":"update_song_mv","title":"《迷星叫》 的 MV 地址","arguments":{"songId":1,"mvUrl":"https://x"},"preview":"《迷星叫》 的 MV 地址 → mvUrl=https://x，songId=1"}\n\n',
        'data:{"type":"done","usedTools":1,"conversationId":"v2-q2","pending":true,"elapsedMs":9600}\n\n',
      ]),
    )
    let card = null
    const done = await chatV2({
      message: '换 MV',
      conversationId: 'v2-q2',
      onEvent: (e) => { if (e.type === 'confirm') card = e },
    })
    expect(card.title).toBe('《迷星叫》 的 MV 地址')
    expect(card.arguments.songId).toBe(1)
    expect(card.preview).toContain('→')
    expect(done.pending).toBe(true)
  })

  it('error 事件原样透给上层（50301 LLM 未就绪）', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse(['data:{"type":"error","code":50301,"message":"Agent 需要 LLM"}\n\n']),
    )
    let err = null
    await chatV2({ message: 'x', conversationId: 'c', onEvent: (e) => { if (e.type === 'error') err = e } })
    expect(err.code).toBe(50301)
  })

  it('HTTP 非 2xx → 抛错（上层提示离线/重试）', async () => {
    global.fetch = vi.fn(async () => ({ ok: false, status: 502, body: null }))
    await expect(chatV2({ message: 'x', conversationId: 'c' })).rejects.toThrow(/stream failed/)
  })
})
