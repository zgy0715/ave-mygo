import { expect, test } from '@playwright/test'
import { apiUp } from './helpers'

/**
 * 回归：小灯面板打开时，滚轮不得滚动背后的主页面
 * 根因曾是 Lenis 平滑滚动接管 wheel 事件（给 html 加 overflow:hidden 无效），
 * 修法：面板打开时 lenis.stop() + 面板加 data-lenis-prevent。
 */
test.describe('小灯面板滚动隔离', () => {
  test('面板内滚轮不带动主页面，关闭后恢复', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动')

    await page.goto('/songs')
    await page.waitForTimeout(800)
    const tall = await page.evaluate(() => document.scrollingElement.scrollHeight > document.scrollingElement.clientHeight + 200)
    test.skip(!tall, '页面不够长，无法验证滚动')

    await page.mouse.move(640, 400)
    await page.mouse.wheel(0, 600)
    await page.waitForTimeout(600)
    const before = await page.evaluate(() => document.scrollingElement.scrollTop)
    expect(before).toBeGreaterThan(0)

    // 打开小灯
    await page.locator('button[aria-label*="AI 助手"]').click()
    const panel = page.locator('[role="dialog"]')
    await expect(panel).toBeVisible()
    const box = await panel.boundingBox()

    // 面板内连续滚轮：主页面纹丝不动
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    for (let i = 0; i < 4; i++) {
      await page.mouse.wheel(0, 600)
      await page.waitForTimeout(200)
    }
    const during = await page.evaluate(() => document.scrollingElement.scrollTop)
    // Lenis 停止瞬间可能残留 1~5px 的缓动尾巴（不可感知），允许 10px 内误差
    expect(Math.abs(during - before)).toBeLessThan(10)

    // 关闭后恢复可滚
    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
    await page.mouse.move(640, 400)
    await page.mouse.wheel(0, 600)
    await page.waitForTimeout(700)
    const after = await page.evaluate(() => document.scrollingElement.scrollTop)
    expect(after).toBeGreaterThan(during)
  })
})
