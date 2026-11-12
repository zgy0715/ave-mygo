import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  listAdminSongs: vi.fn(), createAdminSong: vi.fn(), updateAdminSong: vi.fn(),
  deleteAdminSong: vi.fn(), uploadHeroVideo: vi.fn(),
}))
vi.mock('@/api/admin', () => api)
vi.mock('@/api/song', () => ({ listBands: vi.fn(async () => []), listAlbums: vi.fn(async () => []) }))
const { default: AdminView } = await import('@/views/AdminView.vue')

let wrapper
const probe = vi.fn()

async function showAdmin() {
  wrapper = mount(AdminView, {
    global: {
      stubs: { CoverArt: true, AdminEventsPanel: true, AdminOrdersPanel: true, AdminUsersPanel: true, AdminStatsPanel: true },
    },
  })
  await flushPromises()
  return wrapper
}

describe('管理页 Hero 视频', () => {
  beforeEach(() => {
    api.listAdminSongs.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1 })
    api.uploadHeroVideo.mockReset().mockResolvedValue(null)
    probe.mockReset().mockResolvedValue({ ok: true, headers: new Headers({ 'content-type': 'video/mp4' }) })
    vi.stubGlobal('fetch', probe)
  })
  afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

  it('仅请求首页实际视频的头部，并绕过缓存检查更新', async () => {
    await showAdmin()
    expect(probe).toHaveBeenCalledWith('/assets/band-mygo-720p.mp4', { method: 'HEAD', cache: 'no-store' })
    expect(probe).toHaveBeenCalledWith('/assets/band-mujica-720p.mp4', { method: 'HEAD', cache: 'no-store' })
    expect(wrapper.text()).toContain('✓ 已就位')
  })

  it('失败状态即使声明视频类型也不能显示已就位', async () => {
    probe.mockResolvedValue({ ok: false, headers: new Headers({ 'content-type': 'video/mp4' }) })
    await showAdmin()
    expect(wrapper.text()).not.toContain('✓ 已就位')
    expect(wrapper.text()).toContain('✗ 缺失')
  })

  it('浏览器未识别 MIME 的 MP4 仍可上传，并重新检查首页资源', async () => {
    await showAdmin()
    probe.mockClear()
    const file = new File(['test'], 'clip.MP4', { type: '' })
    const input = wrapper.findAll('input[type="file"]')[0]
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(api.uploadHeroVideo).toHaveBeenCalledWith('mygo', file)
    expect(probe).toHaveBeenCalledWith('/assets/band-mygo-720p.mp4', { method: 'HEAD', cache: 'no-store' })
  })

  it('拒绝不支持的格式，且不发送上传请求', async () => {
    await showAdmin()
    const input = wrapper.findAll('input[type="file"]')[0]
    Object.defineProperty(input.element, 'files', { value: [new File(['test'], 'clip.webm', { type: 'video/webm' })] })
    await input.trigger('change')
    expect(wrapper.text()).toContain('只支持 MP4 格式')
    expect(api.uploadHeroVideo).not.toHaveBeenCalled()
  })
})
