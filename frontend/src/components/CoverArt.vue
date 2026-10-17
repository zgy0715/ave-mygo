<script setup>
/**
 * 全站封面：无真实图片资产时的程序化封面。
 * 用乐队渐变色 + 首字生成，视觉统一且零资源加载；
 * 后端有 cover_url 后换成 <img> 即可（组件对外接口不变）。
 */
const props = defineProps({
  title: { type: String, required: true },
  band: { type: String, default: 'mygo' },
  rounded: { type: String, default: 'rounded-lg' },
})

import { bandById } from '@/data/mock'
const band = bandById(props.band)
</script>

<template>
  <div
    class="relative flex items-center justify-center overflow-hidden text-white select-none"
    :class="rounded"
    :style="{ background: band?.gradient }"
  >
    <!-- 纹理：斜向高光，避免大面积纯色显得闷 -->
    <div class="absolute inset-0 opacity-25" style="background: linear-gradient(115deg, transparent 30%, rgba(255,255,255,.5) 45%, transparent 60%)" />
    <span class="relative font-black tracking-tight" style="text-shadow: 0 1px 6px rgba(0,0,0,.25)">
      {{ title.slice(0, 2) }}
    </span>
  </div>
</template>
