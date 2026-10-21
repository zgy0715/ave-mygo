import request from './request'

/** 歌单接口（需登录） */
export const listPlaylists = () => request.get('/playlists')
export const createPlaylist = (body) => request.post('/playlists', body)      // {name, description?}
export const updatePlaylist = (id, body) => request.patch(`/playlists/${id}`, body)
export const deletePlaylist = (id) => request.delete(`/playlists/${id}`)
export const getPlaylist = (id) => request.get(`/playlists/${id}`)
export const addSongToPlaylist = (id, songId) => request.post(`/playlists/${id}/songs`, { songId })
export const removeSongFromPlaylist = (id, songId) => request.delete(`/playlists/${id}/songs/${songId}`)
