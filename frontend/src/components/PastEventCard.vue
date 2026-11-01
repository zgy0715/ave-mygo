<script setup>
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'

/**
 * 往期回顾卡片（docs/16）：已结束演出专用。
 * 不复用 EventCard——在售卡片有收藏/购票语义，往期只做「回顾」展示。
 * 封面灰化 + 「已结束 ENDED」徽章 + 最后一场日期。
 */
const props = defineProps({ event: { type: Object, required: true } })

const BAND_SLUG = {
  'MyGO!!!!!': 'mygo',
  'Ave Mujica': 'ave-mujica',
  sumimi: 'sumimi',
}
const bandSlug = BAND_SLUG[props.event.bandName] || 'mygo'

/** 最后一场日期：取 lastSessionAt 前 10 位（YYYY-MM-DD）；为空则不显示 */
const dateStr = (props.event.lastSessionAt || '').slice(0, 10)
</script>

<template>
  <RouterLink
    :to="`/events/${event.id}`"
    class="card group flex flex-col overflow-hidden transition hover:-translate-y-1 hover:shadow-md"
  >
    <div class="relative h-40 overflow-hidden">
      <CoverArt
        :title="event.title"
        :band="bandSlug"
        rounded="rounded-none"
        class="h-full w-full opacity-80 grayscale transition duration-300 group-hover:opacity-100 group-hover:grayscale-0"
      />
      <span class="absolute right-3 top-3 rounded bg-line-strong/60 px-2 py-0.5 font-mono text-[10px] tracking-[0.14em] text-white">
        已结束 ENDED
      </span>
    </div>
    <div class="flex flex-1 flex-col gap-1 p-3">
      <p class="serif line-clamp-2 min-h-[2.6em] text-base leading-snug font-semibold">{{ event.title }}</p>
      <p class="mt-auto truncate text-xs text-faint">
        {{ event.venue }}<template v-if="dateStr"> · 最后一场 {{ dateStr }}</template>
      </p>
    </div>
  </RouterLink>
</template>
