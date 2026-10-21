import request from './request'

/** 全局搜索：歌曲 + 演出两组（公开接口） */
export const searchAll = (keyword, limit = 5) => request.get('/search', { params: { keyword, limit } })
