import { expect, test } from '@playwright/test'
import { apiUp, loginAs, API } from './helpers'

/**
 * 旅程 3｜购票主流程（登录 → 选票档 → 下单 → 模拟支付 → 订单页看到已支付）
 * 需要后端 + 测试账号；跑一次会产生 1 条真实订单（dev 库）
 *
 * 选场策略：从演出列表接口里挑「第一个真正有票可买」的演出（ONSALE 场次 + 上架票档 + 有余票），
 * 而不是盲点列表第一个——列表首项可能是已结束演出（2026-10-31 踩过：点进已结束演出没有购买按钮）。
 */
async function findBuyableEvent(request) {
  const listRes = await request.get(`${API}/events?pageSize=20`)
  if (!listRes.ok()) return null
  for (const ev of (await listRes.json()).data.content || []) {
    const d = await request.get(`${API}/events/${ev.id}`)
    if (!d.ok()) continue
    const detail = (await d.json()).data
    const buyable = (detail.sessions || []).some(
      (s) => s.status === 'ONSALE' && (s.ticketTypes || []).some((t) => t.status === 1 && t.availableStock > 0),
    )
    if (buyable) return ev.id
  }
  return null
}

test.describe('购票旅程', () => {
  test('登录用户可完成下单与支付', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过购票用例')
    await loginAs(page, request)

    // 挑一个有票的演出（避免点进已结束/售罄的场次）
    const eventId = await findBuyableEvent(request)
    test.skip(!eventId, '当前没有可购票的演出（场次已结束或售罄），跳过')
    await page.goto(`/events/${eventId}`)
    await expect(page).toHaveURL(new RegExp(`/events/${eventId}`))

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
