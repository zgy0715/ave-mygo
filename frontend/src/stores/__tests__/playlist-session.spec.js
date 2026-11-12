import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({ listPlaylists: vi.fn(), createPlaylist: vi.fn(), updatePlaylist: vi.fn(), deletePlaylist: vi.fn() }))
vi.mock('@/api/playlist', () => api)
vi.mock('@/api/auth', () => ({ login: vi.fn(async () => ({ token: 'new-token', user: { id: 2 } })) }))
const { useAuthStore } = await import('@/stores/auth')
const { usePlaylistStore } = await import('@/stores/playlists')

beforeEach(() => { sessionStorage.clear(); setActivePinia(createPinia()); vi.clearAllMocks() })
describe('歌单账户隔离', () => {
  it('退出立即清空缓存，退出前的慢响应不能恢复旧账户歌单', async () => {
    const auth = useAuthStore()
    const store = usePlaylistStore()
    auth.token = 'old-token'
    store.items = [{ id: 1, name: '旧账号歌单' }]
    let resolve
    api.listPlaylists.mockImplementationOnce(() => new Promise((r) => { resolve = r }))
    const pending = store.load(true)
    auth.logout()
    expect(store.items).toEqual([])
    resolve([{ id: 2, name: '迟到的旧歌单' }])
    await pending
    expect(store.items).toEqual([])
    api.listPlaylists.mockResolvedValueOnce([{ id: 3, name: '新账号歌单' }])
    await auth.login('new-user', 'test-password')
    await store.load()
    expect(store.items.map((p) => p.name)).toEqual(['新账号歌单'])
  })
})
