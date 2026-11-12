import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const api = vi.hoisted(() => ({ listComments: vi.fn(), createComment: vi.fn(), deleteComment: vi.fn() }))
vi.mock('@/api/comment', () => api)
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isLogin: true, token: 'test-token' }) }))
const { default: Comments } = await import('@/components/SongComments.vue')
let wrapper
afterEach(() => wrapper?.unmount())
describe('歌曲评论切换', () => {
  it('切换歌曲会重载评论，旧歌曲迟到的响应不能覆盖当前歌曲', async () => {
    let oldResponse
    api.listComments.mockImplementationOnce(() => new Promise((r) => { oldResponse = r }))
      .mockResolvedValueOnce({ content: [{ id: 2, nickname: '用户', content: '第二首的评论' }], totalElements: 1, totalPages: 1 })
    wrapper = mount(Comments, { props: { songId: 1 }, global: { stubs: { LoginGate: true } } })
    await wrapper.setProps({ songId: 2 })
    await flushPromises()
    expect(api.listComments).toHaveBeenLastCalledWith(2, 1, 10)
    expect(wrapper.text()).toContain('第二首的评论')
    oldResponse({ content: [{ id: 1, content: '第一首的旧评论' }], totalElements: 1, totalPages: 1 })
    await flushPromises()
    expect(wrapper.text()).toContain('第二首的评论')
    expect(wrapper.text()).not.toContain('第一首的旧评论')
  })
})
