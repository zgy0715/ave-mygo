import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CoverArt from '@/components/CoverArt.vue'

describe('缺失本地素材的封面降级', () => {
  it('图片加载失败后显示文字渐变，新图片仍可重试', async () => {
    const wrapper = mount(CoverArt, { props: { title: '测试歌曲', coverUrl: '/img/covers/missing.jpg' } })
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('测试')
    await wrapper.setProps({ coverUrl: '/img/covers/replacement.jpg' })
    expect(wrapper.find('img').attributes('src')).toBe('/img/covers/replacement.jpg')
    wrapper.unmount()
  })
})
