import { expect, test } from '@playwright/test'
import { apiUp, loginAs } from './helpers'

/**
 * 旅程 3｜购票主流程（登录 → 选票档 → 下单 → 模拟支付 → 订单页看到已支付）
 * 需要后端 + 测试账号；跑一次会产生 1 条真实订单（dev 库）
 */
test.describe('购票旅程', () => {
  test('登录用户可完成下单与支付', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过购票用例')
    await loginAs(page, request)

    // 演出列表 → 第一场详情
    await page.goto('/events')
    await page.locator('a[href^="/events/"]').first().click()
    await expect(page).toHaveURL(/\/events\/\d+/)

    // 选择第一个有库存的票档并购买（按钮文案：立即购买）
    const buyBtn = page.getByRole('button', { name: '立即购买' }).first()
    await expect(buyBtn).toBeVisible({ timeout: 10_000 })
    await buyBtn.click()

    // 订单弹窗：「订单已创建」+ 15 分钟倒计时 + 模拟支付
    await expect(page.getByText('订单已创建')).toBeVisible({ timeout: 10_000 })
    const payBtn = page.getByRole('button', { name: '模拟支付' })
    await expect(payBtn).toBeVisible()
    await payBtn.click()

    // 支付成功弹窗
    await expect(page.getByText('购票成功')).toBeVisible({ timeout: 10_000 })

    // 订单页能看到已支付
    await page.goto('/orders')
    await expect(page.getByText('已支付').first()).toBeVisible({ timeout: 10_000 })
  })
})
