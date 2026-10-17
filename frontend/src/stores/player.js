import { defineStore } from 'pinia'
import { Howl } from 'howler'

/**
 * 全局播放器：底部播放条的数据源。
 * 只有 audioUrl 非空的歌能真正出声（种子数据暂无音源，界面先行）。
 */
export const usePlayerStore = defineStore('player', {
  state: () => ({
    current: null,     // 当前歌曲对象
    isPlaying: false,
    howl: null,        // Howl 实例（有音源时才有）
  }),
  actions: {
    play(song) {
      // 重复点同一首 → 切换暂停
      if (this.current?.id === song.id) {
        this.toggle()
        return
      }
      this.howl?.stop()
      this.current = song

      if (song.audioUrl) {
        this.howl = new Howl({ src: [song.audioUrl], html5: true })
        this.howl.play()
        this.isPlaying = true
        this.howl.on('end', () => (this.isPlaying = false))
      } else {
        // 无音源：仅展示播放条 UI
        this.howl = null
        this.isPlaying = true
      }
    },
    toggle() {
      if (!this.current) return
      if (this.howl) {
        this.isPlaying ? this.howl.pause() : this.howl.play()
        this.isPlaying = !this.isPlaying
      } else {
        this.isPlaying = !this.isPlaying   // 无音源时仅切换 UI 状态
      }
    },
    stop() {
      this.howl?.stop()
      this.current = null
      this.isPlaying = false
    },
  },
})
