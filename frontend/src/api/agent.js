import request from './request'

/**
 * 小灯 Agent 运营台（对应后端 AgentController，仅 ADMIN）
 * 文档：docs/11-前端提示词-Agent运营台.md
 */

/** Agent 对话：返回 { type:'reply'|'pending_confirmation', content, token, toolName, arguments, toolCalls } */
export const agentChat = (message, conversationId) =>
  request.post('/admin/agent/chat', { message, conversationId })

/** 确认/取消待执行写操作：{ status:'EXECUTED'|'REJECTED'|'FAILED', result } */
export const agentConfirm = (token, approve) =>
  request.post('/admin/agent/confirm', { token, approve })
