<script setup>
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { myOrders } from '@/api/order'
import { listFavorites } from '@/api/favorite'
import { myReminders } from '@/api/reminder'
import PageBack from '@/components/PageBack.vue'

/** 个人中心：用户信息卡 + 快捷入口（收藏/歌单为 P1，先占位） */
const auth = useAuthStore()
const orderCount = ref('…')
const favoriteCount = ref('…')
const reminders = ref([])

/** 开售倒计时：剩余时间文案 */
function countdown(t) {
  if (!t) return '待定'
  const diff = new Date(String(t).replace(' ', 'T')).getTime() - Date.now()
  if (diff <= 0) return '已开售'
  const h = Math.floor(diff / 3600000)
  if (h >= 24) return `${Math.floor(h / 24)} 天后开售`
  if (h >= 1) return `${h} 小时后开售`
  return `${Math.max(1, Math.floor(diff / 60000))} 分钟后开售`
}

onMounted(async () => {
  try {
    orderCount.value = (await myOrders()).length
  } catch {
    orderCount.value = '—'
  }
  try {
    const [s, e] = await Promise.all([listFavorites('SONG'), listFavorites('EVENT')])
    favoriteCount.value = s.length + e.length
  } catch {
    favoriteCount.value = '—'
  }
  try {
    reminders.value = await myReminders()
  } catch {
    reminders.value = []
  }
})
</script>

<template>
  <div class="wrap py-8">
    <PageBack label="返回" fallback="/" />
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">MY — PROFILE</p>
        <h1 class="serif text-2xl font-semibold">个人中心</h1>
      </div>
    </div>

    <div class="card flex items-center gap-4 p-6">
      <div class="flex h-14 w-14 items-center justify-center rounded-full border border-primary/40 text-xl font-semibold text-primary">
        {{ auth.nickname.slice(0, 1).toUpperCase() }}
      </div>
      <div>
        <p class="text-lg font-bold">{{ auth.nickname }}</p>
        <p class="text-sm text-dim">@{{ auth.user?.username }} · {{ auth.user?.role === 'ADMIN' ? '管理员' : '普通用户' }}</p>
      </div>
    </div>

    <div class="mt-5 grid gap-4 sm:grid-cols-3">
      <RouterLink to="/favorites" class="card p-5 hover:shadow-md">
        <p class="text-2xl font-semibold text-primary tabular">{{ favoriteCount }}</p>
        <p class="mt-1 text-sm text-dim">我的收藏</p>
      </RouterLink>
      <RouterLink to="/orders" class="card p-5 hover:shadow-md">
        <p class="text-2xl font-semibold text-primary tabular">{{ orderCount }}</p>
        <p class="mt-1 text-sm text-dim">我的订单</p>
      </RouterLink>
      <RouterLink to="/playlists" class="card p-5 hover:shadow-md">
        <p class="text-2xl font-semibold text-primary">♫</p>
        <p class="mt-1 text-sm text-dim">我的歌单</p>
      </RouterLink>
    </div>

    <!-- 我的提醒：未开售演出的开售倒计时 -->
    <section v-if="reminders.length" class="mt-8">
      <h2 class="serif mb-3 text-lg font-semibold">我的提醒</h2>
      <ul class="card divide-y divide-line overflow-hidden">
        <li v-for="r in reminders" :key="r.eventId">
          <RouterLink :to="`/events/${r.eventId}`" class="flex items-center gap-3 p-3 transition hover:bg-elevated sm:px-4">
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">🔔</span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ r.eventTitle }}</p>
              <p class="truncate text-[11px] text-faint">{{ r.venue }}</p>
            </div>
            <span class="tabular shrink-0 text-xs" :class="r.notified ? 'text-faint' : 'text-primary'">
              {{ r.notified ? '已开售' : countdown(r.saleStartTime) }}
            </span>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>
