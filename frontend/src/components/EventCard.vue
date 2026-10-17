<script setup>
import { RouterLink } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import { bandById } from '@/data/mock'

/** 演出卡片：列表/首页通用。无海报时用乐队渐变生成海报面 */
const props = defineProps({ event: { type: Object, required: true } })
const band = bandById(props.event.band)
</script>

<template>
  <RouterLink :to="`/events/${event.id}`" class="card group flex flex-col overflow-hidden hover:shadow-md">
    <!-- 海报面 -->
    <div class="relative flex h-40 flex-col justify-between p-4 text-white" :style="{ background: band?.gradient }">
      <div class="absolute inset-0 opacity-20" style="background: linear-gradient(115deg, transparent 30%, rgba(255,255,255,.6) 45%, transparent 60%)" />
      <span class="mono relative text-[11px] tracking-[0.2em] opacity-80">{{ event.date }} · {{ event.openTime }} OPEN</span>
      <p class="relative text-lg leading-snug font-bold" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">
        {{ event.title }}
      </p>
    </div>

    <div class="flex flex-1 items-center justify-between gap-2 p-3">
      <div class="min-w-0">
        <p class="text-sm text-dim"><span class="inline-block h-1.5 w-1.5 rounded-full align-middle" :style="{ background: band?.color }" /> {{ band?.name }}</p>
        <p class="mt-0.5 truncate text-xs text-faint">📍 {{ event.venue }}</p>
      </div>
      <StatusBadge :status="event.status" />
    </div>
  </RouterLink>
</template>
