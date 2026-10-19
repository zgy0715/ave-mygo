<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import EventCard from '@/components/EventCard.vue'
import SongCard from '@/components/SongCard.vue'
import { listEvents } from '@/api/event'
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

  // 成员总数：两支乐队详情各取一次（固定 2 次，不随数据量增长）
  try {
    const details = await Promise.all(bands.value.map((b) => import('@/api/song').then((m) => m.getBand(b.id))))
    memberCount.value = details.reduce((n, d) => n + d.members.length, 0)
  } catch { /* 降级：不显示统计 */ }
})

const onSale = computed(() => events.value.filter((e) => e.status === 'PUBLISHED'))
const featured = computed(() => onSale.value[0] || events.value[0] || null)
const upcoming = computed(() => events.value.filter((e) => e.status !== 'ENDED').slice(0, 3))
const marqueeItems = ['MYGO!!!!!', 'Ave Mujica', 'LIVE HOUSE', 'prima aurora', '迷子でもいい、迷子でも進む', 'BANDORI / LIVE']

/** 乐队卡 logo 加载失败 → 回退文字标题 */
const logoErr = reactive({})

/** 首页 Hero 双 MV：可切换；当前支失效自动跳另一支；全失效回退 aurora */
const HERO_VIDEOS = [
  { id: 'mygo', label: '壱雫空 MV', src: '/assets/band-mygo.mp4' },
  { id: 'mujica', label: 'KiLLKiSS MV', src: '/assets/band-mujica.mp4' },
]
const activeVideoId = ref('mygo')
const videoErr = reactive({})
const activeVideo = computed(() => HERO_VIDEOS.find((v) => v.id === activeVideoId.value))

function onVideoError() {
  videoErr[activeVideoId.value] = true
  const other = HERO_VIDEOS.find((v) => v.id !== activeVideoId.value && !videoErr[v.id])
  if (other) activeVideoId.value = other.id
}

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
  <!-- Hero：满屏视频 + 中央字标（anontokyo 构图，保留） -->
  <section class="relative flex h-[100svh] min-h-[560px] flex-col overflow-hidden">
    <!-- Hero 双 MV 背景：chips 切换，失效自动跳另一支，全失效回退 aurora -->
    <video
      v-if="activeVideo && !videoErr[activeVideo.id]"
      :key="activeVideo.id"
      class="animate-kenburns absolute inset-0 h-full w-full object-cover"
      :src="activeVideo.src"
      autoplay muted loop playsinline
      @error="onVideoError"
    />
    <div v-else class="absolute inset-0 overflow-hidden bg-base" aria-hidden="true">
      <div class="animate-drift absolute -left-1/4 top-[-25%] h-[85vmax] w-[85vmax] rounded-full opacity-25 blur-3xl" style="background: radial-gradient(circle, #7bd, transparent 62%)" />
      <div class="animate-drift-slow absolute -right-1/4 bottom-[-30%] h-[75vmax] w-[75vmax] rounded-full opacity-20 blur-3xl" style="background: radial-gradient(circle, #f89, transparent 62%)" />
    </div>

    <div class="absolute inset-0 bg-gradient-to-b from-base/70 via-base/45 to-base" />

    <p class="vertical-rl mono absolute right-5 top-1/2 hidden -translate-y-1/2 text-[10px] tracking-[0.35em] text-faint lg:block" aria-hidden="true">
      迷子でもいい、迷子でも進む — MYGO!!!!! ✳ AVE MUJICA
    </p>

    <div class="relative flex flex-1 flex-col items-center justify-center px-4 text-center">
      <p class="micro-accent mb-4" v-reveal>BAN!DREAM! FAN PROJECT — 2026</p>
      <h1 class="display text-[15vw] leading-[0.95] text-ink sm:text-[8.5rem]" v-reveal="120">
        BANDORI<span class="mx-1 align-top text-[0.4em] text-primary">✳</span>LIVE
      </h1>
      <p class="mono mt-4 text-sm tracking-[0.45em] text-dim" v-reveal="240">MYGO!!!!! ✳ AVE MUJICA</p>
    </div>

    <!-- MV 切换 chips：右下、情报带上方 -->
    <div class="absolute bottom-24 right-6 z-10 flex gap-2 sm:bottom-28 sm:right-10">
      <button
        v-for="v in HERO_VIDEOS"
        :key="v.id"
        class="rounded-full border px-3 py-1.5 text-xs font-medium backdrop-blur transition-colors duration-300"
        :class="activeVideoId === v.id && !videoErr[v.id]
          ? 'border-primary bg-primary text-white'
          : videoErr[v.id]
            ? 'border-line bg-surface/70 text-faint line-through'
            : 'border-line bg-surface/80 text-dim hover:border-primary hover:text-primary'"
        :disabled="videoErr[v.id]"
        @click="activeVideoId = v.id"
      >
        {{ v.label }}
      </button>
    </div>

    <div class="relative flex items-end justify-between gap-6 px-6 pb-7 sm:px-10">
      <p class="micro hidden sm:block">FAN PROJECT ✳ NOT OFFICIAL</p>
      <p class="micro animate-scroll-hint absolute left-1/2 hidden -translate-x-1/2 sm:block">SCROLL DOWN ↓</p>

      <RouterLink v-if="featured" :to="`/events/${featured.id}`" class="group ml-auto text-right">
        <p class="mono text-sm font-medium text-primary tabular">{{ shortDate }} @{{ featured.venue }}</p>
        <p class="mt-1 text-sm font-bold sm:text-base">{{ featured.title }}</p>
        <p class="micro mt-1.5 transition-colors duration-300 group-hover:!text-primary">
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

  <!-- 02 新曲速递 -->
  <section class="wrap pb-14">
    <div class="section-head" v-reveal>
      <div>
        <p class="micro-accent mb-1.5">02 — DISCOGRAPHY</p>
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
        <p class="micro-accent mb-1.5">03 — BANDS</p>
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
