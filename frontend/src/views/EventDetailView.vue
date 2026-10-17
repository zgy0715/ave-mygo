<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { eventById, bandById, stockLevel } from '@/data/mock'

/**
 * 演出详情（需求 3.4，购票最重页）：
 * 场次 Tab → 票档表（价格/余票状态/数量选择/购票）。
 * 购票：未登录 → 登录引导（带 redirect）；已登录 → mock 下单 → /orders。
 * 后端就绪后：下单调 POST /orders，40901/40903 在行内提示「仅剩 N 张」。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const event = eventById(route.params.id)
const band = computed(() => event && bandById(event.band))
const activeSession = ref(event?.sessions[0]?.id)
const session = computed(() => event?.sessions.find((s) => s.id === activeSession.value))
const quantities = reactive({})

const fmtPrice = (fen) => `¥${(fen / 100).toFixed(0)}`
const left = (tt) => tt.totalStock - tt.soldCount

const LEVEL = {
  ok: { text: '余票充足', cls: 'text-green-700' },
  low: { text: '余票紧张', cls: 'text-amber-600' },
  critical: { text: '余票告急', cls: 'text-red-600' },
  soldout: { text: '已售罄', cls: 'text-faint' },
}

function buy(tt) {
  const qty = quantities[tt.id] || 1
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  if (left(tt) < qty) return alert(`仅剩 ${left(tt)} 张`)
  // mock 下单：真实实现为 POST /orders → 跳 /checkout/:orderNo（P0 后端联调时替换）
  router.push({ path: '/orders', query: { created: tt.id, qty } })
}
</script>

<template>
  <div v-if="event" class="wrap py-8">
    <RouterLink to="/events" class="mb-6 inline-block text-sm text-dim transition hover:text-primary">← 返回演出列表</RouterLink>

    <!-- 头部：海报 + 信息 -->
    <div class="grid gap-6 md:grid-cols-[280px_1fr]">
      <div class="flex h-72 flex-col justify-between rounded-xl p-5 text-white md:h-80" :style="{ background: band?.gradient }">
        <span class="mono text-xs tracking-[0.2em] opacity-80">{{ event.date }} {{ event.openTime }} OPEN</span>
        <p class="text-2xl leading-snug font-black" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">{{ event.title }}</p>
      </div>

      <div class="flex flex-col justify-center">
        <div class="flex items-center gap-3">
          <span class="rounded px-2 py-0.5 text-xs font-medium" :style="{ background: band?.soft, color: band?.color }">{{ band?.name }}</span>
          <StatusBadge :status="event.status" />
        </div>
        <h1 class="mt-3 text-3xl font-black">{{ event.title }}</h1>
        <dl class="mt-5 grid grid-cols-2 gap-y-2 text-sm">
          <div><dt class="text-faint">会场</dt><dd class="mt-0.5 font-medium">{{ event.venue }}</dd></div>
          <div><dt class="text-faint">日期</dt><dd class="tabular mt-0.5 font-medium">{{ event.date }}</dd></div>
          <div><dt class="text-faint">开场 / 开演</dt><dd class="tabular mt-0.5 font-medium">{{ event.openTime }} / {{ event.startTime }}</dd></div>
        </dl>
      </div>
    </div>

    <!-- 简介 -->
    <p class="mt-6 max-w-3xl text-sm leading-relaxed text-dim">{{ event.description }}</p>

    <!-- 购票区 -->
    <section v-if="event.status === 'ONSALE' || event.status === 'NOT_OPEN'" class="mt-8">
      <h2 class="mb-3 text-lg font-bold">选票</h2>

      <!-- 场次 Tab -->
      <div class="mb-4 flex flex-wrap gap-2">
        <button
          v-for="s in event.sessions"
          :key="s.id"
          class="rounded-lg border px-4 py-2 text-sm transition"
          :class="activeSession === s.id ? 'border-primary text-primary' : 'border-line bg-transparent text-dim hover:border-line-strong'"
          @click="activeSession = s.id"
        >
          {{ s.name }}<span class="tabular ml-1.5 text-xs text-faint">{{ s.startTime }}</span>
        </button>
      </div>

      <!-- 票档表 -->
      <div v-if="session" class="card divide-y divide-line overflow-hidden">
        <div v-for="tt in session.ticketTypes" :key="tt.id" class="flex flex-wrap items-center gap-3 p-4 sm:flex-nowrap sm:gap-4">
          <div class="w-24 shrink-0">
            <p class="font-semibold">{{ tt.name }}</p>
            <p class="tabular mt-0.5 text-sm text-dim">{{ fmtPrice(tt.price) }}</p>
          </div>
          <div class="w-24 shrink-0">
            <p class="text-xs font-medium" :class="LEVEL[stockLevel(tt)].cls">{{ LEVEL[stockLevel(tt)].text }}</p>
            <p class="tabular mt-0.5 text-xs text-faint">余 {{ left(tt) }}</p>
          </div>

          <div v-if="stockLevel(tt) !== 'soldout' && event.status === 'ONSALE'" class="flex items-center gap-1 sm:ml-auto">
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="quantities[tt.id] = Math.max(1, (quantities[tt.id] || 1) - 1)">−</button>
            <span class="tabular w-8 text-center text-sm font-medium">{{ quantities[tt.id] || 1 }}</span>
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="quantities[tt.id] = Math.min(4, (quantities[tt.id] || 1) + 1)">＋</button>
          </div>

          <button
            class="btn-primary sm:ml-auto"
            :disabled="stockLevel(tt) === 'soldout' || event.status !== 'ONSALE'"
            @click="buy(tt)"
          >
            {{ event.status === 'NOT_OPEN' ? '未开售' : stockLevel(tt) === 'soldout' ? '已售罄' : '立即购买' }}
          </button>
        </div>
      </div>
    </section>

    <p class="mt-4 text-xs text-faint">每人每票档限购 4 张；下单后 15 分钟内未支付将自动取消。</p>
  </div>

  <div v-else class="wrap py-16">
    <EmptyState icon="🎤" text="演出不存在" action-text="回到演出列表" @action="router.push('/events')" />
  </div>
</template>
