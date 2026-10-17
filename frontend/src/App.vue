<script setup>
import { RouterLink, RouterView, useRouter } from 'vue-router'
import PlayerBar from '@/components/PlayerBar.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

// 档案式导航：编号 + 中英双语
const navItems = [
  { no: '01', to: '/songs', zh: '歌曲库', en: 'SONGS' },
  { no: '02', to: '/events', zh: '演出', en: 'LIVE' },
  { no: '03', to: '/orders', zh: '订单', en: 'ORDERS', needAuth: true },
]

function logout() {
  auth.logout()
  router.push('/')
}
</script>

<template>
  <!-- 顶部信息条 -->
  <div class="border-b border-line bg-elevated">
    <p class="mono mx-auto max-w-6xl px-4 py-1 text-center text-[10px] text-dim">
      PERSONAL LIVE ARCHIVE — BUILT ON EARTH · FOUND ONLINE — 2026
    </p>
  </div>

  <!-- 主导航 -->
  <header class="sticky top-0 z-10 border-b border-line bg-base/90 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-6xl items-center gap-8 px-4">
      <RouterLink to="/" class="mono text-sm font-bold tracking-[0.2em] text-white">
        BANDORI<span class="text-primary">/</span>LIVE
      </RouterLink>

      <nav class="flex gap-6">
        <RouterLink
          v-for="item in navItems.filter(i => !i.needAuth || auth.isLogin)"
          :key="item.to"
          :to="item.to"
          class="group flex items-baseline gap-1.5 text-sm text-gray-400 transition hover:text-white"
          active-class="text-white"
        >
          <span class="mono text-[10px] text-primary">{{ item.no }}</span>
          <span>{{ item.zh }}</span>
          <span class="mono hidden text-[10px] text-dim md:inline">{{ item.en }}</span>
        </RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <template v-if="auth.isLogin">
          <span class="mono text-xs text-gray-300">{{ auth.nickname }}</span>
          <button class="mono text-xs text-dim hover:text-gray-200" @click="logout">LOGOUT</button>
        </template>
        <RouterLink
          v-else to="/login"
          class="mono rounded-sm border border-primary px-4 py-1.5 text-xs tracking-widest text-primary transition hover:bg-primary hover:text-black"
        >
          LOGIN / 登录
        </RouterLink>
      </div>
    </div>
  </header>

  <RouterView />

  <PlayerBar />

  <!-- 页脚 -->
  <footer class="border-t border-line py-8">
    <p class="mono text-center text-[10px] text-dim">© 2026 BANDORI/LIVE — FAN-MADE · NOT OFFICIAL · 建于地球 见于线上</p>
  </footer>
</template>
