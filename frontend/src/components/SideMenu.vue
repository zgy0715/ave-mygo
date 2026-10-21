<script setup>
import { ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 侧滑菜单（anontokyo 式）v4.2：
 * - 右缘固定页签：只留干净的三道杠/✕，竖排字留足呼吸感
 * - 面板 z-[45]：盖过底部播放条(z-40)，不被播放条控件穿透
 * - 路由跳转自动收起
 */
const open = ref(false)
const route = useRoute()
const auth = useAuthStore()
watch(() => route.fullPath, () => (open.value = false))

const links = [
  { to: '/', label: 'TOP', cn: '首页' },
  { to: '/songs', label: 'SONGS', cn: '歌曲库' },
  { to: '/events', label: 'LIVE', cn: '演出' },
  { to: '/bands', label: 'BANDS', cn: '乐队' },
]
const userLinks = [
  { to: '/favorites', label: 'FAVORITES', cn: '我的收藏' },
  { to: '/playlists', label: 'PLAYLISTS', cn: '我的歌单' },
  { to: '/orders', label: 'ORDERS', cn: '我的订单' },
  { to: '/profile', label: 'PROFILE', cn: '个人中心' },
]
const visibleLinks = links.concat(auth.isLogin ? userLinks : [])

function logout() {
  auth.logout()
  open.value = false
}
</script>

<template>
  <!-- 右缘页签：只留图标，干净利落 -->
  <button
    class="fixed right-0 top-0 z-50 flex h-16 w-12 cursor-pointer flex-col items-center justify-center bg-primary text-white transition-colors duration-300 hover:bg-primary-strong sm:w-14"
    :aria-expanded="open"
    aria-label="菜单开关"
    @click="open = !open"
  >
    <span v-if="!open" class="flex flex-col items-end gap-[5px]">
      <span class="block h-[2px] w-5 bg-white" />
      <span class="block h-[2px] w-5 bg-white" />
      <span class="block h-[2px] w-3.5 bg-white" />
    </span>
    <span v-else class="text-lg leading-none">✕</span>
  </button>

  <!-- 侧滑面板 -->
  <Transition name="menu">
    <div v-if="open" class="fixed inset-y-0 right-0 z-[45] flex w-full flex-col bg-primary text-white sm:w-[30rem]">
      <nav class="m-auto flex flex-col items-center gap-1 py-20">
        <RouterLink
          v-for="(l, i) in visibleLinks"
          :key="l.to"
          :to="l.to"
          class="menu-item group flex items-baseline gap-4 py-2"
          :style="{ '--d': `${120 + i * 70}ms` }"
        >
          <span class="mono text-[10px] text-white/45">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="display text-3xl tracking-[0.08em] transition-transform duration-300 group-hover:translate-x-2 sm:text-4xl" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">
            {{ l.label }}
          </span>
          <span class="micro !text-white/55">{{ l.cn }}</span>
        </RouterLink>
      </nav>

      <!-- 底部登录态：醒目入口收在这里 -->
      <div class="menu-item absolute inset-x-0 bottom-0 flex items-center justify-between border-t border-white/15 px-6 py-4" style="--d: 560ms">
        <template v-if="auth.isLogin">
          <p class="text-sm text-white/85">你好，<span class="font-semibold">{{ auth.nickname }}</span></p>
          <button class="micro !text-white/70 transition hover:!text-white" @click="logout">退出 LOGOUT</button>
        </template>
        <template v-else>
          <p class="micro !text-white/60">MEMBER — 登录后可听歌购票</p>
          <RouterLink to="/login?mode=register" class="rounded-md bg-white px-4 py-2 text-sm font-semibold text-primary transition hover:bg-white/90">
            登录 / 注册
          </RouterLink>
        </template>
      </div>
    </div>
  </Transition>
</template>
