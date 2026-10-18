<script setup>
import { RouterLink } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'

/**
 * 演出卡片：列表/首页通用（真接口字段）。
 * 海报面：bandName → 乐队渐变；日期取 nextSessionTime 派生值。
 */
const props = defineProps({ event: { type: Object, required: true } })

const BAND_STYLE = {
  'MyGO!!!!': { gradient: 'linear-gradient(135deg,#2c7da0,#1f6485)', color: '#2c7da0' },
  'Ave Mujica': { gradient: 'linear-gradient(135deg,#6b4fa3,#4a3573)', color: '#6b4fa3' },
  sumimi: { gradient: 'linear-gradient(135deg, #d4789f 0%, #b95 100%)', color: '#d4789f' },
}
const band = BAND_STYLE[props.event.bandName] || BAND_STYLE['MyGO!!!!']

// "11.01" 式日期（官网情报区同款）
const datePart = (props.event.nextSessionTime || '').slice(5, 10).replace('-', '.')
</script>

<template>
  <RouterLink :to="`/events/${event.id}`" class="card group flex flex-col overflow-hidden hover:shadow-md">
    <!-- 海报面 -->
    <div class="relative flex h-40 flex-col justify-between p-4 text-white" :style="{ background: band.gradient }">
      <div class="absolute inset-0 opacity-20" style="background: linear-gradient(115deg, transparent 30%, rgba(255,255,255,.6) 45%, transparent 60%)" />
      <span class="mono relative text-[11px] tracking-[0.2em] opacity-80">{{ datePart }} · {{ event.venue }}</span>
      <p class="serif relative text-xl leading-snug font-semibold" style="text-shadow: 0 1px 10px rgba(0,0,0,.4)">
        {{ event.title }}
      </p>
    </div>

    <div class="flex flex-1 items-center justify-between gap-2 p-3">
      <div class="min-w-0">
        <p class="text-sm text-dim"><span class="inline-block h-1.5 w-1.5 rounded-full align-middle" :style="{ background: band.color }" /> {{ event.bandName }}</p>
        <p class="mt-0.5 truncate text-xs text-faint">{{ event.venue }}</p>
      </div>
      <StatusBadge :status="event.status" />
    </div>
  </RouterLink>
</template>
