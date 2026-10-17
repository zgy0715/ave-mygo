<script setup>
import { onMounted, ref } from 'vue'
import { listSongs } from '@/api/song'

// 响应式状态：改 .value，页面自动更新
const songs = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listSongs({ page: 1, pageSize: 12 })   // request.js 已剥壳，直接拿 data
    songs.value = data.content
    total.value = data.totalElements
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

// 页面挂载后自动加载
onMounted(load)

// "燃,夜勤" → ['燃','夜勤']
function tagList(tags) {
  return (tags || '').split(',').filter(Boolean)
}

function formatDuration(sec) {
  if (!sec) return '—'
  return `${Math.floor(sec / 60)}分${sec % 60}秒`
}
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 py-8">
    <h1 class="mb-6 text-2xl font-bold">
      歌曲库
      <span class="ml-2 text-sm font-normal text-gray-500">共 {{ total }} 首</span>
    </h1>

    <!-- 加载态：骨架屏 -->
    <div v-if="loading" class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div v-for="i in 4" :key="i" class="h-44 animate-pulse rounded-lg bg-gray-200"></div>
    </div>

    <!-- 错误态：提示 + 重试 -->
    <div v-else-if="error" class="rounded-lg bg-red-50 p-4 text-red-600">
      {{ error }}
      <button class="ml-3 underline" @click="load">重试</button>
    </div>

    <!-- 空态 -->
    <div v-else-if="songs.length === 0" class="py-16 text-center text-gray-500">
      曲库空空如也，去添加第一首歌吧 🎸
    </div>

    <!-- 成功态：歌曲卡片网格 -->
    <div v-else class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div
        v-for="s in songs"
        :key="s.id"
        class="rounded-lg border border-gray-200 p-3 transition hover:shadow-md"
      >
        <!-- 封面占位（无图时用渐变色块） -->
        <div
          class="mb-2 flex h-28 items-center justify-center rounded bg-gradient-to-br from-indigo-200 to-pink-200 text-3xl"
        >
          🎵
        </div>
        <p class="truncate font-semibold">{{ s.title }}</p>
        <p class="text-xs text-gray-500">{{ s.bandName }} · {{ formatDuration(s.duration) }}</p>
        <div class="mt-1 flex flex-wrap gap-1">
          <span
            v-for="t in tagList(s.tags)"
            :key="t"
            class="rounded bg-indigo-50 px-1.5 py-0.5 text-[10px] text-indigo-600"
          >
            {{ t }}
          </span>
        </div>
      </div>
    </div>
  </main>
</template>
