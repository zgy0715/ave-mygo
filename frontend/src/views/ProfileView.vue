<script setup>
import { useAuthStore } from '@/stores/auth'
import { orders } from '@/data/mock'

/** 个人中心：用户信息卡 + 快捷入口（收藏/歌单为 P1，先占位） */
const auth = useAuthStore()
const myOrderCount = orders.length
</script>

<template>
  <div class="wrap py-8">
    <h1 class="mb-6 text-2xl font-black">个人中心</h1>

    <div class="card flex items-center gap-4 p-6">
      <div class="flex h-14 w-14 items-center justify-center rounded-full bg-primary text-xl font-bold text-white">
        {{ auth.nickname.slice(0, 1).toUpperCase() }}
      </div>
      <div>
        <p class="text-lg font-bold">{{ auth.nickname }}</p>
        <p class="text-sm text-dim">@{{ auth.user?.username }} · 普通用户</p>
      </div>
    </div>

    <div class="mt-5 grid gap-4 sm:grid-cols-3">
      <RouterLink v-if="false" to="/favorites" class="card p-5 hover:shadow-md">
        <p class="text-2xl font-black text-primary">0</p>
        <p class="mt-1 text-sm text-dim">我的收藏</p>
      </RouterLink>
      <RouterLink to="/orders" class="card p-5 hover:shadow-md">
        <p class="text-2xl font-black text-primary tabular">{{ myOrderCount }}</p>
        <p class="mt-1 text-sm text-dim">我的订单</p>
      </RouterLink>
      <div class="card p-5 opacity-60">
        <p class="text-2xl font-black text-faint">P1</p>
        <p class="mt-1 text-sm text-dim">收藏 / 歌单（待开发）</p>
      </div>
    </div>
  </div>
</template>
