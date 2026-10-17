<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { listBands, listSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

const player = usePlayerStore()

// ===== 筛选状态 =====
const filters = reactive({ bandId: '', keyword: '', tag: '', sort: 'release_desc' })
const page = ref(1)
const pageSize = 12

const bands = ref([])
const TAGS = ['燃', '治愈', '暗', '催泪', '主题曲']

// ===== 列表状态（四态） =====
const songs = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const errMsg = ref('')

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listSongs({
      bandId: filters.bandId || undefined,
      keyword: filters.keyword || undefined,
      tag: filters.tag || undefined,
      sort: filters.sort,
      page: page.value,
      pageSize,
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

// 任一筛选条件变化 → 回到第 1 页重新查
watch(filters, () => { page.value = 1; load() })
watch(page, load)

onMounted(async () => {
  load()
  try { bands.value = await listBands() } catch { /* 乐队下拉失败不阻塞页面 */ }
})
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 pb-24 pt-8">
    <!-- Hero 横幅 -->
    <section class="mb-8 overflow-hidden rounded-2xl border border-line bg-gradient-to-r from-violet-900/60 via-surface to-surface p-8">
      <h1 class="text-2xl font-bold text-white">用歌声，找回前进的理由</h1>
      <p class="mt-2 text-sm text-gray-400">MyGO!!!! 与 Ave Mujica 的歌曲库与演出票务平台 —— 一切从《春日影》之后开始。</p>
    </section>

    <!-- 筛选栏 -->
    <section class="mb-6 flex flex-wrap items-center gap-3">
      <select v-model="filters.bandId" class="input-select">
        <option value="">全部乐队</option>
        <option v-for="b in bands" :key="b.id" :value="b.id">{{ b.name }}</option>
      </select>
      <input v-model.trim="filters.keyword" placeholder="搜索歌名…" class="input-field w-44" />
      <div class="flex gap-1.5">
        <button
          v-for="t in TAGS" :key="t"
          class="rounded-full border px-3 py-1 text-xs transition"
          :class="filters.tag === t ? 'border-primary bg-primary/20 text-primary' : 'border-line text-gray-400 hover:text-gray-200'"
          @click="filters.tag = filters.tag === t ? '' : t"
        >{{ t }}</button>
      </div>
      <select v-model="filters.sort" class="input-select ml-auto">
        <option value="release_desc">最新发行</option>
        <option value="release_asc">最早发行</option>
        <option value="title_asc">按标题</option>
      </select>
    </section>

    <!-- 四态 -->
    <div v-if="loading" class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div v-for="i in 4" :key="i" class="h-48 animate-pulse rounded-xl bg-surface"></div>
    </div>
    <div v-else-if="errMsg" class="rounded-lg bg-red-950/50 p-4 text-sm text-red-400">
      {{ errMsg }} <button class="ml-2 underline" @click="load">重试</button>
    </div>
    <div v-else-if="songs.length === 0" class="py-20 text-center text-gray-500">没有符合条件的歌曲 🎸</div>

    <!-- 歌曲网格 -->
    <div v-else class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div
        v-for="s in songs" :key="s.id"
        class="group cursor-pointer overflow-hidden rounded-xl border border-line bg-surface transition hover:border-primary/50 hover:shadow-[0_0_24px_rgba(139,92,246,0.25)]"
        @click="player.play(s)"
      >
        <div class="relative flex h-36 items-center justify-center bg-gradient-to-br from-violet-800/40 to-pink-800/30 text-3xl">
          🎵
          <!-- 悬停播放按钮 -->
          <span class="absolute inset-0 flex items-center justify-center bg-black/40 text-4xl opacity-0 transition group-hover:opacity-100">
            {{ player.current?.id === s.id && player.isPlaying ? '⏸' : '▶' }}
          </span>
        </div>
        <div class="p-3">
          <p class="truncate font-semibold text-white">{{ s.title }}</p>
          <p class="mt-0.5 text-xs text-gray-500">
            {{ s.bandName }}
            <span v-if="s.albumName" class="ml-1">· {{ s.albumName }}</span>
          </p>
          <div class="mt-1.5 flex flex-wrap gap-1">
            <span v-for="t in (s.tags || '').split(',').filter(Boolean)" :key="t"
              class="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">{{ t }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-3 text-sm">
      <button class="page-btn" :disabled="page <= 1" @click="page--">上一页</button>
      <span class="tabular text-gray-400">{{ page }} / {{ totalPages }}（共 {{ totalElements }} 首）</span>
      <button class="page-btn" :disabled="page >= totalPages" @click="page++">下一页</button>
    </div>
  </main>
</template>
