<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import PageBack from '@/components/PageBack.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getPlaylist, removeSongFromPlaylist } from '@/api/playlist'
import { usePlaylistStore } from '@/stores/playlists'
import { usePlayerStore } from '@/stores/player'
import { slugByBandId } from '@/utils/band'

/** 歌单详情：播放全部 / 单曲播放 / 移除；改名删除走列表页入口 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const store = usePlaylistStore()

const detail = ref(null)
const loading = ref(true)
const errMsg = ref('')
const acting = ref(0)      // 正在移除的 songId（防连点）

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    detail.value = await getPlaylist(route.params.id)
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const songs = computed(() => detail.value?.songs || [])

function playAll() {
  if (songs.value.length) player.playQueue(songs.value, 0)
}
function playOne(i) {
  player.playQueue(songs.value, i)
}

async function removeSong(s) {
  if (acting.value) return
  acting.value = s.id
  try {
    detail.value = await removeSongFromPlaylist(detail.value.id, s.id)
    store.patchSummary(detail.value)
    // 若正在播的正是这首歌且在队列里，不强行停播（只从歌单移除）
  } catch (e) {
    errMsg.value = e.message
  } finally {
    acting.value = 0
  }
}

const fmt = (sec) => (sec ? `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` : '—')
</script>

<template>
  <div class="wrap py-8">
    <PageBack label="返回歌单列表" fallback="/playlists" />

    <div v-if="loading" class="mt-4 card p-4"><div class="h-48 animate-pulse rounded-lg bg-base"></div></div>

    <div v-else-if="errMsg && !detail" class="mt-4 card p-4 text-sm text-red-500">
      {{ errMsg }} <button class="ml-2 underline" @click="load">重试</button>
    </div>

    <template v-else-if="detail">
      <!-- 头部 -->
      <div class="mt-4 flex flex-wrap items-end justify-between gap-4">
        <div class="min-w-0">
          <h1 class="serif truncate text-2xl font-semibold sm:text-3xl">{{ detail.name }}</h1>
          <p v-if="detail.description" class="mt-1 text-sm text-dim">{{ detail.description }}</p>
          <p class="mt-1 text-xs text-faint">{{ songs.length }} 首</p>
        </div>
        <div class="flex gap-2">
          <button class="btn-primary !px-4 !py-2 !text-xs" :disabled="!songs.length" @click="playAll">▶ 播放全部</button>
          <button class="btn-ghost !px-3 !py-2 !text-xs" @click="router.push('/songs')">＋ 添加歌曲</button>
        </div>
      </div>

      <!-- 歌曲列表 -->
      <div v-if="songs.length" class="card mt-5 divide-y divide-line overflow-hidden">
        <div
          v-for="(s, i) in songs"
          :key="s.id"
          class="group flex items-center gap-3 p-3 transition hover:bg-elevated sm:gap-4 sm:px-4"
        >
          <button class="relative shrink-0 cursor-pointer" :aria-label="`播放 ${s.title}`" @click="playOne(i)">
            <CoverArt :title="s.title" :band="slugByBandId(s.bandId)" :cover-url="s.coverUrl || ''" class="h-11 w-11" rounded="rounded-md" />
            <span class="absolute inset-0 flex items-center justify-center rounded-md bg-black/45 text-white opacity-0 transition group-hover:opacity-100">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            </span>
          </button>

          <RouterLink :to="`/songs/${s.id}`" class="min-w-0 flex-1">
            <p class="truncate text-sm font-semibold" :class="player.current?.id === s.id && 'text-primary'">{{ s.title }}</p>
            <p class="mt-0.5 truncate text-xs text-faint">{{ s.albumName || '未收录单曲' }} · {{ s.bandName }}</p>
          </RouterLink>

          <span class="tabular hidden w-12 text-right text-xs text-faint sm:block">{{ fmt(s.duration) }}</span>

          <button
            class="flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-faint opacity-0 transition group-hover:opacity-100 hover:bg-base hover:text-red-500 disabled:opacity-40"
            title="从歌单移除"
            aria-label="从歌单移除"
            :disabled="acting === s.id"
            @click="removeSong(s)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13" /></svg>
          </button>
        </div>
      </div>

      <EmptyState
        v-else
        icon="♫"
        text="这个歌单还是空的"
        hint="去歌曲库挑几首加进来吧"
        action-text="去歌曲库"
        @action="router.push('/songs')"
      />

      <p v-if="errMsg" class="mt-3 text-xs text-red-500">{{ errMsg }}</p>
    </template>
  </div>
</template>
