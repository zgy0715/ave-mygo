<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { listEvents } from '@/api/event'
import { listSongs, listBands } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

gsap.registerPlugin(ScrollTrigger)
const player = usePlayerStore()

const events = ref([])
const songs = ref([])
const bands = ref([])

const MARQUEE = ['MYGO!!!!!', 'AVE MUJICA', 'CRYCHIC', 'LIVE HOUSE', '迷星叫', '春日影', '壱雫空', '黒のバースデイ']
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

onMounted(async () => {
  try {
    const [ev, so, bd] = await Promise.all([
      listEvents({ page: 1, pageSize: 4 }),
      listSongs({ page: 1, pageSize: 6 }),
      listBands(),
    ])
    events.value = ev.content
    songs.value = so.content
    bands.value = bd
  } catch { /* 各区块独立降级 */ }

  // ===== GSAP 入场编排 =====
  if (reducedMotion) return

  // Hero：标题逐行上浮 + 元素错落（stagger 200ms 级）
  gsap.from('.hero-line', { yPercent: 120, opacity: 0, duration: 1.1, ease: 'power4.out', stagger: 0.12, delay: 0.15 })
  gsap.from('.hero-fade', { y: 24, opacity: 0, duration: 0.9, ease: 'power3.out', stagger: 0.18, delay: 0.7 })

  // 滚动浮现：所有 .reveal 区块进入视口时上浮
  document.querySelectorAll('.reveal').forEach((el) => {
    gsap.from(el, {
      y: 48, opacity: 0, duration: 1, ease: 'power3.out',
      scrollTrigger: { trigger: el, start: 'top 85%' },
    })
  })

  // Hero 背景光斑缓慢漂移
  gsap.to('.aurora-1', { xPercent: 18, yPercent: 12, duration: 14, repeat: -1, yoyo: true, ease: 'sine.inOut' })
  gsap.to('.aurora-2', { xPercent: -14, yPercent: -16, duration: 18, repeat: -1, yoyo: true, ease: 'sine.inOut' })
})

onBeforeUnmount(() => {
  ScrollTrigger.getAll().forEach((t) => t.kill())
})

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
</script>

