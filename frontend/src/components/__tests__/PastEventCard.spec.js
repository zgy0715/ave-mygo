import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * PastEventCard 用例（docs/16）：
 * - 「已结束 ENDED」徽章、标题、场馆 + 最后一场日期
 * - lastSessionAt 为空 → 只显示场馆
 * - 整卡可点 → /events/{id}
 */
const { default: PastEventCard } = await import('@/components/PastEventCard.vue')

const EV = {
  id: 21,
  title: 'Ave Mujica LIVE TOUR 2026「Exitus」',
  venue: '全国ツアー',
  bandName: 'Ave Mujica',
  status: 'ENDED',
  lastSessionAt: '2026-08-09T18:00:00',
}

const mountCard = (event = EV) =>
  mount(PastEventCard, {
    props: { event },
    global: { stubs: { RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] } } },
  })

describe('PastEventCard', () => {
  it('渲染「已结束 ENDED」徽章与标题', () => {
    const w = mountCard()
    expect(w.text()).toContain('已结束 ENDED')
    expect(w.text()).toContain('Ave Mujica LIVE TOUR 2026「Exitus」')
  })

  it('显示场馆 + 最后一场日期（取前 10 位）', () => {
    const w = mountCard()
    expect(w.text()).toContain('全国ツアー')
    expect(w.text()).toContain('最后一场 2026-08-09')
    expect(w.text()).not.toContain('18:00')
  })

  it('lastSessionAt 为空 → 只显示场馆不显示日期', () => {
    const w = mountCard({ ...EV, lastSessionAt: null })
    expect(w.text()).toContain('全国ツアー')
    expect(w.text()).not.toContain('最后一场')
  })

  it('整卡链接指向 /events/{id}', () => {
    const w = mountCard()
    expect(w.find('a').attributes('href')).toBe('/events/21')   // stub 的 href = 传入的 to
  })
})
