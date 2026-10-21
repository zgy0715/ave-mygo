<script setup>
import { computed, onMounted, ref, watch } from 'vue'
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
const mvLoaded = ref(false)      // MV 播放器需手动载入：载入会暂停底部播放器，避免双音轨
const mvNotice = ref('')         // 互斥提示（谁让谁静音）
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
// 时长格式化：空/0 显示「—」，避免出现 0:00
const fmtDur = (sec) => (sec ? `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` : '—')

// 乐队徽标：详情里只有 bandName，按前缀归一化（库里 'MyGO!!!!' / 'MyGO!!!!!' 两种写法都有）
const BAND_THEME = {
  mygo: { color: '#2c7da0', soft: '#e3f0f7', slug: 'mygo' },
  'ave-mujica': { color: '#6b4fa3', soft: '#efeaf7', slug: 'ave-mujica' },
  sumimi: { color: '#d4789f', soft: '#fbe9ef', slug: 'sumimi' },
}
const band = computed(() => {
  const n = song.value?.bandName || ''
  if (n.startsWith('MyGO')) return BAND_THEME.mygo
  if (n.startsWith('Ave Mujica')) return BAND_THEME['ave-mujica']
  if (n.toLowerCase().includes('sumimi')) return BAND_THEME.sumimi
  return BAND_THEME.mygo
})

async function load() {
  loading.value = true
  errMsg.value = ''
  sameAlbum.value = []   // 切歌时先清空，避免上一首的「同专辑」残留
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

// 关键：同专辑曲目互跳时组件被复用，onMounted 不会重跑 → 必须监听 id 变化重新加载
watch(() => route.params.id, (id, old) => {
  if (id && id !== old) {
    window.scrollTo(0, 0)
    load()
  }
})

function onPlay() {
  // 听歌不设登录门槛（游客可试听，登录只影响购票/收藏等）
  player.play(song.value)
}

/** 载入 MV：先暂停底部播放器（B 站 iframe 无法被程序暂停，只能让播放器让路） */
function loadMv() {
  if (player.isPlaying) {
    player.toggle()
    mvNotice.value = '已暂停底部播放器，避免与 MV 声音重叠'
  } else {
    mvNotice.value = ''
  }
  mvLoaded.value = true
}

/** 反向互斥：一旦开始播放音频，卸载 MV（否则两个音轨同时响） */
watch(() => player.isPlaying, (playing) => {
  if (playing && mvLoaded.value) {
    mvLoaded.value = false
    mvNotice.value = '已卸载 MV 播放器，避免与音频重叠；想继续看 MV 请重新载入'
  }
})
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
          ▶ {{ song.audioUrl ? '播放试听（30 秒）' : '播放（无试听·静音演示）' }}
        </button>
        <p v-if="song.audioUrl" class="mt-2 text-center text-xs text-faint">
          站内仅提供官方 30 秒试听片段（不托管完整音源）
        </p>
        <p v-else class="mt-2 text-center text-xs text-faint">
          该曲暂无官方试听外链，可观看 MV 或前往官方平台
        </p>

        <!-- 听全曲：有官方外链直达，否则引导到 MV / B 站搜索 -->
        <a v-if="song.streamUrl" :href="song.streamUrl" target="_blank" rel="noopener"
           class="btn-ghost mt-2 w-full !py-1.5 !text-xs">收听完整版（官方平台）↗</a>
        <button v-else-if="mvEmbedUrl" class="btn-ghost mt-2 w-full !py-1.5 !text-xs" @click="loadMv">
          用下方 MV 听全曲 ↓
        </button>
        <a v-else
           :href="`https://search.bilibili.com/all?keyword=${encodeURIComponent(song.title + ' ' + song.bandName)}`"
           target="_blank" rel="noopener" class="btn-ghost mt-2 w-full !py-1.5 !text-xs">
          在 B 站搜索完整版 ↗
        </a>
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

          <!-- 可内嵌（player.html?bvid=…）：手动载入，避免与底部播放器双音轨 -->
          <template v-if="mvEmbedUrl">
            <p v-if="mvNotice" class="mb-2 rounded-md bg-amber-50 px-3 py-1.5 text-xs text-amber-700">
              {{ mvNotice }}
            </p>
            <div v-if="mvLoaded" class="aspect-video overflow-hidden rounded-xl border border-line">
              <iframe :src="mvEmbedUrl" class="h-full w-full" allowfullscreen scrolling="no" />
            </div>
            <button v-else class="card flex w-full items-center gap-4 p-4 text-left transition hover:shadow-md" @click="loadMv">
              <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-white">▶</span>
              <span class="min-w-0 flex-1">
                <span class="block text-sm font-semibold">载入 MV 播放器</span>
                <span class="mt-0.5 block text-xs text-faint">载入时会自动暂停底部播放器，避免两个音轨重叠</span>
              </span>
            </button>
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

          <div v-else class="card p-6 text-center">
            <p class="text-sm text-dim">该曲暂无官方 MV 源</p>
            <p class="mt-1 text-xs text-faint">可先在 B 站搜索完整版／live 版本</p>
            <a
              :href="`https://search.bilibili.com/all?keyword=${encodeURIComponent(song.title + ' ' + song.bandName)}`"
              target="_blank" rel="noopener" class="btn-ghost mt-3 inline-flex !py-1.5 !text-xs"
            >
              在 B 站搜索「{{ song.title }}」↗
            </a>
          </div>
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
