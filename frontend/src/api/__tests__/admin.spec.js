import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/router', () => ({
  default: { currentRoute: { value: { path: '/admin', fullPath: '/admin' } }, push: vi.fn() },
}))
const { default: request } = await import('@/api/request')
const { uploadHeroVideo } = await import('@/api/admin')
const originalAdapter = request.defaults.adapter
afterEach(() => { request.defaults.adapter = originalAdapter; sessionStorage.clear() })

describe('Hero 上传 HTTP 请求', () => {
  it('携带槽位与文件，为100MB上传单独提供5分钟超时', async () => {
    const file = new File(['test'], 'clip.mp4', { type: 'video/mp4' })
    let sent
    request.defaults.adapter = async (config) => {
      sent = config
      return { data: { code: 0, message: 'ok', data: null }, status: 200, statusText: 'OK', headers: {}, config }
    }
    await expect(uploadHeroVideo('mygo', file)).resolves.toBeNull()
    expect(sent.url).toBe('/admin/hero-videos')
    expect(sent.timeout).toBe(300_000)
    expect(sent.data.get('slot')).toBe('mygo')
    expect(sent.data.get('file')).toBe(file)
    expect(request.defaults.timeout).toBe(10_000)
  })

  it('代理返回413时明确提示上传过大', async () => {
    request.defaults.adapter = async (config) => {
      const error = new Error('HTTP 413')
      error.config = config
      error.response = { status: 413, data: '<html>Request Entity Too Large</html>' }
      throw error
    }
    await expect(uploadHeroVideo('mygo', new File(['test'], 'clip.mp4')))
      .rejects.toThrow('上传请求过大，视频不能超过 100MB')
  })
})
