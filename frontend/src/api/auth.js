import request from './request'

/** 认证接口（对应后端 AuthController） */
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
