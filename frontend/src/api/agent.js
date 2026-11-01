import request from './request'

/**
 * Agent 写操作确认（docs/15 v2 接入后）
 * 对话统一走 /assistant/chat/v2，写操作由后端下发 confirm 事件；
 * 前端点「确认执行」后调用本接口，不再需要独立的 agentChat。
 *
 * 超时单独放开：核对/执行会走服务层（可能 10~60s），共享默认 10s 会误杀。
 */
export const agentConfirm = (token, approve) =>
  request.post('/admin/agent/confirm', { token, approve }, { timeout: 60_000 })
