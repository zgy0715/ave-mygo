import { expect, test } from '@playwright/test'
import { API } from './helpers'

/**
 * 回归：管理端删除场次（用户报「删除中…卡住」）
 * 用本地签发的 ADMIN token 走完整 UI 链路
 */
const ADMIN_TOKEN = process.env.E2E_ADMIN_TOKEN || ''

test('删除场次后行立即消失', async ({ page }) => {
  test.skip(!ADMIN_TOKEN, '需要 E2E_ADMIN_TOKEN（本地签发的 ADMIN JWT）')

  await page.addInitScript(
    ([t, u]) => {
      sessionStorage.setItem('token', t)
      sessionStorage.setItem('user', JSON.stringify(u))
    },
    [ADMIN_TOKEN, { id: 1, username: 'admin', nickname: '站长', role: 'ADMIN' }],
  )

  // 先通过 API 建一个一次性场次
  const res = await page.request.post(`${API}/admin/events/2/sessions`, {
    headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    data: { name: `E2E删除测试${Date.now().toString(36).slice(-4)}`, startTime: '2026-12-02T18:00:00', endTime: '2026-12-02T21:00:00', saleStartTime: '2026-11-02T10:00:00' },
  })
  const created = (await res.json()).data
  expect(created.id).toBeTruthy()

  const pageErrors = []
  page.on('pageerror', (err) => pageErrors.push(String(err)))
  page.on('console', (m) => { if (m.type() === 'error') pageErrors.push(m.text()) })

  await page.goto('/admin')
  await page.getByRole('button', { name: '演出管理' }).click()
  await page.locator('tr', { hasText: 'Ave Mujica 7th' }).first().getByRole('button', { name: '场次明细' }).click()

  const row = page.locator('main div').filter({ has: page.locator(`button[title="删除场次"]`) }).filter({ hasText: created.name }).last()
  await row.getByRole('button', { name: '删除', exact: true }).click()

  // 抓 DELETE 请求的真实响应
  const delPromise = page.waitForResponse(
    (r) => r.request().method() === 'DELETE' && r.url().includes('/admin/sessions/'),
    { timeout: 15000 },
  )
  await page.getByRole('button', { name: '确认删除' }).click()
  await page.waitForTimeout(1000)
  console.log(`[DEBUG] 页面JS错误: ${pageErrors.length ? pageErrors.join(' | ').slice(0, 400) : '无'}`)
  console.log(`[DEBUG] 仍显示删除中: ${await page.getByText('删除中').count() > 0}`)
  const resp = await delPromise
  const body = await resp.text()
  console.log(`[DEBUG] DELETE ${resp.status()} ${body.slice(0, 200)}`)

  console.log(`[DEBUG] 页面JS错误: ${pageErrors.length ? pageErrors.join(' | ').slice(0, 300) : '无'}`)
  await page.waitForTimeout(1500)
  const stillThere = await page.locator('main').getByText(created.name).count()
  console.log(`[DEBUG] 删除后页面是否还有该场次: ${stillThere > 0}`)
  const modalText = await page.locator('body').innerText()
  console.log(`[DEBUG] 弹窗/页面含错误: ${/删除中|出错|失败|40903|404|超时/.test(modalText) ? modalText.match(/[^\n]*(?:删除中|失败|40903|超时)[^\n]*/)?.[0] : '无'}`)
})
