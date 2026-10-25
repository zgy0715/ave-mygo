<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getAdminStats } from '@/api/admin'

// 按需引入，避免整包体积（echarts 只打进用到的图表与组件）
echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

/**
 * 管理端 · 数据看板
 * 口径（后端定义）：只统计 PAID；退款单不计入营收/票数；每日趋势按 paid_at 归集并补 0。
 */
const days = ref(30)
const data = ref(null)
const loading = ref(true)
const errMsg = ref('')
const chartEl = ref(null)
let chart = null
let ro = null

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    data.value = await getAdminStats(days.value)
    await nextTick()
    renderChart()
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(days, load)

const totals = computed(() => data.value?.totals || {})
const sessions = computed(() => data.value?.sessions || [])
const daily = computed(() => data.value?.daily || [])
const isEmpty = computed(() => !loading.value && !errMsg.value && (totals.value.orders || 0) === 0 && !daily.value.some((d) => d.orders > 0))

const fmtMoney = (n) => `¥${Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
const fmtDate = (d) => (d || '').slice(5)          // 10-21
const fmtTime = (t) => (t ? t.replace('T', ' ').slice(5, 16) : '—')

const KPIS = computed(() => [
  { label: '已支付订单', value: totals.value.orders ?? 0, sub: `退款 ${totals.value.refundedOrders ?? 0} 单` },
  { label: '销售额', value: fmtMoney(totals.value.revenue), sub: '不含退款' },
  { label: '售出票数', value: totals.value.ticketsSold ?? 0, sub: '有效票' },
  { label: '注册用户', value: totals.value.users ?? 0, sub: '全站累计' },
])

function renderChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const dates = daily.value.map((d) => fmtDate(d.date))
  const revenue = daily.value.map((d) => Number(d.revenue || 0))
  const orders = daily.value.map((d) => d.orders || 0)

  chart.setOption({
    grid: { left: 8, right: 8, top: 36, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      valueFormatter: undefined,
      formatter: (params) => {
        const day = params[0]?.axisValue || ''
        const rev = params.find((p) => p.seriesName === '销售额')?.data ?? 0
        const ord = params.find((p) => p.seriesName === '订单数')?.data ?? 0
        return `${day}<br/>销售额：${fmtMoney(rev)}<br/>订单数：${ord}`
      },
    },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11, color: '#7a7a7a' } },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e5e5e5' } },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, color: '#9a9a9a', interval: Math.max(0, Math.floor(dates.length / 8)) },
    },
    yAxis: [
      {
        type: 'value',
        name: '销售额',
        nameTextStyle: { fontSize: 10, color: '#9a9a9a' },
        splitLine: { lineStyle: { color: '#f0f0f0' } },
        axisLabel: { fontSize: 10, color: '#9a9a9a' },
      },
      {
        type: 'value',
        name: '订单数',
        nameTextStyle: { fontSize: 10, color: '#9a9a9a' },
        splitLine: { show: false },
        axisLabel: { fontSize: 10, color: '#9a9a9a' },
        minInterval: 1,
      },
    ],
    series: [
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 0,
        data: revenue,
        areaStyle: { opacity: 0.12 },
        lineStyle: { width: 2 },
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: '38%',
        itemStyle: { borderRadius: [3, 3, 0, 0], opacity: 0.55 },
        data: orders,
      },
    ],
  }, true)

  // 自适应：容器尺寸变化时 resize（弹窗/侧栏收缩都能跟上）
  if (!ro && typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(() => chart?.resize())
    ro.observe(chartEl.value)
  }
}

onBeforeUnmount(() => {
  ro?.disconnect()
  ro = null
  chart?.dispose()
  chart = null
})

/** 上座率配色：>90% 红、<30% 灰、其余主色 */
function occClass(o) {
  if (o >= 90) return 'bg-red-500'
  if (o < 30) return 'bg-line-strong'
  return 'bg-primary'
}
const pct = (o) => `${Math.round(o || 0)}%`   // 后端已给 0-100 百分数
</script>

<template>
  <div>
    <!-- 时间范围 -->
    <div class="mb-4 flex items-center gap-1.5">
      <button
        v-for="d in [7, 30, 90]"
        :key="d"
        class="cursor-pointer rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="days === d ? 'bg-primary text-white' : 'bg-base text-dim hover:text-ink'"
        @click="days = d"
      >
        近 {{ d }} 天
      </button>
      <p class="ml-auto micro !text-[10px]">口径：仅统计已支付订单，退款不计入</p>
    </div>

    <p v-if="errMsg" class="card mb-4 p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></p>

    <!-- KPI -->
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="k in KPIS" :key="k.label" class="card p-4">
        <p class="micro !text-[10px]">{{ k.label }}</p>
        <p class="tabular mt-1.5 text-2xl font-semibold">{{ loading ? '…' : k.value }}</p>
        <p class="mt-0.5 text-[11px] text-faint">{{ k.sub }}</p>
      </div>
    </div>

    <!-- 空数据 -->
    <div v-if="isEmpty" class="card mt-4 p-10 text-center">
      <p class="text-2xl">📊</p>
      <p class="mt-2 text-sm text-dim">暂无成交数据，先卖几张票吧</p>
      <p class="mt-1 text-xs text-faint">完成一笔支付后，趋势图和上座率会出现在这里</p>
    </div>

    <template v-else>
      <!-- 趋势 -->
      <div class="card mt-4 p-4">
        <div class="mb-2 flex items-baseline justify-between">
          <h2 class="text-sm font-semibold">近 {{ days }} 天销售额与订单数</h2>
          <span class="text-[11px] text-faint">左轴金额 / 右轴订单</span>
        </div>
        <div ref="chartEl" class="h-64 w-full" />
      </div>

      <!-- 场次上座率 -->
      <div class="card mt-4 overflow-x-auto">
        <div class="flex items-baseline justify-between border-b border-line px-4 py-3">
          <h2 class="text-sm font-semibold">各场次上座率</h2>
          <span class="text-[11px] text-faint">≥90% 红 · &lt;30% 灰</span>
        </div>
        <table class="w-full min-w-[40rem] text-sm">
          <thead>
            <tr class="border-b border-line text-left text-xs text-faint">
              <th class="px-4 py-2 font-medium">演出 / 场次</th>
              <th class="px-4 py-2 font-medium">开演</th>
              <th class="px-4 py-2 font-medium">售出 / 总库存</th>
              <th class="w-2/5 px-4 py-2 font-medium">上座率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in sessions" :key="s.sessionId" class="border-b border-line last:border-0">
              <td class="max-w-72 px-4 py-2.5">
                <p class="truncate font-medium">{{ s.eventTitle }}</p>
                <p class="truncate text-[11px] text-faint">{{ s.sessionName }}</p>
              </td>
              <td class="tabular px-4 py-2.5 text-xs text-dim">{{ fmtTime(s.startTime) }}</td>
              <td class="tabular px-4 py-2.5 text-xs">{{ s.soldCount }} / {{ s.totalStock }}</td>
              <td class="px-4 py-2.5">
                <div class="flex items-center gap-2">
                  <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-line/60">
                    <div class="h-full rounded-full transition-[width]" :class="occClass(s.occupancy)" :style="{ width: Math.min(100, s.occupancy || 0) + '%' }" />
                  </div>
                  <span class="tabular w-10 text-right text-xs" :class="s.occupancy >= 90 ? 'text-red-500' : 'text-dim'">{{ pct(s.occupancy) }}</span>
                </div>
              </td>
            </tr>
            <tr v-if="!sessions.length">
              <td colspan="4" class="px-4 py-6 text-center text-sm text-faint">还没有场次数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
