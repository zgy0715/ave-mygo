<script setup>
import { onMounted, ref } from 'vue'
import { myOrders, cancelOrder } from '@/api/order'

const orders = ref([])
const loading = ref(true)
const errMsg = ref('')

const STATUS_STYLE = {
  PENDING_PAYMENT: 'bg-yellow-500/15 text-yellow-400',
  PAID: 'bg-green-500/15 text-green-400',
  CANCELLED: 'bg-gray-500/15 text-gray-500',
  EXPIRED: 'bg-red-500/15 text-red-400',
  REFUNDED: 'bg-blue-500/15 text-blue-400',
}
const STATUS_LABEL = { PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', EXPIRED: '已超时', REFUNDED: '已退款' }

async function load() {
  loading.value = true
  try { orders.value = await myOrders() } catch (e) { errMsg.value = e.message } finally { loading.value = false }
}
onMounted(load)

async function cancel(orderNo) {
  await cancelOrder(orderNo)
  await load()
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 19) : '' }
</script>

<template>
  <main class="mx-auto max-w-3xl px-4 pb-24 pt-8">
    <h1 class="mb-6 text-2xl font-bold">我的订单</h1>

    <div v-if="loading" class="h-32 animate-pulse rounded-xl bg-surface"></div>
    <div v-else-if="errMsg" class="rounded-lg bg-red-950/50 p-4 text-sm text-red-400">{{ errMsg }}</div>
    <div v-else-if="orders.length === 0" class="py-20 text-center text-gray-600">还没有订单，去看演出吧 🎤</div>

    <div v-else class="space-y-3">
      <div v-for="o in orders" :key="o.orderNo" class="rounded-xl border border-line bg-surface p-4">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-600">{{ o.orderNo }}</span>
          <span class="rounded px-2 py-0.5 text-[10px]" :class="STATUS_STYLE[o.status]">{{ STATUS_LABEL[o.status] }}</span>
        </div>
        <div class="mt-2 flex items-center justify-between">
          <div class="text-sm">
            <span class="text-gray-600">{{ o.quantity }} 张 · </span>
            <span class="tabular font-bold text-accent">¥{{ Number(o.totalAmount).toFixed(2) }}</span>
          </div>
          <button
            v-if="o.status === 'PENDING_PAYMENT'"
            class="rounded-lg border border-line px-3 py-1.5 text-xs text-gray-600 hover:border-accent hover:text-accent"
            @click="cancel(o.orderNo)"
          >取消订单</button>
        </div>
        <p class="mt-2 text-[10px] text-gray-700">创建于 {{ fmt(o.createdAt) }}</p>
      </div>
    </div>
  </main>
</template>
