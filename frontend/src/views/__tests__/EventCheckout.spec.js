import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({ createOrder: vi.fn(), payOrder: vi.fn(), cancelOrder: vi.fn(), myOrders: vi.fn(async () => []) }))
vi.mock('@/api/order', () => api)
vi.mock('@/router', () => ({ default: { currentRoute: { value: { path: '/events/1', fullPath: '/events/1' } }, push: vi.fn() } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isLogin: true }) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '1' }, fullPath: '/events/1' }), useRouter: () => ({ push: vi.fn() }), RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('@/api/event', () => ({
  getEvent: vi.fn(async () => ({ id: 1, title: '测试演出', status: 'PUBLISHED', sessions: [{ id: 11, name: '测试场次', status: 'ONSALE', ticketTypes: [{ id: 21, name: '普通票', price: 100, availableStock: 20, maxPerUser: 4 }] }] })),
  getSetlist: vi.fn(async () => []),
}))
vi.mock('@/api/song', () => ({ listBands: vi.fn(async () => []) }))
vi.mock('@/api/reminder', () => ({ myReminders: vi.fn(async () => []), subscribeReminder: vi.fn(), cancelReminder: vi.fn() }))
const { default: EventDetail } = await import('@/views/EventDetailView.vue')
let wrapper
afterEach(() => wrapper?.unmount())

describe('演出购票', () => {
  it('请求未完成时连续点击只下单一次，失败后可重试', async () => {
    let fail
    api.createOrder.mockReset().mockImplementationOnce(() => new Promise((resolve, reject) => { fail = reject }))
      .mockResolvedValueOnce({ orderNo: 'TEST-2', status: 'PENDING_PAYMENT', payDeadline: '2030-01-01T12:00:00', totalAmount: 100, quantity: 1 })
    wrapper = mount(EventDetail, { global: { stubs: { FavoriteButton: true, PageBack: true, LoginGate: true, StatusBadge: true } } })
    await flushPromises()
    const button = wrapper.findAll('button').find((b) => b.text() === '立即购买')
    button.element.click()
    button.element.click()
    await flushPromises()
    expect(api.createOrder).toHaveBeenCalledTimes(1)
    expect(button.element.disabled).toBe(true)
    fail(new Error('测试断网'))
    await flushPromises()
    expect(button.element.disabled).toBe(false)
    await button.trigger('click')
    await flushPromises()
    expect(api.createOrder).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('TEST-2')
  })
})
