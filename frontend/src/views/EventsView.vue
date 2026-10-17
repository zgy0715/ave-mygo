<script setup>
import { computed, ref } from 'vue'
import EventCard from '@/components/EventCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { events } from '@/data/mock'

/** 演出列表（需求 3.4 上游）：状态 Tab 筛选 */
const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'ONSALE', label: '销售中' },
  { id: 'NOT_OPEN', label: '即将开售' },
  { id: 'ENDED', label: '已结束' },
]
const tab = ref('ALL')
const filtered = computed(() => (tab.value === 'ALL' ? events : events.filter((e) => e.status === tab.value)))
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">LIVE — SCHEDULE</p>
        <h1 class="serif text-2xl font-semibold">演出日程</h1>
      </div>
      <p class="text-sm text-faint tabular">{{ filtered.length }} 场</p>
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

    <div v-if="filtered.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <EventCard v-for="(e, i) in filtered" :key="e.id" :event="e" v-reveal="(i % 3) * 80" />
    </div>
    <EmptyState v-else icon="🎫" text="该状态下暂无演出" action-text="查看全部" @action="tab = 'ALL'" />
  </div>
</template>
