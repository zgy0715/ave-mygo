<script setup>
import { RouterLink, RouterView, useRouter } from 'vue-router'
import PlayerBar from '@/components/PlayerBar.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

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
  <!-- 顶部梅红渐变条：官网同款 signature -->
  <div class="grad-plum h-[3px] w-full"></div>

  <div class="noise relative">
    <header class="sticky top-0 z-10 border-b border-line bg-base/85 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center gap-8 px-4">
        <RouterLink to="/" class="font-display text-lg font-bold tracking-wide text-white">
          Bandori <span class="italic text-salmon">Live</span>
        </RouterLink>

        <nav class="flex gap-6">
          <RouterLink
            v-for="item in navItems.filter(i => !i.needAuth || auth.isLogin)"
            :key="item.to"
            :to="item.to"
            class="font-display text-sm text-gray-300 transition hover:text-salmon"
            active-class="text-salmon"
          >
            {{ item.zh }}
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-3">
          <template v-if="auth.isLogin">
            <span class="font-display text-sm text-gray-200">{{ auth.nickname }}</span>
            <button class="mono text-xs text-dim hover:text-gray-100" @click="logout">LOGOUT</button>
          </template>
          <RouterLink
            v-else to="/login"
            class="font-display rounded-none border border-salmon px-4 py-1 text-sm tracking-widest text-salmon transition hover:bg-salmon hover:text-base"
          >
            LOGIN
          </RouterLink>
        </div>
      </div>
    </header>

    <RouterView />
    <PlayerBar />

    <footer class="border-t border-line py-10">
      <p class="font-display text-center text-sm italic text-dim">BanG Dream! fan archive — built with love & noise</p>
      <p class="mono mt-2 text-center text-[10px] text-dim">© 2026 BANDORI/LIVE · NOT OFFICIAL</p>
    </footer>
  </div>
</template>
