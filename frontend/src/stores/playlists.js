import { defineStore } from 'pinia'
import { createPlaylist, deletePlaylist, listPlaylists, updatePlaylist } from '@/api/playlist'

/**
 * 歌单列表缓存：供「加入歌单」下拉与 /playlists 页共用，避免重复拉取。
 * 详情（含歌曲）不缓存，进详情页实时拉。
 */
export const usePlaylistStore = defineStore('playlists', {
  state: () => ({
    items: [],
    loaded: false,
    loading: false,
    generation: 0,
    pendingLoad: null,
  }),

  actions: {
    async load(force = false) {
      if (this.loading) return this.pendingLoad
      if (this.loaded && !force) return this.items
      const generation = this.generation
      this.loading = true
      this.pendingLoad = (async () => {
        try {
          const items = await listPlaylists()
          if (generation === this.generation) { this.items = items; this.loaded = true }
        } catch { /* 保留当前账户已有缓存，下次仍可重试 */ }
        finally {
          if (generation === this.generation) { this.loading = false; this.pendingLoad = null }
        }
        return generation === this.generation ? this.items : []
      })()
      return this.pendingLoad
    },

    async create(name, description) {
      const generation = this.generation
      const created = await createPlaylist({ name, description })
      if (generation !== this.generation) throw new Error('登录状态已变化，请重试')
      this.items = [created, ...this.items]      // 新建排在最前
      this.loaded = true
      return created
    },

    async update(id, body) {
      const generation = this.generation
      const updated = await updatePlaylist(id, body)
      if (generation !== this.generation) throw new Error('登录状态已变化，请重试')
      const i = this.items.findIndex((p) => p.id === id)
      if (i >= 0) this.items[i] = { ...this.items[i], ...updated }
      return updated
    },

    async remove(id) {
      const generation = this.generation
      await deletePlaylist(id)
      if (generation !== this.generation) throw new Error('登录状态已变化，请重试')
      this.items = this.items.filter((p) => p.id !== id)
    },

    /** 加入歌单后同步卡片上的曲目数与封面 */
    patchSummary(detail) {
      const i = this.items.findIndex((p) => p.id === detail.id)
      if (i < 0) return
      this.items[i] = {
        ...this.items[i],
        name: detail.name,
        songCount: detail.songs?.length ?? this.items[i].songCount,
        coverUrl: detail.songs?.[0]?.coverUrl ?? this.items[i].coverUrl,
        updatedAt: detail.updatedAt,
      }
    },

    clear() {
      this.generation++
      this.items = []
      this.loaded = false
      this.loading = false
      this.pendingLoad = null
    },
  },
})
