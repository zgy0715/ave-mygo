import { defineStore } from 'pinia'
import { Howl } from 'howler'

const VOL_KEY = 'player:volume'
const MODE_KEY = 'player:mode'

/**
 * 全局播放器（Pinia）：
 * - 有 audioUrl → Howler 真实播放（官方音源外链，站内不托管）
 * - 无 audioUrl → 演示模式：进度条模拟走动（明确标注"试听待补充"）
 *
 * 关键修复：Howler 播放时用同步定时器把真实播放位置写回 store，
 * 否则 UI 进度条永远停在 0:00。
 * 播放模式：order 顺序 / repeat 单曲循环 / shuffle 随机。
 */
export const usePlayerStore = defineStore('player', {
  state: () => ({
    queue: [],
    currentIndex: -1,
    isPlaying: false,
    position: 0,                                   // 秒
    volume: Number(localStorage.getItem(VOL_KEY)) || 0.8,
    muted: false,
    mode: localStorage.getItem(MODE_KEY) || 'order',  // order | repeat | shuffle
  }),

  getters: {
    current: (s) => s.queue[s.currentIndex] || null,
    progressPct(s) {
      const d = this.current?.duration || 0
      return d ? Math.min(100, (s.position / d) * 100) : 0
    },
    /** 实际生效音量（静音时为 0） */
    effectiveVolume: (s) => (s.muted ? 0 : s.volume),
  },

  actions: {
    /** 单曲入队并播放 */
    play(song) {
      if (this.current?.id === song.id) return this.toggle()
      this.queue = [song]
      this.currentIndex = 0
      this._start()
    },

    /** 整单入队，从第 index 首开始播；点的若正是当前曲 → 暂停/恢复 */
    playQueue(list, index = 0) {
      if (this.current?.id === list[index]?.id) return this.toggle()
      this.queue = [...list]
      this.currentIndex = index
      this._start()
    },

    _start() {
      this._teardown()
      this.position = 0
      this._paused = false
      const song = this.current
      if (!song) return

      if (song.audioUrl) {
        this._howl = new Howl({
          src: [song.audioUrl],
          html5: true,
          volume: this.effectiveVolume,
        })
        this._howl.play()
        this._howl.on('end', () => this._onEnded())
        this._howl.on('playerror', () => {
          console.warn('[player] 音源失效，跳下一首')
          this.next()
        })
        // 位置同步：真实播放进度写回 store（否则进度条永远 0:00）
        this._syncTimer = setInterval(() => {
          if (!this._howl || this._paused) return
          const t = this._howl.seek()
          if (typeof t === 'number' && !Number.isNaN(t)) this.position = t
        }, 250)
      } else {
        // 演示模式：每 250ms 推进 0.25s；duration 缺失则用 0 兜底（防 NaN）
        const dur = Number(song.duration) || 0
        this._timer = setInterval(() => {
          if (this._paused || !dur) return
          this.position = Math.min(this.position + 0.25, dur)
          if (this.position >= dur) this._onEnded()
        }, 250)
      }
      this.isPlaying = true
    },

    /** 播放结束：按模式决定循环当前 / 下一首 */
    _onEnded() {
      if (this.mode === 'repeat') {
        this.position = 0
        this._start()
        return
      }
      if (this.mode === 'shuffle' && this.queue.length > 1) {
        let i = this.currentIndex
        while (i === this.currentIndex) i = Math.floor(Math.random() * this.queue.length)
        this.currentIndex = i
        this._start()
        return
      }
      this.next()
    },

    toggle() {
      if (!this.current) return
      if (this._howl) {
        this.isPlaying ? this._howl.pause() : this._howl.play()
      }
      this._paused = this.isPlaying
      this.isPlaying = !this.isPlaying
    },

    /** 跳到百分比位置 */
    seek(pct) {
      const d = this.current?.duration || 0
      if (!d) return
      this.position = Math.max(0, Math.min(d, (pct / 100) * d))
      if (this._howl) this._howl.seek(this.position)
    },

    setVolume(v) {
      this.volume = Math.max(0, Math.min(1, v))
      this.muted = false
      localStorage.setItem(VOL_KEY, String(this.volume))
      this._howl?.volume(this.volume)
    },

    toggleMute() {
      this.muted = !this.muted
      this._howl?.volume(this.effectiveVolume)
    },

    /** 顺序 → 单曲循环 → 随机 → 顺序 */
    cycleMode() {
      const order = ['order', 'repeat', 'shuffle']
      this.mode = order[(order.indexOf(this.mode) + 1) % order.length]
      localStorage.setItem(MODE_KEY, this.mode)
    },

    next() { this._step(+1) },
    prev() {
      // 播放超过 3 秒时「上一首」先回到本曲开头（音乐播放器惯例）
      if (this.position > 3) return this.seek(0)
      this._step(-1)
    },

    _step(dir) {
      if (!this.queue.length) return
      this.currentIndex = (this.currentIndex + dir + this.queue.length) % this.queue.length
      this._start()
    },

    removeAt(i) {
      this.queue.splice(i, 1)
      if (i < this.currentIndex) this.currentIndex--
      else if (i === this.currentIndex) this.queue.length ? this._start() : this.stop()
    },

    stop() {
      this._teardown()
      this.queue = []
      this.currentIndex = -1
      this.isPlaying = false
      this.position = 0
    },

    _teardown() {
      this._howl?.unload()
      this._howl = null
      clearInterval(this._timer)
      clearInterval(this._syncTimer)
      this._timer = null
      this._syncTimer = null
    },
  },
})
