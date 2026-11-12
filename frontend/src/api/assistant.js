import request from './request'

/**
 * AI 助手「小灯」（对应后端 AssistantController）
 * v2（docs/15）：POST /assistant/chat/v2 事件流；身份由 token 决定能力
 *   - 游客 / 普通用户：只读工具
 *   - ADMIN：全部工具（写操作会下发 confirm 事件，人工确认后才执行）
 */

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api/v1'

/**
 * v2 对话流（SSE over POST）
 * 事件：status(phase=thinking) / delta(text) / tool(name,status:RUNNING|OK|FAIL,summary) /
 *      confirm(token,toolName,title,arguments,preview) / done(usedTools,elapsedMs,pending?) /
 *      error(code,message)
 *
 * 注意：不要用 EventSource（只支持 GET）；不要给这个请求设短超时
 *      （多轮工具可能 20~30s），只在用户点「停止」时 abort。
 *
 * @param {object} p { message, conversationId, onEvent(evt), signal }
 * @returns {Promise<object>} done 事件
 */
export async function chatV2({ message, conversationId, persona, onEvent, signal }) {
  const token = sessionStorage.getItem('token')
  const res = await fetch(`${API_BASE}/assistant/chat/v2`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, conversationId, persona }),
    signal,
  })
  if (!res.ok || !res.body) throw new Error(`stream failed: ${res.status}`)

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let doneEvt = null

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 事件以空行分隔；最后一段可能被 TCP 切断，留在 buffer 等下一个 chunk
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) {
      const line = frame.split('\n').find((l) => l.startsWith('data:'))
      if (!line) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      let evt
      try { evt = JSON.parse(raw) } catch { continue }
      if (evt.type === 'done') doneEvt = evt
      onEvent?.(evt)
    }
  }
  return doneEvt || {}
}

/**
 * v1 一次性问答：v2 流不可用时的兜底（老后端 / 代理不支持流式）
 * 返回 { reply, intent, personaName }
 */
export const chatWithAssistant = (body, config = {}) => request.post('/assistant/chat', body, { ...config, timeout: 60_000 })
