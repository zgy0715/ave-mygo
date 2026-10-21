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
  }),

  actions: {
    async load(force = false) {
      if (this.loading || (this.loaded && !force)) return this.items
      this.loading = true
      try {
        this.items = await listPlaylists()
        this.loaded = true
      } catch {
        this.items = this.items || []
      } finally {
        this.loading = false
      }
      return this.items
    },

    async create(name, description) {
      const created = await createPlaylist({ name, description })
      this.items = [created, ...this.items]      // 新建排在最前
      this.loaded = true
      return created
    },

    async update(id, body) {
      const updated = await updatePlaylist(id, body)
      const i = this.items.findIndex((p) => p.id === id)
      if (i >= 0) this.items[i] = { ...this.items[i], ...updated }
      return updated
    },

    async remove(id) {
      await deletePlaylist(id)
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
      this.items = []
      this.loaded = false
    },
  },
})
