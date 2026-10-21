import request from './request'

/** 开售提醒（需登录） */
export const subscribeReminder = (eventId) => request.post(`/events/${eventId}/reminder`)
export const cancelReminder = (eventId) => request.delete(`/events/${eventId}/reminder`)
export const myReminders = () => request.get('/reminders')
