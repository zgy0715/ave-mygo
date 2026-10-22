<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SetlistEditor from '@/components/SetlistEditor.vue'
import { deleteAdminEvent, deleteAdminSession } from '@/api/admin'
import { listBands } from '@/api/song'
import {
  createAdminEvent,
  createAdminSession,
  createTicketType,
  listAdminEvents,
  listAdminSessions,
  updateTicketType,
} from '@/api/admin'

/**
 * 管理端 · 演出管理
 * - 列表：状态 chips 筛选 + 分页，行内显示场次数，点击行展开场次
 * - 场次内票档表：价格/库存直接改 → PATCH → 原地更新（余票重算）；错误红字
 * - 三个新建弹窗：演出 / 场次（datetime-local）/ 票档
 * - 校验错误（40001）：按 message 里的中文字段名挂到对应输入框下方
 */
const bands = ref([])
const rows = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const pageErr = ref('')
const status = ref('')   // '' = 全部

const EVENT_STATUS = [
  { v: '', t: '全部' },
  { v: 'DRAFT', t: '草稿' },
  { v: 'PUBLISHED', t: '已发布' },
  { v: 'ENDED', t: '已结束' },
]

watch([status, page], load)

async function load() {
  loading.value = true
  pageErr.value = ''
  try {
    const params = { page: page.value, pageSize }
    if (status.value) params.status = status.value
    const data = await listAdminEvents(params)
    rows.value = data.content
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    pageErr.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  try { bands.value = await listBands() } catch { /* 下拉失败不阻塞 */ }
})

const bandName = (id) => bands.value.find((b) => b.id === id)?.name || id
const fmt = (s) => (s ? s.slice(5, 16).replace('T', ' ') : '—')
const money = (n) => (n == null ? '—' : `¥${Number(n)}`)

/** 把 40001 的 message 按中文字段名派发到对应输入框 */
function routeError(msg, fieldErr, formErr, map) {
  const hit = Object.keys(map).find((k) => (msg || '').includes(k))
  if (hit) fieldErr[map[hit]] = msg
  else formErr.value = msg
}
const clearErr = (obj) => Object.keys(obj).forEach((k) => delete obj[k])

/* ===== 展开场次 ===== */
const expandedId = ref(null)
const setlistTarget = ref(null)   // 正在编辑歌单的场次
const confirmDel = ref(null)      // {type:'event'|'session', id, name}
const deleting = ref(false)
const errMsg = ref('')            // 删除弹窗内的错误提示（40903 等）
const sessions = ref([])
const sessLoading = ref(false)
const sessErr = ref('')
const sessionCache = new Map()   // eventId → sessions（避免每次展开/收起都重拉）

async function toggleExpand(ev) {
  if (expandedId.value === ev.id) { expandedId.value = null; return }
  expandedId.value = ev.id
  sessErr.value = ''
  if (sessionCache.has(ev.id)) { sessions.value = sessionCache.get(ev.id); return }
  sessions.value = []
  await loadSessions(ev.id)
}

async function loadSessions(eventId) {
  sessLoading.value = true
  sessErr.value = ''
  try {
    sessions.value = await listAdminSessions(eventId)
    sessionCache.set(eventId, sessions.value)   // 写回缓存（新建场次/票档后刷新也走这里）
  } catch (e) {
    sessErr.value = e.message
  } finally {
    sessLoading.value = false
  }
}

/* ===== 票档行内编辑 ===== */
const ticketDraft = reactive({})
const ticketErr = reactive({})
const ticketSaving = ref(null)

function draftOf(t) {
  if (!ticketDraft[t.id]) ticketDraft[t.id] = { price: t.price, totalStock: t.totalStock }
  return ticketDraft[t.id]
}

async function saveTicket(t) {
  const d = draftOf(t)
  ticketSaving.value = t.id
  ticketErr[t.id] = ''
  try {
    await updateTicketType(t.id, { price: Number(d.price), totalStock: Number(d.totalStock) })
    // 原地更新：余票 = 库存 - 已售
    t.price = Number(d.price)
    t.totalStock = Number(d.totalStock)
    t.remaining = t.totalStock - t.soldCount
  } catch (e) {
    ticketErr[t.id] = e.message
  } finally {
    ticketSaving.value = null
  }
}

/* ===== 失败提示 toast（红字，3.2s 自动消失） ===== */
const toast = ref('')
let toastTimer = null
function showToast(msg) {
  toast.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 3200)
}
onBeforeUnmount(() => clearTimeout(toastTimer))

