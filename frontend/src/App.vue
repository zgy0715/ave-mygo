<script setup>
import { RouterLink, RouterView, useRouter } from 'vue-router'
import PlayerBar from '@/components/PlayerBar.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

// 导航项集中定义：加页面 = 加一行
const navItems = [
  { to: '/', label: '歌曲库' },
  { to: '/events', label: '演出' },
  { to: '/orders', label: '我的订单', needAuth: true },
]

function logout() {
  auth.logout()
  router.push('/')
}
</script>

<template>
  <!-- 全局导航：站点骨架，所有页面共用 -->
  <header class="sticky top-0 z-10 border-b border-line bg-base/90 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
      <RouterLink to="/" class="flex items-center gap-2 font-bold text-primary">
        <span class="text-xl">🎸</span>
        <span>Bandori Live</span>
      </RouterLink>

      <nav class="flex gap-1">
        <RouterLink
          v-for="item in navItems.filter(i => !i.needAuth || auth.isLogin)"
          :key="item.to"
          :to="item.to"
          class="rounded-md px-3 py-1.5 text-sm text-gray-400 hover:bg-elevated hover:text-gray-200"
          active-class="bg-primary/15 text-primary"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <template v-if="auth.isLogin">
          <span class="text-sm text-gray-300">{{ auth.nickname }}</span>
          <button class="text-xs text-gray-500 hover:text-gray-200" @click="logout">退出</button>
        </template>
        <RouterLink
          v-else to="/login"
          class="rounded-md bg-primary px-3 py-1.5 text-sm text-white hover:bg-violet-500"
        >
          登录
        </RouterLink>
      </div>
    </div>
  </header>

  <!-- 页面出口：路由匹配的组件渲染在这里 -->
  <RouterView />

  <!-- 全局底部播放条 -->
  <PlayerBar />
</template>
