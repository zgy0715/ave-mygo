import request from './request'

/** 演出接口（对应后端 EventController） */
export const listEvents = (params) => request.get('/events', { params })
export const getEvent = (id) => request.get(`/events/${id}`)
/** 某场次的演出歌单（setlist + song 联取） */
export const getSetlist = (sessionId) => request.get(`/events/sessions/${sessionId}/setlist`)
