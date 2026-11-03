import { expect, test } from '@playwright/test'
import { API, apiUp, loginAs } from './helpers'

/**
 * 回归：列表行的「加入歌单」下拉不能被列表容器裁切
 * 之前的 bug：面板 absolute 定位在 overflow-hidden 的卡片里，只露出一条白边
 */
test.describe('加入歌单下拉', () => {
  test('面板完整可见（不被父级 overflow 裁切）', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动')
    await loginAs(page, request)
    await page.goto('/songs')
    const row = page.locator('main .group').first()
    await row.hover()

    const btn = row.getByRole('button', { name: '加入歌单' })
    await expect(btn).toBeVisible()
    await btn.click()

    const menu = page.getByRole('menu')
    await expect(menu).toBeVisible({ timeout: 5000 })

    // 关键断言：面板高度足够（不是被裁成一条），且完整落在视口内
    const box = await menu.boundingBox()
    expect(box).not.toBeNull()
    expect(box.height).toBeGreaterThan(80)
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize().height)

    // 依次关闭
    await page.keyboard.press('Escape')
    await page.mouse.click(5, 5)
    await expect(menu).toBeHidden()
  })

  test('可就地新建歌单并加入当前歌曲', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动')
    const { token } = await loginAs(page, request)
    await page.goto('/songs')

    const row = page.locator('main .group').first()
    await row.hover()
    await row.getByRole('button', { name: '加入歌单' }).click()

    // 下拉里的歌单会随每次 E2E 累积，新建入口可能滚出可视区 → 先滚动到可见再点
    const newBtn = page.getByRole('button', { name: '＋ 新建歌单' })
    await newBtn.scrollIntoViewIfNeeded()
    await newBtn.click()
    const name = `E2E歌单${Date.now().toString(36).slice(-4)}`
    await page.getByPlaceholder('歌单名').fill(name)
    await page.getByRole('button', { name: '新建并加入' }).click()

    await expect(page.getByText(/已新建/)).toBeVisible({ timeout: 8000 })

    // 歌单页能看到刚建的歌单，且曲目数 >= 1
    await page.goto('/playlists')
    const card = page.getByText(name).first()
    await expect(card).toBeVisible({ timeout: 8000 })

    // 自清理（docs/19 P1-6）：断言过后删掉自己建的歌单，防止数据逐次累积
    // 放 try/finally：上面断言失败也不能留下脏数据
    try {
      const list = await request.get(`${API}/playlists`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      const items = (await list.json()).data ?? []
      const mine = items.find((p) => p.name === name)
      if (mine) {
        const del = await request.delete(`${API}/playlists/${mine.id}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        expect(del.ok()).toBeTruthy()
      }
    } finally {
      // 即使清理请求本身出错，也不掩盖主断言结果
    }
  })
})
