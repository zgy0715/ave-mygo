import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Howler 打桩：只关心「是否被创建」和最小接口，不真的加载音频
const howlCtor = vi.fn()
vi.mock('howler', () => ({
  Howl: class {
    constructor(opts) {
      howlCtor(opts)
      this._opts = opts
    }
    play() { this.playing = true }
    pause() { this.playing = false }
    seek(t) { if (t === undefined) return 12; return t }
    volume() {}
    on() {}
    unload() {}
  },
}))

const { usePlayerStore } = await import('@/stores/player')

const song = (over = {}) => ({
  id: 1, title: '迷星叫', duration: 246, audioUrl: null, bandId: 1, ...over,
})

describe('player store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    howlCtor.mockClear()
    vi.useFakeTimers()
  })

  it('play() 单曲入队并开始播放', () => {
    const p = usePlayerStore()
    p.play(song())
    expect(p.queue).toHaveLength(1)
    expect(p.current.id).toBe(1)
    expect(p.isPlaying).toBe(true)
    p.stop()
  })

  it('toggle() 暂停后恢复（_paused 同步）', () => {
    const p = usePlayerStore()
    p.play(song())
    p.toggle()
    expect(p.isPlaying).toBe(false)
    expect(p._paused).toBe(true)
    p.toggle()
    expect(p.isPlaying).toBe(true)
    expect(p._paused).toBe(false)
    p.stop()
  })

  it('无 audioUrl 不创建 Howl（演示模式）', () => {
    const p = usePlayerStore()
    p.play(song({ audioUrl: null }))
    expect(howlCtor).not.toHaveBeenCalled()
    p.stop()
  })

  it('有 audioUrl 时创建 Howl 且音量取自 store', () => {
    const p = usePlayerStore()
    p.setVolume(0.35)
    p.play(song({ audioUrl: 'https://example.com/a.mp3' }))
    expect(howlCtor).toHaveBeenCalledTimes(1)
    expect(howlCtor.mock.calls[0][0]).toMatchObject({ src: ['https://example.com/a.mp3'], volume: 0.35 })
    p.stop()
  })

  it('addToQueue：空队列直接播放；重复曲目不重复入队', () => {
    const p = usePlayerStore()
    expect(p.addToQueue(song())).toBe('started')
    expect(p.queue).toHaveLength(1)
    expect(p.addToQueue(song({ id: 2, title: '歌2' }))).toBe('added')
    expect(p.queue).toHaveLength(2)
    expect(p.addToQueue(song({ id: 2, title: '歌2' }))).toBe('exists')
    expect(p.queue).toHaveLength(2)
    p.stop()
  })

  it('next/prev 在队列内循环', () => {
    const p = usePlayerStore()
    p.playQueue([song({ id: 1 }), song({ id: 2 }), song({ id: 3 })], 0)
    p.next(); expect(p.current.id).toBe(2)
    p.next(); p.next(); expect(p.current.id).toBe(1)
    p.prev(); expect(p.current.id).toBe(3)
    p.stop()
  })

  it('无时长歌曲不会把进度算成 NaN', () => {
    const p = usePlayerStore()
    p.play(song({ duration: null }))
    vi.advanceTimersByTime(1000)
    expect(Number.isNaN(p.position)).toBe(false)
    p.stop()
  })

  it('stop() 清空队列并归零', () => {
    const p = usePlayerStore()
    p.play(song())
    p.stop()
    expect(p.queue).toHaveLength(0)
    expect(p.current).toBeNull()
    expect(p.isPlaying).toBe(false)
    expect(p.position).toBe(0)
  })
})
