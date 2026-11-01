import { expect, test } from '@playwright/test'
import { API, loginAs } from './helpers'

/**
 * 小灯 v2 旅程（docs/15）：悬浮球 → 直接对话（无运营开关）
 * - 只读用例：任意登录用户（含游客）都能用
 * - 写操作用例：需要 E2E_ADMIN_TOKEN（管理员 JWT）；未提供则 skip，
 *   因为普通账号后端不会开放写工具，卡片自然不会出现（这是设计，不是 bug）
 */
test.setTimeout(300_000)

const ADMIN_TOKEN = process.env.E2E_ADMIN_TOKEN || ''

async function llmReady(request, token) {
  const res = await request.post(`${API}/assistant/chat/v2`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { message: '你好', conversationId: 'e2e-probe' },
  })
  const body = await res.text()
  return !/50301/.test(body)
}

test.describe('小灯 v2', () => {
  test('面板无运营开关；只读提问出工具条目与回答', async ({ page, request }) => {
    const auth = await loginAs(page, request, 'e2e_admin', 'e2e_admin_pw')
    test.skip(!(await llmReady(request, auth.token)), 'LLM 未就绪（50301），跳过')

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await page.locator('button[aria-label*="AI 助手"]').click()
    const panel = page.locator('[role="dialog"]')
    await expect(panel).toBeVisible()
    await expect(panel.getByText('运营')).toHaveCount(0)

    await panel.locator('textarea').fill('站里现在有几场演出')
    await panel.locator('textarea').press('Enter')
    await expect(panel.getByText('🔧').first()).toBeVisible({ timeout: 120_000 })
    await expect(panel.getByText(/场演出|共 ?\d+ ?场/).first()).toBeVisible({ timeout: 120_000 })
  })

  test('管理员写指令出确认卡片，取消后不落库', async ({ page, request }) => {
    test.skip(!ADMIN_TOKEN, '需要 E2E_ADMIN_TOKEN（管理员 JWT）')
    test.skip(!(await llmReady(request, ADMIN_TOKEN)), 'LLM 未就绪（50301），跳过')

    await page.addInitScript(([t]) => {
      sessionStorage.setItem('token', t)
      sessionStorage.setItem('user', JSON.stringify({ id: 1, username: 'admin', nickname: '站长', role: 'ADMIN' }))
    }, [ADMIN_TOKEN])

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await page.locator('button[aria-label*="AI 助手"]').click()
    const panel = page.locator('[role="dialog"]')
    await expect(panel).toBeVisible()

    await panel.locator('textarea').fill('把迷星叫的 MV 换成 https://example.com/e2e-check')
    await panel.locator('textarea').press('Enter')
    const card = panel.getByText(/待确认：/).first()
    await expect(card).toBeVisible({ timeout: 120_000 })
    // 标题必须是人类可读的对象名，而不是工具名
    await expect(panel.getByText(/待确认：.*《/).first()).toBeVisible()

    await panel.getByRole('button', { name: '取消' }).first().click()
    await expect(panel.getByText('已取消').first()).toBeVisible({ timeout: 30_000 })
  })
})
