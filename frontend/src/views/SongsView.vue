<script setup>
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { listBands, listSongs } from '@/api/song'

/**
 * 歌曲库：乐队 Tab + 关键词搜索 + 服务端分页（真接口版）。
 * 筛选走后端 query 参数（bandId/keyword/page）——数据上千首也不怕。
 * 列表行式布局：信息密度高于卡片网格，对标网易云。
 */
const player = usePlayerStore()
const auth = useAuthStore()
const router = useRouter()

const bands = ref([])
const PAGE_SIZE = 20

const bandFilter = ref('all')          // 'all' | bandId(数字)
const keyword = ref('')
const page = ref(1)

// 四态
const songs = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const errMsg = ref('')

// 乐队视觉：id → slug → CoverArt / 徽标配色（来自真接口的 bands）
const slugById = (id) => bands.value.find((b) => b.id === id)?.slug || 'mygo'
const BAND_THEME = {
  mygo: { color: '#2c7da0', soft: '#e3f0f7' },
  'ave-mujica': { color: '#6b4fa3', soft: '#efeaf7' },
}
const bandTheme = (id) => BAND_THEME[slugById(id)] || BAND_THEME.mygo

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listSongs({
      bandId: bandFilter.value === 'all' ? undefined : bandFilter.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize: PAGE_SIZE,
    })
    songs.value = data.content
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}

// 筛选变化 → 回第 1 页重新查（服务端分页的固定套路）
watch([bandFilter, keyword], () => { page.value = 1; load() })
watch(page, load)

onMounted(async () => {
  load()
  try { bands.value = await listBands() } catch { /* 下拉失败不阻塞列表 */ }
})

const fmt = (sec) => (sec ? `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` : '—')

function onPlay(song) {
  // 听歌不设登录门槛（产品定位：游客可浏览 / 试听，登录才需要买票）
  // 列表播放 = 整个当前页结果入队，从该曲开始（歌单体验）
  player.playQueue(songs.value, songs.value.findIndex((s) => s.id === song.id))
}

function clearFilters() {
  keyword.value = ''
  bandFilter.value = 'all'
}
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">SONGS — LIBRARY</p>
        <h1 class="serif text-2xl font-semibold">歌曲库</h1>
      </div>
      <p class="text-sm text-faint tabular">{{ totalElements }} 首</p>
    </div>

    <!-- 筛选栏：乐队 Tab 由真接口驱动 -->
    <div class="mb-5 flex flex-wrap items-center gap-2">
      <button
        v-for="b in [{ id: 'all', name: '全部' }, ...bands]"
        :key="b.id"
        class="rounded-full border px-3.5 py-1.5 text-sm transition"
        :class="bandFilter == b.id ? 'border-primary bg-primary text-white' : 'border-line bg-transparent text-dim hover:border-line-strong'"
        @click="bandFilter = b.id"
      >
        {{ b.name }}
      </button>
      <input v-model="keyword" type="search" placeholder="搜索歌名…" class="input ml-auto max-w-56">
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="card divide-y divide-line overflow-hidden">
      <div v-for="i in 6" :key="i" class="flex items-center gap-4 p-3 sm:px-4">
        <div class="h-11 w-11 shrink-0 animate-pulse rounded-md bg-base"></div>
        <div class="h-4 w-1/3 animate-pulse rounded bg-base"></div>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">
      {{ errMsg }}
      <button class="ml-2 underline" @click="load">重试</button>
    </div>

    <!-- 列表 -->
    <div v-else-if="songs.length" class="card divide-y divide-line overflow-hidden">
      <div
        v-for="s in songs"
        :key="s.id"
        class="group flex items-center gap-3 p-3 transition hover:bg-elevated sm:gap-4 sm:px-4"
      >
        <button
          class="relative shrink-0"
          :aria-label="`播放 ${s.title}`"
          @click="onPlay(s)"
        >
          <CoverArt :title="s.title" :band="slugById(s.bandId)" :cover-url="s.coverUrl || ''" class="h-11 w-11" rounded="rounded-md" />
          <span class="absolute inset-0 flex items-center justify-center rounded-md bg-black/45 text-white opacity-0 transition group-hover:opacity-100">
            <svg v-if="!(player.current?.id === s.id && player.isPlaying)" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
          </span>
        </button>

        <RouterLink :to="`/songs/${s.id}`" class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold" :class="player.current?.id === s.id && 'text-primary'">{{ s.title }}</p>
          <p class="mt-0.5 truncate text-xs text-faint">{{ s.albumName || '未收录单曲' }} · {{ s.releaseDate }}</p>
        </RouterLink>

        <span class="hidden rounded px-1.5 py-0.5 text-[11px] font-medium sm:inline" :style="{ background: bandTheme(s.bandId).soft, color: bandTheme(s.bandId).color }">
          {{ s.bandName }}
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
      @action="clearFilters"
    />

    <!-- 服务端分页 -->
    <div v-if="!loading && totalPages > 1" class="mt-6 flex items-center justify-center gap-3 text-sm">
      <button class="btn-ghost" :disabled="page <= 1" @click="page--">上一页</button>
      <span class="mono tabular text-xs text-faint">{{ page }} / {{ totalPages }} — {{ totalElements }} SONGS</span>
      <button class="btn-ghost" :disabled="page >= totalPages" @click="page++">下一页</button>
    </div>
  </div>
</template>
