import request from './request'

/**
 * AI 助手（对应后端 AssistantController，公开接口无需登录）
 * body: { message: string(≤500字), sessionId: string(前端生成，后端凭它续会话) }
 * 返回: { reply, intent: 'SONG_QA'|'EVENT_RECO'|'GENERAL'|'FALLBACK' }
 */
// 大模型首答可能较慢（本地模型加载），单独放宽到 60s
export const chatWithAssistant = (body) => request.post('/assistant/chat', body, { timeout: 60_000 })

/**
 * 流式问答（SSE）：POST /assistant/chat/stream
 * 返回 text/event-stream，逐条 data: {"delta":"..."} … 最后 data: {"done":true,...}
 * 用 fetch + ReadableStream 解析（EventSource 不支持 POST）
 * @param {object} p { message, sessionId, persona, onDelta(text), signal }
 * @returns {Promise<object>} 结束帧（含 intent / personaName）
 */
export async function chatStream({ message, sessionId, persona, onDelta, signal }) {
  const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api/v1'
  const token = sessionStorage.getItem('token')
  const res = await fetch(`${base}/assistant/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, sessionId, persona }),
    signal,
  })
  if (!res.ok || !res.body) throw new Error(`stream failed: ${res.status}`)

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let meta = null

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // SSE 事件以空行分隔；最后一段可能不完整，留在 buffer
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    for (const chunk of chunks) {
      const line = chunk.split('\n').find((l) => l.startsWith('data:'))
      if (!line) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      let obj
      try { obj = JSON.parse(raw) } catch { continue }
      if (obj.delta) onDelta?.(obj.delta)
      if (obj.done) meta = obj
    }
  }
  return meta || {}
}
