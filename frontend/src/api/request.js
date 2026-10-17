import axios from 'axios'
import router from '@/router'

/**
 * 全站统一的 HTTP 客户端。
 * 调用方式：request.get('/songs') —— 返回的永远是后端的 data 部分。
 *
 * token 存储取舍：sessionStorage（关浏览器即清除）。
 * 已知风险：XSS 可读取；生产级方案 = 短期 token 放内存 + 刷新令牌走 HttpOnly Cookie。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api/v1',
  timeout: 10_000,
})

// 请求拦截：自动携带 token
request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一拆包 + 统一错误
request.interceptors.response.use(
  (res) => {
    const { code, message, data } = res.data
    if (code === 0) return data                       // 成功：剥壳，直接给业务数据
    if (code === 40101) {                             // 业务层判定未登录
      sessionStorage.removeItem('token')
      router.push('/login')
      return Promise.reject(new Error('请先登录'))
    }
    return Promise.reject(new Error(message || '操作失败'))
  },
  (err) => {
    // HTTP 层错误：401（Security 拦截）/ 网络断开 / 500
    if (err.response?.status === 401) {
      sessionStorage.removeItem('token')
      router.push('/login')
      return Promise.reject(new Error('请先登录'))
    }
    return Promise.reject(new Error(err.response?.data?.message || '服务开小差了，请稍后重试'))
  },
)

export default request
