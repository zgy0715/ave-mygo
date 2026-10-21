import { describe, expect, it, vi } from 'vitest'
import { chatStream } from '@/api/assistant'

/**
 * SSE 解析测试：用假的 ReadableStream 模拟后端分片，
 * 重点验证「一个事件被 TCP 切成两半」时也能正确拼接。
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

describe('chatStream（SSE）', () => {
  it('按 delta 逐段回调，并在 done 帧返回元信息', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse([
        'data:{"delta":"小灯："}\n\n',
        'data:{"delta":"你好"}\n\n',
        'data:{"done":true,"intent":"GENERAL","persona":"xiaodeng","personaName":"小灯"}\n\n',
      ]),
    )
    const deltas = []
    const meta = await chatStream({ message: 'hi', sessionId: 's', persona: 'xiaodeng', onDelta: (d) => deltas.push(d) })
    expect(deltas.join('')).toBe('小灯：你好')
    expect(meta).toMatchObject({ done: true, personaName: '小灯' })
  })

  it('事件被拆到两个分片里也能正确解析', async () => {
    global.fetch = vi.fn(async () =>
      sseResponse([
        'data:{"del',            // 半个事件
        'ta":"A"}\n\ndata:{"delta":"B"}\n\n',
        'data:{"done":true}\n\n',
      ]),
    )
    const deltas = []
    await chatStream({ message: 'x', sessionId: 's', persona: 'anon', onDelta: (d) => deltas.push(d) })
    expect(deltas.join('')).toBe('AB')
  })

  it('HTTP 非 2xx 时抛错（供调用方降级到普通接口）', async () => {
    global.fetch = vi.fn(async () => ({ ok: false, status: 502, body: null }))
    await expect(chatStream({ message: 'x', sessionId: 's', persona: 'anon' })).rejects.toThrow(/stream failed/)
  })
})
