<script setup>
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'

/**
 * 往期回顾卡片（docs/16）：已结束演出专用。
 * 不复用 EventCard——在售卡片有收藏/购票语义，往期只做「回顾」展示。
 * 封面优先用 event.posterUrl 真图（无图回退乐队渐变），不做灰化处理。
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
    <!-- 封面：真实海报（posterUrl），竖版图用 4:3 裁切保留人物上半身 -->
    <div class="relative aspect-[4/3] overflow-hidden">
      <CoverArt
        :title="event.title"
        :band="bandSlug"
        :cover-url="event.posterUrl || ''"
        rounded="rounded-none"
        class="h-full w-full transition duration-500 group-hover:scale-[1.04]"
      />
      <div class="pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/35 to-transparent" />
      <span class="absolute right-2.5 top-2.5 rounded-full bg-black/55 px-2 py-0.5 font-mono text-[10px] tracking-[0.12em] text-white backdrop-blur-sm">
        已结束 ENDED
      </span>
    </div>
    <div class="flex flex-1 flex-col gap-1 p-3">
      <p class="serif line-clamp-2 min-h-[2.6em] text-[15px] leading-snug font-semibold">{{ event.title }}</p>
      <p class="truncate text-xs text-faint">{{ event.venue }}</p>
      <p v-if="dateStr" class="tabular text-[11px] text-faint">最后一场 {{ dateStr }}</p>
    </div>
  </RouterLink>
</template>