/* ===== 票档停售 / 恢复（就地切换，不用弹窗） ===== */
const togglingId = ref(null)

async function toggleTicketStatus(t) {
  togglingId.value = t.id
  const next = t.status === 1 ? 0 : 1
  try {
    await updateTicketType(t.id, { status: next })
    t.status = next   // 就地更新：按钮文案与输入禁用状态随之切换
  } catch (e) {
    showToast(e.message)
  } finally {
    togglingId.value = null
  }
}

/* ===== 新建演出 ===== */
const showEvent = ref(false)
const eventSubmitting = ref(false)
const eventFormErr = ref('')
const eventErr = reactive({})
// 默认 PUBLISHED：DRAFT 在前台不可见，演示时最容易被误判成「建完看不到」
const eventForm = reactive({ title: '', bandId: '', venue: '', status: 'PUBLISHED' })

function openEvent() {
  Object.assign(eventForm, { title: '', bandId: '', venue: '', status: 'PUBLISHED' })
  clearErr(eventErr)
  eventFormErr.value = ''
  showEvent.value = true
}

async function submitEvent() {
  clearErr(eventErr)
  eventFormErr.value = ''
  if (!eventForm.title.trim()) { eventErr.title = '请填写演出标题'; return }
  if (!eventForm.bandId) { eventErr.bandId = '请选择乐队'; return }
  if (!eventForm.venue.trim()) { eventErr.venue = '请填写场馆'; return }
  eventSubmitting.value = true
  try {
    await createAdminEvent({
      title: eventForm.title.trim(),
      bandId: Number(eventForm.bandId),
      venue: eventForm.venue.trim(),
      status: eventForm.status,
    })
    showEvent.value = false
    page.value = 1
    await load()
  } catch (e) {
    routeError(e.message, eventErr, eventFormErr, { 标题: 'title', 乐队: 'bandId', 场馆: 'venue', 状态: 'status' })
  } finally {
    eventSubmitting.value = false
  }
}

/* ===== 新建场次 ===== */
const showSession = ref(false)
const sessionSubmitting = ref(false)
const sessionFormErr = ref('')
const sessionErr = reactive({})
const sessionEventId = ref(null)
// 默认 ONSALE：NOT_OPEN 状态下用户买不了票；三值为后端仅有的合法枚举
const sessionForm = reactive({ name: '', startTime: '', endTime: '', saleStartTime: '', status: 'ONSALE' })

const SESSION_STATUS = [
  { v: 'ONSALE', t: '立即开售 ONSALE' },
  { v: 'NOT_OPEN', t: '未开售 NOT_OPEN' },
  { v: 'ENDED', t: '已结束 ENDED' },
]

async function doDelete() {
  if (!confirmDel.value || deleting.value) return
  deleting.value = true
  errMsg.value = ''
  try {
    if (confirmDel.value.type === 'event') {
      await deleteAdminEvent(confirmDel.value.id)
      if (expandedId.value === confirmDel.value.id) expandedId.value = null
      sessionCache.delete(confirmDel.value.id)
      confirmDel.value = null
      await load()                 // 重拉列表（若当前页被删空会自然回退）
    } else {
      await deleteAdminSession(confirmDel.value.id)
      confirmDel.value = null
      // 场次一定属于当前展开的演出；SessionView 无 eventId 字段，用 expandedId
      if (expandedId.value) await loadSessions(expandedId.value)
    }
  } catch (e) {
    errMsg.value = e.message       // 典型：40903 有未完成订单
  } finally {
    deleting.value = false
  }
}

function openSession(ev) {
  sessionEventId.value = ev.id
  Object.assign(sessionForm, { name: '', startTime: '', endTime: '', saleStartTime: '', status: 'ONSALE' })
  clearErr(sessionErr)
  sessionFormErr.value = ''
  showSession.value = true
}

// datetime-local（分钟精度）→ 后端 LocalDateTime（秒精度）
const toIso = (v) => (v ? (v.length === 16 ? `${v}:00` : v) : null)

