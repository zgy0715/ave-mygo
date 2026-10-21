<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

/** 歌曲卡片：封面网格用（真接口字段）。点击封面进详情，播放钮直接入队 */
const props = defineProps({ song: { type: Object, required: true } })
const player = usePlayerStore()
const router = useRouter()
const auth = useAuthStore()

// 库里乐队名写法不统一（'MyGO!!!!' 与 'MyGO!!!!!' 都有），按前缀归一化，别用精确匹配
const slugOf = (name = '') => {
  if (name.startsWith('MyGO')) return 'mygo'
  if (name.startsWith('Ave Mujica')) return 'ave-mujica'
  if (name.toLowerCase().includes('sumimi')) return 'sumimi'
  return 'mygo'
}
const slug = computed(() => slugOf(props.song.bandName))
const BAND_STYLE = {
  mygo: { name: 'MyGO!!!!!', color: '#2c7da0', soft: '#e3f0f7' },
  'ave-mujica': { name: 'Ave Mujica', color: '#6b4fa3', soft: '#efeaf7' },
  sumimi: { name: 'sumimi', color: '#d4789f', soft: '#fbe9ef' },
}
const band = computed(() => BAND_STYLE[slug.value])
const tags = computed(() => (props.song.tags || '').split(',').filter(Boolean))

function onPlay() {
  // 无官方试听：静音"演示播放"没有意义，直接进详情页走 MV / 外链
  if (!props.song.audioUrl) {
    router.push(`/songs/${props.song.id}`)
    return
  }
  // 听歌不设登录门槛（游客可试听，登录只影响购票等操作）
  player.play(props.song)
}
</script>

<template>
  <div class="card group relative overflow-hidden p-3 hover:shadow-md">
    <RouterLink :to="`/songs/${song.id}`" class="block">
      <div class="relative overflow-hidden rounded-lg">
        <CoverArt :title="song.title" :band="slug" :cover-url="song.coverUrl || ''" class="zoomable aspect-square w-full" rounded="rounded-lg" />
        <span class="tabular absolute bottom-1.5 right-1.5 rounded bg-black/55 px-1.5 py-0.5 text-[11px] text-white">
          {{ song.duration ? `${Math.floor(song.duration / 60)}:${String(song.duration % 60).padStart(2, '0')}` : '--:--' }}
        </span>
      </div>
      <p class="mt-2.5 truncate text-sm font-semibold" :title="song.title">{{ song.title }}</p>
      <p class="mt-0.5 truncate text-xs text-dim">{{ song.albumName || '未收录单曲' }}</p>
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
      <span class="rounded px-1.5 py-0.5 text-[11px] font-medium" :style="{ background: band.soft, color: band.color }">
        {{ song.bandName }}
      </span>
      <span v-for="t in tags.slice(0, 1)" :key="t" class="text-[11px] text-faint">#{{ t }}</span>
    </div>
  </div>
</template>
