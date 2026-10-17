<script setup>
import { onMounted, ref } from 'vue'
import { listEvents } from '@/api/event'
import { listSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

const player = usePlayerStore()
const events = ref([])
const songs = ref([])

const MARQUEE = ['MYGO!!!!!', 'AVE MUJICA', 'CRYCHIC', 'LIVE HOUSE', '迷星叫', '春日影', '壱雫空', '黒のバースデイ']

onMounted(async () => {
  try {
    const [ev, so] = await Promise.all([listEvents({ page: 1, pageSize: 4 }), listSongs({ page: 1, pageSize: 8 })])
    events.value = ev.content
    songs.value = so.content
  } catch { /* 静默：首页各区块独立降级 */ }
})

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
</script>

<template>
  <main>
    <!-- ===== HERO：全屏大字排印 ===== -->
    <section class="relative flex h-[70vh] min-h-[420px] flex-col justify-center overflow-hidden border-b border-line">
      <!-- 背景网格装饰 -->
      <div class="absolute inset-0 opacity-[0.06]" style="background-image: linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px); background-size: 64px 64px;"></div>
      <div class="relative mx-auto w-full max-w-6xl px-4">
        <p class="mono mb-4 text-xs text-primary">BANG DREAM! FAN PROJECT — 2026</p>
        <h1 class="hero-type">BANDORI<br /><span class="text-primary">/</span>LIVE</h1>
        <p class="mt-6 max-w-md text-sm leading-6 text-gray-400">
          失物乐团与面具人偶的舞台档案。歌曲、成员、演出与门票 ——<br />
          迷茫也没关系，向前走就好。
        </p>
        <div class="mono mt-8 flex gap-6 text-xs">
          <RouterLink to="/songs" class="border-b border-white pb-1 text-white hover:border-primary hover:text-primary">浏览曲库 ↓</RouterLink>
          <RouterLink to="/events" class="text-dim hover:text-white">购票 LIVE →</RouterLink>
        </div>
      </div>
      <!-- 底部跑马灯 -->
      <div class="absolute bottom-0 left-0 w-full overflow-hidden border-t border-line py-2">
        <div class="animate-marquee flex w-max gap-8 whitespace-nowrap">
          <span v-for="i in 2" :key="i" class="mono text-xs text-dim">
            <template v-for="w in MARQUEE" :key="w">{{ w }}&nbsp;&nbsp;✳&nbsp;&nbsp;</template>
          </span>
        </div>
      </div>
    </section>

    <!-- ===== _01_ 乐队 ===== -->
    <section class="mx-auto max-w-6xl px-4 py-16">
      <div class="section-head">
        <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_01_</span>乐队 <span class="mono ml-2 text-[10px] text-dim">BANDS</span></h2>
      </div>
      <div class="grid gap-4 md:grid-cols-2">
        <RouterLink to="/bands/1" class="group relative overflow-hidden rounded-xl border border-line p-8 transition hover:border-primary/60">
          <p class="mono text-[10px] text-dim">BAND / 01 — GO !!!! まで、進め。</p>
          <h3 class="mt-3 text-3xl font-black text-white group-hover:text-primary">MyGO!!!!!</h3>
          <p class="mt-3 text-sm leading-6 text-gray-500">失落与寻找的乐团。五个人，五份迷茫，在舞台上第一次坦诚相对。</p>
          <span class="mono absolute bottom-6 right-6 text-xs text-dim transition group-hover:text-primary">MEMBERS →</span>
        </RouterLink>
        <RouterLink to="/bands/2" class="group relative overflow-hidden rounded-xl border border-line p-8 transition hover:border-accent/60">
          <p class="mono text-[10px] text-dim">BAND / 02 — mask · masquerade</p>
          <h3 class="mt-3 text-3xl font-black text-white group-hover:text-accent">Ave Mujica</h3>
          <p class="mt-3 text-sm leading-6 text-gray-500">面具下的人偶剧团。完美的演出背后，是被拆散又重组的命运。</p>
          <span class="mono absolute bottom-6 right-6 text-xs text-dim transition group-hover:text-accent">MEMBERS →</span>
        </RouterLink>
      </div>
    </section>

    <!-- ===== _02_ 演出预告 ===== -->
    <section class="border-y border-line bg-surface/50 py-16">
      <div class="mx-auto max-w-6xl px-4">
        <div class="section-head">
          <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_02_</span>演出预告 <span class="mono ml-2 text-[10px] text-dim">LIVE SCHEDULE</span></h2>
          <RouterLink to="/events" class="mono text-xs text-dim hover:text-white">ALL →</RouterLink>
        </div>
        <div v-for="e in events" :key="e.id">
          <RouterLink :to="`/events/${e.id}`" class="group flex items-baseline justify-between border-b border-line py-4 transition hover:bg-elevated/50">
            <div class="flex items-baseline gap-4">
              <span class="tabular mono text-xs text-dim">{{ fmt(e.nextSessionTime) }}</span>
              <span class="font-semibold text-white group-hover:text-primary">{{ e.title }}</span>
            </div>
            <span class="mono text-xs text-dim">{{ e.venue }}</span>
          </RouterLink>
        </div>
        <p v-if="events.length === 0" class="text-sm text-dim">暂无演出预告</p>
      </div>
    </section>

    <!-- ===== _03_ 收录歌曲 ===== -->
    <section class="mx-auto max-w-6xl px-4 py-16">
      <div class="section-head">
        <h2 class="text-lg font-bold text-white"><span class="mono mr-2 text-xs text-primary">_03_</span>收录歌曲 <span class="mono ml-2 text-[10px] text-dim">SONGS</span></h2>
        <RouterLink to="/songs" class="mono text-xs text-dim hover:text-white">ALL →</RouterLink>
      </div>
      <div class="grid gap-2">
        <div
          v-for="(s, i) in songs" :key="s.id"
          class="group flex cursor-pointer items-center gap-4 border-b border-line px-2 py-3 transition hover:bg-elevated/60"
          @click="player.play(s)"
        >
          <span class="mono w-8 text-xs text-dim">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="flex-1 truncate text-sm text-gray-200 group-hover:text-white">{{ s.title }}</span>
          <span class="hidden text-xs text-dim md:inline">{{ s.bandName }}</span>
          <span class="mono w-16 text-right text-xs text-dim">{{ player.current?.id === s.id && player.isPlaying ? '▶ PLAYING' : '' }}</span>
        </div>
      </div>
    </section>
  </main>
</template>
