<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="wrap flex flex-col items-center py-24 text-center">
    <p class="mono text-7xl font-black text-primary-soft">404</p>
    <p class="mt-4 text-lg font-semibold">迷路了也没关系</p>
    <p class="mt-1 text-sm text-dim">但要继续前行 —— 页面不存在</p>
    <div class="mt-6 flex gap-3">
      <RouterLink to="/" class="btn-primary">回首页</RouterLink>
      <RouterLink to="/songs" class="btn-ghost">逛逛歌曲库</RouterLink>
    </div>
  </div>
</template>
