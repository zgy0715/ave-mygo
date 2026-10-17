<script setup>
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/** 顶部导航：黑金编辑风。当前项用金色下划线而非色块，保持克制 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)

const links = [
  { to: '/', label: '首页', en: 'TOP' },
  { to: '/songs', label: '歌曲库', en: 'SONGS' },
  { to: '/events', label: '演出', en: 'LIVE' },
  { to: '/bands', label: '乐队', en: 'BANDS' },
]

const isActive = (to) => (to === '/' ? route.path === '/' : route.path.startsWith(to))

function logout() {
  auth.logout()
  mobileOpen.value = false
  router.push('/')
}
</script>

<template>
  <header class="sticky top-0 z-40 border-b border-line bg-base/85 backdrop-blur-md">
    <nav class="wrap flex h-16 items-center justify-between gap-4">
      <!-- Logo：衬线 + mono 双层 -->
      <RouterLink to="/" class="flex items-baseline gap-2" @click="mobileOpen = false">
        <span class="serif text-xl font-semibold tracking-wide text-ink">Bandori</span>
        <span class="micro-accent">/ LIVE</span>
      </RouterLink>

      <!-- 桌面菜单：mono 大写 + 金色下划线态 -->
      <div class="hidden items-center gap-8 md:flex">
        <RouterLink
          v-for="l in links"
          :key="l.to"
          :to="l.to"
          class="micro relative py-1 transition hover:text-ink"
          :class="isActive(l.to) && 'text-primary after:absolute after:-bottom-1.5 after:left-1/2 after:h-px after:w-5 after:-translate-x-1/2 after:bg-primary'"
        >
          {{ l.label }} <span class="ml-1 opacity-60">{{ l.en }}</span>
        </RouterLink>
      </div>

      <!-- 右侧：登录态 -->
      <div class="hidden items-center gap-3 md:flex">
        <template v-if="auth.isLogin">
          <RouterLink to="/orders" class="micro py-1 transition hover:text-primary">我的订单 ORDERS</RouterLink>
          <RouterLink
            to="/profile"
            class="flex h-8 w-8 items-center justify-center border border-primary/40 text-sm font-semibold text-primary transition hover:bg-primary-soft"
            :title="auth.nickname"
          >
            {{ auth.nickname.slice(0, 1).toUpperCase() }}
          </RouterLink>
        </template>
        <template v-else>
          <RouterLink to="/login" class="micro py-1 transition hover:text-primary">登录 LOGIN</RouterLink>
          <RouterLink to="/login?mode=register" class="btn-ghost !px-4 !py-1.5">注册</RouterLink>
        </template>
      </div>

      <!-- 移动端汉堡 -->
      <button class="p-2 md:hidden" aria-label="菜单" @click="mobileOpen = !mobileOpen">
        <div class="space-y-1.5">
          <span class="block h-px w-6 bg-ink transition" :class="mobileOpen && 'translate-y-[7px] rotate-45'" />
          <span class="block h-px w-6 bg-ink transition" :class="mobileOpen && 'opacity-0'" />
          <span class="block h-px w-6 bg-ink transition" :class="mobileOpen && '-translate-y-[7px] -rotate-45'" />
        </div>
      </button>
    </nav>

    <!-- 移动端抽屉 -->
    <div v-if="mobileOpen" class="border-t border-line bg-surface px-4 py-4 md:hidden">
      <div class="flex flex-col gap-1">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to" class="flex items-baseline justify-between px-2 py-3" :class="isActive(l.to) ? 'text-primary' : 'text-dim'" @click="mobileOpen = false">
          <span class="text-sm">{{ l.label }}</span>
          <span class="micro">{{ l.en }}</span>
        </RouterLink>
        <hr class="my-2 border-line">
        <template v-if="auth.isLogin">
          <RouterLink to="/orders" class="px-2 py-3 text-sm text-dim" @click="mobileOpen = false">我的订单</RouterLink>
          <button class="px-2 py-3 text-left text-sm text-wine" @click="logout">退出登录</button>
        </template>
        <RouterLink v-else to="/login" class="btn-primary mt-2" @click="mobileOpen = false">登录 / 注册</RouterLink>
      </div>
    </div>
  </header>
</template>
