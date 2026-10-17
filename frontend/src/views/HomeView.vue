<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import EventCard from '@/components/EventCard.vue'
import SongCard from '@/components/SongCard.vue'
import { events, songs, bands, members } from '@/data/mock'

/**
 * 首页：近期演出（在售优先）→ 新曲速递 → 乐队入口。
 * 需求 3.1：无在售演出时给「看看往期」引导。
 */
const onSale = computed(() => events.filter((e) => e.status === 'ONSALE'))
const upcoming = computed(() => events.filter((e) => e.status !== 'ENDED').slice(0, 3))
const newSongs = songs.slice(0, 8)
</script>

<template>
  <!-- Hero：主打在售演出 -->
  <section class="border-b border-line bg-surface">
    <div class="wrap py-10 sm:py-14">
      <div v-if="onSale.length" class="grid gap-8 md:grid-cols-[1.2fr_1fr] md:items-center">
        <div>
          <p class="mono mb-3 text-xs tracking-[0.3em] text-primary">NOW ON SALE</p>
          <h1 class="text-3xl leading-tight font-black sm:text-4xl">{{ onSale[0].title }}</h1>
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

  <!-- 乐队入口 -->
  <section class="wrap pb-14">
    <div class="section-head">
      <h2 class="text-xl font-bold">乐队</h2>
      <RouterLink to="/bands" class="text-sm text-dim hover:text-primary">全部 →</RouterLink>
    </div>
    <div class="grid gap-4 sm:grid-cols-2">
      <RouterLink
        v-for="b in bands"
        :key="b.id"
        :to="`/bands/${b.id}`"
        class="card group flex items-center justify-between overflow-hidden p-6 hover:shadow-md"
      >
        <div>
          <p class="text-2xl font-black" :style="{ color: b.color }">{{ b.name }}</p>
          <p class="mt-1 text-sm text-dim">{{ b.nameCn }} · {{ members.filter((m) => m.band === b.id).length }} 名成员</p>
        </div>
        <span class="text-3xl opacity-20 transition group-hover:translate-x-1 group-hover:opacity-40" :style="{ color: b.color }">→</span>
      </RouterLink>
    </div>
  </section>
</template>
