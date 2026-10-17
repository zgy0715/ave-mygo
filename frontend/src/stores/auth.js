import { defineStore } from 'pinia'
import { login as apiLogin } from '@/api/auth'

/**
 * 登录态仓库：token + 用户信息。
 * 存储取舍：sessionStorage（关浏览器即清），生产级方案见 request.js 注释。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('token') || '',
    user: JSON.parse(sessionStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLogin: (s) => !!s.token,
    nickname: (s) => s.user?.nickname || s.user?.username || '',
  },
  actions: {
    async login(username, password) {
      const data = await apiLogin({ username, password })
      this.token = data.token
      this.user = data.user
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('user', JSON.stringify(data.user))
    },
    logout() {
      this.token = ''
      this.user = null
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
    },
  },
})
