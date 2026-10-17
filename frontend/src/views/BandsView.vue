<script setup>
import { RouterLink } from 'vue-router'
import { bands, members, songs } from '@/data/mock'

/** 乐队总览：双乐队入口卡 */
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <h1 class="text-2xl font-black">乐队</h1>
    </div>
    <div class="grid gap-6 md:grid-cols-2">
      <RouterLink
        v-for="b in bands"
        :key="b.id"
        :to="`/bands/${b.id}`"
        class="card group overflow-hidden hover:shadow-md"
      >
        <div class="flex h-32 items-end p-5 text-white" :style="{ background: b.gradient }">
          <p class="text-3xl font-black" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">{{ b.name }}</p>
        </div>
        <div class="p-5">
          <p class="text-sm leading-relaxed text-dim">{{ b.intro }}</p>
          <p class="mt-3 text-xs text-faint">{{ members.filter((m) => m.band === b.id).length }} 名成员 · {{ songs.filter((s) => s.band === b.id).length }} 首收录歌曲</p>
        </div>
      </RouterLink>
    </div>
  </div>
</template>
