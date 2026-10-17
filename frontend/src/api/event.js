import request from './request'

/** 演出接口（对应后端 EventController） */
export const listEvents = (params) => request.get('/events', { params })
export const getEvent = (id) => request.get(`/events/${id}`)
