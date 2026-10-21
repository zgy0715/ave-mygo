<script setup>
import { computed, ref } from 'vue'
import { usePlayerStore } from '@/stores/player'
import CoverArt from '@/components/CoverArt.vue'
import { slugByBandId } from '@/utils/band'

/**
 * 全局底部播放条：播放/暂停、进度拖拽、音量、上下曲、队列抽屉。
 * 封面 = 当前曲官方封面（无图回退程序化渐变）。
 */
const player = usePlayerStore()
const showQueue = ref(false)

const currentSlug = computed(() => slugByBandId(player.current?.bandId))

const curText = computed(() => {
  const m = Math.floor(player.position / 60)
  const s = String(Math.floor(player.position % 60)).padStart(2, '0')
  return `${m}:${s}`
})
const durText = computed(() => {
  const d = player.current?.duration || 0
  if (!d) return '—'
  return `${Math.floor(d / 60)}:${String(Math.floor(d % 60)).padStart(2, '0')}`
})

function onSeek(e) {
  player.seek(Number(e.target.value))
}
function onVolume(e) {
  player.setVolume(Number(e.target.value))
}
</script>

<template>
  <!-- 播放条：有 current 才出现 -->
  <transition enter-active-class="transition duration-200" enter-from-class="translate-y-full">
    <div v-if="player.current" class="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 backdrop-blur">
      <!-- 队列抽屉 -->
      <div v-if="showQueue" class="border-b border-line bg-surface">
        <div class="wrap flex items-center justify-between py-2">
          <p class="text-sm font-semibold">播放队列（{{ player.queue.length }}）</p>
          <button class="text-xs text-faint hover:text-dim" @click="showQueue = false">收起</button>
        </div>
        <ul class="wrap max-h-56 overflow-y-auto pb-3">
          <li
            v-for="(s, i) in player.queue"
            :key="s.id"
            class="group flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 hover:bg-base"
            :class="i === player.currentIndex && 'text-primary'"
            @click="player.playQueue(player.queue, i)"
          >
            <span class="mono w-6 text-center text-xs text-faint">{{ i + 1 }}</span>
            <span class="flex-1 truncate text-sm">{{ s.title }}</span>
            <span class="hidden text-xs text-faint sm:block">{{ s.albumName || '未收录单曲' }}</span>
            <button class="text-xs text-faint opacity-0 group-hover:opacity-100 hover:text-red-500" @click.stop="player.removeAt(i)">✕</button>
          </li>
        </ul>
      </div>

      <div class="wrap flex h-16 items-center gap-3 sm:gap-4">
        <!-- 封面 = 当前曲官方封面（缺图回退渐变） -->
        <CoverArt
          v-if="player.current"
          :title="player.current.title"
          :band="currentSlug"
          :cover-url="player.current.coverUrl || ''"
          class="hidden h-10 w-10 shrink-0 sm:flex"
          rounded="rounded-md"
        />
        <div class="w-28 min-w-0 sm:w-44">
          <p class="flex items-center gap-2 truncate text-sm font-semibold">
            <span class="truncate">{{ player.current.title }}</span>
            <!-- 无音源：演示模式（进度条模拟，无声音）——明确标注，避免被当成 bug -->
            <span
              v-if="!player.current.audioUrl"
              class="shrink-0 rounded border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-[10px] font-normal text-amber-700"
            >
              试听待补充
            </span>
          </p>
          <p class="truncate text-xs text-faint">{{ player.current.album }}</p>
        </div>

        <!-- 控制区 -->
        <div class="flex items-center gap-1 sm:gap-2">
          <button class="p-2 text-dim hover:text-ink" aria-label="上一首" @click="player.prev">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" /></svg>
          </button>
          <button class="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-white hover:bg-primary-strong" aria-label="播放/暂停" @click="player.toggle">
            <svg v-if="!player.isPlaying" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
          </button>
          <button class="p-2 text-dim hover:text-ink" aria-label="下一首" @click="player.next">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg>
          </button>
        </div>

        <!-- 进度（桌面） -->
        <div class="hidden flex-1 items-center gap-2 md:flex">
          <span class="tabular text-xs text-faint">{{ curText }}</span>
          <input
            type="range" min="0" max="100" step="0.1" :value="player.progressPct"
            class="h-1 flex-1 cursor-pointer appearance-none rounded-full bg-line accent-primary"
            @input="onSeek"
          >
          <span class="tabular text-xs text-faint">{{ durText }}</span>
        </div>

        <!-- 音量 + 队列（宽屏） -->
        <div class="hidden items-center gap-3 lg:flex">
          <input type="range" min="0" max="1" step="0.05" :value="player.volume" class="h-1 w-20 cursor-pointer appearance-none rounded-full bg-line accent-primary" @input="onVolume">
          <button class="text-sm" :class="showQueue ? 'text-primary' : 'text-dim hover:text-ink'" aria-label="队列" @click="showQueue = !showQueue">
            ☰
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>
