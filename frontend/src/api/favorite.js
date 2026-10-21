import request from './request'

/** 收藏接口（需登录；targetType = SONG | EVENT） */
export const listFavorites = (targetType = 'SONG') => request.get('/favorites', { params: { targetType } })
export const addFavorite = (targetType, targetId) => request.post('/favorites', { targetType, targetId })
export const removeFavorite = (targetType, targetId) => request.delete(`/favorites/${targetType}/${targetId}`)
