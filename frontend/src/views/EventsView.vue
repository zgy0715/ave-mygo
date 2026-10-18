<script setup>
import { computed, onMounted, ref } from 'vue'
import EventCard from '@/components/EventCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listEvents } from '@/api/event'

/** 演出列表（需求 3.4 上游）：状态 Tab 筛选（真接口，状态为后端枚举） */
const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'PUBLISHED', label: '售票中' },
  { id: 'ENDED', label: '已结束' },
]
const tab = ref('ALL')

const events = ref([])
const loading = ref(true)
const errMsg = ref('')

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listEvents({ status: tab.value === 'ALL' ? undefined : tab.value })
    events.value = data.content
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const filtered = computed(() => events.value)
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

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 3" :key="i" class="h-64 animate-pulse rounded-xl bg-surface"></div>
    </div>
    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></div>

    <div v-else-if="filtered.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <EventCard v-for="(e, i) in filtered" :key="e.id" :event="e" v-reveal="i * 90" />
    </div>

    <EmptyState
      v-else
      icon="🎤"
      text="该状态下暂无演出"
      hint="切换状态看看，或留意后续公告"
      action-text="看全部"
      @action="tab = 'ALL'"
    />
  </div>
</template>
