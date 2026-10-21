import { expect, test } from '@playwright/test'
import { apiUp, collectConsoleErrors } from './helpers'

/**
 * 旅程 1｜游客浏览
 * 首页 → 歌曲库（筛选/搜索）→ 歌曲详情 → 播放；全程不登录
 */
test.describe('游客旅程', () => {
  test('首页可打开且无控制台报错', async ({ page }) => {
    const errors = collectConsoleErrors(page)
    await page.goto('/')
    await expect(page).toHaveTitle(/Bandori/)
    await expect(page.getByRole('link', { name: 'Bandori' })).toBeVisible()
    // 过滤掉图片 404 之类的资源噪声，只关心 JS 异常
    const jsErrors = errors.filter((e) => !/Failed to load resource|net::ERR/.test(e))
    expect(jsErrors).toEqual([])
  })

  test('歌曲库筛选与搜索可用', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过数据相关用例')
    await page.goto('/songs')
    await expect(page.getByRole('heading', { name: '歌曲库' })).toBeVisible()

    // 歌曲库内的「搜索歌名…」输入框（注意与顶栏全局搜索区分，后者需点图标才展开）
    const search = page.getByPlaceholder('搜索歌名…')
    await search.fill('迷星')
    await expect(page.locator('main a[href^="/songs/"]').first()).toBeVisible({ timeout: 10_000 })
  })

  test('点歌曲进入详情页并显示播放按钮', async ({ page, request }) => {
    test.skip(!(await apiUp(request)), '后端未启动，跳过数据相关用例')
    await page.goto('/songs')
    const firstSong = page.locator('main a[href^="/songs/"]').first()
    await expect(firstSong).toBeVisible({ timeout: 10_000 })
    // 直接取 href 跳转：行内锚点被封面上的播放按钮覆盖，点击会命中按钮而非链接
    const href = await firstSong.getAttribute('href')
    await page.goto(href)
    await expect(page).toHaveURL(/\/songs\/\d+/)
    await expect(page.locator('main').getByRole('button', { name: /播放/ }).first()).toBeVisible({ timeout: 10_000 })
  })
})

/**
 * 旅程 2｜登录守卫（纯前端，不依赖后端数据）
 */
test.describe('登录守卫', () => {
  test('游客访问 /favorites 被重定向到登录页并带 redirect', async ({ page }) => {
    await page.goto('/favorites')
    await expect(page).toHaveURL(/\/login/)
    expect(page.url()).toContain('redirect')
  })

  test('游客访问 /admin 被重定向到登录页', async ({ page }) => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/login/)
  })
})
