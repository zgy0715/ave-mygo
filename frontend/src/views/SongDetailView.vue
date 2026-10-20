<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import PageBack from '@/components/PageBack.vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { getSong, listSongs } from '@/api/song'

/**
 * 歌曲详情（需求 3.3）——真接口版。
 * GET /songs/{id} → SongDetail；同专辑曲目用 GET /songs?bandId= 客户端过滤。
 * 透明底问题同 CoverArt：tag 是后端逗号串 → 前端拆数组。
 */
const route = useRoute()
const router = useRouter()
const player = usePlayerStore()
const auth = useAuthStore()

const song = ref(null)
const sameAlbum = ref([])
const loading = ref(true)
const errMsg = ref('')

const tags = computed(() => (song.value?.tags || '').split(',').filter(Boolean))

/**
 * MV 链接解析（三种来源）：
 * - player.html?bvid=… → 可内嵌 iframe
 * - mvPlatform='BILIBILI_PAGE' 或 player.html?ep_id=… → 番剧播放页，B 站禁止第三方嵌入，
 *   一律降级为跳转（ep_id 形式自动改写成 bangumi/play/epX）
 * - youtu.be / 其他外链 → 直接跳转
 */
const mvIsBangumi = computed(
  () => song.value?.mvPlatform === 'BILIBILI_PAGE' || /[?&]ep_id=/.test(song.value?.mvUrl || ''),
)
const mvEmbedUrl = computed(() => {
  const u = song.value?.mvUrl || ''
  if (song.value?.mvPlatform === 'BILIBILI_PAGE') return '' // 番剧页永不 iframe
  return /player\.bilibili\.com/.test(u) && /[?&]bvid=/.test(u) ? u : ''
})
const mvExternalUrl = computed(() => {
  const u = song.value?.mvUrl || ''
  if (!u) return ''
  const ep = u.match(/[?&]ep_id=(\d+)/)
  if (ep) return `https://www.bilibili.com/bangumi/play/ep${ep[1]}`
  const bv = u.match(/[?&]bvid=([A-Za-z0-9]+)/)
  if (bv) return `https://www.bilibili.com/video/${bv[1]}`
  return u
})
const mvLinkLabel = computed(() => {
  if (song.value?.mvPlatform === 'BILIBILI_PAGE') return '前往 B 站番剧观看'
  if (song.value?.mvPlatform === 'YOUTUBE') return '在 YouTube 观看 MV'
  return '在 B 站观看 MV'
})
const fmtDur = (sec) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`

// 乐队徽标：详情里只有 bandName，按名字映射配色
const BAND_THEME = {
  'MyGO!!!!': { color: '#2c7da0', soft: '#e3f0f7', slug: 'mygo' },
  'Ave Mujica': { color: '#6b4fa3', soft: '#efeaf7', slug: 'ave-mujica' },
  sumimi: { color: '#d4789f', soft: '#fbe9ef', slug: 'sumimi' },
}
const band = computed(() => BAND_THEME[song.value?.bandName] || BAND_THEME['MyGO!!!!'])

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    song.value = await getSong(route.params.id)
    // 同专辑曲目：同乐队列表里滤出专辑相同者（数据量小，够用）
    if (song.value.albumId) {
      const data = await listSongs({ bandId: song.value.bandId, pageSize: 50 })
      sameAlbum.value = data.content.filter((s) => s.albumId === song.value.albumId && s.id !== song.value.id)
    }
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onPlay() {
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  player.play(song.value)
}
</script>

<template>
  <div v-if="loading" class="wrap py-8"><div class="h-72 animate-pulse rounded-xl bg-surface"></div></div>

  <div v-else-if="errMsg" class="wrap py-8">
    <div class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></div>
  </div>

  <div v-else-if="song" class="wrap py-8">
    <PageBack label="返回歌曲库" fallback="/songs" />

    <div class="grid gap-8 md:grid-cols-[280px_1fr]">
      <!-- 左：封面 + 播放 -->
      <div>
        <CoverArt :title="song.title" :band="band.slug" :cover-url="song.coverUrl || ''" class="aspect-square w-full shadow-md" rounded="rounded-xl" />
        <button class="btn-primary mt-4 w-full" @click="onPlay">
          ▶ {{ song.audioUrl ? '播放试听' : '播放（演示模式）' }}
        </button>
        <p v-if="!song.audioUrl" class="mt-2 text-center text-xs text-faint">暂无官方试听外链，可观看 MV ↓</p>
      </div>

      <!-- 右：元信息 -->
      <div>
        <span class="rounded px-2 py-0.5 text-xs font-medium" :style="{ background: band.soft, color: band.color }">{{ song.bandName }}</span>
        <h1 class="serif mt-3 text-3xl font-semibold sm:text-4xl">{{ song.title }}</h1>
        <p class="mt-2 text-dim">{{ song.albumName || '未收录单曲' }}</p>

        <dl class="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <div><dt class="text-faint">发行日期</dt><dd class="tabular mt-0.5 font-medium">{{ song.releaseDate }}</dd></div>
          <div><dt class="text-faint">时长</dt><dd class="tabular mt-0.5 font-medium">{{ fmtDur(song.duration) }}</dd></div>
          <div><dt class="text-faint">标签</dt><dd class="mt-0.5 font-medium">{{ tags.join(' / ') || '—' }}</dd></div>
        </dl>

        <!-- MV 区：番剧 ep_id 不可外嵌，降级为跳转卡片；bvid 播放器正常内嵌 -->
        <div class="mt-8">
          <h2 class="mb-3 text-lg font-bold">MV</h2>

          <!-- 可内嵌（player.html?bvid=…） -->
          <template v-if="mvEmbedUrl">
            <div class="aspect-video overflow-hidden rounded-xl border border-line">
              <iframe :src="mvEmbedUrl" class="h-full w-full" allowfullscreen scrolling="no" />
            </div>
            <a :href="mvExternalUrl" target="_blank" rel="noopener" class="btn-ghost mt-2 inline-flex !py-1.5 !text-xs">
              内嵌无法播放？在 B 站打开 ↗
            </a>
          </template>

          <!-- 番剧页 / 其他外链：只给跳转，不硬塞 iframe -->
          <a
            v-else-if="mvExternalUrl"
            :href="mvExternalUrl"
            target="_blank"
            rel="noopener"
            class="card flex items-center gap-4 p-4 transition hover:shadow-md"
          >
            <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-white">▶</span>
            <span class="min-w-0 flex-1">
              <span class="block text-sm font-semibold">
                {{ mvLinkLabel }} ↗
                <span v-if="mvIsBangumi" class="micro ml-1.5 !text-[9px]">番剧页 · 不支持站内播放</span>
              </span>
              <span class="mt-0.5 block truncate text-xs text-faint">{{ mvExternalUrl }}</span>
            </span>
            <span class="text-primary">↗</span>
          </a>

          <EmptyState
            v-else
            icon="📺"
            text="MV 外链待补充"
            hint="管理端维护 mv_url / mv_platform 字段即可"
          />
        </div>

        <!-- 同专辑其他曲目 -->
        <div v-if="sameAlbum.length" class="mt-8">
          <h2 class="mb-3 text-lg font-bold">同专辑曲目</h2>
          <ul class="card divide-y divide-line overflow-hidden">
            <li v-for="s in sameAlbum" :key="s.id">
              <RouterLink :to="`/songs/${s.id}`" class="flex items-center justify-between p-3 text-sm hover:bg-base">
                <span class="font-medium">{{ s.title }}</span>
                <span class="tabular text-xs text-faint">{{ fmtDur(s.duration) }}</span>
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
