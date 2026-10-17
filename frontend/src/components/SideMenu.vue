<script setup>
import { ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 侧滑菜单（anontokyo 式）：
 * - 右缘固定 MENU 页签（蓝块 + 汉堡 + 竖排字），开合同一个按钮
 * - 面板从右侧滑入（apple 缓动），大号 display 链接垂直居中，交错入场
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
  <!-- 右缘页签：开合同钮，盖在面板之上形成一体 -->
  <button
    class="fixed right-0 top-0 z-50 flex h-16 w-12 cursor-pointer flex-col items-center justify-center gap-1.5 bg-primary text-white transition-colors hover:bg-primary-strong sm:w-14"
    :aria-expanded="open"
    aria-label="菜单开关"
    @click="open = !open"
  >
    <template v-if="!open">
      <span class="space-y-1">
        <span class="block h-0.5 w-5 bg-white" />
        <span class="block h-0.5 w-5 bg-white" />
        <span class="block h-0.5 w-5 bg-white" />
      </span>
      <span class="vertical-rl mono text-[10px] tracking-[0.3em]">MENU</span>
    </template>
    <template v-else>
      <span class="text-xl leading-none">✕</span>
      <span class="vertical-rl mono text-[10px] tracking-[0.3em]">CLOSE</span>
    </template>
  </button>

  <!-- 侧滑面板：官网同款纯色大板 + 大号链接 -->
  <Transition name="menu">
    <div v-if="open" class="fixed inset-y-0 right-0 z-40 flex w-full flex-col bg-primary text-white sm:w-[30rem]">
      <nav class="m-auto flex flex-col items-center gap-1 py-20">
        <RouterLink
          v-for="(l, i) in visibleLinks"
          :key="l.to"
          :to="l.to"
          class="menu-item group flex items-baseline gap-3 py-2"
          :style="{ '--d': `${120 + i * 70}ms` }"
        >
          <span class="mono text-[10px] text-white/45">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="display text-3xl tracking-[0.08em] transition-transform duration-300 group-hover:translate-x-2 sm:text-4xl" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">
            {{ l.label }}
          </span>
          <span class="micro !text-white/55">{{ l.cn }}</span>
        </RouterLink>
      </nav>

      <!-- 底部登录态 -->
      <div class="menu-item absolute inset-x-0 bottom-0 flex items-center justify-between border-t border-white/15 px-6 py-4" style="--d: 560ms">
        <template v-if="auth.isLogin">
          <p class="text-sm text-white/85">你好，<span class="font-semibold">{{ auth.nickname }}</span></p>
          <button class="micro !text-white/70 transition hover:!text-white" @click="logout">退出 LOGOUT</button>
        </template>
        <template v-else>
          <p class="micro !text-white/60">MEMBER LOGIN</p>
          <RouterLink to="/login?mode=register" class="rounded-md bg-white px-4 py-1.5 text-sm font-semibold text-primary transition hover:bg-white/90">
            登录 / 注册
          </RouterLink>
        </template>
      </div>
    </div>
  </Transition>
</template>
