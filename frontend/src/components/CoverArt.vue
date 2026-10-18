<script setup>
/**
 * 全站封面：无真实图片资产时的程序化封面。
 * 用乐队渐变色 + 首字生成，视觉统一且零资源加载；
 * 后端有 cover_url 后换成 <img> 即可（组件对外接口不变）。
 */
import { computed, ref } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  band: { type: String, default: 'mygo' },          // 传 band slug
  rounded: { type: String, default: 'rounded-lg' },
  coverUrl: { type: String, default: '' },          // 有官方曲绘时传本地路径
})

// 乐队视觉 token：slug → 渐变/强调色/浅底（v2.1 从 mock 解耦，视觉内聚到组件）
const BAND_VISUALS = {
  mygo: {
    gradient: 'linear-gradient(135deg, #2c7da0 0%, #1f6485 100%)',
    color: '#2c7da0',
    soft: '#e3f0f7',
  },
  'ave-mujica': {
    gradient: 'linear-gradient(135deg, #6b4fa3 0%, #4a3573 100%)',
    color: '#6b4fa3',
    soft: '#efeaf7',
  },
}

const band = computed(() => BAND_VISUALS[props.band] || BAND_VISUALS.mygo)
const failed = ref(false)   // 封面 404 → 回退程序化渐变
</script>

<template>
  <div
    class="relative flex items-center justify-center overflow-hidden bg-surface text-white select-none"
    :class="rounded"
    :style="coverUrl ? {} : { background: band.gradient }"
  >
    <!-- 封面图（404 自动回退渐变） -->
    <img v-if="coverUrl && !failed" :src="coverUrl" :alt="title" class="absolute inset-0 h-full w-full object-cover" loading="lazy" @error="failed = true">
    <template v-else>
      <!-- 纹理：斜向高光，避免大面积纯色显得闷 -->
      <div class="absolute inset-0 opacity-25" style="background: linear-gradient(115deg, transparent 30%, rgba(255,255,255,.5) 45%, transparent 60%)" />
      <span class="relative font-black tracking-tight" style="text-shadow: 0 1px 6px rgba(0,0,0,.25)">
        {{ title.slice(0, 2) }}
      </span>
    </template>
  </div>
</template>
