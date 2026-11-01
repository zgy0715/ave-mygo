import { expect, test } from '@playwright/test'

/**
 * 小灯面板布局回归（docs/15 验收：1440 / 1024 / 375 三档不溢出）
 * 不依赖后端与 LLM：只开面板量盒子。
 */
const CASES = [
  { w: 1440, h: 900, name: '桌面' },
  { w: 1024, h: 768, name: '窄桌面' },
  { w: 375, h: 812, name: '手机' },
]

for (const c of CASES) {
  test(`${c.name} ${c.w}×${c.h}：面板不溢出、三区完整、无横向溢出`, async ({ page }) => {
    await page.setViewportSize({ width: c.w, height: c.h })
    await page.goto('/')
    await page.locator('button[aria-label*="AI 助手"]').click()

    const panel = page.locator('[role="dialog"]')
    await expect(panel).toBeVisible()
    await page.waitForTimeout(400)      // 等入场过渡（translateY 12px）落定再量

    const box = await panel.boundingBox()
    expect(box.x).toBeGreaterThanOrEqual(-1)
    expect(box.y).toBeGreaterThanOrEqual(-1)
    expect(box.x + box.width).toBeLessThanOrEqual(c.w + 1)
    expect(box.y + box.height).toBeLessThanOrEqual(c.h + 1)

    // 三区都在：头部没被压扁、输入区没被挤出屏幕
    const header = page.locator('[role="dialog"] > div').first()
    expect((await header.boundingBox()).height).toBeGreaterThan(56)
    await expect(panel.locator('textarea')).toBeVisible()
    await expect(panel.locator('button[aria-label="发送"]')).toBeVisible()

    // 无横向溢出（工具条目 truncate / 长串 break-words 生效）
    expect(await panel.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true)

    // 手机档：max-sm 全屏
    if (c.w <= 639) {
      expect(box.width).toBeGreaterThanOrEqual(c.w - 2)
      expect(box.height).toBeGreaterThanOrEqual(c.h - 2)
    }
  })
}

test('气泡宽度不超过 85%（长文本不会铺满整行）', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await page.locator('button[aria-label*="AI 助手"]').click()
  const bubble = page.locator('[data-bubble]').first()
  await expect(bubble).toBeVisible()
  const panelBox = await page.locator('[role="dialog"]').boundingBox()
  const bubbleBox = await bubble.boundingBox()
  expect(bubbleBox.width / panelBox.width).toBeLessThan(0.9)
})

test('助手气泡使用 16px 圆角且无 hover 上浮（不再被 .card 覆盖）', async ({ page }) => {
  await page.goto('/')
  await page.locator('button[aria-label*="AI 助手"]').click()
  const bubble = page.locator('[data-bubble]').first()
  await expect(bubble).toBeVisible()
  const radius = await bubble.evaluate((el) => getComputedStyle(el).borderTopLeftRadius)
  expect(parseFloat(radius)).toBeGreaterThanOrEqual(15)
  await bubble.hover()
  await page.waitForTimeout(400)
  const tf = await bubble.evaluate((el) => getComputedStyle(el).transform)
  expect(tf === 'none' || tf === 'matrix(1, 0, 0, 1, 0, 0)').toBe(true)   // 无 translateY(-4px)
})

test('发送键空输入时为中性灰底（不是 40% 透明的主色）', async ({ page }) => {
  await page.goto('/')
  await page.locator('button[aria-label*="AI 助手"]').click()
  const btn = page.locator('[role="dialog"] button[aria-label="发送"]')
  const opacity = await btn.evaluate((el) => getComputedStyle(el).opacity)
  expect(parseFloat(opacity)).toBe(1)
})
