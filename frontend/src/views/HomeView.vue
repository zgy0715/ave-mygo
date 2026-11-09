<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import EventCard from '@/components/EventCard.vue'
import PastEventCard from '@/components/PastEventCard.vue'
import SongCard from '@/components/SongCard.vue'
import { listEvents, getEvent } from '@/api/event'
import { listSongs, listBands } from '@/api/song'

/**
 * 首页 v4.1：满屏视频 Hero（保留）+ 数据区全部接真接口。
 * featured = 最早一场在售演出；数据失败时各区块独立降级。
 */

const events = ref([])
const newSongs = ref([])
const bands = ref([])
const memberCount = ref(0)

onMounted(async () => {
  try {
    const [ev, so, bd] = await Promise.all([
      listEvents({ page: 1, pageSize: 6 }),
      listSongs({ page: 1, pageSize: 8, sort: 'release_desc' }),
      listBands(),
    ])
    events.value = ev.content
    newSongs.value = so.content
    bands.value = bd
  } catch { /* 各区块独立降级 */ }

  // 往期回顾：独立请求，失败也只影响本区块
  void loadPast()

  // 成员总数：两支乐队详情各取一次（固定 2 次，不随数据量增长）
  try {
    const details = await Promise.all(bands.value.map((b) => import('@/api/song').then((m) => m.getBand(b.id))))
    memberCount.value = details.reduce((n, d) => n + d.members.length, 0)
  } catch { /* 降级：不显示统计 */ }
})

const onSale = computed(() => events.value.filter((e) => e.status === 'PUBLISHED'))
// 只把「售票中」的演出放进 Hero 购票位；没有在售就不显示（避免给已结束演出挂「立即购票」）
const featured = computed(() => onSale.value[0] || null)
const upcoming = computed(() => events.value.filter((e) => e.status !== 'ENDED').slice(0, 3))

/* ---- 往期回顾：独立请求取 ENDED，按「最后一场时间」倒序取 4 场（docs/16） ---- */
const past = ref([])
const pastLoaded = ref(false)
async function loadPast() {
  try {
    const resp = await listEvents({ status: 'ENDED', pageSize: 12 })
    const list = resp.content || []
    const withDate = await Promise.all(
      list.map(async (ev) => {
        try {
          const d = await getEvent(ev.id)
          const times = (d.sessions || []).map((s) => s.startTime).filter(Boolean).sort()
          return { ...ev, lastSessionAt: times.length ? times[times.length - 1] : null }
        } catch {
          return { ...ev, lastSessionAt: null }   // 详情取不到也照常渲染，只是不显示日期
        }
      }),
    )
    past.value = withDate
      .sort((a, b) => String(b.lastSessionAt || '').localeCompare(String(a.lastSessionAt || '')))
      .slice(0, 4)
  } catch {
    past.value = []   // 静默失败：整块不渲染
  } finally {
    pastLoaded.value = true
  }
}

const marqueeItems = ['MYGO!!!!!', 'Ave Mujica', 'LIVE HOUSE', 'prima aurora', '迷子でもいい、迷子でも進む', 'BANG DREAM! / LIVE']

/** 乐队卡 logo 加载失败 → 回退文字标题 */
const logoErr = reactive({})

/** 首页 Hero 双 MV：可切换；当前支失效自动跳另一支；全失效回退 aurora */
const HERO_VIDEOS = [
  { id: 'mygo', label: '壱雫空 MV', src: '/assets/band-mygo-720p.mp4', poster: '/assets/band-mygo-poster.jpg' },
  { id: 'mujica', label: 'KiLLKiSS MV', src: '/assets/band-mujica-720p.mp4', poster: '/assets/band-mujica-poster.jpg' },
]
const activeVideoId = ref('mygo')
const videoErr = reactive({})
const posterErr = reactive({})
const videoReady = reactive({})   // canplay 之后才允许视频显形，避免黑帧/灰框
const activeVideo = computed(() => HERO_VIDEOS.find((v) => v.id === activeVideoId.value))

/**
 * 移动端策略（2026-11-09）：不自动下载 8.4MB 视频。
 * 线上带宽约 68KB/s，自动播放等于让用户对着灰屏等两分钟——
 * 改为「先出 poster 首帧，点 MV 名才加载播放」。
 */
