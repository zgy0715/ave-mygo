import request from './request'

/**
 * AI 助手（对应后端 AssistantController，公开接口无需登录）
 * body: { message: string(≤500字), sessionId: string(前端生成，后端凭它续会话) }
 * 返回: { reply, intent: 'SONG_QA'|'EVENT_RECO'|'GENERAL'|'FALLBACK' }
 */
// 大模型首答可能较慢（本地模型加载），单独放宽到 60s
export const chatWithAssistant = (body) => request.post('/assistant/chat', body, { timeout: 60_000 })
