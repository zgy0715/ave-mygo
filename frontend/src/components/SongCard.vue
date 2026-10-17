<script setup>
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { bandById } from '@/data/mock'

/** 歌曲卡片：封面网格用（首页新曲推荐）。点击封面进详情，点击播放钮直接入队 */
const props = defineProps({ song: { type: Object, required: true } })
const player = usePlayerStore()
const auth = useAuthStore()
const router = useRouter()
const band = bandById(props.song.band)

function onPlay() {
  // 需求走查缺口①：游客点播放不 404，引导登录
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: `/songs/${props.song.id}` } })
  player.play(props.song)
}
</script>

<template>
  <div class="card group relative overflow-hidden p-3 hover:shadow-md">
    <RouterLink :to="`/songs/${song.id}`" class="block">
      <div class="relative overflow-hidden rounded-lg">
        <CoverArt :title="song.title" :band="song.band" class="zoomable aspect-square w-full" rounded="rounded-lg" />
        <span class="tabular absolute bottom-1.5 right-1.5 rounded bg-black/55 px-1.5 py-0.5 text-[11px] text-white">
          {{ Math.floor(song.duration / 60) }}:{{ String(song.duration % 60).padStart(2, '0') }}
        </span>
      </div>
      <p class="mt-2.5 truncate text-sm font-semibold" :title="song.title">{{ song.title }}</p>
      <p class="mt-0.5 truncate text-xs text-dim">{{ song.album }}</p>
    </RouterLink>

    <button
      class="absolute right-4 top-[7.25rem] flex h-9 w-9 scale-75 items-center justify-center rounded-full bg-primary text-white opacity-0 shadow-lg transition-all duration-300 group-hover:scale-100 group-hover:opacity-100"
      :class="player.current?.id === song.id && player.isPlaying && '!scale-100 !opacity-100'"
      style="transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1)"
      aria-label="播放"
      @click="onPlay"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
    </button>

    <div class="mt-2 flex items-center gap-1.5">
      <span class="rounded px-1.5 py-0.5 text-[11px] font-medium" :style="{ background: band?.soft, color: band?.color }">
        {{ band?.name }}
      </span>
      <span v-for="t in song.tags.slice(0, 1)" :key="t" class="text-[11px] text-faint">#{{ t }}</span>
    </div>
  </div>
</template>
