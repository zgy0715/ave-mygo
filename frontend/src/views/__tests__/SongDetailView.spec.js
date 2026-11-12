import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import SongDetailView from '@/views/SongDetailView.vue'

const { getSong } = vi.hoisted(() => ({ getSong: vi.fn() }))
vi.mock('@/api/song', () => ({ getSong, listSongs: vi.fn() }))

let wrapper
afterEach(() => wrapper?.unmount())

async function showSong(mvUrl, mvPlatform = 'BILIBILI') {
  getSong.mockResolvedValue({
    id: 42, title: '测试曲目', bandName: 'MyGO!!!!!', albumId: null, mvUrl, mvPlatform,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/songs/:id', component: SongDetailView }],
  })
  await router.push('/songs/42')
  await router.isReady()
  wrapper = mount(SongDetailView, {
    global: {
      plugins: [createPinia(), router],
      stubs: { CoverArt: true, FavoriteButton: true, AddToPlaylistMenu: true, PageBack: true },
    },
  })
  await flushPromises()
  return wrapper
}

describe('歌曲详情 MV 来源边界', () => {
  it.each([
    'javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'https://player.bilibili.com.evil.example/player.html?bvid=BV1test',
    'https://evil.example/player.bilibili.com?bvid=BV1test',
    'https://user@player.bilibili.com/player.html?bvid=BV1test',
    'https://player.bilibili.com:8443/player.html?bvid=BV1test',
  ])('不载入不可信历史地址：%s', async (url) => {
    const view = await showSong(url)
    expect(view.get('h1').text()).toBe('测试曲目')
    expect(view.text()).toContain('该曲暂无官方 MV 源')
    expect(view.text()).not.toContain('载入 MV 播放器')
    expect(view.find('iframe').exists()).toBe(false)
    expect(view.findAll('a').some((link) => link.attributes('href') === url)).toBe(false)
  })

  it('正确构造 B 站 iframe，并保留分 P、画质及关闭自动播放设置', async () => {
    const view = await showSong('http://player.bilibili.com/player.html?bvid=BV1test&page=2&autoplay=1&unknown=bad')
    const button = view.findAll('button').find((button) => button.text().includes('载入 MV 播放器'))
    await button.trigger('click')
    const iframe = new URL(view.get('iframe').attributes('src'))
    expect(iframe.origin).toBe('https://player.bilibili.com')
    expect(iframe.pathname).toBe('/player.html')
    expect(Object.fromEntries(iframe.searchParams)).toEqual({
      bvid: 'BV1test', page: '2', high_quality: '1', danmaku: '0', as_wide: '1', autoplay: '0',
    })
    expect(view.findAll('a').some((link) => link.attributes('href') === 'https://www.bilibili.com/video/BV1test')).toBe(true)
  })

  it.each([
    ['https://player.bilibili.com/player.html?ep_id=123', 'BILIBILI', 'https://www.bilibili.com/bangumi/play/ep123'],
    ['https://player.bilibili.com/player.html?bvid=BV1test', 'BILIBILI_PAGE', 'https://www.bilibili.com/video/BV1test'],
    ['https://www.youtube.com/watch?v=test', 'YOUTUBE', 'https://www.youtube.com/watch?v=test'],
    ['https://youtu.be/test', 'YOUTUBE', 'https://youtu.be/test'],
  ])('番剧页或平台外链安全跳转：%s', async (url, platform, expected) => {
    const view = await showSong(url, platform)
    expect(view.find('iframe').exists()).toBe(false)
    expect(view.text()).not.toContain('载入 MV 播放器')
    expect(view.findAll('a').some((link) => link.attributes('href') === expected)).toBe(true)
  })

  it.each(['not a url', '/player.bilibili.com?bvid=BV1test', 'https://[invalid'])('畸形地址不影响歌曲详情：%s', async (url) => {
    const view = await showSong(url)
    expect(view.get('h1').text()).toBe('测试曲目')
    expect(view.text()).toContain('该曲暂无官方 MV 源')
  })

  it('历史 MV 超过存储长度时隐藏外链', async () => {
    const prefix = 'https://www.youtube.com/watch?v='
    const url = prefix + 'a'.repeat(256 - prefix.length)
    const view = await showSong(url, 'YOUTUBE')
    expect(view.text()).toContain('该曲暂无官方 MV 源')
  })
})
