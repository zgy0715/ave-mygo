<script setup>
import { onMounted, ref } from 'vue'
import { listEvents } from '@/api/event'

const events = ref([])
const loading = ref(true)
const errMsg = ref('')
const status = ref('')

const STATUS_LABEL = { PUBLISHED: '售票中', ENDED: '已结束', CANCELLED: '已取消' }

async function load() {
  loading.value = true
  try {
    const data = await listEvents({ status: status.value || undefined })
    events.value = data.content
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '待定'
}
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 pb-24 pt-8">
    <div class="mb-6 flex items-center gap-4">
      <h1 class="text-2xl font-bold">演出</h1>
      <select v-model="status" class="input-select" @change="load">
        <option value="">全部状态</option>
        <option value="PUBLISHED">售票中</option>
        <option value="ENDED">已结束</option>
      </select>
    </div>

    <div v-if="loading" class="grid gap-4 md:grid-cols-2">
      <div v-for="i in 2" :key="i" class="h-36 animate-pulse rounded-xl bg-surface"></div>
    </div>
    <div v-else-if="errMsg" class="rounded-lg bg-red-950/50 p-4 text-sm text-red-400">{{ errMsg }}</div>
    <div v-else-if="events.length === 0" class="py-20 text-center text-gray-600">暂时没有演出</div>

    <div v-else class="grid gap-4 md:grid-cols-2">
      <RouterLink
        v-for="e in events" :key="e.id"
        :to="`/events/${e.id}`"
        class="flex gap-4 rounded-xl border border-line bg-surface p-4 transition hover:border-primary/50"
      >
        <div class="flex h-28 w-40 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary/20 to-pink-500/10 text-3xl">🎤</div>
        <div class="min-w-0 flex-1">
          <div class="flex items-start justify-between gap-2">
            <p class="truncate font-semibold text-gray-900">{{ e.title }}</p>
            <span class="shrink-0 rounded bg-primary/15 px-2 py-0.5 text-[10px] text-primary">{{ STATUS_LABEL[e.status] || e.status }}</span>
          </div>
          <p class="mt-1 text-xs text-gray-600">{{ e.bandName }} · {{ e.venue }}</p>
          <p class="mt-2 text-xs text-gray-500">最近场次：{{ fmt(e.nextSessionTime) }}</p>
          <p class="mt-1 text-sm font-semibold text-accent">
            <span v-if="e.minPrice != null" class="tabular">¥{{ e.minPrice }} 起</span>
            <span v-else class="text-xs text-gray-600">暂无票档</span>
          </p>
        </div>
      </RouterLink>
    </div>
  </main>
</template>
