<script setup>
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SearchBox from '@/components/SearchBox.vue'

/**
 * 顶部栏 v4：极简化（anontokyo 式）——只留 Logo + 登录态。
 * 全站导航移到右缘 MENU 页签（SideMenu），登录/注册做成醒目实心按钮。
 */
const auth = useAuthStore()
const route = useRoute()
</script>

<template>
  <header class="sticky top-0 z-30 border-b border-line bg-base/85 backdrop-blur-md">
    <nav class="wrap flex h-16 items-center justify-between gap-4 !pr-16 sm:!pr-24">
      <!-- Logo：衬线 + mono 双层（给右缘 MENU 页签留出位置） -->
      <RouterLink to="/" class="flex items-baseline gap-2">
        <span class="serif text-xl font-semibold tracking-wide text-ink">BanG Dream!</span>
        <span class="micro-accent">/ LIVE</span>
      </RouterLink>

      <!-- 右侧：搜索 + 登录态 -->
      <div class="flex items-center gap-2 sm:gap-3">
        <SearchBox />
        <template v-if="auth.isLogin">
          <RouterLink
            v-if="auth.user?.role === 'ADMIN'"
            to="/admin"
            class="micro hidden py-1 transition-colors duration-300 hover:text-primary sm:block"
            :class="route.path.startsWith('/admin') && 'text-primary'"
          >
            管理 ADMIN
          </RouterLink>
          <RouterLink
            to="/orders"
            class="micro hidden py-1 transition-colors duration-300 hover:text-primary sm:block"
            :class="route.path.startsWith('/orders') && 'text-primary'"
          >
            我的订单 ORDERS
          </RouterLink>
          <RouterLink
            to="/profile"
            class="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-semibold text-white shadow-md transition-transform duration-300 hover:scale-105"
            :title="auth.nickname"
            style="transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1)"
          >
            {{ auth.nickname.slice(0, 1).toUpperCase() }}
          </RouterLink>
        </template>
        <RouterLink v-else to="/login" class="link-ext mono text-xs tracking-[0.2em] text-ink">
          登录 LOGIN <span class="arrow">↗</span>
        </RouterLink>
      </div>
    </nav>
  </header>
</template>