<template>
  <main>
    <!-- ===== HERO：光斑 + 巨字逐行入场 ===== -->
    <section class="relative flex h-[78vh] min-h-[460px] flex-col justify-center overflow-hidden border-b border-line">
      <!-- 舞台光斑（aurora）：梅红/鲑鱼粉缓慢漂移 -->
      <div class="pointer-events-none absolute inset-0">
        <div class="aurora-1 absolute -top-32 left-[10%] h-96 w-96 rounded-full bg-plum/40 blur-[120px]"></div>
        <div class="aurora-2 absolute bottom-0 right-[15%] h-80 w-80 rounded-full bg-salmon/20 blur-[110px]"></div>
        <div class="absolute inset-0 opacity-[0.04]" style="background-image: linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px); background-size: 72px 72px;"></div>
      </div>

      <div class="relative mx-auto w-full max-w-6xl px-4">
        <p class="hero-fade mono mb-5 text-xs text-salmon">BANG DREAM! FAN PROJECT — 2026</p>
        <h1 class="overflow-hidden">
          <span class="hero-line font-display block text-[clamp(3rem,10vw,7.5rem)] font-bold leading-[1.02] text-white">迷子でも、</span>
          <span class="hero-line font-display block text-[clamp(3rem,10vw,7.5rem)] font-bold leading-[1.02]">進んでいく <span class="italic grad-plum-text">Live</span></span>
          <span class="hero-line font-display mt-4 block text-[clamp(1.1rem,2.6vw,1.6rem)] text-gray-300">就算迷路，也要前进 —— MyGO!!!!! × Ave Mujica 档案</span>
        </h1>
        <div class="hero-fade mono mt-10 flex flex-wrap gap-6 text-xs">
          <RouterLink to="/songs" class="border-b border-white pb-1 text-white transition hover:border-salmon hover:text-salmon">浏览曲库 SONGS ↓</RouterLink>
          <RouterLink to="/events" class="text-dim transition hover:text-white">购票 LIVE →</RouterLink>
          <RouterLink to="/bands/1" class="text-dim transition hover:text-white">MyGO!!!!! 成员 →</RouterLink>
        </div>
      </div>

      <!-- 跑马灯 -->
      <div class="absolute bottom-0 left-0 w-full overflow-hidden border-t border-line py-2.5">
        <div class="animate-marquee flex w-max gap-8 whitespace-nowrap">
          <span v-for="i in 2" :key="i" class="mono text-xs text-dim">
            <template v-for="w in MARQUEE" :key="w">{{ w }}&nbsp;&nbsp;✳&nbsp;&nbsp;</template>
          </span>
        </div>
      </div>
    </section>

    <!-- ===== 数据条：编辑排版 ===== -->
    <section class="reveal border-b border-line">
      <div class="mx-auto grid max-w-6xl grid-cols-2 divide-x divide-line px-4 md:grid-cols-4">
        <div class="p-6"><p class="tabular text-3xl font-black text-white">02</p><p class="mono mt-1 text-[10px] text-dim">BANDS / 乐队</p></div>
        <div class="p-6"><p class="tabular text-3xl font-black text-white">10</p><p class="mono mt-1 text-[10px] text-dim">MEMBERS / 成员</p></div>
        <div class="p-6"><p class="tabular text-3xl font-black text-white">09</p><p class="mono mt-1 text-[10px] text-dim">SONGS / 收录曲目</p></div>
        <div class="p-6"><p class="tabular text-3xl font-black text-white">01</p><p class="mono mt-1 text-[10px] text-dim">LIVE ON SALE / 售票中</p></div>
      </div>
    </section>

    <!-- ===== 乐队 ===== -->
    <section class="reveal mx-auto max-w-6xl px-4 py-20">
      <div class="section-head">
        <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_01_</span>乐队 <span class="mono ml-2 text-[10px] text-dim">BANDS</span></h2>
      </div>
      <div class="grid gap-4 md:grid-cols-2">
        <RouterLink
          v-for="(b, i) in bands" :key="b.id"
          :to="`/bands/${b.id}`"
          class="group relative flex min-h-[240px] flex-col justify-end overflow-hidden rounded-2xl border border-line p-8 transition"
          :class="i === 0 ? 'bg-gradient-to-br from-sky-950/60 to-surface' : 'bg-gradient-to-br from-red-950/60 to-surface'"
        >
          <div class="absolute -right-10 -top-10 select-none text-[10rem] font-black leading-none opacity-[0.07] transition group-hover:opacity-[0.14]">
            {{ String(i + 1).padStart(2, '0') }}
          </div>
          <p class="mono text-[10px] text-dim">{{ i === 0 ? 'GO !!!! まで、進め。' : 'mask · masquerade' }}</p>
          <h3 class="mt-3 text-4xl font-black text-white transition group-hover:translate-x-1" :class="i === 0 ? 'group-hover:text-sky-300' : 'group-hover:text-accent'">{{ b.name }}</h3>
          <p class="mt-3 max-w-sm text-sm leading-6 text-gray-500">{{ b.description }}</p>
          <span class="mono mt-4 text-xs text-dim transition group-hover:text-white">MEMBERS & DISCOGRAPHY →</span>
        </RouterLink>
      </div>
    </section>

    <!-- ===== 演出时间线 ===== -->
    <section class="reveal border-y border-line bg-surface/40 py-20">
      <div class="mx-auto max-w-6xl px-4">
        <div class="section-head">
          <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_02_</span>演出预告 <span class="mono ml-2 text-[10px] text-dim">LIVE SCHEDULE</span></h2>
          <RouterLink to="/events" class="mono text-xs text-dim hover:text-white">ALL →</RouterLink>
        </div>
        <div v-for="e in events" :key="e.id">
          <RouterLink :to="`/events/${e.id}`" class="group flex flex-wrap items-baseline justify-between gap-2 border-b border-line py-5 transition hover:bg-elevated/50">
            <div class="flex flex-wrap items-baseline gap-4">
              <span class="tabular mono text-xs text-primary">{{ fmt(e.nextSessionTime) }}</span>
              <span class="text-lg font-bold text-white group-hover:translate-x-1 group-hover:text-primary transition">{{ e.title }}</span>
              <span class="text-xs text-accent"><span v-if="e.minPrice != null" class="tabular">¥{{ e.minPrice }} 起</span></span>
            </div>
            <span class="mono text-xs text-dim">{{ e.venue }}</span>
          </RouterLink>
        </div>
        <p v-if="events.length === 0" class="text-sm text-dim">暂无演出预告</p>
      </div>
    </section>

    <!-- ===== 歌曲 ===== -->
    <section class="reveal mx-auto max-w-6xl px-4 py-20">
      <div class="section-head">
        <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_03_</span>收录歌曲 <span class="mono ml-2 text-[10px] text-dim">SONGS</span></h2>
        <RouterLink to="/songs" class="mono text-xs text-dim hover:text-white">ALL →</RouterLink>
      </div>
      <div class="grid gap-2">
        <div
          v-for="(s, i) in songs" :key="s.id"
          class="group flex cursor-pointer items-center gap-4 border-b border-line px-2 py-3.5 transition hover:bg-elevated/60"
          @click="player.play(s)"
        >
          <span class="mono w-8 text-xs text-dim">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="flex-1 truncate text-sm text-gray-200 group-hover:text-white">{{ s.title }}</span>
          <span class="hidden text-xs text-dim md:inline">{{ s.bandName }}</span>
          <span class="mono w-20 text-right text-xs text-primary">{{ player.current?.id === s.id && player.isPlaying ? '▶ NOW PLAYING' : '▶' }}</span>
        </div>
      </div>
    </section>
  </main>
</template>