const isMobile = ref(false)
const videoEl = ref(null)
onMounted(() => {
  // jsdom / 老浏览器没有 matchMedia：缺失时按桌面处理（自动播放），不影响主流程
  isMobile.value = window.matchMedia?.('(max-width: 767px)')?.matches ?? false
})

function onVideoError() {
  videoErr[activeVideoId.value] = true
  videoReady[activeVideoId.value] = false
  const other = HERO_VIDEOS.find((v) => v.id !== activeVideoId.value && !videoErr[v.id])
  if (other) activeVideoId.value = other.id
}

/** 点 MV 名：切歌；移动端额外触发加载并播放（需用户手势，符合浏览器自动播放策略） */
async function pickVideo(id) {
  activeVideoId.value = id
  if (!isMobile.value || videoErr[id]) return
  await nextTick()
  const el = videoEl.value
  if (!el) return
  el.load()
  try {
    await el.play()
  } catch {
    // 被浏览器拦下就留在 poster，用户再点一次即可
  }
}

/** 有画面可看（视频未失败，或海报还在）→ 白字透画面；两者都失败才回 aurora 墨字 */
const videoActive = computed(() => {
  const v = activeVideo.value
  if (!v) return false
  return !videoErr[v.id] || !posterErr[v.id]
})

/** 视频与海报都不可用才显示 aurora 兜底 */
const showAurora = computed(() => {
  const v = activeVideo.value
  if (!v) return true
  return !!videoErr[v.id] && !!posterErr[v.id]
})

