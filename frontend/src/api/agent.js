import request from './request'

/**
 * 小灯 Agent 运营台（对应后端 AgentController，仅 ADMIN）
 * 文档：docs/11-前端提示词-Agent运营台.md
 *
 * 超时必须单独放开：共享实例默认 10s，而 Agent 一次请求内部要跑多轮 LLM
 * （工具调用 → 结果回填 → 生成回答），本地 8B 模型冷启动可达 60s+。
 * 2026-10-31 真机联调实测：沿用 10s 会稳定报「请求超时」，看起来像"功能坏了"。
 */

/** Agent 对话：返回 { type:'reply'|'pending_confirmation', content, token, toolName, arguments, toolCalls } */
export const agentChat = (message, conversationId) =>
  request.post('/admin/agent/chat', { message, conversationId }, { timeout: 180_000 })

/** 确认/取消待执行写操作：{ status:'EXECUTED'|'REJECTED'|'FAILED', result } */
export const agentConfirm = (token, approve) =>
  request.post('/admin/agent/confirm', { token, approve }, { timeout: 60_000 })

// 超时说明：运营模式一次请求内部跑多轮 LLM（20~60s），共享 axios 默认 10s 会误杀
