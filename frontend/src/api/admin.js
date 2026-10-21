import request from './request'

/**
 * 管理端接口（对应后端 AdminController，/api/v1/admin/** 仅 ADMIN 可访问；
 * 401/403 由 request.js 拦截器统一处理）
 */
export const listAdminSongs = (params) => request.get('/admin/songs', { params })

/** 新建歌曲：body 全量字段，albumId/trackNo 等可缺省 */
export const createAdminSong = (body) => request.post('/admin/songs', body)

/** 更新歌曲：null 字段后端不更新 */
export const updateAdminSong = (id, body) => request.patch(`/admin/songs/${id}`, body)

/** 软删除 */
export const deleteAdminSong = (id) => request.delete(`/admin/songs/${id}`)

/**
 * 首页 Hero 背景视频上传（multipart：slot=mygo|mujica，file=mp4）
 * ⚠️ 后端接口由全栈智能体实现（POST /admin/hero-videos，覆盖 frontend/public/assets/band-{slot}.mp4）
 */
export const uploadHeroVideo = (slot, file) => {
  const fd = new FormData()
  fd.append('slot', slot)
  fd.append('file', file)
  return request.post('/admin/hero-videos', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/* ===== 演出 / 场次 / 票档（管理端）===== */
export const listAdminEvents = (params) => request.get('/admin/events', { params })
export const createAdminEvent = (body) => request.post('/admin/events', body)
export const updateAdminEvent = (id, body) => request.patch(`/admin/events/${id}`, body)

export const listAdminSessions = (eventId) => request.get(`/admin/events/${eventId}/sessions`)
export const createAdminSession = (eventId, body) => request.post(`/admin/events/${eventId}/sessions`, body)
export const updateAdminSession = (id, body) => request.patch(`/admin/sessions/${id}`, body)

export const createTicketType = (sessionId, body) => request.post(`/admin/sessions/${sessionId}/ticket-types`, body)
export const updateTicketType = (id, body) => request.patch(`/admin/ticket-types/${id}`, body)

/* ===== 订单管理（管理端） ===== */
export const listAdminOrders = (params) => request.get('/admin/orders', { params })
export const getAdminStats = (days = 30) => request.get('/admin/stats/overview', { params: { days } })
export const refundAdminOrder = (orderNo, body) => request.patch(`/admin/orders/${orderNo}/refund`, body)

/* ===== 用户管理（管理端） ===== */
export const listAdminUsers = (params) => request.get('/admin/users', { params })
export const updateAdminUserStatus = (id, status) => request.patch(`/admin/users/${id}/status`, { status })
