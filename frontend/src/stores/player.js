import { defineStore } from 'pinia'
import { Howl } from 'howler'

/**
 * 全局播放器（Pinia）：
 * - 有 audioUrl → Howler 真实播放（官方音源外链，站内不托管）
 * - 无 audioUrl → 演示模式：进度条模拟走动，方便界面开发验收
 * 队列支持：歌曲卡片「播放」= 单曲入队即播；后续歌单「全部播放」直接灌整队。
 */
export const usePlayerStore = defineStore('player', {
  state: () => ({
    queue: [],
    currentIndex: -1,
    isPlaying: false,
    position: 0,   // 秒
    volume: 0.8,
  }),

  getters: {
    current: (s) => s.queue[s.currentIndex] || null,
    progressPct(s) {
      const d = this.current?.duration || 0
      return d ? Math.min(100, (s.position / d) * 100) : 0
    },
  },

  actions: {
    /** 单曲入队并播放 */
    play(song) {
      if (this.current?.id === song.id) return this.toggle()
      this.queue = [song]
      this.currentIndex = 0
      this._start()
    },

    /** 整单入队，从第 index 首开始播
     *  点的若正是当前播放曲 → 暂停/恢复，而不是从头重播（点击即所得） */
    playQueue(list, index = 0) {
      if (this.current?.id === list[index]?.id) return this.toggle()
      this.queue = [...list]
      this.currentIndex = index
      this._start()
    },

    _start() {
      this._teardown()
      this.position = 0
      this._paused = false   // 换曲必须清除暂停标志，否则「暂停后切歌」进度冻结
      const song = this.current
      if (!song) return

      if (song.audioUrl) {
        this._howl = new Howl({ src: [song.audioUrl], html5: true, volume: this.volume })
        this._howl.play()
        this._howl.on('end', () => this.next())
        this._howl.on('playerror', () => {
          // 需求走查兜底：外链失效 → 跳下一首（UI 层可另行 toast）
          console.warn('[player] 音源失效，跳下一首')
          this.next()
        })
      } else {
        // 演示模式：每 250ms 推进 0.25s
        // 注意：duration 可能为 null（库里没填时长）→ 用 0 兜底，
        // 否则 Math.min(x, undefined) 会算出 NaN，进度条直接崩
        const dur = Number(song.duration) || 0
        this._timer = setInterval(() => {
          if (this._paused || !dur) return
          this.position = Math.min(this.position + 0.25, dur)
          if (this.position >= dur) this.next()
        }, 250)
      }
      this.isPlaying = true
    },

    toggle() {
      if (!this.current) return
      if (this._howl) {
        this.isPlaying ? this._howl.pause() : this._howl.play()
      }
      this._paused = this.isPlaying
      this.isPlaying = !this.isPlaying
    },

    seek(pct) {
      const d = this.current?.duration || 0
      if (!d) return
      this.position = (pct / 100) * d
      if (this._howl) this._howl.seek(this.position)
    },

    setVolume(v) {
      this.volume = v
      this._howl?.volume(v)
    },

    next() { this._step(+1) },
    prev() { this._step(-1) },

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
      this._timer = null
    },
  },
})
