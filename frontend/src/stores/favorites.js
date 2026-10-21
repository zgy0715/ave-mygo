import { defineStore } from 'pinia'
import { addFavorite, listFavorites, removeFavorite } from '@/api/favorite'

const key = (type, id) => `${type}:${id}`

/**
 * 收藏状态仓库：
 * - 只缓存「是否已收藏」的 id 集合，供心形按钮即时判断（避免每张卡片发一次请求）
 * - 登录后加载一次全量收藏；退出登录清空
 * - toggle 采用乐观更新：先改 UI，失败回滚
 */
export const useFavoriteStore = defineStore('favorites', {
  state: () => ({
    keys: new Set(),        // 'SONG:1' / 'EVENT:2'
    loaded: false,
    loading: false,
    pending: new Set(),     // 正在请求的 key，防连点
  }),

  actions: {
    isFav(type, id) {
      return this.keys.has(key(type, id))
    },

    /** 拉取两类收藏（登录后调用；已加载过则跳过） */
    async loadAll(force = false) {
      if (this.loading || (this.loaded && !force)) return
      this.loading = true
      try {
        const [songs, events] = await Promise.all([listFavorites('SONG'), listFavorites('EVENT')])
        const next = new Set()
        songs.forEach((f) => next.add(key(f.targetType, f.targetId)))
        events.forEach((f) => next.add(key(f.targetType, f.targetId)))
        this.keys = next
        this.loaded = true
      } catch {
        /* 未登录/网络异常：保持空集合，不阻塞页面 */
      } finally {
        this.loading = false
      }
    },

    /** 切换收藏；返回 true=已收藏 false=已取消，失败抛错由调用方提示 */
    async toggle(type, id) {
      const k = key(type, id)
      if (this.pending.has(k)) return this.keys.has(k)
      this.pending.add(k)
      const wasFav = this.keys.has(k)
      // 乐观更新（Set 需换新引用才能触发响应式）
      const next = new Set(this.keys)
      wasFav ? next.delete(k) : next.add(k)
      this.keys = next
      try {
        if (wasFav) await removeFavorite(type, id)
        else await addFavorite(type, id)
        return !wasFav
      } catch (e) {
        const rollback = new Set(this.keys)
        wasFav ? rollback.add(k) : rollback.delete(k)
        this.keys = rollback
        throw e
      } finally {
        this.pending.delete(k)
      }
    },

    clear() {
      this.keys = new Set()
      this.loaded = false
    },
  },
})
