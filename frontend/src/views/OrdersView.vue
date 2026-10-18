<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { myOrders, payOrder, cancelOrder } from '@/api/order'

/**
 * 我的订单（需求 3.5）——真接口版。
 * GET /orders/my → OrderView[]；PENDING 可支付（带倒计时）/取消。
 * 状态机：PENDING_PAYMENT / PAID / CANCELLED / EXPIRED / REFUNDED。
 */
const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'PENDING_PAYMENT', label: '待支付' },
  { id: 'PAID', label: '已支付' },
  { id: 'CANCELLED', label: '已取消' },
  { id: 'EXPIRED', label: '已超时' },
]
const tab = ref('ALL')

// 四态
const orders = ref([])
const loading = ref(true)
const errMsg = ref('')
const actingNo = ref('')            // 正在支付/取消的订单号（防重复点击）
const actionErr = ref('')

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    orders.value = await myOrders()
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const filtered = computed(() =>
  tab.value === 'ALL' ? orders.value : orders.value.filter((o) => o.status === tab.value),
)

const fmtPrice = (p) => `¥${Number(p).toFixed(2)}`
const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '')

/** 倒计时：1s tick，按 payDeadline 算剩余 */
const now = ref(Date.now())
let timer
onMounted(() => { timer = setInterval(() => (now.value = Date.now()), 1000) })
onUnmounted(() => clearInterval(timer))

function countdown(o) {
  const deadline = new Date(o.payDeadline).getTime()
  const left = Math.max(0, Math.floor((deadline - now.value) / 1000))
  const m = String(Math.floor(left / 60)).padStart(2, '0')
  const s = String(left % 60).padStart(2, '0')
  return left > 0 ? `${m}:${s}` : '已超时'
}

async function pay(o) {
  actingNo.value = o.orderNo
  actionErr.value = ''
  try {
    // 就地更新状态，不整页重拉（体验更顺）
    const idx = orders.value.findIndex((x) => x.orderNo === o.orderNo)
    orders.value[idx] = { ...orders.value[idx], ...(await payOrder(o.orderNo)) }
  } catch (e) {
    actionErr.value = e.message
  } finally {
    actingNo.value = ''
  }
}

async function cancel(o) {
  actingNo.value = o.orderNo
  actionErr.value = ''
  try {
    const idx = orders.value.findIndex((x) => x.orderNo === o.orderNo)
    orders.value[idx] = { ...orders.value[idx], ...(await cancelOrder(o.orderNo)) }
  } catch (e) {
    actionErr.value = e.message
  } finally {
    actingNo.value = ''
  }
}
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">MY — ORDERS</p>
        <h1 class="serif text-2xl font-semibold">我的订单</h1>
      </div>
    </div>

    <div class="mb-5 flex flex-wrap gap-2">
      <button
        v-for="t in TABS"
        :key="t.id"
        class="rounded-full border px-3.5 py-1.5 text-sm transition"
        :class="tab === t.id ? 'border-primary bg-primary !text-[11px] text-white' : 'border-line bg-transparent text-dim hover:border-line-strong'"
        @click="tab = t.id"
      >
        {{ t.label }}
      </button>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="space-y-3">
      <div v-for="i in 3" :key="i" class="card h-20 animate-pulse"></div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">
      {{ errMsg }} <button class="ml-2 underline" @click="load">重试</button>
    </div>

    <div v-else-if="filtered.length" class="space-y-3">
      <div v-for="o in filtered" :key="o.orderNo" class="card flex flex-wrap items-center gap-4 p-4 sm:flex-nowrap">
        <!-- 单号与状态 -->
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <StatusBadge :status="o.status" />
            <p class="mono truncate text-xs text-faint">{{ o.orderNo }}</p>
          </div>
          <p class="mt-1 text-xs text-faint">{{ o.quantity }} 张 · {{ fmtTime(o.createdAt) }}</p>
        </div>

        <!-- 金额 + 倒计时 -->
        <div class="text-right">
          <p class="tabular font-bold">{{ fmtPrice(o.totalAmount) }}</p>
          <p v-if="o.status === 'PENDING_PAYMENT'" class="tabular mt-0.5 text-xs" :class="countdown(o) === '已超时' ? 'text-faint' : 'text-accent'">
            {{ countdown(o) === '已超时' ? '支付已超时' : `剩余 ${countdown(o)}` }}
          </p>
          <p v-else class="mt-0.5 text-xs text-faint">{{ fmtTime(o.paidAt || o.cancelledAt || '') }}</p>
        </div>

        <!-- 操作 -->
        <div class="flex gap-2">
          <button
            v-if="o.status === 'PENDING_PAYMENT' && countdown(o) !== '已超时'"
            class="btn-primary"
            :disabled="actingNo === o.orderNo"
            @click="pay(o)"
          >{{ actingNo === o.orderNo ? '处理中…' : '去支付' }}</button>
          <button
            v-if="o.status === 'PENDING_PAYMENT'"
            class="btn-ghost"
            :disabled="actingNo === o.orderNo"
            @click="cancel(o)"
          >取消</button>
        </div>
      </div>
      <p v-if="actionErr" class="text-sm text-red-500">{{ actionErr }}</p>
    </div>

    <EmptyState
      v-else
      icon="🎫"
      text="这里还没有订单"
      hint="去看一场喜欢的 LIVE 吧"
      action-text="浏览演出"
      @action="$router.push('/events')"
    />
  </div>
</template>
