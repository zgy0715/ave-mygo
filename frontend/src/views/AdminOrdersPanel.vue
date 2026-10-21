<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusBadge from '@/components/StatusBadge.vue'
import { listAdminEvents, listAdminOrders, listAdminSessions, refundAdminOrder } from '@/api/admin'

/**
 * 管理端 · 订单管理
 * - 筛选：状态 chips + 关键词（订单号/用户名）+ 演出下拉（服务端 eventId）+ 场次下拉（当前页内）
 *   后端 GET /admin/orders 只支持 status/eventId/keyword，故场次筛选在前端过滤
 * - 已支付行可退款：弹窗填原因（2-100 字）→ PATCH → 就地改状态 + toast
 * - 筛选与页码写进 URL query，刷新不丢
 */
const route = useRoute()
const router = useRouter()

const STATUS_TABS = [
  { v: '', t: '全部' },
  { v: 'PENDING_PAYMENT', t: '待支付' },
  { v: 'PAID', t: '已支付' },
  { v: 'CANCELLED', t: '已取消' },
  { v: 'EXPIRED', t: '已超时' },
  { v: 'REFUNDED', t: '已退款' },
]

const status = ref(route.query.status || '')
const keyword = ref(route.query.keyword || '')
const eventId = ref(route.query.eventId ? Number(route.query.eventId) : '')
const sessionName = ref('')      // 前端过滤（后端无 sessionId 参数）
const page = ref(Number(route.query.page) || 1)
const pageSize = 15

const rows = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const pageErr = ref('')

const events = ref([])
const sessions = ref([])
const toast = ref('')
let toastTimer = null
const showToast = (m) => {
  toast.value = m
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2600)
}
onBeforeUnmount(() => clearTimeout(toastTimer))

/* ---- 数据加载（带序号丢弃过期响应） ---- */
let seq = 0
async function load() {
  const my = ++seq
  loading.value = true
  pageErr.value = ''
  try {
    const params = { page: page.value, pageSize }
    if (status.value) params.status = status.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (eventId.value) params.eventId = eventId.value
    const data = await listAdminOrders(params)
    if (my !== seq) return
    rows.value = data.content
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    if (my === seq) pageErr.value = e.message
  } finally {
    if (my === seq) loading.value = false
  }
}

/** 筛选/页码同步到 URL（刷新与分享都能复原） */
function syncQuery() {
  const q = {}
  if (status.value) q.status = status.value
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (eventId.value) q.eventId = String(eventId.value)
  if (page.value > 1) q.page = String(page.value)
  router.replace({ query: q })
}

onMounted(async () => {
  load()
  try {
    const data = await listAdminEvents({ page: 1, pageSize: 100 })
    events.value = data.content
    if (eventId.value) await loadSessions(eventId.value)
  } catch { /* 下拉失败不阻塞列表 */ }
})

async function loadSessions(id) {
  sessions.value = []
  sessionName.value = ''
  if (!id) return
  try {
    sessions.value = await listAdminSessions(id)
  } catch { sessions.value = [] }
}

watch(status, () => { page.value = 1; syncQuery(); load() })
watch(eventId, (v) => { page.value = 1; sessionName.value = ''; loadSessions(v); syncQuery(); load() })
watch(page, () => { syncQuery(); load() })

let kwTimer = null
watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => {
    page.value = 1
    syncQuery()
    load()
  }, 300)
})

/** 场次筛选（当前页内前端过滤） */
const visibleRows = computed(() =>
  sessionName.value ? rows.value.filter((r) => r.sessionName === sessionName.value) : rows.value,
)
const sessionNames = computed(() => [...new Set(sessions.value.map((s) => s.name))])

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '—')
const fmtMoney = (n) => `¥${Number(n || 0).toFixed(2)}`

/* ---- 退款 ---- */
const refundTarget = ref(null)
const refundReason = ref('')
const refundErr = ref('')
const refunding = ref(false)

function openRefund(row) {
  refundTarget.value = row
  refundReason.value = ''
  refundErr.value = ''
}

async function submitRefund() {
  const reason = refundReason.value.trim()
  if (reason.length < 2) { refundErr.value = '请填写退款原因（至少 2 个字）'; return }
  if (reason.length > 100) { refundErr.value = '原因请控制在 100 字以内'; return }
  refunding.value = true
  refundErr.value = ''
  try {
    const data = await refundAdminOrder(refundTarget.value.orderNo, { reason })
    const idx = rows.value.findIndex((r) => r.orderNo === refundTarget.value.orderNo)
    if (idx >= 0) rows.value[idx] = { ...rows.value[idx], ...data }
    refundTarget.value = null
    showToast('已退款并回滚库存')
  } catch (e) {
    // 40902 订单当前状态不可退款 / 40401 订单不存在：原样展示后端文案
    refundErr.value = e.message
  } finally {
    refunding.value = false
  }
}
</script>

