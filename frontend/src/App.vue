<script setup>
import { RouterLink, RouterView } from 'vue-router'

// 导航项集中定义：加页面 = 加一行
const navItems = [
  { to: '/', label: '歌曲库' },
  { to: '/events', label: '演出' },
]
</script>

<template>
  <!-- 全局导航：站点骨架，所有页面共用 -->
  <header class="sticky top-0 z-10 border-b border-gray-200 bg-white/90 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
      <RouterLink to="/" class="flex items-center gap-2 font-bold text-indigo-600">
        <span class="text-xl">🎸</span>
        <span>Bandori Live</span>
      </RouterLink>

      <nav class="flex gap-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="rounded-md px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 hover:text-gray-900"
          active-class="bg-indigo-50 text-indigo-600"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <!-- 登录入口占位：M4-② 接 authStore 后显示昵称/退出 -->
      <div class="ml-auto">
        <RouterLink
          to="/login"
          class="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white hover:bg-indigo-500"
        >
          登录
        </RouterLink>
      </div>
    </div>
  </header>

  <!-- 页面出口：路由匹配的组件渲染在这里 -->
  <RouterView />
</template>
