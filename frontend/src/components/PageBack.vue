<script setup>
import { useRouter } from 'vue-router'

/**
 * 页面返回按钮（子页面统一入口）：
 * - 有站内历史 → router.back()（尊重用户真实的来路，如首页 Hero → 演出详情 → 返回=首页）
 * - 直链/新标签进入（无历史）→ 兜底跳 fallback 列表页，保证永远有路可退
 */
const props = defineProps({
  label: { type: String, default: '返回' },
  fallback: { type: String, default: '/' },
})
const router = useRouter()

function go() {
  if (window.history.state?.back) router.back()
  else router.push(props.fallback)
}
</script>

<template>
  <button
    class="group inline-flex cursor-pointer items-center gap-1.5 text-sm text-dim transition-colors duration-300 hover:text-primary"
    @click="go"
  >
    <span
      class="inline-block transition-transform duration-300 group-hover:-translate-x-1"
      style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)"
    >←</span>
    {{ label }}
  </button>
</template>
