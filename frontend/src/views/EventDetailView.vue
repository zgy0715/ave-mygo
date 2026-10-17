<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEvent } from '@/api/event'
import { createOrder, payOrder, cancelOrder } from '@/api/order'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const event = ref(null)
const sessions = ref([])
const activeSessionIdx = ref(0)
const loading = ref(true)
const errMsg = ref('')

// ===== 下单面板 =====
const selectedType = ref(null)
const quantity = ref(1)
const ordering = ref(false)
const orderInfo = ref(null)        // 下单成功后的 {orderNo, totalAmount, payDeadline...}
const orderErr = ref('')
const remainSec = ref(0)           // 支付倒计时
let timer = null

const activeSession = computed(() => sessions.value[activeSessionIdx.value] || null)

onMounted(async () => {
  try {
    const data = await getEvent(route.params.id)
    event.value = data
    sessions.value = data.sessions
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
})
onBeforeUnmount(() => clearInterval(timer))

function selectType(t) {
  if (t.availableStock === 0) return
  selectedType.value = t
  quantity.value = 1
}

async function submitOrder() {
  if (!auth.isLogin) { router.push('/login'); return }
  ordering.value = true
  orderErr.value = ''
  try {
    const data = await createOrder({
      sessionId: activeSession.value.id,
      ticketTypeId: selectedType.value.id,
      quantity: quantity.value,
    })
    orderInfo.value = data
    startCountdown(data.payDeadline)
  } catch (e) {
    orderErr.value = e.message
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
    const data = await payOrder(orderInfo.value.orderNo)
    orderInfo.value = { ...orderInfo.value, ...data }
  } catch (e) { orderErr.value = e.message }
}

async function cancel() {
  try {
    await cancelOrder(orderInfo.value.orderNo)
    closeOrderModal()
  } catch (e) { orderErr.value = e.message }
}

function closeOrderModal() {
  orderInfo.value = null
  selectedType.value = null
  clearInterval(timer)
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
function fmtPrice(p) { return p != null ? Number(p).toFixed(2) : '—' }
</script>

<template>
  <main class="mx-auto max-w-4xl px-4 pb-24 pt-8">
    <div v-if="loading" class="h-64 animate-pulse rounded-xl bg-surface"></div>
    <div v-else-if="errMsg" class="rounded-lg bg-red-950/50 p-4 text-sm text-red-400">{{ errMsg }}</div>

    <template v-else-if="event">
      <!-- 演出头部 -->
      <section class="flex gap-5 rounded-2xl border border-line bg-surface p-6">
        <div class="flex h-36 w-56 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-800/40 to-pink-800/30 text-5xl">🎤</div>
        <div>
          <h1 class="text-xl font-bold text-white">{{ event.title }}</h1>
          <p class="mt-1 text-sm text-gray-500">{{ event.bandName }} · {{ event.venue }}</p>
          <p class="mt-3 text-sm leading-6 text-gray-400">{{ event.description }}</p>
        </div>
      </section>

      <!-- 场次切换 -->
      <div class="mt-6 flex gap-2">
        <button
          v-for="(s, i) in sessions" :key="s.id"
          class="rounded-lg border px-4 py-2 text-sm transition"
          :class="i === activeSessionIdx ? 'border-primary bg-primary/15 text-white' : 'border-line text-gray-400 hover:text-gray-200'"
          @click="activeSessionIdx = i; selectedType = null"
        >
          {{ s.name }}
          <span class="ml-1 text-xs text-gray-500">{{ fmt(s.startTime) }}</span>
        </button>
      </div>

      <!-- 票档 -->
      <section v-if="activeSession" class="mt-4 space-y-3">
        <p class="text-xs text-gray-500">开售时间：{{ fmt(activeSession.saleStartTime) }}</p>
        <div
          v-for="t in activeSession.ticketTypes" :key="t.id"
          class="flex cursor-pointer items-center justify-between rounded-xl border p-4 transition"
          :class="[
            selectedType?.id === t.id ? 'border-primary bg-primary/10' : 'border-line bg-surface hover:border-gray-500',
            t.availableStock === 0 ? 'cursor-not-allowed opacity-40' : ''
          ]"
          @click="selectType(t)"
        >
          <div>
            <p class="font-semibold text-white">{{ t.name }}</p>
            <p class="mt-0.5 text-xs text-gray-500">
              每人限购 {{ t.maxPerUser }} 张 · 余票
              <span class="tabular" :class="t.availableStock === 0 ? 'text-red-400' : 'text-gray-300'">
                {{ t.availableStock === 0 ? '售罄' : t.availableStock }}
              </span>
            </p>
          </div>
          <p class="tabular text-lg font-bold text-accent">¥{{ fmtPrice(t.price) }}</p>
        </div>

        <!-- 下单操作区 -->
        <div v-if="selectedType" class="flex items-center justify-between rounded-xl border border-line bg-elevated p-4">
          <div class="flex items-center gap-3">
            <span class="text-sm text-gray-400">数量</span>
            <button class="qty-btn" @click="quantity > 1 && quantity--">−</button>
            <span class="tabular w-8 text-center font-bold">{{ quantity }}</span>
            <button
              class="qty-btn"
              @click="quantity < Math.min(selectedType.maxPerUser, selectedType.availableStock, 10) && quantity++"
            >＋</button>
            <span class="tabular ml-4 text-lg font-bold text-accent">
              ¥{{ fmtPrice(selectedType.price * quantity) }}
            </span>
          </div>
          <button class="rounded-lg bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-violet-500" @click="submitOrder">
            {{ auth.isLogin ? '立即购票' : '登录后购票' }}
          </button>
        </div>
      </section>
    </template>

    <!-- ===== 下单/支付弹窗 ===== -->
    <div v-if="orderInfo" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="closeOrderModal">
      <div class="w-96 rounded-2xl border border-line bg-elevated p-6">
        <template v-if="orderInfo.status === 'PAID'">
          <p class="text-center text-4xl">🎉</p>
          <h3 class="mt-2 text-center font-bold text-white">购票成功！</h3>
          <p class="mt-2 text-center text-sm text-gray-400">{{ orderInfo.quantity }} 张 · ¥{{ fmtPrice(orderInfo.totalAmount) }}</p>
          <button class="mt-6 w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-white" @click="closeOrderModal">完成</button>
        </template>

        <template v-else>
          <h3 class="text-center font-bold text-white">订单已创建</h3>
          <p class="mt-1 text-center text-xs text-gray-500">单号 {{ orderInfo.orderNo }}</p>
          <div class="my-5 text-center">
            <p class="tabular text-4xl font-bold text-white">{{ countdownText }}</p>
            <p class="mt-1 text-xs text-gray-500">内完成支付，逾期订单自动取消</p>
            <p class="tabular mt-3 text-2xl font-bold text-accent">¥{{ fmtPrice(orderInfo.totalAmount) }}</p>
          </div>
          <p v-if="orderErr" class="mb-3 text-center text-sm text-accent">{{ orderErr }}</p>
          <button class="w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-white hover:bg-violet-500" @click="pay">
            模拟支付
          </button>
          <button class="mt-2 w-full rounded-lg border border-line py-2.5 text-sm text-gray-400 hover:text-gray-200" @click="cancel">
            取消订单
          </button>
        </template>
      </div>
    </div>
  </main>
</template>
