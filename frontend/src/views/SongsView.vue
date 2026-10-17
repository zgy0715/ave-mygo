<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { listBands, listSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

const player = usePlayerStore()

const filters = reactive({ bandId: '', keyword: '', tag: '', sort: 'release_desc' })
const page = ref(1)
const pageSize = 12

const bands = ref([])
const TAGS = ['燃', '治愈', '暗', '催泪', '主题曲']

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

watch(filters, () => { page.value = 1; load() })
watch(page, load)

onMounted(async () => {
  load()
  try { bands.value = await listBands() } catch { /* 降级 */ }
})

function tagList(tags) { return (tags || '').split(',').filter(Boolean) }
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 pb-24 pt-8">
    <div class="section-head">
      <h2 class="text-lg font-bold text-gray-900"><span class="mono mr-2 text-xs text-primary">_S_</span>歌曲库 <span class="mono ml-2 text-[10px] text-dim">SONG LIBRARY</span></h2>
      <span class="mono text-xs text-dim">共 {{ totalElements }} 首</span>
    </div>

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
          class="mono rounded-full border px-3 py-1 text-xs transition"
          :class="filters.tag === t ? 'border-primary text-primary' : 'border-line text-dim hover:text-gray-900'"
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
    <div v-else-if="songs.length === 0" class="py-20 text-center text-dim">没有符合条件的歌曲</div>

    <!-- 卡片网格 -->
    <div v-else class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div
        v-for="s in songs" :key="s.id"
        class="group cursor-pointer overflow-hidden rounded-xl border border-line bg-surface transition hover:border-primary/50"
        @click="player.play(s)"
      >
        <div class="relative flex h-36 items-center justify-center bg-gradient-to-br from-primary/15 to-transparent text-3xl">
          🎵
          <span class="absolute inset-0 flex items-center justify-center bg-black/50 text-3xl opacity-0 transition group-hover:opacity-100">
            {{ player.current?.id === s.id && player.isPlaying ? '⏸' : '▶' }}
          </span>
        </div>
        <div class="p-3">
          <p class="truncate font-semibold text-gray-900">{{ s.title }}</p>
          <p class="mt-0.5 text-xs text-gray-600">
            {{ s.bandName }}
            <span v-if="s.albumName" class="ml-1">· {{ s.albumName }}</span>
          </p>
          <div class="mt-1.5 flex flex-wrap gap-1">
            <span v-for="t in tagList(s.tags)" :key="t" class="mono rounded bg-elevated px-1.5 py-0.5 text-[10px] text-dim">{{ t }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-3 text-sm">
      <button class="page-btn" :disabled="page <= 1" @click="page--">上一页</button>
      <span class="mono tabular text-xs text-dim">{{ page }} / {{ totalPages }} — {{ totalElements }} SONGS</span>
      <button class="page-btn" :disabled="page >= totalPages" @click="page++">下一页</button>
    </div>
  </main>
</template>
