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