/** 「11.01 Sun.」式日期：官网情报区同款格式 */
const shortDate = computed(() => {
  if (!featured.value?.nextSessionTime) return ''
  const d = new Date(featured.value.nextSessionTime)
  const wd = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getDay()]
  return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${wd}.`
})

const gradientOf = (slug) =>
  ({
    'ave-mujica': 'linear-gradient(135deg,#6b4fa3,#4a3573)',
    sumimi: 'linear-gradient(135deg, #d4789f 0%, #b95 100%)',
  }[slug] || 'linear-gradient(135deg,#2c7da0,#1f6485)')
</script>

<template>
  <!-- Hero：满屏视频 + 中央字标（anontokyo 构图，保留）；高度扣掉 sticky 顶栏 4rem，否则底部信息带被推出视口 -->
  <section class="relative flex h-[calc(100svh-4rem)] min-h-[560px] flex-col overflow-hidden">
    <!-- 兜底层：仅当视频与海报都不可用（极弱网 / 文件缺失）才出现 -->
    <div v-if="showAurora" class="absolute inset-0 overflow-hidden bg-base" aria-hidden="true">
      <div class="animate-drift absolute -left-1/4 top-[-25%] h-[85vmax] w-[85vmax] rounded-full opacity-25 blur-3xl" style="background: radial-gradient(circle, #7bd, transparent 62%)" />
      <div class="animate-drift-slow absolute -right-1/4 bottom-[-30%] h-[75vmax] w-[75vmax] rounded-full opacity-20 blur-3xl" style="background: radial-gradient(circle, #f89, transparent 62%)" />
    </div>

    <!-- 海报层：视频就绪前一直垫在下面。原来视频没加载完就是一片灰，手机端尤其明显 -->
    <img
      v-if="activeVideo && !posterErr[activeVideo.id]"
      :key="activeVideo.poster"
      :src="activeVideo.poster"
      alt=""
      class="absolute inset-0 h-full w-full object-cover"
      @error="posterErr[activeVideo.id] = true"
    />

    <!-- 视频层：桌面自动播放；移动端 preload=none，点 MV 名才加载（省 8.4MB 流量与等待） -->
    <video
      v-if="activeVideo && !videoErr[activeVideo.id]"
      :key="activeVideo.id"
      ref="videoEl"
      class="animate-kenburns absolute inset-0 h-full w-full object-cover transition-opacity duration-700"
      :class="videoReady[activeVideo.id] ? 'opacity-100' : 'opacity-0'"
      :src="activeVideo.src"
      :poster="activeVideo.poster"
      :autoplay="!isMobile"
      :preload="isMobile ? 'none' : 'metadata'"
      muted
      loop
      playsinline
      @canplay="videoReady[activeVideoId] = true"
      @error="onVideoError"
    />

    <!-- 雾面：视频模式暗调压底（白字透视频），aurora 模式浅雾保黑字 -->
    <div
      class="absolute inset-0"
      :class="videoActive
        ? 'bg-gradient-to-b from-black/40 via-black/25 to-base'
        : 'bg-gradient-to-b from-base/70 via-base/45 to-base'"
    />

    <p
      class="vertical-rl mono absolute right-5 top-1/2 hidden -translate-y-1/2 text-[10px] tracking-[0.35em] lg:block"
      :class="videoActive ? 'text-white/50' : 'text-faint'"
      aria-hidden="true"
    >
      迷子でもいい、迷子でも進む — MYGO!!!!! ✳ AVE MUJICA
    </p>

    <div class="relative flex flex-1 flex-col items-center justify-center px-4 text-center">
      <p class="micro-accent mb-4" :class="videoActive && '!text-white/75'" v-reveal>BAN!DREAM! FAN PROJECT — 2026</p>
      <h1
        class="display text-[13vw] leading-[0.95] sm:text-[8.5rem]"
        :class="videoActive
          ? 'text-white/90 [text-shadow:0_2px_40px_rgba(0,0,0,.35)]'
          : 'text-ink'"
        v-reveal="120"
      >
        BANG DREAM!<span class="mx-1 align-top text-[0.4em]" :class="videoActive ? 'text-white/80' : 'text-primary'">✳</span>LIVE
      </h1>
      <p class="mono mt-4 text-sm tracking-[0.45em] v-reveal" :class="videoActive ? 'text-white/75' : 'text-dim'" v-reveal="240">
        MYGO!!!!! ✳ AVE MUJICA
      </p>
    </div>

    <!-- MV 切换 chips：桌面右下情报带上方；窄屏移到右上（避开底部演出信息与右下 AI 悬浮球），点了才加载视频 -->
    <div class="absolute right-4 top-20 z-10 flex gap-2 sm:bottom-40 sm:right-10 sm:top-auto">
      <button
        v-for="v in HERO_VIDEOS"
        :key="v.id"
        class="rounded-full border px-2.5 py-1 text-[11px] font-medium backdrop-blur transition-colors duration-300 sm:px-3 sm:py-1.5 sm:text-xs"
        :class="activeVideoId === v.id && !videoErr[v.id]
          ? 'border-primary bg-primary text-white'
          : videoErr[v.id]
            ? 'border-line bg-surface/70 text-faint line-through'
            : 'border-line bg-surface/80 text-dim hover:border-primary hover:text-primary'"
        :disabled="videoErr[v.id]"
        @click="pickVideo(v.id)"
      >
        {{ v.label }}<span v-if="isMobile && !videoReady[v.id]" class="ml-1">▶</span>
      </button>
    </div>

    <div class="relative flex items-end justify-between gap-6 pb-7 pl-6 pr-[5.5rem] sm:pl-10 sm:pr-24">
      <p class="micro hidden sm:block">FAN PROJECT ✳ NOT OFFICIAL</p>
      <p class="micro animate-scroll-hint absolute left-1/2 hidden -translate-x-1/2 sm:block">SCROLL DOWN ↓</p>

      <RouterLink v-if="featured" :to="`/events/${featured.id}`" class="group ml-auto max-w-[88%] pr-16 text-right sm:max-w-none sm:pr-0">
        <p class="mono truncate text-sm font-medium text-primary tabular">{{ shortDate }} @{{ featured.venue }}</p>
        <p class="mt-1 line-clamp-2 text-sm font-bold sm:line-clamp-none sm:text-[16px]">{{ featured.title }}</p>
        <p class="micro mt-1.5 hidden transition-colors duration-300 group-hover:!text-primary sm:block">
          TICKET ON SALE — 立即购票 ↗
        </p>
      </RouterLink>
    </div>
  </section>

  <!-- 跑马灯 -->
  <div class="overflow-hidden border-b border-line bg-surface py-3" aria-hidden="true">
    <div class="animate-marquee flex w-max whitespace-nowrap">
      <span v-for="(item, i) in [...marqueeItems, ...marqueeItems]" :key="i" class="micro mx-6 !text-[11px] !text-faint">
        {{ item }} <span class="ml-12 text-primary">✳</span>
      </span>
    </div>
  </div>

  <!-- 01 近期演出 -->
  <section class="wrap py-14">
    <div class="section-head" v-reveal>
      <div>
        <p class="micro-accent mb-1.5">01 — SCHEDULE</p>
        <h2 class="serif text-2xl font-semibold">近期演出</h2>
      </div>
      <RouterLink to="/events" class="micro py-1 transition hover:text-primary">全部 →</RouterLink>
    </div>
    <div v-if="upcoming.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <EventCard v-for="(e, i) in upcoming" :key="e.id" :event="e" v-reveal="i * 90" />
    </div>
    <p v-else class="py-6 text-sm text-faint">演出日程整理中，敬请期待。</p>
  </section>

  <!-- 02 往期回顾 -->
  <section v-if="past.length" class="wrap py-14">
    <div class="section-head" v-reveal>
      <div>
        <p class="micro-accent mb-1.5">02 — PAST LIVES</p>
        <h2 class="serif text-2xl font-semibold">往期回顾</h2>
      </div>
      <RouterLink to="/events" class="micro py-1 transition hover:text-primary">全部 →</RouterLink>
    </div>
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <PastEventCard v-for="(e, i) in past" :key="e.id" :event="e" v-reveal="(i % 4) * 90" />
    </div>
  </section>


  <!-- 02 新曲速递 -->
  <section class="wrap pb-14">
    <div class="section-head" v-reveal>
      <div>
        <p class="micro-accent mb-1.5">03 — DISCOGRAPHY</p>
        <h2 class="serif text-2xl font-semibold">新曲速递</h2>
      </div>
      <RouterLink to="/songs" class="micro py-1 transition hover:text-primary">歌曲库 →</RouterLink>
    </div>
    <div v-if="newSongs.length" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <SongCard v-for="(s, i) in newSongs" :key="s.id" :song="s" v-reveal="(i % 4) * 70" />
    </div>
    <p v-else class="py-6 text-sm text-faint">曲库整理中。</p>
  </section>

  <!-- 03 乐队 -->
  <section class="wrap pb-20">
    <div class="section-head" v-reveal>
      <div>
        <p class="micro-accent mb-1.5">04 — BANDS</p>
        <h2 class="serif text-2xl font-semibold">乐队</h2>
      </div>
      <RouterLink to="/bands" class="micro py-1 transition hover:text-primary">全部 →</RouterLink>
    </div>
    <div class="grid gap-4 sm:grid-cols-2">
      <RouterLink
        v-for="(b, i) in bands"
        :key="b.id"
        :to="`/bands/${b.id}`"
        class="card group overflow-hidden"
        v-reveal="i * 120"
      >
        <!-- 官方 logo 叠加渐变底（加载失败回退文字） -->
        <div class="shine relative h-32 overflow-hidden" :style="{ background: gradientOf(b.slug) }">
          <img
            v-if="!logoErr[b.id]"
            :src="`/img/logo/${b.slug}.png`"
            :alt="b.name"
            class="zoomable absolute left-1/2 top-1/2 h-14 w-auto max-w-[80%] -translate-x-1/2 -translate-y-1/2 drop-shadow-[0_2px_10px_rgba(0,0,0,.45)]"
            loading="lazy"
            @error="logoErr[b.id] = true"
          >
          <p v-if="logoErr[b.id]" class="display absolute bottom-5 left-5 text-3xl tracking-wide text-white" style="text-shadow: 0 1px 12px rgba(0,0,0,.5)">{{ b.name }}</p>
        </div>
        <div class="flex items-center justify-between p-4">
          <p class="text-sm text-dim">{{ b.description?.slice(0, 24) }}…</p>
          <span class="text-primary transition-transform duration-300 group-hover:translate-x-1.5" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">→</span>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