async function submitSession() {
  clearErr(sessionErr)
  sessionFormErr.value = ''
  if (!sessionForm.name.trim()) { sessionErr.name = '请填写场次名称'; return }
  if (!sessionForm.startTime) { sessionErr.startTime = '请选择开始时间'; return }
  if (!sessionForm.endTime) { sessionErr.endTime = '请选择结束时间'; return }
  // 后端硬规则：结束必须晚于开始（本地先拦，省一次往返）
  if (sessionForm.endTime <= sessionForm.startTime) { sessionErr.endTime = '结束时间必须晚于开演时间'; return }
  sessionSubmitting.value = true
  try {
    await createAdminSession(sessionEventId.value, {
      name: sessionForm.name.trim(),
      startTime: toIso(sessionForm.startTime),
      endTime: toIso(sessionForm.endTime),
      saleStartTime: toIso(sessionForm.saleStartTime),
      status: sessionForm.status,
    })
    showSession.value = false
    await loadSessions(sessionEventId.value)
  } catch (e) {
    routeError(e.message, sessionErr, sessionFormErr, { 名称: 'name', 开始: 'startTime', 结束: 'endTime', 开售: 'saleStartTime' })
  } finally {
    sessionSubmitting.value = false
  }
}

/* ===== 新建票档 ===== */
const showTicket = ref(false)
const ticketSubmitting = ref(false)
const ticketFormErr = ref('')
const ticketFormErrMap = reactive({})
const ticketSessionId = ref(null)
const tForm = reactive({ name: '', price: '', totalStock: '', maxPerUser: '' })

function openTicket(s) {
  ticketSessionId.value = s.id
  Object.assign(tForm, { name: '', price: '', totalStock: '', maxPerUser: '' })
  clearErr(ticketFormErrMap)
  ticketFormErr.value = ''
  showTicket.value = true
}

async function submitTicket() {
  clearErr(ticketFormErrMap)
  ticketFormErr.value = ''
  if (!tForm.name.trim()) { ticketFormErrMap.name = '请填写票档名称'; return }
  if (tForm.price === '' || Number(tForm.price) < 0) { ticketFormErrMap.price = '请填写有效价格'; return }
  if (!tForm.totalStock || Number(tForm.totalStock) < 0) { ticketFormErrMap.totalStock = '请填写有效库存'; return }
  ticketSubmitting.value = true
  try {
    await createTicketType(ticketSessionId.value, {
      name: tForm.name.trim(),
      price: Number(tForm.price),
      totalStock: Number(tForm.totalStock),
      maxPerUser: tForm.maxPerUser ? Number(tForm.maxPerUser) : null,
    })
    showTicket.value = false
    await loadSessions(expandedId.value)
  } catch (e) {
    routeError(e.message, ticketFormErrMap, ticketFormErr, { 名称: 'name', 价格: 'price', 库存: 'totalStock', 限购: 'maxPerUser' })
  } finally {
    ticketSubmitting.value = false
  }
}
</script>

