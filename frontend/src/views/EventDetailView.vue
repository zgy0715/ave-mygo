<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import PageBack from '@/components/PageBack.vue'
import LoginGate from '@/components/LoginGate.vue'
import FavoriteButton from '@/components/FavoriteButton.vue'
import { myReminders, subscribeReminder, cancelReminder } from '@/api/reminder'
import { isGateDismissed } from '@/utils/loginGate'
import { useAuthStore } from '@/stores/auth'
import { getEvent, getSetlist } from '@/api/event'
import { listBands } from '@/api/song'
import { createOrder, payOrder, cancelOrder, myOrders } from '@/api/order'

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
const boughtBySession = ref({})     // sessionId → 该用户已购张数（限购要扣掉）
const orderExpired = ref(false)     // 倒计时归零：弹窗切"已超时"态
const setlist = ref([])             // 当前场次歌单
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

/** 本场次剩余可购 = maxPerUser - 已购（后端按场次累计限购，前端要同口径） */
function remainingAllowance(tt) {
  if (!session.value) return 0
  const max = tt.maxPerUser || 4
  const bought = boughtBySession.value[session.value.id] || 0
  return Math.max(0, max - bought)
}

function changeQty(tt, delta) {
  const cap = Math.min(remainingAllowance(tt), tt.availableStock)
  if (cap <= 0) return
  quantities.value[tt.id] = Math.min(cap, Math.max(1, qtyOf(tt) + delta))
}

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const [data, bands] = await Promise.all([getEvent(route.params.id), listBands()])
    event.value = data
    // 刷新时保留用户当前选中的场次（支付后重查余票不应把用户弹回第一场）
    const prev = activeSessionId.value
    activeSessionId.value = data.sessions.some((s) => s.id === prev) ? prev : (data.sessions[0]?.id ?? null)
    loadReminderState()
    bandSlug.value = bands.find((b) => b.name === data.bandName)?.slug || 'mygo'
    loadSetlist()
    loadMyPurchases()
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
load()

// 详情页互跳时组件被复用，需监听 id 变化重新加载
watch(() => route.params.id, (id, old) => {
  if (id && id !== old) { window.scrollTo(0, 0); load() }
})

/** 登录用户在本场各场次已购数量：限购校验与数量上限都要减掉它（后端按场次累计） */
async function loadMyPurchases() {
  if (!auth.isLogin) return (boughtBySession.value = {})
  try {
    const orders = await myOrders()
    const map = {}
    for (const o of orders) {
      if (o.status === 'PENDING_PAYMENT' || o.status === 'PAID') {
        map[o.sessionId] = (map[o.sessionId] || 0) + o.quantity
      }
    }
    boughtBySession.value = map
  } catch {
    boughtBySession.value = {}      // 拉取失败不阻塞购票，后端仍会兜底拦截
  }
}

/** 支付/取消后刷新演出数据：余票与售罄状态必须立刻更新 */
async function refreshEvent() {
  try {
    event.value = await getEvent(route.params.id)
  } catch { /* 刷新失败保持原数据，不影响弹窗结果 */ }
}

async function loadSetlist() {
  if (!activeSessionId.value) return (setlist.value = [])
  try {
    setlist.value = await getSetlist(activeSessionId.value)
  } catch {
    setlist.value = []   // 歌单拉取失败不阻塞购票主流程
  }
}

// 切场次 → 歌单跟随
function pickSession(id) {
  activeSessionId.value = id
  loadSetlist()
}

// ===== 下单 → 倒计时 → 支付/取消 =====
const gateOpen = ref(false)

// ===== 开售提醒 =====
const reminded = ref(false)
const reminding = ref(false)
async function loadReminderState() {
  reminded.value = false
  if (!auth.isLogin || !event.value) return
  try {
    const list = await myReminders()
    reminded.value = list.some((r) => r.eventId === Number(route.params.id))
  } catch { /* 忽略 */ }
}
async function toggleReminder() {
  if (!requireLogin('购票')) return
  reminding.value = true
  try {
    if (reminded.value) { await cancelReminder(route.params.id); reminded.value = false }
    else { await subscribeReminder(route.params.id); reminded.value = true }
  } catch (e) {
    buyErr.value = e.message
  } finally {
    reminding.value = false
  }
}

