<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import EventCard from '@/components/EventCard.vue'
import SongCard from '@/components/SongCard.vue'
import { events, songs, bands, members } from '@/data/mock'

/**
 * 首页 v3.1：官网级编辑排版（浅色版）。
 * Hero（衬线大标题 + mono 微标签）→ 跑马灯 → 编号区块（01 演出 / 02 新曲 / 03 乐队）。
 */
const onSale = computed(() => events.filter((e) => e.status === 'ONSALE'))
const featured = onSale.value[0] || events[0]
const upcoming = computed(() => events.filter((e) => e.status !== 'ENDED').slice(0, 3))
const newSongs = songs.slice(0, 8)
const marqueeItems = ['MYGO!!!!!', 'Ave Mujica', 'LIVE HOUSE', 'prima aurora', '迷子でもいい、迷子でも進む', 'BANDORI / LIVE']
</script>

<template>
  <!-- Hero：主打在售演出 -->
  <section class="relative overflow-hidden border-b border-line">
    <!-- 底部蓝雾光：个人站 aurora 同源 -->
    <div class="pointer-events-none absolute -bottom-40 left-1/2 h-96 w-[60rem] -translate-x-1/2 rounded-full opacity-20 blur-3xl" style="background: radial-gradient(ellipse, #7bd, transparent 70%)" />

    <div class="wrap relative py-16 sm:py-24">
      <div class="grid gap-10 lg:grid-cols-[1.25fr_1fr] lg:items-center">
        <div v-reveal>
          <p class="micro-accent mb-5">NOW ON SALE ✳ {{ onSale.length }} CONCERTS</p>
          <h1 class="serif text-4xl leading-tight font-semibold sm:text-5xl lg:text-6xl">
            {{ featured.title }}
          </h1>
          <p class="mt-5 text-dim">
            {{ featured.venue }} <span class="mx-2 text-faint">✳</span>
            <span class="tabular">{{ featured.date }}</span>
            <span class="mx-2 text-faint">✳</span> {{ featured.startTime }} 开演
          </p>
          <div class="mt-8 flex flex-wrap gap-3">
            <RouterLink :to="`/events/${featured.id}`" class="btn-primary">立即购票 TICKETS</RouterLink>
            <RouterLink to="/events" class="btn-ghost">全部演出 ALL LIVE</RouterLink>
          </div>
        </div>

        <!-- 演出概览卡 -->
        <RouterLink :to="`/events/${featured.id}`" class="card group overflow-hidden" v-reveal="120">
          <div class="shine relative flex h-48 flex-col justify-between overflow-hidden p-6 sm:h-56" :style="{ background: bands.find((b) => b.id === featured.band)?.gradient }">
            <span class="mono relative text-[11px] tracking-[0.25em] text-white/75">{{ featured.date }} {{ featured.openTime }} OPEN</span>
            <p class="serif relative text-2xl font-semibold text-white" style="text-shadow: 0 1px 14px rgba(0,0,0,.5)">{{ featured.venue }}</p>
          </div>
          <div class="flex items-center justify-between p-4">
            <p class="micro">{{ onSale.length }} 场演出正在售票</p>
            <span class="text-sm text-primary transition-transform duration-300 group-hover:translate-x-1.5" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">→</span>
          </div>
        </RouterLink>
      </div>
      <!-- 下滚引导：个人站同款 Scroll down -->
      <p class="micro mt-14 text-center">往下滚动 SCROLL DOWN ↓</p>
    </div>
  </section>

  <!-- 跑马灯：个人站同款符号分隔 -->
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
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <EventCard v-for="(e, i) in upcoming" :key="e.id" :event="e" v-reveal="i * 90" />
    </div>
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
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <SongCard v-for="(s, i) in newSongs" :key="s.id" :song="s" v-reveal="(i % 4) * 70" />
    </div>
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
      <RouterLink v-for="(b, i) in bands" :key="b.id" :to="`/bands/${b.id}`" class="card group overflow-hidden" v-reveal="i * 120">
        <div class="shine flex h-32 items-end p-5" :style="{ background: b.gradient }">
          <p class="serif text-3xl font-semibold text-white" style="text-shadow: 0 1px 12px rgba(0,0,0,.5)">{{ b.name }}</p>
        </div>
        <div class="flex items-center justify-between p-4">
          <p class="text-sm text-dim">{{ b.nameCn }} · {{ members.filter((m) => m.band === b.id).length }} 名成员</p>
          <span class="text-primary transition-transform duration-300 group-hover:translate-x-1.5" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">→</span>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
