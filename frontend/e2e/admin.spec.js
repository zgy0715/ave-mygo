import { expect, test } from '@playwright/test'
import { apiUp, loginAs, API } from './helpers'

/**
 * 旅程 4｜管理端（ADMIN 登录 → 演出管理 → 场次删除 → 演出删除）
 * 使用专用测试管理员 e2e_admin（dev 库已提权，勿动站长账号 tomori_fan）。
 * 会真实写 dev 库：每轮自建自删，历史残留也会在开头清掉。
 */
const ADMIN = { username: 'e2e_admin', password: 'e2e_admin_pw' }
const uniq = Date.now().toString(36)
const TITLE = `[TEST] 管理端删除旅程 ${uniq}`

test.describe('管理端旅程', () => {
  test('管理员可完整走通：建演出→建场次→删场次→删演出', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过管理端用例')

    // ---- 前置：登录 + 建测试演出/场次 ----
    const data = await loginAs(page, request, ADMIN.username, ADMIN.password)
    const authed = {
      'Authorization': `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    }
    // 清理历史残留（之前失败轮次留下的 [TEST] 演出，删除接口会级联软删场次）
    const listRes = await request.get(`${API}/admin/events?pageSize=50`, { headers: authed })
    if (listRes.ok()) {
      for (const ev of (await listRes.json()).data.content || []) {
        if (ev.title?.startsWith('[TEST] 管理端删除旅程')) {
          await request.delete(`${API}/admin/events/${ev.id}`, { headers: authed })
        }
      }
    }
    const evRes = await request.post(`${API}/admin/events`, {
      headers: authed,
      data: { title: TITLE, bandId: 1, venue: 'E2E 会场', status: 'PUBLISHED' },
    })
    expect(evRes.ok()).toBeTruthy()
    const ev = (await evRes.json()).data
    const ssRes = await request.post(`${API}/admin/events/${ev.id}/sessions`, {
      headers: authed,
      data: {
        name: `E2E场次 ${uniq}`,
        startTime: '2027-09-01T14:00:00',
        endTime: '2027-09-01T17:00:00',
        status: 'ONSALE',
      },
    })
    expect(ssRes.ok()).toBeTruthy()

    // 记录 DELETE 响应，失败时能直接看到状态码
    const deleteResponses = []
    page.on('response', (r) => {
      if (r.request().method() === 'DELETE') deleteResponses.push(`${r.status()} ${r.url()}`)
    })

    // ---- 页面操作 ----
    await page.goto('/admin')
    await expect(page.getByRole('button', { name: '演出管理' })).toBeVisible()
    await page.getByRole('button', { name: '演出管理' }).click()

    const row = page.locator('tr', { hasText: TITLE }).first()
    await expect(row).toBeVisible({ timeout: 10_000 })

    // 展开场次明细 → 删除场次（按钮 title=删除场次，可见文本=删除）
    await row.getByRole('button', { name: '场次明细' }).click()
    const sessionRow = page.locator('tr', { hasText: `E2E场次 ${uniq}` }).first()
    await expect(sessionRow).toBeVisible()
    await sessionRow.getByTitle('删除场次').click()
    await expect(page.getByText('确定删除场次')).toBeVisible()
    await expect(page.getByText(`「E2E场次 ${uniq}」`)).toBeVisible()

    // 探针：等待 DELETE 响应并打印，定位"删不掉"假象
    const delResp = page.waitForResponse(
      (r) => r.request().method() === 'DELETE' && r.url().includes('/admin/sessions/'),
      { timeout: 15_000 },
    ).catch(() => null)
    await page.getByRole('button', { name: '确认删除' }).click()
    const resp = await delResp
    if (resp) {
      console.log(`[probe] DELETE ${resp.url()} -> HTTP ${resp.status()} body=${(await resp.text()).slice(0, 160)}`)
    } else {
      console.log('[probe] 确认删除点击后 15s 内没有发出任何 DELETE 请求！')
    }

    // 场次行消失 + 弹窗关闭
    await expect(sessionRow).toHaveCount(0, { timeout: 10_000 })
    await expect(page.getByText('确定删除场次')).toHaveCount(0)

    // 删除演出
    await row.getByTitle('删除演出').click()
    await expect(page.getByText('确定删除演出')).toBeVisible()
    await expect(page.getByText(`「${TITLE}」`)).toBeVisible()
    await page.getByRole('button', { name: '确认删除' }).click()
    await expect(row).toHaveCount(0, { timeout: 10_000 })

    // 兜底输出：万一以后再出"删不掉"假象，这里能直接看到 DELETE 的真实响应
    expect(deleteResponses.join('\n') || '（无 DELETE 响应）').toContain('200')
  })

  test('游客访问 /admin 被重定向登录页', async ({ page }) => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/login/)
  })
})
