<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import EventCard from '@/components/EventCard.vue'
import SongCard from '@/components/SongCard.vue'
import { events, songs, bands, members } from '@/data/mock'

/**
 * 首页：近期演出（在售优先）→ 新曲速递 → 乐队入口。
 * 需求 3.1：无在售演出时给「看看往期」引导。
 * v2.1 视觉：逐字母 blur 入场 / 跑马灯 / 乐队区整幅填色（anontokyo .projects 手法）。
 */
const onSale = computed(() => events.filter((e) => e.status === 'ONSALE'))
const upcoming = computed(() => events.filter((e) => e.status !== 'ENDED').slice(0, 3))
const newSongs = songs.slice(0, 8)

// Hero 标题拆字符：逐个 letter-in，延迟 .25s 起每字 +0.06s
const heroTitle = onSale.value.length ? onSale.value[0].title : 'BANDORI LIVE'
const heroLetters = heroTitle.split('')
</script>

<template>
  <!-- Hero：主打在售演出 -->
  <section class="relative overflow-hidden border-b border-line">
    <!-- aurora 光斑：白底上的蓝粉柔光 -->
    <div class="aurora aurora-blue"></div>
    <div class="aurora aurora-pink"></div>

    <div class="wrap relative py-12 sm:py-16">
      <div v-if="onSale.length" class="grid gap-8 md:grid-cols-[1.2fr_1fr] md:items-center">
        <div>
          <p class="hero-sub mb-4 text-primary">NOW ON SALE</p>
          <h1 class="letter-in text-3xl leading-tight font-black sm:text-4xl">
            <span v-for="(ch, i) in heroLetters" :key="i" :style="{ animationDelay: 0.25 + i * 0.06 + 's' }">{{ ch }}</span>
          </h1>
          <p class="mt-3 text-dim">{{ onSale[0].venue }} · {{ onSale[0].date }} {{ onSale[0].startTime }} 开演</p>
          <div class="mt-6 flex gap-3">
            <RouterLink :to="`/events/${onSale[0].id}`" class="btn-primary">立即购票</RouterLink>
            <RouterLink to="/events" class="btn-ghost">全部演出</RouterLink>
          </div>
        </div>
        <!-- 演出概览卡：海报面 -->
        <RouterLink :to="`/events/${onSale[0].id}`" class="card overflow-hidden hover:shadow-md">
          <div class="flex h-44 flex-col justify-between p-5 text-white md:h-52" :style="{ background: bands.find((b) => b.id === onSale[0].band)?.gradient }">
            <span class="mono text-xs tracking-[0.2em] opacity-80">{{ onSale[0].date }}</span>
            <p class="text-xl font-bold" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">{{ onSale[0].venue }}</p>
          </div>
          <div class="flex items-center justify-between p-4">
            <p class="text-sm text-dim">{{ events.filter((e) => e.status === 'ONSALE').length }} 场演出正在售票</p>
            <span class="text-sm font-medium text-primary">查看详情 →</span>
          </div>
        </RouterLink>
      </div>
      <div v-else class="py-6 text-center">
        <p class="text-lg font-semibold">暂无在售演出</p>
        <RouterLink to="/events" class="mt-3 inline-block text-sm text-primary">看看往期演出 →</RouterLink>
      </div>
    </div>

    <!-- 跑马灯：内容带 ✳ 分隔，出 hero 即入 -->
    <div class="marquee relative border-t border-line bg-surface/60 py-2.5">
      <div class="marquee-track mono gap-8 whitespace-nowrap text-xs text-dim">
        <span v-for="i in 2" :key="i" class="flex gap-8">
          <template v-for="w in ['MYGO!!!!!', 'AVE MUJICA', 'CRYCHIC', 'LIVE HOUSE', '迷星叫', '春日影', '壱雫空', '黒のバースデイ']" :key="w">
            {{ w }}&nbsp;&nbsp;✳&nbsp;&nbsp;
          </template>
        </span>
      </div>
    </div>
  </section>

  <!-- 近期演出 -->
  <section class="wrap py-10">
    <div class="section-head">
      <h2 class="text-xl font-bold">近期演出</h2>
      <RouterLink to="/events" class="text-sm text-dim hover:text-primary">全部 →</RouterLink>
    </div>
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <EventCard v-for="e in upcoming" :key="e.id" :event="e" />
    </div>
  </section>

  <!-- 新曲速递 -->
  <section class="wrap pb-10">
    <div class="section-head">
      <h2 class="text-xl font-bold">新曲速递</h2>
      <RouterLink to="/songs" class="text-sm text-dim hover:text-primary">歌曲库 →</RouterLink>
    </div>
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <SongCard v-for="s in newSongs" :key="s.id" :song="s" />
    </div>
  </section>

  <!-- 乐队入口：整幅填色区（.projects 手法）—— 白/蓝节奏的"高级感"来源 -->
  <section class="bg-primary py-16 text-white">
    <div class="wrap">
      <div class="section-head !border-white/25">
        <h2 class="text-xl font-bold"><span class="mono mr-2 text-xs text-white/60">_01_</span>乐队 <span class="mono ml-2 text-[10px] text-white/50">BANDS</span></h2>
        <RouterLink to="/bands" class="text-sm text-white/70 hover:text-white">全部 →</RouterLink>
      </div>
      <div class="grid gap-4 sm:grid-cols-2">
        <RouterLink
          v-for="(b, i) in bands"
          :key="b.id"
          :to="`/bands/${b.id}`"
          class="group relative flex min-h-[220px] flex-col justify-end overflow-hidden rounded-xl border border-white/30 p-7 transition hover:bg-white/10"
        >
          <!-- 巨型编号水印 -->
          <span class="font-display pointer-events-none absolute -right-6 -top-10 select-none text-[10rem] leading-none text-white/10 transition group-hover:text-white/20">
            {{ String(i + 1).padStart(2, '0') }}
          </span>
          <p class="tag-mono !text-white/60">{{ i === 0 ? 'BAND / 01 — GO !!!! まで、進め。' : 'BAND / 02 — mask · masquerade' }}</p>
          <p class="font-display mt-3 text-5xl tracking-wide">{{ b.name }}</p>
          <p class="mt-2 text-sm text-white/80">{{ b.nameCn }} · {{ members.filter((m) => m.band === b.id).length }} 名成员</p>
          <span class="tag-mono mt-5 border-t border-white/30 pt-4 transition group-hover:text-white">MEMBERS →</span>
        </RouterLink>
      </div>
    </div>
  </section>
</template>
