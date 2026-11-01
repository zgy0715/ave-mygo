import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * HomeView 回归用例（锁定一次真实事故）：
 * 往期回顾的 loadPast() 曾被误插两次并塞进 Promise.all，
 * 导致解构 [ev, so, bd] 的第二项变成 undefined → so.content 抛错
 * → 整页数据区全空（显示「曲库整理中。」）。
 */
const listEvents = vi.fn()
const getEvent = vi.fn()
const listSongs = vi.fn()
const listBands = vi.fn()
const getBand = vi.fn()

vi.mock('@/api/event', () => ({
  listEvents: (...a) => listEvents(...a),
  getEvent: (...a) => getEvent(...a),
}))
vi.mock('@/api/song', () => ({
  listSongs: (...a) => listSongs(...a),
  listBands: (...a) => listBands(...a),
  getBand: (...a) => getBand(...a),
}))

const { default: HomeView } = await import('@/views/HomeView.vue')

const SongStub = { props: ['song'], template: '<a class="song-stub" :href="`/songs/${song.id}`">{{ song.title }}</a>' }
const EventStub = { props: ['event'], template: '<a class="event-stub" :href="`/events/${event.id}`">{{ event.title }}</a>' }
const PastStub = { props: ['event'], template: '<a class="past-stub" :href="`/events/${event.id}`">{{ event.title }}</a>' }
const RouterLinkStub = { props: ['to'], template: '<a :href="to"><slot /></a>' }

const mountHome = () =>
  mount(HomeView, {
    global: {
      stubs: {
        SongCard: SongStub,
        EventCard: EventStub,
        PastEventCard: PastStub,
        RouterLink: RouterLinkStub,
      },
      directives: { reveal: {} },
    },
  })

describe('HomeView 数据加载', () => {
  beforeEach(() => {
    listEvents.mockReset()
    getEvent.mockReset()
    listSongs.mockReset()
    listBands.mockReset()
    getBand.mockReset()

    listEvents.mockImplementation((params = {}) => {
      if (params.status === 'ENDED') return Promise.resolve({ content: [{ id: 21, title: '往期甲', status: 'ENDED' }] })
      return Promise.resolve({ content: [{ id: 11, title: '在售演出', status: 'PUBLISHED' }] })
    })
    getEvent.mockResolvedValue({ sessions: [{ startTime: '2026-08-09 18:00:00' }] })
    listSongs.mockResolvedValue({ content: [{ id: 1, title: '迷星叫' }, { id: 2, title: '壱雫空' }] })
    listBands.mockResolvedValue([{ id: 1, name: 'MyGO!!!!!' }])
    getBand.mockResolvedValue({ members: [{ id: 1 }, { id: 2 }] })
  })

  it('新曲/演出/往期三个区块都有数据（防 Promise.all 解构错位再次清空首页）', async () => {
    const w = mountHome()
    await flushPromises()
    await flushPromises()

    expect(w.text()).not.toContain('曲库整理中')        // 事故现场文案
    expect(w.findAll('.song-stub').map((n) => n.text())).toEqual(['迷星叫', '壱雫空'])
    expect(w.findAll('.event-stub').map((n) => n.text())).toContain('在售演出')
    expect(w.findAll('.past-stub').map((n) => n.text())).toContain('往期甲')
  })

  it('往期回顾走独立请求（status=ENDED），且不参与主查询解构', async () => {
    mountHome()
    await flushPromises()

    const ended = listEvents.mock.calls.filter(([p]) => p?.status === 'ENDED')
    expect(ended).toHaveLength(1)                       // 只调用一次（曾重复插入两次）
    expect(listSongs).toHaveBeenCalledTimes(1)
  })

  it('歌曲接口失败时只影响本区块，往期仍渲染', async () => {
    listSongs.mockRejectedValue(new Error('boom'))
    const w = mountHome()
    await flushPromises()
    await flushPromises()
    expect(w.findAll('.past-stub').length).toBe(1)
  })
})