/** 需要登录的操作：优先弹引导弹窗；用户选过「随便逛逛」则直接跳登录 */
function requireLogin(action) {
  if (auth.isLogin) return true
  if (isGateDismissed()) router.push({ path: '/login', query: { redirect: route.fullPath } })
  else gateOpen.value = true
  return false
}

async function buy(tt) {
  if (!requireLogin('购票')) return
  ordering.value = true
  buyErr.value = ''
  try {
    const data = await createOrder({
      sessionId: session.value.id,
      ticketTypeId: tt.id,
      quantity: qtyOf(tt),
    })
    orderInfo.value = data
    orderExpired.value = false
    startCountdown(data.payDeadline)
  } catch (e) {
    buyErr.value = e.message          // 40901 余票不足 / 40903 超限购
  } finally {
    ordering.value = false
  }
}

function startCountdown(deadline) {
  clearInterval(timer)
  orderExpired.value = false
  remainSec.value = Math.max(0, Math.floor((new Date(deadline) - Date.now()) / 1000))
  if (remainSec.value <= 0) orderExpired.value = true
  timer = setInterval(() => {
    remainSec.value--
    if (remainSec.value <= 0) {
      clearInterval(timer)
      orderExpired.value = true          // 归零：切"已超时"，禁止继续点支付
    }
  }, 1000)
}
const countdownText = computed(() => {
  const m = Math.floor(remainSec.value / 60), s = remainSec.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

async function pay() {
  try {
    orderInfo.value = { ...orderInfo.value, ...(await payOrder(orderInfo.value.orderNo)) }
    await load()    // 支付成功：重查演出，余票/已售立刻更新
  } catch (e) { buyErr.value = e.message }
}

async function cancel() {
  try {
    await cancelOrder(orderInfo.value.orderNo)
    closeOrder()
    await refreshEvent()                                     // 取消回滚库存 → 余票刷新
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
    <PageBack label="返回演出列表" fallback="/events" />

    <!-- 头部：海报 + 信息 -->
    <div class="grid gap-6 md:grid-cols-[280px_1fr]">
      <div class="relative flex h-72 flex-col justify-between overflow-hidden rounded-xl p-5 text-white md:h-80">
        <img v-if="event.posterUrl" :src="event.posterUrl" :alt="event.title" class="absolute inset-0 h-full w-full object-cover">
        <div v-else class="absolute inset-0" :style="{ background: bandSlug === 'mygo' ? 'linear-gradient(135deg,#2c7da0,#1f6485)' : 'linear-gradient(135deg,#6b4fa3,#4a3573)' }"></div>
        <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-black/15 to-transparent"></div>
        <span class="mono relative text-xs tracking-[0.2em] opacity-80">{{ event.sessions[0] ? fmtTime(event.sessions[0].startTime) : '' }}</span>
        <p class="relative text-2xl leading-snug font-black" style="text-shadow: 0 1px 8px rgba(0,0,0,.3)">{{ event.title }}</p>
      </div>

      <div class="flex flex-col justify-center">
        <div class="flex items-center gap-3">
          <span class="rounded bg-primary-soft px-2 py-0.5 text-xs font-medium text-primary">{{ event.bandName }}</span>
          <StatusBadge :status="event.status" />
        </div>
        <h1 class="serif mt-3 text-3xl font-semibold sm:text-4xl">{{ event.title }}</h1>
        <div class="mt-3 flex items-center gap-2">
          <FavoriteButton type="EVENT" :id="event.id" :size="16" />
          <span class="text-xs text-faint">收藏这场演出</span>
        </div>
        <dl class="mt-5 grid grid-cols-2 gap-y-2 text-sm">
          <div><dt class="text-faint">会场</dt><dd class="mt-0.5 font-medium">{{ event.venue }}</dd></div>
          <div><dt class="text-faint">状态</dt><dd class="mt-0.5 font-medium">{{ event.status }}</dd></div>
        </dl>
      </div>
    </div>

    <!-- 简介 -->
    <p class="mt-6 max-w-3xl text-sm leading-relaxed text-dim">{{ event.description }}</p>

    <!-- 本场歌单 -->
    <section v-if="setlist.length" class="mt-10">
      <div class="section-head">
        <div>
          <p class="micro-accent mb-1.5">SETLIST — {{ session?.name }}</p>
          <h2 class="serif text-2xl font-semibold">本场歌单</h2>
        </div>
        <p class="micro">{{ setlist.length }} SONGS</p>
      </div>
      <ul class="card divide-y divide-line overflow-hidden">
        <li v-for="item in setlist" :key="item.orderNo" class="flex items-center gap-3 p-3 transition hover:bg-elevated sm:px-4">
          <span class="mono w-8 text-xs" :class="item.encore ? 'text-live' : 'text-faint'">
            {{ item.encore ? '✳' : String(item.orderNo).padStart(2, '0') }}
          </span>
          <RouterLink :to="`/songs/${item.songId}`" class="min-w-0 flex-1 truncate text-sm font-medium hover:text-primary">
            {{ item.songTitle }}
            <span v-if="item.encore" class="ml-2 rounded bg-live/10 px-1.5 py-0.5 text-[10px] font-bold text-live">ENCORE</span>
          </RouterLink>
          <span v-if="item.duration" class="tabular text-xs text-faint">
            {{ Math.floor(item.duration / 60) }}:{{ String(item.duration % 60).padStart(2, '0') }}
          </span>
        </li>
      </ul>
    </section>

    <!-- 购票区 -->
    <section class="mt-8">
      <div class="mb-3 flex items-center justify-between gap-3">
        <h2 class="text-lg font-bold">选票</h2>
        <!-- 未开售才提供开售提醒（已开售直接买就行） -->
        <button
          v-if="session && session.status === 'NOT_OPEN'"
          class="btn-ghost !px-3 !py-1.5 !text-xs"
          :class="reminded ? '!border-primary !text-primary' : ''"
          :disabled="reminding"
          @click="toggleReminder"
        >
          {{ reminding ? '处理中…' : (reminded ? '已订阅开售提醒 ✓' : '🔔 开售提醒我') }}
        </button>
      </div>

      <!-- 场次 Tab -->
      <div class="mb-4 flex flex-wrap gap-2">
        <button
          v-for="s in event.sessions"
          :key="s.id"
          class="rounded-lg border px-4 py-2 text-sm transition"
          :class="activeSessionId === s.id ? 'border-primary text-primary' : 'border-line bg-transparent text-dim hover:border-line-strong'"
          @click="pickSession(s.id)"
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

          <div v-if="remainingAllowance(tt) <= 0" class="text-xs text-amber-600 sm:ml-auto">
            本场已达限购上限（{{ tt.maxPerUser }} 张）
          </div>
          <div v-else-if="stockLevel(tt) !== 'soldout' && event.status === 'PUBLISHED'" class="flex items-center gap-1 sm:ml-auto">
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="changeQty(tt, -1)">−</button>
            <span class="tabular w-8 text-center text-sm font-medium">{{ qtyOf(tt) }}</span>
            <button class="h-8 w-8 rounded-full border border-line text-dim hover:border-primary hover:text-primary" @click="changeQty(tt, 1)">＋</button>
          </div>

          <button
            class="btn-primary sm:ml-auto"
            :disabled="stockLevel(tt) === 'soldout' || event.status !== 'PUBLISHED' || session.status !== 'ONSALE' || remainingAllowance(tt) <= 0"
            @click="buy(tt)"
          >
            {{ session.status !== 'ONSALE'
              ? (session.status === 'NOT_OPEN' ? '未开售' : '已结束')
              : stockLevel(tt) === 'soldout' ? '已售罄'
              : remainingAllowance(tt) <= 0 ? '已达限购' : '立即购买' }}
          </button>
        </div>
      </div>
      <p v-if="buyErr && !orderInfo" class="mt-3 text-sm text-red-500">{{ buyErr }}</p>
    </section>

    <p class="mt-4 text-xs text-faint">每人每场次限购 {{ session?.ticketTypes[0]?.maxPerUser || 4 }} 张（已购数量计入）；下单后 15 分钟内未支付将自动取消。</p>
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
        <p v-if="orderExpired" class="mt-2 text-sm font-medium text-red-500">已超时，该订单将自动取消，请重新下单</p>
        <p v-else class="mt-1 text-xs text-faint">内完成支付，逾期订单自动取消</p>

        <p v-if="buyErr" class="mt-3 text-sm text-red-500">{{ buyErr }}</p>

        <button class="btn-primary mt-5 w-full" :disabled="orderExpired" @click="pay">
          {{ orderExpired ? '订单已超时' : '模拟支付' }}
        </button>
        <button class="btn-ghost mt-2 w-full" @click="cancel">取消订单</button>
      </template>
    </div>
  </div>

  <!-- 游客购票引导 -->
  <LoginGate v-model:open="gateOpen" action="购票" />
</template>
