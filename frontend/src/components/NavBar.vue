<script setup>
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePlayerStore } from '@/stores/player'

/** 顶部导航：桌面横向菜单 + 移动端抽屉；登录态切换右侧操作区 */
const auth = useAuthStore()
const player = usePlayerStore()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)

const links = [
  { to: '/', label: '首页' },
  { to: '/songs', label: '歌曲库' },
  { to: '/events', label: '演出' },
  { to: '/bands', label: '乐队' },
]

const isActive = (to) => (to === '/' ? route.path === '/' : route.path.startsWith(to))

function logout() {
  auth.logout()
  mobileOpen.value = false
  router.push('/')
}
</script>

<template>
  <header class="sticky top-0 z-40 border-b border-line bg-surface/90 backdrop-blur">
    <nav class="wrap flex h-14 items-center justify-between gap-4">
      <!-- Logo -->
      <RouterLink to="/" class="flex items-baseline gap-1.5 font-black tracking-tight" @click="mobileOpen = false">
        <span class="text-lg text-primary">BANDORI</span>
        <span class="mono text-[11px] tracking-[0.25em] text-dim">/ LIVE</span>
      </RouterLink>

      <!-- 桌面菜单 -->
      <div class="hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="l in links"
          :key="l.to"
          :to="l.to"
          class="rounded-lg px-3 py-1.5 text-sm transition"
          :class="isActive(l.to) ? 'bg-primary-soft font-semibold text-primary' : 'text-dim hover:bg-base hover:text-ink'"
        >
          {{ l.label }}
        </RouterLink>
      </div>

      <!-- 右侧：登录态 -->
      <div class="hidden items-center gap-2 md:flex">
        <template v-if="auth.isLogin">
          <RouterLink to="/orders" class="rounded-lg px-3 py-1.5 text-sm text-dim transition hover:text-ink">我的订单</RouterLink>
          <RouterLink
            to="/profile"
            class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white"
            :title="auth.nickname"
          >
            {{ auth.nickname.slice(0, 1).toUpperCase() }}
          </RouterLink>
          <button class="text-xs text-faint hover:text-dim" @click="logout">退出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn-ghost">登录</RouterLink>
          <RouterLink to="/login?mode=register" class="btn-primary">注册</RouterLink>
        </template>
      </div>

      <!-- 移动端汉堡 -->
      <button class="p-2 md:hidden" aria-label="菜单" @click="mobileOpen = !mobileOpen">
        <div class="space-y-1.5">
          <span class="block h-0.5 w-5 bg-ink" :class="mobileOpen && 'translate-y-2 rotate-45 transition'" />
          <span class="block h-0.5 w-5 bg-ink" :class="mobileOpen && 'opacity-0 transition'" />
          <span class="block h-0.5 w-5 bg-ink" :class="mobileOpen && '-translate-y-2 -rotate-45 transition'" />
        </div>
      </button>
    </nav>

    <!-- 移动端抽屉 -->
    <div v-if="mobileOpen" class="border-t border-line bg-surface px-4 py-3 md:hidden">
      <div class="flex flex-col">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to" class="rounded-lg px-2 py-2.5 text-sm" :class="isActive(l.to) ? 'font-semibold text-primary' : 'text-dim'" @click="mobileOpen = false">
          {{ l.label }}
        </RouterLink>
        <hr class="my-2 border-line">
        <template v-if="auth.isLogin">
          <RouterLink to="/orders" class="rounded-lg px-2 py-2.5 text-sm text-dim" @click="mobileOpen = false">我的订单</RouterLink>
          <button class="rounded-lg px-2 py-2.5 text-left text-sm text-red-500" @click="logout">退出登录</button>
        </template>
        <RouterLink v-else to="/login" class="btn-primary mt-1" @click="mobileOpen = false">登录 / 注册</RouterLink>
      </div>
    </div>
  </header>
</template>
