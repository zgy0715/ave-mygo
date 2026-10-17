import request from './request'

/** 歌曲库接口（对应后端 SongController） */
export const listSongs = (params) => request.get('/songs', { params })
export const getSong = (id) => request.get(`/songs/${id}`)
export const listAlbums = (bandId) => request.get('/albums', { params: { bandId } })
