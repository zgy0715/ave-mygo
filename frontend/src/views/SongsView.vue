<script setup>
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { songs, bands, bandById } from '@/data/mock'

/**
 * 歌曲库：乐队 Tab + 关键词搜索（前端过滤，后端就绪后换成 query 参数）。
 * 列表行式布局：信息密度高于卡片网格，对标网易云。
 */
const player = usePlayerStore()
const auth = useAuthStore()
const router = useRouter()

const bandFilter = ref('all')
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return songs.filter((s) => {
    if (bandFilter.value !== 'all' && s.band !== bandFilter.value) return false
    if (kw && !(s.title.toLowerCase().includes(kw) || s.album.toLowerCase().includes(kw))) return false
    return true
  })
})

const fmt = (sec) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`

function onPlay(song) {
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: '/songs' } })
  // 列表播放 = 整个当前筛选结果入队，从该曲开始（歌单体验）
  player.playQueue(filtered.value, filtered.value.findIndex((s) => s.id === song.id))
}
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <h1 class="text-2xl font-black">歌曲库</h1>
      <p class="text-sm text-faint tabular">{{ filtered.length }} 首</p>
    </div>

    <!-- 筛选栏 -->
    <div class="mb-5 flex flex-wrap items-center gap-2">
      <button
        v-for="b in [{ id: 'all', name: '全部' }, ...bands]"
        :key="b.id"
        class="rounded-full border px-3.5 py-1.5 text-sm transition"
        :class="bandFilter === b.id ? 'border-primary bg-primary text-white' : 'border-line bg-surface text-dim hover:border-line-strong'"
        @click="bandFilter = b.id"
      >
        {{ b.name }}
      </button>
      <input v-model="keyword" type="search" placeholder="搜索歌名 / 专辑…" class="input ml-auto max-w-56">
    </div>

    <!-- 列表 -->
    <div v-if="filtered.length" class="card divide-y divide-line overflow-hidden">
      <div
        v-for="s in filtered"
        :key="s.id"
        class="group flex items-center gap-3 p-3 transition hover:bg-base sm:gap-4 sm:px-4"
      >
        <button
          class="relative shrink-0"
          :aria-label="`播放 ${s.title}`"
          @click="onPlay(s)"
        >
          <CoverArt :title="s.title" :band="s.band" class="h-11 w-11" rounded="rounded-md" />
          <span class="absolute inset-0 flex items-center justify-center rounded-md bg-black/45 text-white opacity-0 transition group-hover:opacity-100">
            <svg v-if="!(player.current?.id === s.id && player.isPlaying)" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
          </span>
        </button>

        <RouterLink :to="`/songs/${s.id}`" class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold" :class="player.current?.id === s.id && 'text-primary'">{{ s.title }}</p>
          <p class="mt-0.5 truncate text-xs text-faint">{{ s.album }} · {{ s.releaseDate }}</p>
        </RouterLink>

        <span class="hidden rounded px-1.5 py-0.5 text-[11px] font-medium sm:inline" :style="{ background: bandById(s.band)?.soft, color: bandById(s.band)?.color }">
          {{ bandById(s.band)?.name }}
        </span>
        <span class="tabular hidden w-12 text-right text-xs text-faint sm:block">{{ fmt(s.duration) }}</span>
      </div>
    </div>

    <EmptyState
      v-else
      icon="🔍"
      text="没有找到匹配的歌曲"
      hint="换个关键词，或切换乐队筛选试试"
      action-text="清空筛选"
      @action="keyword = ''; bandFilter = 'all'"
    />
  </div>
</template>
