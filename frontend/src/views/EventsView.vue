<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EventCard from '@/components/EventCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getEvent, listEvents } from '@/api/event'

/**
 * 演出日程：列表 / 日历双视图。
 * 日历数据来源：GET /events（只有 nextSessionTime）+ 逐场 GET /events/{id} 取全部场次日期（有缓存）。
 */
const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'PUBLISHED', label: '售票中' },
  { id: 'ENDED', label: '已结束' },
]
const tab = ref('ALL')
const view = ref(localStorage.getItem('events:view') || 'list')   // list | calendar

const events = ref([])
const loading = ref(true)
const errMsg = ref('')

const sessionDates = reactive({})   // eventId → ['2026-11-14', ...]

const fmtDate = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const todayStr = fmtDate(new Date())

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listEvents({ status: tab.value === 'ALL' ? undefined : tab.value, pageSize: 100 })
    events.value = data.content
    await loadSessionDates(data.content)
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** 补全每场演出的全部场次日期（已有缓存则跳过；失败回退 nextSessionTime） */
async function loadSessionDates(list) {
  await Promise.all(
    list.map(async (ev) => {
      if (sessionDates[ev.id]) return
      try {
        const detail = await getEvent(ev.id)
        const ds = (detail.sessions || []).map((s) => s.startTime?.slice(0, 10)).filter(Boolean)
        sessionDates[ev.id] = ds.length ? [...new Set(ds)] : (ev.nextSessionTime ? [ev.nextSessionTime.slice(0, 10)] : [])
      } catch {
        sessionDates[ev.id] = ev.nextSessionTime ? [ev.nextSessionTime.slice(0, 10)] : []
      }
    }),
  )
}

const filtered = computed(() => events.value)

/* ===== 日历 ===== */
const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const selectedDate = ref(todayStr)

const monthTitle = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)
const WEEK = ['日', '一', '二', '三', '四', '五', '六']

/** 6×7 网格：前后补齐相邻月日期 */
const cells = computed(() => {
  const first = new Date(cursor.value)
  const start = new Date(first)
  start.setDate(1 - first.getDay())          // 周日开头
  const out = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    out.push({
      key: fmtDate(d),
      day: d.getDate(),
      inMonth: d.getMonth() === first.getMonth(),
      isToday: fmtDate(d) === todayStr,
      events: eventsOn(fmtDate(d)),
    })
  }
  return out
})

/** 某天有哪些演出（按全部场次日期匹配） */
function eventsOn(dateStr) {
  return events.value.filter((ev) => (sessionDates[ev.id] || []).includes(dateStr))
}

const dayEvents = computed(() => eventsOn(selectedDate.value))

