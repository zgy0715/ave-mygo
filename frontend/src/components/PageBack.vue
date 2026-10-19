<script setup>
import { useRouter } from 'vue-router'

/**
 * 页面返回按钮（子页面统一入口，固定悬浮）：
 * - 位置：fixed 钉在左上角、导航栏正下方，滚动始终可见（与右缘 MENU 页签对位）
 * - 行为：有站内历史 → router.back()（尊重真实来路）；
 *         直链/新标签进入（无历史）→ 兜底跳 fallback 列表页
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
    class="fixed left-4 top-[4.75rem] z-30 inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-full border border-line bg-surface/90 px-3.5 text-sm text-dim backdrop-blur transition-colors duration-300 hover:border-line-strong hover:text-primary"
    @click="go"
  >
    <span
      class="inline-block transition-transform duration-300 group-hover:-translate-x-1"
      style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)"
    >←</span>
    {{ label }}
  </button>
</template>