<template>
  <!-- 筛选栏 -->
  <div class="mb-4 space-y-3">
    <div class="flex flex-wrap items-center gap-1.5">
      <button
        v-for="s in STATUS_TABS"
        :key="s.v"
        class="cursor-pointer rounded-full px-3 py-1 text-xs font-medium transition-colors duration-200"
        :class="status === s.v ? 'bg-primary text-white' : 'bg-base text-dim hover:text-ink'"
        @click="status = s.v"
      >
        {{ s.t }}
      </button>
      <p class="ml-auto text-sm text-faint tabular">共 {{ totalElements }} 单</p>
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <input v-model="keyword" type="search" class="input !w-56 !py-1.5 !text-xs" placeholder="订单号 / 用户名">
      <select v-model="eventId" class="input !w-52 !py-1.5 !text-xs">
        <option value="">全部演出</option>
        <option v-for="ev in events" :key="ev.id" :value="ev.id">{{ ev.title }}</option>
      </select>
      <select v-model="sessionName" class="input !w-40 !py-1.5 !text-xs" :disabled="!eventId">
        <option value="">全部场次</option>
        <option v-for="n in sessionNames" :key="n" :value="n">{{ n }}</option>
      </select>
      <span class="micro !text-[10px]">场次筛选作用于当前页</span>
    </div>
  </div>

  <p v-if="pageErr" class="card mb-4 p-4 text-sm text-red-500">{{ pageErr }} <button class="ml-2 underline" @click="load">重试</button></p>
  <div v-if="loading" class="card p-4"><div class="h-56 animate-pulse rounded-lg bg-base"></div></div>

  <div v-else-if="visibleRows.length" class="card overflow-x-auto">
    <table class="w-full min-w-[56rem] text-sm">
      <thead>
        <tr class="border-b border-line text-left text-xs text-faint">
          <th class="px-3 py-2.5 font-medium">订单号</th>
          <th class="px-3 py-2.5 font-medium">用户</th>
          <th class="px-3 py-2.5 font-medium">演出 / 场次</th>
          <th class="px-3 py-2.5 font-medium">票档</th>
          <th class="px-3 py-2.5 font-medium">数量</th>
          <th class="px-3 py-2.5 font-medium">金额</th>
          <th class="px-3 py-2.5 font-medium">状态</th>
          <th class="px-3 py-2.5 font-medium">下单时间</th>
          <th class="px-3 py-2.5 font-medium">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="o in visibleRows" :key="o.orderNo" class="border-b border-line last:border-0">
          <td class="px-3 py-2.5"><span class="mono text-[11px]">{{ o.orderNo }}</span></td>
          <td class="px-3 py-2.5">{{ o.username || `#${o.userId}` }}</td>
          <td class="max-w-64 px-3 py-2.5">
            <p class="truncate font-medium">{{ o.eventTitle }}</p>
            <p class="truncate text-[11px] text-faint">{{ o.sessionName }}</p>
          </td>
          <td class="px-3 py-2.5 text-dim">{{ o.ticketTypeName }}</td>
          <td class="px-3 py-2.5 tabular">{{ o.quantity }}</td>
          <td class="px-3 py-2.5 tabular font-medium">{{ fmtMoney(o.totalAmount) }}</td>
          <td class="px-3 py-2.5">
            <span :title="o.refundReason || ''"><StatusBadge :status="o.status" /></span>
            <p v-if="o.refundReason" class="mt-1 max-w-32 truncate text-[10px] text-faint">{{ o.refundReason }}</p>
          </td>
          <td class="px-3 py-2.5 tabular text-xs text-dim">{{ fmtTime(o.createdAt) }}</td>
          <td class="px-3 py-2.5">
            <button v-if="o.status === 'PAID'" class="btn-ghost !px-2.5 !py-1 !text-xs" @click="openRefund(o)">退款</button>
            <span v-else class="text-xs text-faint">—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <p v-else class="card p-6 text-center text-sm text-dim">
    {{ sessionName ? '该场次在当前页没有订单，翻页看看' : '没有匹配的订单' }}
  </p>

  <!-- 分页 -->
  <div v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-3">
    <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page <= 1" @click="page--">← 上一页</button>
    <span class="tabular text-xs text-faint">{{ page }} / {{ totalPages }}</span>
    <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page >= totalPages" @click="page++">下一页 →</button>
  </div>

  <!-- 退款弹窗 -->
  <Teleport to="body">
    <div v-if="refundTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="refundTarget = null">
      <div class="card w-full max-w-md p-6">
        <h2 class="serif mb-1 text-lg font-semibold">退款</h2>
        <p class="mb-4 text-xs text-faint">
          订单 <span class="mono">{{ refundTarget.orderNo }}</span> · {{ refundTarget.ticketTypeName }} ×
          {{ refundTarget.quantity }} · {{ fmtMoney(refundTarget.totalAmount) }}
        </p>
        <label class="mb-1 block text-xs font-medium text-dim">退款原因 *</label>
        <textarea v-model="refundReason" rows="3" maxlength="100" class="input" placeholder="如：场次取消 / 用户申请（2-100 字）" />
        <div class="mt-1 flex justify-between text-[11px] text-faint">
          <span>退款后将回滚该票档已售数量</span>
          <span class="tabular">{{ refundReason.length }}/100</span>
        </div>
        <p v-if="refundErr" class="mt-2 text-xs text-red-500">{{ refundErr }}</p>
        <div class="mt-4 flex justify-end gap-2">
          <button class="btn-ghost" @click="refundTarget = null">取消</button>
          <button class="btn-primary" :disabled="refunding" @click="submitRefund">{{ refunding ? '处理中…' : '确认退款' }}</button>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- 结果提示 -->
  <p
    v-if="toast"
    class="fixed bottom-8 left-1/2 z-[60] -translate-x-1/2 rounded-lg border border-green-200 bg-surface px-4 py-2 text-xs text-green-700 shadow-lg"
  >
    {{ toast }}
  </p>
</template>
