import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

// 用真实 router（内存 history），避免 mock 全局 vue-router 影响 '@/router' 的导入链
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/songs/:id', name: 'song', component: { template: '<div />' } },
  ],
})
router.push('/')
await router.isReady()

const { default: SongCard } = await import('@/components/SongCard.vue')

const SONG = {
  id: 42,
  title: '迷星叫',
  bandId: 1,
  bandName: 'MyGO!!!!',
  albumName: '迷星叫',
  duration: 246,
  audioUrl: null,
  coverUrl: '',
  tags: '燃',
}

const mountCard = (over = {}) =>
  mount(SongCard, {
    props: { song: { ...SONG, ...over } },
    global: { plugins: [createPinia(), router] },
  })

describe('SongCard', () => {
  it('时长为空时渲染 --:--', () => {
    const w = mountCard({ duration: null })
    expect(w.text()).toContain('--:--')
  })

  it('有时长时渲染 m:ss', () => {
    const w = mountCard()
    expect(w.text()).toContain('4:06')
  })

  it('无音源时点击播放 → 跳详情页', async () => {
    await router.push('/')
    const w = mountCard({ audioUrl: null })
    await w.find('button[aria-label="播放"]').trigger('click')
    await flushPromises()          // 导航是异步 Promise，等它落地
    expect(router.currentRoute.value.fullPath).toBe('/songs/42')
  })

  it('有音源时不跳详情（走播放器）', async () => {
    await router.push('/')
    const w = mountCard({ audioUrl: 'https://example.com/a.mp3' })
    await w.find('button[aria-label="播放"]').trigger('click')
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('渲染歌曲标题与乐队名', () => {
    const w = mountCard()
    expect(w.text()).toContain('迷星叫')
    expect(w.text()).toContain('MyGO!!!!')
  })
})
