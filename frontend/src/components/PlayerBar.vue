<script setup>
import { usePlayerStore } from '@/stores/player'

const player = usePlayerStore()

function fmt(sec) {
  if (!sec) return '00:00'
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}
</script>

<template>
  <!-- 全局底部播放条：有 current 才显示 -->
  <div
    v-if="player.current"
    class="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-elevated/95 backdrop-blur"
  >
    <div class="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
      <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-700 to-pink-700 text-xl">
        {{ player.isPlaying ? '🔊' : '🔇' }}
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-semibold text-white">{{ player.current.title }}</p>
        <p class="text-xs text-gray-500">
          {{ player.current.bandName }}
          <span v-if="!player.current.audioUrl" class="ml-2 rounded bg-yellow-500/15 px-1.5 text-[10px] text-yellow-400">暂无试听音源</span>
        </p>
      </div>
      <div class="flex items-center gap-2">
        <button
          class="flex h-10 w-10 items-center justify-center rounded-full bg-white text-black hover:scale-105 transition"
          @click="player.toggle()"
        >{{ player.isPlaying ? '⏸' : '▶' }}</button>
        <button class="px-2 text-gray-500 hover:text-gray-200" @click="player.stop()">✕</button>
      </div>
    </div>
  </div>
</template>
