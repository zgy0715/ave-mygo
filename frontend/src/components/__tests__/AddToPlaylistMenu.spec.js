import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
const api = vi.hoisted(() => ({ listPlaylists: vi.fn(), createPlaylist: vi.fn(), updatePlaylist: vi.fn(), deletePlaylist: vi.fn(), addSongToPlaylist: vi.fn() }))
vi.mock('@/api/playlist', () => api)
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isLogin: true, token: 'test-token' }) }))
const { usePlaylistStore } = await import('@/stores/playlists')
const { default: Menu } = await import('@/components/AddToPlaylistMenu.vue')
let wrapper
let pinia
beforeEach(() => { pinia = createPinia(); setActivePinia(pinia); vi.clearAllMocks() })
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks() })
describe('加入歌单菜单', () => {
  it('重新打开时刷新已缓存的列表，不展示已在其他页面删除的歌单', async () => {
    const store = usePlaylistStore()
    store.items = [{ id: 1, name: '已删除的歌单' }]; store.loaded = true
    api.listPlaylists.mockResolvedValue([{ id: 2, name: '当前歌单' }])
    wrapper = mount(Menu, { props: { songId: 1 }, global: { plugins: [pinia], stubs: { LoginGate: true } } })
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('当前歌单')
    expect(document.body.textContent).not.toContain('已删除的歌单')
  })
  it('等待歌单响应时卸载，不再注册全局滚动和窗口监听', async () => {
    let resolve
    api.listPlaylists.mockImplementationOnce(() => new Promise((r) => { resolve = r }))
    wrapper = mount(Menu, { props: { songId: 1 }, global: { plugins: [pinia], stubs: { LoginGate: true } } })
    const add = vi.spyOn(window, 'addEventListener')
    await wrapper.find('button').trigger('click')
    wrapper.unmount(); wrapper = null
    resolve([])
    await flushPromises()
    expect(add.mock.calls.filter(([name]) => name === 'scroll' || name === 'resize')).toHaveLength(0)
  })
})
