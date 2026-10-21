import { expect, test } from '@playwright/test'

/**
 * E2E 公共辅助
 * - apiUp(): 后端是否可用（不可用则用例 skip，避免把「服务没起」误报成前端 bug）
 * - loginAs(): 用测试账号登录并注入 token（跳过 UI 登录，聚焦被测流程）
 */
export const API = process.env.E2E_API_URL || 'http://localhost:8080/api/v1'

export async function apiUp(request) {
  try {
    const res = await request.get(`${API}/events?pageSize=1`, { timeout: 4000 })
    return res.ok()
  } catch {
    return false
  }
}

/**
 * 登录并把 token 注入 sessionStorage（前端 token 存 sessionStorage）。
 * 传 null 用户名时自动注册一个全新账号：购票有「每场次限购」，固定账号重复跑会被限购挡住。
 */
export async function loginAs(page, request, username = null, password = 'e2e12345') {
  if (!username) {
    username = `e2e_${Date.now().toString(36)}`
    const reg = await request.post(`${API}/auth/register`, { data: { username, password, nickname: 'E2E' } })
    if (!reg.ok()) throw new Error(`注册失败: ${reg.status()}`)
  }
  const res = await request.post(`${API}/auth/login`, { data: { username, password } })
  if (!res.ok()) throw new Error(`登录失败: ${res.status()}`)
  const body = await res.json()
  const data = body.data
  await page.addInitScript(
    ([t, u]) => {
      sessionStorage.setItem('token', t)
      sessionStorage.setItem('user', JSON.stringify(u))
    },
    [data.token, data.user],
  )
  return data
}

/** 断言页面没有 Vue 运行时错误（控制台 error 抓一遍） */
export function collectConsoleErrors(page) {
  const errors = []
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text())
  })
  return errors
}
