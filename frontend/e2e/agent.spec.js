import { expect, test } from '@playwright/test'
import { apiUp, loginAs, API } from './helpers'

/**
 * 旅程 5｜小灯 Agent 运营台（ADMIN → 自然语言指令 → 工具调用 / 确认门控）
 *
 * 依赖：后端 + 本地 LLM（Ollama qwen3:8b）。
 * 没有 LLM 时后端返回 50301，本用例自动 skip——Agent 不做规则兜底，这是设计行为。
 * 只走「只读指令」和「写操作→取消」两条安全路径，不修改任何业务数据。
 */
test.setTimeout(300_000)   // 真 LLM：单步 20~60s

test.describe('运营台旅程', () => {
  test('只读指令可用 + 写操作需确认且可取消', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过运营台用例')

    const auth = await loginAs(page, request, 'e2e_admin', 'e2e_admin_pw')

    // LLM 探活：无 LLM 时后端返回 50301，直接跳过（不是失败）
    const probe = await request.post(`${API}/admin/agent/chat`, {
      headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' },
      data: { message: '你好', conversationId: 'e2e-probe' },
    })
    const probeBody = await probe.json()
    test.skip(probeBody.code === 50301, 'LLM 未就绪（50301），跳过运营台用例')

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/admin')
    await page.getByRole('button', { name: '运营台' }).click()
    await expect(page.getByRole('heading', { name: '小灯 · 运营台' })).toBeVisible()

    // 1) 只读指令：出现工具条目 + EXECUTED
    await page.getByPlaceholder(/输入指令/).fill('列出所有演出')
    await page.keyboard.press('Enter')
    await expect(page.getByText('🔧').first()).toBeVisible({ timeout: 180_000 })
    await expect(page.getByText('EXECUTED').first()).toBeVisible({ timeout: 180_000 })

    // 2) 写指令：出现确认卡片，此时数据未动
    await page.getByPlaceholder(/输入指令/).fill('把歌曲 id=1 的 MV 换成 https://example.com/e2e-check')
    await page.keyboard.press('Enter')
    await expect(page.getByText(/待确认：修改歌曲 MV/).first()).toBeVisible({ timeout: 180_000 })

    // 3) 点取消：卡片变「已取消」
    await page.getByRole('button', { name: '取消' }).first().click()
    await expect(page.getByText('🚫 已取消')).toBeVisible({ timeout: 30_000 })
  })
})
