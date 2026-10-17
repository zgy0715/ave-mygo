<script setup>
import { onMounted, ref } from 'vue'
import gsap from 'gsap'
import { listEvents } from '@/api/event'
import { listSongs, listBands } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

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

  if (reducedMotion) return

  // Hero 逐行入场（白屏期间黑字浮现，2.6s 后随背景反转）
  gsap.from('.hero-line', { yPercent: 120, opacity: 0, duration: 1.1, ease: 'power4.out', stagger: 0.12, delay: 0.15 })
  gsap.from('.hero-fade', { y: 24, opacity: 0, duration: 0.9, ease: 'power3.out', stagger: 0.18, delay: 0.7 })

  // 滚动浮现
  document.querySelectorAll('.reveal').forEach((el) => {
    gsap.from(el, { y: 48, opacity: 0, duration: 1, ease: 'power3.out', scrollTrigger: { trigger: el, start: 'top 85%' } })
  })
})

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
</script>

<template>
  <main>
    <!-- ===== HERO：白屏开场 → 蓝粉 aurora → 渐变入黑 ===== -->
    <section class="hero-splash relative flex h-[80vh] min-h-[480px] flex-col justify-center overflow-hidden">
      <!-- 蓝粉光斑（白底时是柔和粉蓝，入黑后变暗夜光斑） -->
      <div class="aurora aurora-1"></div>
      <div class="aurora aurora-2"></div>

      <div class="relative z-10 mx-auto w-full max-w-6xl px-4">
        <p class="hero-fade tag-mono mb-5 !text-[#0a0a0a99] transition-colors duration-1000 delay-[2600ms] group-hover:text-current">
          BANG DREAM! FAN PROJECT — 2026
        </p>
        <h1 class="overflow-hidden">
          <span class="hero-line splash-ink block text-[clamp(3rem,11vw,8.5rem)] font-bold leading-[0.98] tracking-tight">BANDORI<span class="text-primary">/</span>LIVE</span>
          <span class="hero-line splash-ink-soft mt-3 block text-[clamp(1.1rem,2.6vw,1.7rem)]">迷子でも、進んでいく —— 就算迷路，也要前进</span>
        </h1>
        <div class="hero-fade mono mt-10 flex flex-wrap gap-6 text-xs">
          <RouterLink to="/songs" class="splash-ink border-b border-current pb-1 transition hover:text-primary">浏览曲库 SONGS ↓</RouterLink>
          <RouterLink to="/events" class="splash-ink-soft transition hover:text-primary">购票 LIVE →</RouterLink>
          <RouterLink to="/bands/1" class="splash-ink-soft transition hover:text-primary">MyGO!!!!! 成员 →</RouterLink>
        </div>
        <!-- 渐变文字 scroll cue -->
        <p class="splash-ink-soft mono mt-16 text-[10px] tracking-[0.3em]">
          <span class="grad-text font-bold">SCROLL DOWN ↓ 往下滚动</span>
        </p>
      </div>

      <!-- 跑马灯 -->
      <div class="absolute bottom-0 left-0 w-full overflow-hidden border-t border-line-strong bg-base/60 py-2.5 backdrop-blur">
        <div class="animate-marquee flex w-max gap-8 whitespace-nowrap">
          <span v-for="i in 2" :key="i" class="mono text-xs text-dim">
            <template v-for="w in MARQUEE" :key="w">{{ w }}&nbsp;&nbsp;✳&nbsp;&nbsp;</template>
          </span>
        </div>
      </div>
    </section>

    <!-- ===== 数据条 ===== -->
    <section class="reveal border-b border-line">
      <div class="mx-auto grid max-w-6xl grid-cols-2 divide-x divide-line px-4 md:grid-cols-4">
        <div class="p-6"><p class="tabular text-3xl font-bold text-white">02</p><p class="tag-mono mt-1">BANDS / 乐队</p></div>
        <div class="p-6"><p class="tabular text-3xl font-bold text-white">10</p><p class="tag-mono mt-1">MEMBERS / 成员</p></div>
        <div class="p-6"><p class="tabular text-3xl font-bold text-white">09</p><p class="tag-mono mt-1">SONGS / 收录曲目</p></div>
        <div class="p-6"><p class="tabular text-3xl font-bold text-white">01</p><p class="tag-mono mt-1">ON SALE / 售票中</p></div>
      </div>
    </section>

    <!-- ===== 乐队 ===== -->
    <section class="reveal mx-auto max-w-6xl px-4 py-20">
      <div class="section-head">
        <h2 class="text-lg font-bold"><span class="mono mr-2 text-xs text-primary">_01_</span>乐队 <span class="tag-mono ml-2">BANDS</span></h2>
      </div>
      <div class="grid gap-4 md:grid-cols-2">
        <RouterLink
          v-for="(b, i) in bands" :key="b.id"
          :to="`/bands/${b.id}`"
          class="group relative flex min-h-[250px] flex-col justify-end overflow-hidden rounded-sm border border-line-strong p-8 transition hover:bg-elevated"
        >
          <div class="absolute -right-12 -top-14 select-none text-[11rem] font-bold leading-none text-white/5 transition group-hover:text-white/10">
            {{ String(i + 1).padStart(2, '0') }}
          </div>
          <p class="tag-mono">{{ i === 0 ? 'BAND / 01 — GO !!!! まで、進め。' : 'BAND / 02 — mask · masquerade' }}</p>
          <h3 class="mt-4 text-4xl font-bold text-white transition group-hover:translate-x-1" :class="i === 0 ? 'group-hover:text-steel' : 'group-hover:text-pink'">{{ b.name }}</h3>
          <p class="mt-3 max-w-sm text-sm leading-6 text-dim">{{ b.description }}</p>
          <span class="tag-mono mt-5 border-t border-line-strong pt-4 transition group-hover:text-white">MEMBERS & DISCOGRAPHY →</span>
        </RouterLink>
      </div>
    </section>

    <!-- ===== 演出时间线 ===== -->
    <section class="reveal border-y border-line py-20">
      <div class="mx-auto max-w-6xl px-4">
        <div class="section-head">
          <h2 class="text-lg font-bold"><span class="mono mr-2 text-xs text-primary">_02_</span>演出预告 <span class="tag-mono ml-2">LIVE SCHEDULE</span></h2>
          <RouterLink to="/events" class="tag-mono hover:text-white">ALL →</RouterLink>
        </div>
        <div v-for="e in events" :key="e.id">
          <RouterLink :to="`/events/${e.id}`" class="group flex flex-wrap items-baseline justify-between gap-2 border-b border-line py-5 transition hover:bg-elevated/60">
            <div class="flex flex-wrap items-baseline gap-4">
              <span class="tabular mono text-xs text-blue-light">{{ fmt(e.nextSessionTime) }}</span>
              <span class="text-lg font-bold text-white transition group-hover:translate-x-1 group-hover:text-blue-light">{{ e.title }}</span>
              <span class="text-xs text-pink"><span v-if="e.minPrice != null" class="tabular">¥{{ e.minPrice }} 起</span></span>
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
        <h2 class="text-lg font-bold"><span class="mono mr-2 text-xs text-primary">_03_</span>收录歌曲 <span class="tag-mono ml-2">SONGS</span></h2>
        <RouterLink to="/songs" class="tag-mono hover:text-white">ALL →</RouterLink>
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
          <span class="mono w-20 text-right text-xs text-pink">{{ player.current?.id === s.id && player.isPlaying ? '▶ NOW PLAYING' : '▶' }}</span>
        </div>
      </div>
    </section>
  </main>
</template>
