import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * request 封装测试：
 * - 业务码非 0 抛错（带后端 message）
 * - 403 段业务码 / HTTP 403 → 权限文案
 * - 40101 / HTTP 401 → 清 token + 广播 auth:expired + 跳登录
 * - 5xx：GET 重试 2 次，POST 不重试
 */
vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { path: '/songs', fullPath: '/songs' } },
    push: vi.fn(),
  },
}))

const { default: request } = await import('@/api/request')
const { default: router } = await import('@/router')

/** 用可控 adapter 模拟后端：按顺序返回预设响应 */
function mockAdapter(steps) {
  let i = 0
  request.defaults.adapter = async (config) => {
    const step = steps[Math.min(i++, steps.length - 1)]
    if (step.status) {
      const err = new Error(`HTTP ${step.status}`)
      err.config = config
      err.response = { status: step.status, data: step.data || {} }
      throw err
    }
    return { data: step.data, status: 200, statusText: 'OK', headers: {}, config }
  }
}

describe('request 响应拦截', () => {
  beforeEach(() => {
    sessionStorage.clear()
    router.push.mockClear()
    vi.spyOn(window, 'dispatchEvent')
  })

  it('code=0 时直接返回 data（剥壳）', async () => {
    mockAdapter([{ data: { code: 0, message: 'ok', data: { id: 1 } } }])
    await expect(request.get('/songs/1')).resolves.toEqual({ id: 1 })
  })

  it('code 非 0 时抛错并带后端 message', async () => {
    mockAdapter([{ data: { code: 40901, message: '余票不足', data: null } }])
    await expect(request.get('/orders')).rejects.toThrow('余票不足')
  })

  it('业务码 403xx → 权限文案', async () => {
    mockAdapter([{ data: { code: 40301, message: '', data: null } }])
    await expect(request.get('/admin/orders')).rejects.toThrow('没有权限执行该操作')
  })

  it('HTTP 403 → 需要管理员账号', async () => {
    mockAdapter([{ status: 403, data: { message: 'Forbidden' } }])
    await expect(request.get('/admin/users')).rejects.toThrow(/没有权限/)
  })

  it('40101 → 清 token、广播 auth:expired、跳登录', async () => {
    sessionStorage.setItem('token', 't')
    sessionStorage.setItem('user', '{}')
    mockAdapter([{ data: { code: 40101, message: '未登录', data: null } }])
    await expect(request.get('/orders/my')).rejects.toThrow('请先登录')
    expect(sessionStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('user')).toBeNull()
    expect(window.dispatchEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'auth:expired' }))
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({ path: '/login' }))
  })

  it('GET 5xx 自动重试 2 次后失败（共 3 次请求）', async () => {
    vi.useFakeTimers()
    let calls = 0
    request.defaults.adapter = async (config) => {
      calls++
      const err = new Error('HTTP 500')
      err.config = config
      err.response = { status: 500, data: {} }
      throw err
    }
    const p = request.get('/songs')
    const assertion = expect(p).rejects.toThrow('服务开小差了')
    await vi.runAllTimersAsync()
    await assertion
    expect(calls).toBe(3)
    vi.useRealTimers()
  })

  it('POST 5xx 不重试（避免重复下单）', async () => {
    vi.useFakeTimers()
    let calls = 0
    request.defaults.adapter = async (config) => {
      calls++
      const err = new Error('HTTP 500')
      err.config = config
      err.response = { status: 500, data: {} }
      throw err
    }
    const p = request.post('/orders', {})
    const assertion = expect(p).rejects.toThrow()
    await vi.runAllTimersAsync()
    await assertion
    expect(calls).toBe(1)
    vi.useRealTimers()
  })
})
