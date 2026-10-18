<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { getEvent, listBands } from '@/api/event'
import { createOrder, payOrder, cancelOrder } from '@/api/order'

/**
 * 演出详情（需求 3.4，购票最重页）——真接口版。
 * 场次 Tab → 票档表（实时余票/数量/限购）→ 下单弹窗（15 分钟倒计时 → 模拟支付/取消）。
 * 后端契约：GET /events/{id}；POST /orders；POST /orders/{no}/pay；PATCH /orders/{no}/cancel。
 * 错误码：40901 余票不足 / 40902 状态不可购 / 40903 超限购 —— 均映射为行内提示。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const event = ref(null)
const bandSlug = ref('mygo')
const loading = ref(true)
const errMsg = ref('')

const activeSessionId = ref(null)
const session = computed(() => event.value?.sessions.find((s) => s.id === activeSessionId.value))

const quantities = ref({})          // ticketTypeId → 数量
const ordering = ref(false)
const buyErr = ref('')

// 下单弹窗状态
const orderInfo = ref(null)         // OrderCreated / OrderView
const remainSec = ref(0)
let timer = null

const fmtPrice = (p) => `¥${Number(p).toFixed(2)}`
const fmtTime = (t) => (t ? t.replace('T', ' ').slice(5, 16) : '')

function stockLevel(tt) {
  if (tt.availableStock <= 0) return 'soldout'
  if (tt.availableStock <= 5) return 'critical'
  if (tt.availableStock <= 20) return 'low'
  return 'ok'
}
const LEVEL = {
  ok: { text: '余票充足', cls: 'text-green-700' },
  low: { text: '余票紧张', cls: 'text-amber-600' },
  critical: { text: '余票告急', cls: 'text-red-600' },
  soldout: { text: '已售罄', cls: 'text-faint' },
}

const qtyOf = (tt) => quantities.value[tt.id] || 1
function changeQty(tt, delta) {
  const cap = Math.min(tt.maxPerUser, tt.availableStock)
  quantities.value[tt.id] = Math.min(cap, Math.max(1, qtyOf(tt) + delta))
}

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const [data, bands] = await Promise.all([getEvent(route.params.id), listBands()])
    event.value = data
    activeSessionId.value = data.sessions[0]?.id ?? null
    bandSlug.value = bands.find((b) => b.name === data.bandName)?.slug || 'mygo'
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
load()

// ===== 下单 → 倒计时 → 支付/取消 =====
async function buy(tt) {
  if (!auth.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  ordering.value = true
  buyErr.value = ''
  try {
    const data = await createOrder({
      sessionId: session.value.id,
      ticketTypeId: tt.id,
      quantity: qtyOf(tt),
    })
    orderInfo.value = data
    startCountdown(data.payDeadline)
  } catch (e) {
    buyErr.value = e.message          // 40901 余票不足 / 40903 超限购
  } finally {
    ordering.value = false
  }
}

function startCountdown(deadline) {
  clearInterval(timer)
  remainSec.value = Math.max(0, Math.floor((new Date(deadline) - Date.now()) / 1000))
  timer = setInterval(() => {
    remainSec.value--
    if (remainSec.value <= 0) clearInterval(timer)
  }, 1000)
}
const countdownText = computed(() => {
  const m = Math.floor(remainSec.value / 60), s = remainSec.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

async function pay() {
  try {
    orderInfo.value = { ...orderInfo.value, ...(await payOrder(orderInfo.value.orderNo)) }
  } catch (e) { buyErr.value = e.message }
}

async function cancel() {
  try {
    await cancelOrder(orderInfo.value.orderNo)
    closeOrder()
  } catch (e) { buyErr.value = e.message }
}

function closeOrder() {
  orderInfo.value = null
  clearInterval(timer)
  load()                            // 回滚/扣减后刷新实时余票
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div v-if="loading" class="wrap py-8"><div class="h-72 animate-pulse rounded-xl bg-surface"></div></div>

  <div v-else-if="errMsg" class="wrap py-8">
    <div class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></div>
  </div>

  <div v-else-if="event" class="wrap py-8">
    <RouterLink to="/events" class="mb-6 inline-block text-sm text-dim transition hover:text-primary">← 返回演出列表</RouterLink>

    <!-- 头部：海报 + 信息 -->
    <div class="grid gap-6 md:grid-cols-[280px_1fr]">
      <div class="relative flex h-72 flex-col justify-between overflow-hidden rounded-xl p-5 text-white md:h-80">
        <div class="absolute inset-0" :style="{ background: bandSlug === 'mygo' ? 'linear-gradient(135deg,#2c7da0,#1f6485)' : 'linear-gradient(135deg,#6b4fa3,#4a3573)' }"></div>
        <span class="mono relative text-xs tracking-[0.2em] opacity-80">{{ event.sessions[0] ? fmtTime(event.sessions[0].startTime) : '' }}</span>
        <p class="relative text-2xl leading-snug font-black" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">{{ event.title }}</p>
      </div>

      <div class="flex flex-col justify-center">
        <div class="flex items-center gap-3">
          <span class="rounded bg-primary-soft px-2 py-0.5 text-xs font-medium text-primary">{{ event.bandName }}</span>
          <StatusBadge :status="event.status" />
        </div>
        <h1 class="serif mt-3 text-3xl font-semibold sm:text-4xl">{{ event.title }}</h1>
        <dl class="mt-5 grid grid-cols-2 gap-y-2 text-sm">
          <div><dt class="text-faint">会场</dt><dd class="mt-0.5 font-medium">{{ event.venue }}</dd></div>
          <div><dt class="text-faint">状态</dt><dd class="mt-0.5 font-medium">{{ event.status }}</dd></div>
        </dl>
      </div>
    </div>

    <!-- 简介 -->
    <p class="mt-6 max-w-3xl text-sm leading-relaxed text-dim">{{ event.description }}</p>

    <!-- 购票区 -->
    <section class="mt-8">
      <h2 class="mb-3 text-lg font-bold">选票</h2>

      <!-- 场次 Tab -->
      <div class="mb-4 flex flex-wrap gap-2">
        <button
          v-for="s in event.sessions"
          :key="s.id"
          class="rounded-lg border px-4 py-2 text-sm transition"
          :class="activeSessionId === s.id ? 'border-primary text-primary' : 'border-line bg-transparent text-dim hover:border-line-strong'"
          @click="activeSessionId = s.id"
        >
          {{ s.name }}<span class="tabular ml-1.5 text-xs text-faint">{{ fmtTime(s.startTime) }}</span>
        </button>
      </div>

      <!-- 票档表：availableStock 为后端实时派生值 -->
      <div v-if="session" class="card divide-y divide-line overflow-hidden">
        <div v-for="tt in session.ticketTypes" :key="tt.id" class="flex flex-wrap items-center gap-3 p-4 sm:flex-nowrap sm:gap-4">
          <div class="w-24 shrink-0">
            <p class="font-semibold">{{ tt.name }}</p>
            <p class="tabular mt-0.5 text-sm text-dim">{{ fmtPrice(tt.price) }}</p>
          </div>
          <div class="w-24 shrink-0">
            <p class="text-xs font-medium" :class="LEVEL[stockLevel(tt)].cls">{{ LEVEL[stockLevel(tt)].text }}</p>
            <p class="tabular mt-0.5 text-xs text-faint">余 {{ tt.availableStock }}</p>
          </div>

          <div v-if="stockLevel(tt) !== 'soldout' && event.status === 'PUBLISHED'" class="flex items-center gap-1 sm:ml-auto">
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="changeQty(tt, -1)">−</button>
            <span class="tabular w-8 text-center text-sm font-medium">{{ qtyOf(tt) }}</span>
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="changeQty(tt, 1)">＋</button>
          </div>

          <button
            class="btn-primary sm:ml-auto"
            :disabled="stockLevel(tt) === 'soldout' || event.status !== 'PUBLISHED' || session.status !== 'ONSALE'"
            @click="buy(tt)"
          >
            {{ session.status !== 'ONSALE' ? (session.status === 'NOT_OPEN' ? '未开售' : '已结束') : stockLevel(tt) === 'soldout' ? '已售罄' : '立即购买' }}
          </button>
        </div>
      </div>
      <p v-if="buyErr && !orderInfo" class="mt-3 text-sm text-red-500">{{ buyErr }}</p>
    </section>

    <p class="mt-4 text-xs text-faint">每人每票档限购 {{ session?.ticketTypes[0]?.maxPerUser || 4 }} 张；下单后 15 分钟内未支付将自动取消。</p>
  </div>

  <div v-else class="wrap py-16">
    <EmptyState icon="🎤" text="演出不存在" action-text="回到演出列表" @action="router.push('/events')" />
  </div>

  <!-- ===== 下单弹窗：倒计时 + 模拟支付/取消 ===== -->
  <div v-if="orderInfo" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" @click.self="orderInfo.status === 'PENDING_PAYMENT' && cancel()">
    <div class="card w-full max-w-sm p-7 text-center">
      <template v-if="orderInfo.status === 'PAID'">
        <p class="text-4xl">🎉</p>
        <h3 class="mt-3 text-lg font-bold">购票成功</h3>
        <p class="mt-2 text-sm text-dim">{{ orderInfo.quantity }} 张 · {{ fmtPrice(orderInfo.totalAmount) }}</p>
        <RouterLink to="/orders" class="btn-primary mt-6 w-full">查看我的订单</RouterLink>
      </template>

      <template v-else>
        <h3 class="text-lg font-bold">订单已创建</h3>
        <p class="mono mt-1 text-[10px] tracking-[0.15em] text-faint">{{ orderInfo.orderNo }}</p>
        <p class="tabular my-5 text-5xl font-bold" :class="remainSec <= 60 ? 'text-red-500' : 'text-ink'">{{ countdownText }}</p>
        <p class="tabular text-2xl font-bold text-accent">{{ fmtPrice(orderInfo.totalAmount) }}</p>
        <p class="mt-1 text-xs text-faint">内完成支付，逾期订单自动取消</p>

        <p v-if="buyErr" class="mt-3 text-sm text-red-500">{{ buyErr }}</p>

        <button class="btn-primary mt-5 w-full" @click="pay">模拟支付</button>
        <button class="btn-ghost mt-2 w-full" @click="cancel">取消订单</button>
      </template>
    </div>
  </div>
</template>