function shiftMonth(n) {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + n, 1)
}
function backToThisMonth() {
  const now = new Date()
  cursor.value = new Date(now.getFullYear(), now.getMonth(), 1)
  selectedDate.value = todayStr
}
function pickDate(cell) {
  selectedDate.value = cell.key
  if (!cell.inMonth) {
    const d = new Date(cell.key)
    cursor.value = new Date(d.getFullYear(), d.getMonth(), 1)
  }
}
function switchView(v) {
  view.value = v
  localStorage.setItem('events:view', v)
}
const fmtDateTime = (t) => (t ? t.slice(5, 16).replace('T', ' ') : '待定')
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">LIVE — SCHEDULE</p>
        <h1 class="serif text-2xl font-semibold">演出日程</h1>
      </div>
      <div class="flex items-center gap-3">
        <!-- 视图切换 -->
        <div class="flex rounded-full border border-line p-0.5">
          <button
            v-for="v in [{ id: 'list', label: '列表' }, { id: 'calendar', label: '日历' }]"
            :key="v.id"
            class="cursor-pointer rounded-full px-3 py-1 text-xs font-medium transition"
            :class="view === v.id ? 'bg-primary text-white' : 'text-dim hover:text-ink'"
            @click="switchView(v.id)"
          >
            {{ v.label }}
          </button>
        </div>
        <p class="tabular text-sm text-faint">{{ filtered.length }} 场</p>
      </div>
    </div>

    <div class="mb-5 flex gap-2">
      <button
        v-for="t in TABS"
        :key="t.id"
        class="cursor-pointer rounded-full border px-3.5 py-1.5 text-sm transition"
        :class="tab === t.id ? 'border-primary bg-primary !text-[11px] text-white' : 'border-line bg-transparent text-dim hover:border-line-strong'"
        @click="tab = t.id; load()"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 3" :key="i" class="h-64 animate-pulse rounded-xl bg-surface"></div>
    </div>
    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></div>

    <!-- ===== 列表视图 ===== -->
    <template v-else-if="view === 'list'">
      <div v-if="filtered.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <EventCard v-for="(e, i) in filtered" :key="e.id" :event="e" v-reveal="i * 90" />
      </div>
      <EmptyState
        v-else
        icon="🎤"
        text="该状态下暂无演出"
        hint="切换状态看看，或留意后续公告"
        action-text="看全部"
        @action="tab = 'ALL'; load()"
      />
    </template>

    <!-- ===== 日历视图 ===== -->
    <template v-else>
      <div class="card overflow-hidden">
        <div class="flex items-center justify-between border-b border-line px-4 py-3">
          <div class="flex items-center gap-2">
            <button class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:bg-base hover:text-ink" aria-label="上个月" @click="shiftMonth(-1)">‹</button>
            <p class="serif min-w-32 text-center text-base font-semibold">{{ monthTitle }}</p>
            <button class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:bg-base hover:text-ink" aria-label="下个月" @click="shiftMonth(1)">›</button>
          </div>
          <button class="micro cursor-pointer rounded-full border border-line px-3 py-1 transition hover:text-primary" @click="backToThisMonth">回到本月</button>
        </div>

        <!-- 星期头 -->
        <div class="grid grid-cols-7 border-b border-line bg-base/40">
          <p v-for="w in WEEK" :key="w" class="py-2 text-center text-[11px] text-faint">{{ w }}</p>
        </div>

        <!-- 日期网格 -->
        <div class="grid grid-cols-7">
          <button
            v-for="c in cells"
            :key="c.key"
            class="relative flex min-h-16 cursor-pointer flex-col items-center gap-1 border-b border-r border-line/50 px-1 py-2 transition last:border-r-0 hover:bg-base/60 sm:min-h-20"
            :class="[
              !c.inMonth && 'opacity-40',
              c.key === selectedDate && 'bg-primary/5 ring-1 ring-inset ring-primary/40',
            ]"
            @click="pickDate(c)"
          >
            <span
              class="tabular text-xs"
              :class="c.isToday ? 'flex h-5 w-5 items-center justify-center rounded-full bg-primary text-white' : 'text-dim'"
            >{{ c.day }}</span>

            <!-- 演出标记：多场显示数字 -->
            <template v-if="c.events.length">
              <span v-if="c.events.length === 1" class="h-1.5 w-1.5 rounded-full" :class="c.events[0].status === 'ENDED' ? 'bg-line-strong' : 'bg-primary'" />
              <span
                v-else
                class="rounded-full px-1.5 text-[9px] font-bold leading-4"
                :class="c.events.every((e) => e.status === 'ENDED') ? 'bg-base text-faint' : 'bg-primary text-white'"
              >{{ c.events.length }}</span>
            </template>
          </button>
        </div>
      </div>

      <!-- 当日演出 -->
      <div class="mt-6">
        <div class="mb-3 flex items-baseline gap-3">
          <h2 class="text-lg font-bold">{{ selectedDate }} 的演出</h2>
          <span class="text-xs text-faint">{{ dayEvents.length }} 场</span>
        </div>
        <div v-if="dayEvents.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="e in dayEvents" :key="e.id">
            <EventCard :event="e" />
            <p class="mt-1 text-center text-[11px] text-faint">{{ fmtDateTime(e.nextSessionTime) }} 开演</p>
          </div>
        </div>
        <p v-else class="card p-6 text-center text-sm text-dim">这天没有演出，换个日期看看</p>
      </div>
    </template>
  </div>
</template>