<template>
  <!-- 工具栏 -->
  <div class="mb-4 flex flex-wrap items-center gap-2">
    <div class="flex gap-1.5">
      <button
        v-for="s in EVENT_STATUS"
        :key="s.v"
        class="cursor-pointer rounded-full px-3 py-1 text-xs font-medium transition-colors duration-200"
        :class="status === s.v ? 'bg-primary text-white' : 'bg-base text-dim hover:text-ink'"
        @click="status = s.v; page = 1"
      >
        {{ s.t }}
      </button>
    </div>
    <p class="ml-auto text-sm text-faint tabular">共 {{ totalElements }} 场演出</p>
  </div>

  <p v-if="pageErr" class="card mb-4 p-4 text-sm text-red-500">{{ pageErr }} <button class="ml-2 underline" @click="load">重试</button></p>

  <div v-if="loading" class="card p-4"><div class="h-56 animate-pulse rounded-lg bg-base"></div></div>

  <div v-else-if="rows.length" class="card overflow-x-auto">
    <table class="w-full min-w-[52rem] text-sm">
      <thead>
        <tr class="border-b border-line text-left text-xs text-faint">
          <th class="px-3 py-2.5 font-medium">演出 / ID</th>
          <th class="px-3 py-2.5 font-medium">乐队</th>
          <th class="px-3 py-2.5 font-medium">场馆</th>
          <th class="px-3 py-2.5 font-medium">场次</th>
          <th class="px-3 py-2.5 font-medium">状态</th>
          <th class="px-3 py-2.5 font-medium">操作</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="ev in rows" :key="ev.id">
          <tr
            class="cursor-pointer border-b border-line transition hover:bg-elevated"
            @click="toggleExpand(ev)"
          >
            <td class="max-w-56 px-3 py-2.5">
              <p class="truncate font-medium">{{ ev.title }}</p>
              <p class="mono text-[10px] text-faint">#{{ ev.id }}</p>
            </td>
            <td class="px-3 py-2.5 text-dim">{{ ev.bandName || bandName(ev.bandId) }}</td>
            <td class="px-3 py-2.5 text-dim">{{ ev.venue }}</td>
            <td class="px-3 py-2.5 tabular text-dim">{{ ev.sessionCount ?? 0 }} 场</td>
            <td class="px-3 py-2.5"><StatusBadge :status="ev.status" /></td>
            <td class="px-3 py-2.5">
              <div class="flex gap-1.5 whitespace-nowrap">
                <button class="btn-ghost !px-2.5 !py-1 !text-xs" @click.stop="openSession(ev)">＋ 场次</button>
                <button class="btn-ghost !px-2.5 !py-1 !text-xs" @click.stop="toggleExpand(ev)">
                  {{ expandedId === ev.id ? '收起' : '场次明细' }}
                </button>
                <button
                  class="btn-ghost !px-2.5 !py-1 !text-xs hover:!border-red-300 hover:!text-red-500"
                  title="删除演出"
                  @click.stop="confirmDel = { type: 'event', id: ev.id, name: ev.title, eventId: ev.id }"
                >删除</button>
              </div>
            </td>
          </tr>

          <!-- 展开：场次 + 票档 -->
          <tr v-if="expandedId === ev.id">
            <td colspan="6" class="bg-base/60 px-4 py-4">
              <p v-if="sessErr" class="mb-2 text-xs text-red-500">{{ sessErr }}</p>
              <p v-if="sessLoading" class="text-xs text-faint">加载场次…</p>
              <p v-else-if="!sessions.length" class="text-xs text-faint">还没有场次，先「＋ 场次」建一个。</p>

              <div v-for="s in sessions" :key="s.id" class="mb-3 rounded-lg border border-line bg-surface p-3">
                <div class="flex flex-wrap items-center gap-2">
                  <p class="text-sm font-semibold">{{ s.name }}</p>
                  <span class="mono text-[11px] text-faint">{{ fmt(s.startTime) }} — {{ fmt(s.endTime) }}</span>
                  <StatusBadge :status="s.status" />
                  <button class="btn-ghost ml-auto !px-2.5 !py-1 !text-xs" @click="setlistTarget = s">歌单</button>
                  <button
                    class="btn-ghost !px-2.5 !py-1 !text-xs hover:!border-red-300 hover:!text-red-500"
                    title="删除场次"
                    @click="confirmDel = { type: 'session', id: s.id, name: s.name, eventId: s.eventId }"
                  >删除</button>
                  <button class="btn-ghost !px-2.5 !py-1 !text-xs" @click="openTicket(s)">＋ 票档</button>
                </div>

                <table v-if="s.ticketTypes?.length" class="mt-2 w-full text-xs">
                  <thead>
                    <tr class="text-left text-faint">
                      <th class="py-1 pr-3 font-medium">票档</th>
                      <th class="py-1 pr-3 font-medium">价格</th>
                      <th class="py-1 pr-3 font-medium">库存</th>
                      <th class="py-1 pr-3 font-medium">已售</th>
                      <th class="py-1 pr-3 font-medium">余票</th>
                      <th class="py-1 pr-3 font-medium">状态</th>
                      <th class="py-1 font-medium">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="t in s.ticketTypes" :key="t.id" class="border-t border-line/60" :class="t.status === 0 && 'opacity-55'">
                      <td class="py-1.5 pr-3">{{ t.name }}</td>
                      <td class="py-1.5 pr-3">
                        <input v-model="draftOf(t).price" type="number" min="0" step="0.01" class="input !w-24 !py-1 !text-xs" :disabled="t.status === 0">
                      </td>
                      <td class="py-1.5 pr-3">
                        <input v-model="draftOf(t).totalStock" type="number" min="0" class="input !w-20 !py-1 !text-xs" :disabled="t.status === 0">
                      </td>
                      <td class="py-1.5 pr-3 tabular text-dim">{{ t.soldCount }}</td>
                      <td class="py-1.5 pr-3 tabular" :class="t.remaining <= 0 ? 'text-red-500' : 'text-dim'">{{ t.remaining }}</td>
                      <td class="py-1.5 pr-3">
                        <span class="micro" :class="t.status === 1 ? '!text-green-700' : '!text-faint'">
                          {{ t.status === 1 ? '在售' : '停售' }}
                        </span>
                      </td>
                      <td class="py-1.5">
                        <div class="flex items-center gap-1.5 whitespace-nowrap">
                          <button
                            class="btn-primary !px-2.5 !py-1 !text-xs"
                            :disabled="t.status === 0 || ticketSaving === t.id"
                            @click="saveTicket(t)"
                          >
                            {{ ticketSaving === t.id ? '保存中…' : '保存' }}
                          </button>
                          <button
                            class="btn-ghost !px-2.5 !py-1 !text-xs"
                            :class="t.status === 1 && '!border-red-200 !text-red-500'"
                            :disabled="togglingId === t.id"
                            @click="toggleTicketStatus(t)"
                          >
                            {{ togglingId === t.id ? '切换中…' : t.status === 1 ? '停售' : '恢复' }}
                          </button>
                        </div>
                        <p v-if="ticketErr[t.id]" class="mt-1 max-w-36 text-xs text-red-500">{{ ticketErr[t.id] }}</p>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-else class="mt-2 text-xs text-faint">暂无票档</p>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>

  <p v-else class="card p-6 text-center text-sm text-dim">没有匹配的演出</p>

  <!-- 分页 -->
  <div v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-3">
    <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page <= 1" @click="page--">← 上一页</button>
    <span class="tabular text-xs text-faint">{{ page }} / {{ totalPages }}</span>
    <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page >= totalPages" @click="page++">下一页 →</button>
  </div>

  <Teleport to="body">
    <!-- 新建演出 -->
    <div v-if="showEvent" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="showEvent = false">
      <div class="card w-full max-w-md p-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="serif text-lg font-semibold">新建演出</h2>
          <button class="text-dim hover:text-ink" @click="showEvent = false">✕</button>
        </div>
        <form class="space-y-3" @submit.prevent="submitEvent">
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">标题 *</label>
            <input v-model="eventForm.title" type="text" class="input" placeholder="如：MyGO!!!!! 5th LIVE">
            <p v-if="eventErr.title" class="mt-1 text-xs text-red-500">{{ eventErr.title }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">乐队 *</label>
            <select v-model="eventForm.bandId" class="input">
              <option value="" disabled>选择乐队</option>
              <option v-for="b in bands" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
            <p v-if="eventErr.bandId" class="mt-1 text-xs text-red-500">{{ eventErr.bandId }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">场馆 *</label>
            <input v-model="eventForm.venue" type="text" class="input" placeholder="如：横滨 PIA ARENA MM">
            <p v-if="eventErr.venue" class="mt-1 text-xs text-red-500">{{ eventErr.venue }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">状态</label>
            <select v-model="eventForm.status" class="input">
              <option value="PUBLISHED">已发布 PUBLISHED（前台可见）</option>
              <option value="DRAFT">草稿 DRAFT（前台不可见）</option>
              <option value="ENDED">已结束 ENDED</option>
            </select>
            <p class="mt-1 text-[11px] text-faint">默认已发布，草稿状态在前台完全看不到。</p>
          </div>
          <p v-if="eventFormErr" class="text-xs text-red-500">{{ eventFormErr }}</p>
          <div class="flex justify-end gap-2 pt-1">
            <button type="button" class="btn-ghost" @click="showEvent = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="eventSubmitting">{{ eventSubmitting ? '提交中…' : '创建' }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 新建场次 -->
    <div v-if="showSession" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="showSession = false">
      <div class="card w-full max-w-md p-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="serif text-lg font-semibold">新建场次</h2>
          <button class="text-dim hover:text-ink" @click="showSession = false">✕</button>
        </div>
        <form class="space-y-3" @submit.prevent="submitSession">
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">场次名称 *</label>
            <input v-model="sessionForm.name" type="text" class="input" placeholder="如：DAY 1 / 夜场">
            <p v-if="sessionErr.name" class="mt-1 text-xs text-red-500">{{ sessionErr.name }}</p>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">开始时间 *</label>
              <input v-model="sessionForm.startTime" type="datetime-local" class="input">
              <p v-if="sessionErr.startTime" class="mt-1 text-xs text-red-500">{{ sessionErr.startTime }}</p>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">结束时间 *</label>
              <input v-model="sessionForm.endTime" type="datetime-local" class="input">
              <p v-if="sessionErr.endTime" class="mt-1 text-xs text-red-500">{{ sessionErr.endTime }}</p>
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">开售时间</label>
            <input v-model="sessionForm.saleStartTime" type="datetime-local" class="input">
            <p class="mt-1 text-[11px] text-faint">不填默认当前时间。</p>
            <p v-if="sessionErr.saleStartTime" class="mt-1 text-xs text-red-500">{{ sessionErr.saleStartTime }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">场次状态</label>
            <select v-model="sessionForm.status" class="input">
              <option v-for="s in SESSION_STATUS" :key="s.v" :value="s.v">{{ s.t }}</option>
            </select>
            <p class="mt-1 text-[11px] text-faint">只有「立即开售」状态下用户才能下单购票。</p>
          </div>
          <p v-if="sessionFormErr" class="text-xs text-red-500">{{ sessionFormErr }}</p>
          <div class="flex justify-end gap-2 pt-1">
            <button type="button" class="btn-ghost" @click="showSession = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="sessionSubmitting">{{ sessionSubmitting ? '提交中…' : '创建' }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 新建票档 -->
    <div v-if="showTicket" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="showTicket = false">
      <div class="card w-full max-w-md p-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="serif text-lg font-semibold">新建票档</h2>
          <button class="text-dim hover:text-ink" @click="showTicket = false">✕</button>
        </div>
        <form class="space-y-3" @submit.prevent="submitTicket">
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">票档名称 *</label>
            <input v-model="tForm.name" type="text" class="input" placeholder="如：指定席 / VIP / 站席">
            <p v-if="ticketFormErrMap.name" class="mt-1 text-xs text-red-500">{{ ticketFormErrMap.name }}</p>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">价格（元）*</label>
              <input v-model="tForm.price" type="number" min="0" step="0.01" class="input">
              <p v-if="ticketFormErrMap.price" class="mt-1 text-xs text-red-500">{{ ticketFormErrMap.price }}</p>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">总库存 *</label>
              <input v-model="tForm.totalStock" type="number" min="0" class="input">
              <p v-if="ticketFormErrMap.totalStock" class="mt-1 text-xs text-red-500">{{ ticketFormErrMap.totalStock }}</p>
            </div>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-dim">每人限购</label>
            <input v-model="tForm.maxPerUser" type="number" min="1" class="input" placeholder="不填默认 4 人">
            <p v-if="ticketFormErrMap.maxPerUser" class="mt-1 text-xs text-red-500">{{ ticketFormErrMap.maxPerUser }}</p>
          </div>
          <p v-if="ticketFormErr" class="text-xs text-red-500">{{ ticketFormErr }}</p>
          <div class="flex justify-end gap-2 pt-1">
            <button type="button" class="btn-ghost" @click="showTicket = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="ticketSubmitting">{{ ticketSubmitting ? '提交中…' : '创建' }}</button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>

  <!-- 操作失败 toast：红字，3.2s 自动消失 -->
  <p
    v-if="toast"
    class="fixed bottom-8 left-1/2 z-[60] -translate-x-1/2 rounded-lg border border-red-200 bg-surface px-4 py-2 text-xs text-red-500 shadow-lg"
  >
    {{ toast }}
  </p>

  <!-- 删除确认 -->
  <Teleport to="body">
    <div v-if="confirmDel" class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4" @click.self="confirmDel = null">
      <div class="card w-full max-w-sm p-6 text-center">
        <p class="text-sm">
          确定{{ confirmDel.type === 'event' ? '删除演出' : '删除场次' }}
          <span class="font-semibold">「{{ confirmDel.name }}」</span>？
        </p>
        <p class="mt-1 text-xs text-faint">
          {{ confirmDel.type === 'event' ? '软删除，前台即刻不可见；有未完成订单时会被拒绝' : '其票档与歌单会一并移除；有未完成订单时会被拒绝' }}
        </p>
        <p v-if="errMsg" class="mt-2 text-xs text-red-500">{{ errMsg }}</p>
        <div class="mt-5 flex justify-center gap-2">
          <button class="btn-ghost" @click="confirmDel = null">取消</button>
          <button class="btn-primary !bg-red-500 hover:!bg-red-600" :disabled="deleting" @click="doDelete">
            {{ deleting ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- 歌单录入抽屉 -->  <!-- 歌单录入抽屉 -->
  <SetlistEditor
    v-if="setlistTarget"
    :session-id="setlistTarget.id"
    :session-name="setlistTarget.name"
    @close="setlistTarget = null"
    @saved="setlistTarget = null"
  />
</template>
