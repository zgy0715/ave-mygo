import request from './request'

/** 歌曲评论（公开可读；发布/删除需登录） */
export const listComments = (songId, page = 1, pageSize = 10) =>
  request.get(`/songs/${songId}/comments`, { params: { page, pageSize } })
export const createComment = (songId, content) => request.post('/comments', { songId, content })
export const deleteComment = (id) => request.delete(`/comments/${id}`)
