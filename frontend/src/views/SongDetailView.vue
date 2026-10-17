<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { songById, songs, bandById } from '@/data/mock'

/**
 * 歌曲详情（需求 3.3）：大封面 + 元信息 + MV embed。
 * 兜底：无音源时播放钮降级为「观看 MV」；mvUrl 也为空则整块提示。
 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const auth = useAuthStore()

const song = songById(route.params.id)
const band = computed(() => song && bandById(song.band))
const sameAlbum = computed(() => song ? songs.filter((s) => s.album === song.album && s.id !== song.id) : [])

function onPlay() {
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  player.play(song)
}
</script>

<template>
  <div v-if="song" class="wrap py-8">
    <RouterLink to="/songs" class="mb-6 inline-block text-sm text-dim hover:text-primary">← 返回歌曲库</RouterLink>

    <div class="grid gap-8 md:grid-cols-[280px_1fr]">
      <!-- 左：封面 + 播放 -->
      <div>
        <CoverArt :title="song.title" :band="song.band" class="aspect-square w-full shadow-md" rounded="rounded-xl" />
        <button class="btn-primary mt-4 w-full" @click="onPlay">
          ▶ {{ song.audioUrl ? '播放试听' : '播放（演示模式）' }}
        </button>
        <p v-if="!song.audioUrl" class="mt-2 text-center text-xs text-faint">暂无官方试听外链，可观看 MV ↓</p>
      </div>

      <!-- 右：元信息 -->
      <div>
        <span class="rounded px-2 py-0.5 text-xs font-medium" :style="{ background: band?.soft, color: band?.color }">{{ band?.name }}</span>
        <h1 class="mt-3 text-3xl font-black sm:text-4xl">{{ song.title }}</h1>
        <p class="mt-2 text-dim">{{ song.album }}</p>

        <dl class="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <div><dt class="text-faint">发行日期</dt><dd class="tabular mt-0.5 font-medium">{{ song.releaseDate }}</dd></div>
          <div><dt class="text-faint">时长</dt><dd class="tabular mt-0.5 font-medium">{{ Math.floor(song.duration / 60) }}:{{ String(song.duration % 60).padStart(2, '0') }}</dd></div>
          <div><dt class="text-faint">标签</dt><dd class="mt-0.5 font-medium">{{ song.tags.join(' / ') }}</dd></div>
        </dl>

        <!-- MV 区：有 mvUrl 渲染 B 站 iframe，否则外链兜底 -->
        <div class="mt-8">
          <h2 class="mb-3 text-lg font-bold">MV</h2>
          <div v-if="song.mvUrl" class="aspect-video overflow-hidden rounded-xl border border-line">
            <iframe :src="song.mvUrl" class="h-full w-full" allowfullscreen scrolling="no" />
          </div>
          <EmptyState
            v-else
            icon="📺"
            text="MV 外链待补充"
            hint="后端就绪后在管理端维护 mv_url 字段即可"
          />
        </div>

        <!-- 同专辑其他曲目 -->
        <div v-if="sameAlbum.length" class="mt-8">
          <h2 class="mb-3 text-lg font-bold">同专辑曲目</h2>
          <ul class="card divide-y divide-line overflow-hidden">
            <li v-for="s in sameAlbum" :key="s.id">
              <RouterLink :to="`/songs/${s.id}`" class="flex items-center justify-between p-3 text-sm hover:bg-base">
                <span class="font-medium">{{ s.title }}</span>
                <span class="tabular text-xs text-faint">{{ Math.floor(s.duration / 60) }}:{{ String(s.duration % 60).padStart(2, '0') }}</span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="wrap py-16">
    <EmptyState icon="🤔" text="歌曲不存在或已下架" action-text="回到歌曲库" @action="router.push('/songs')" />
  </div>
</template>
