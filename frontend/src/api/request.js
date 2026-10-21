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

/**
 * 统一处理登录失效：
 * 清 sessionStorage + 通知 Pinia 清状态（用事件解耦，避免 request ↔ store 循环依赖）
 * + 跳登录并带 redirect，登录后可回原页
 */
function handleUnauthorized() {
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
  window.dispatchEvent(new Event('auth:expired'))
  const cur = router.currentRoute.value
  if (cur.path !== '/login') router.push({ path: '/login', query: { redirect: cur.fullPath } })
}

// 响应拦截：统一拆包 + 统一错误
request.interceptors.response.use(
  (res) => {
    const { code, message, data } = res.data
    if (code === 0) return data                       // 成功：剥壳，直接给业务数据
    if (code === 40101) {                             // 业务层判定未登录
      handleUnauthorized()
      return Promise.reject(new Error('请先登录'))
    }
    if (code >= 40300 && code < 40400) {              // 业务层权限错误（40301 封禁等）
      return Promise.reject(new Error(message || '没有权限执行该操作'))
    }
    return Promise.reject(new Error(message || '操作失败'))
  },
  async (err) => {
    // HTTP 层错误：401 未登录 / 403 无权限 / 超时 / 断网 / 5xx
    const status = err.response?.status

    // 5xx 自动重试：只对幂等 GET 生效（POST 重试可能重复下单/重复注册）
    const cfg = err.config || {}
    const retried = cfg.__retryCount || 0
    const isRetriable = cfg.method === 'get' && (status >= 500 || !err.response)
    if (isRetriable && retried < 2) {
      cfg.__retryCount = retried + 1
      await new Promise((r) => setTimeout(r, 300 * (retried + 1)))
      return request(cfg)
    }

    if (status === 401) {
      handleUnauthorized()
      return Promise.reject(new Error('请先登录'))
    }
    if (status === 403) {
      return Promise.reject(new Error('没有权限执行该操作（需要管理员账号）'))
    }
    if (err.code === 'ECONNABORTED' || String(err.message).includes('timeout')) {
      return Promise.reject(new Error('请求超时，请检查网络后重试'))
    }
    if (!err.response) {
      return Promise.reject(new Error('无法连接服务器，请确认后端已启动'))
    }
    return Promise.reject(new Error(err.response?.data?.message || '服务开小差了，请稍后重试'))
  },
)

export default request
