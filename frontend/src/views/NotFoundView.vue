<script setup>
import { RouterLink, useRoute, useRouter } from 'vue-router'

/**
 * 404：显示实际访问的地址 + 多出口（返回上一页 / 回首页 / 逛歌曲库），
 * 避免用户困在死胡同里。
 */
const route = useRoute()
const router = useRouter()

function goBack() {
  if (window.history.state?.back) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="wrap flex flex-col items-center py-24 text-center">
    <p class="display text-8xl text-primary/20">404</p>
    <p class="mt-4 text-lg font-semibold">迷路了也没关系</p>
    <p class="mt-1 text-sm text-dim">但要继续前行 —— 页面不存在</p>
    <p class="mono mt-3 rounded bg-base px-3 py-1 text-xs text-faint">/{{ route.fullPath.replace(/^\//, '') }}</p>
    <div class="mt-6 flex flex-wrap justify-center gap-3">
      <button class="btn-ghost" @click="goBack">← 返回上一页</button>
      <RouterLink to="/" class="btn-primary">回首页</RouterLink>
      <RouterLink to="/songs" class="btn-ghost">逛逛歌曲库</RouterLink>
    </div>
  </div>
</template>
