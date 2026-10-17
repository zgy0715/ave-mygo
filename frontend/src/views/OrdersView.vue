<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { orders, eventById } from '@/data/mock'

/**
 * 我的订单（需求 3.5 简化版）：状态 Tab + 卡片列表。
 * 待支付订单带支付倒计时（以 payDeadline 为准，走查缺口④）。
 * 后端就绪后：GET /orders/my + 支付/取消接口。
 */
const route = useRoute()
const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'PENDING', label: '待支付' },
  { id: 'PAID', label: '已支付' },
  { id: 'CANCELLED', label: '已取消' },
]
const tab = ref('ALL')
const filtered = computed(() => (tab.value === 'ALL' ? orders : orders.filter((o) => o.status === tab.value)))

const fmtPrice = (fen) => `¥${(fen / 100).toFixed(0)}`

/** 倒计时：1s tick 一次，算 payDeadline 的剩余时间 */
const now = ref(Date.now())
let timer
onMounted(() => { timer = setInterval(() => (now.value = Date.now()), 1000) })
onUnmounted(() => clearInterval(timer))

function countdown(o) {
  const deadline = new Date(o.payDeadline.replace(' ', 'T')).getTime()
  const left = Math.max(0, Math.floor((deadline - now.value) / 1000))
  const m = String(Math.floor(left / 60)).padStart(2, '0')
  const s = String(left % 60).padStart(2, '0')
  return left > 0 ? `${m}:${s}` : '已超时'
}
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <h1 class="text-2xl font-black">我的订单</h1>
    </div>

    <div class="mb-5 flex gap-2">
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

    <div v-if="filtered.length" class="space-y-3">
      <div v-for="o in filtered" :key="o.id" class="card flex flex-wrap items-center gap-4 p-4 sm:flex-nowrap">
        <!-- 演出信息 -->
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <p class="truncate font-semibold">{{ eventById(o.eventId)?.title }}</p>
            <StatusBadge :status="o.status" />
          </div>
          <p class="mt-1 text-xs text-faint">
            {{ o.orderNo }} · {{ eventById(o.eventId)?.venue }} · {{ o.quantity }} 张
          </p>
        </div>

        <!-- 金额 + 倒计时 -->
        <div class="text-right">
          <p class="tabular font-bold">{{ fmtPrice(o.amount) }}</p>
          <p v-if="o.status === 'PENDING'" class="tabular mt-0.5 text-xs" :class="countdown(o) === '已超时' ? 'text-faint' : 'text-pink'">
            {{ countdown(o) === '已超时' ? '支付已超时' : `剩余 ${countdown(o)}` }}
          </p>
          <p v-else class="mt-0.5 text-xs text-faint">{{ o.createdAt }}</p>
        </div>

        <!-- 操作 -->
        <div class="flex gap-2">
          <button v-if="o.status === 'PENDING' && countdown(o) !== '已超时'" class="btn-primary">去支付</button>
          <button v-if="o.status === 'PENDING'" class="btn-ghost">取消</button>
        </div>
      </div>
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
