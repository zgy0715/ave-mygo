<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'

/**
 * 消息列表：日期胶囊 + 智能自动滚动（距底 <80px 才跟）+ 回到底部浮标
 */
const props = defineProps({
  messages: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  waitingFirst: { type: Boolean, default: false },
  confirming: { type: Boolean, default: false },
  emptyHint: { type: Array, default: () => [] },
})
const emit = defineEmits(['confirm', 'retry', 'send'])

const listEl = ref(null)
const stickToBottom = ref(true)

const rows = computed(() => {
  const out = []
  let lastDay = ''
  for (const m of props.messages) {
    const day = dayLabel(m.at)
    if (day && day !== lastDay) {
      out.push({ type: 'date', id: `d-${day}`, label: day })
      lastDay = day
    }
    out.push({ type: 'msg', id: m.id, msg: m })
  }
  return out
})

function dayLabel(at) {
  if (!at) return ''
  const d = new Date(at)
  const now = new Date()
  const same = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const yest = new Date(now); yest.setDate(now.getDate() - 1)
  if (same(d, now)) return '今天'
  if (same(d, yest)) return '昨天'
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}`
}

function onScroll() {
  const el = listEl.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function scrollBottom(behavior = 'smooth') {
  nextTick(() => {
    const el = listEl.value
    if (!el) return
    if (typeof el.scrollTo === 'function') el.scrollTo({ top: el.scrollHeight, behavior })
    else el.scrollTop = el.scrollHeight
  })
}

watch(
  () => props.messages.map((m) => `${m.id}:${(m.text || '').length}`).join('|'),
  () => { if (stickToBottom.value) scrollBottom() },
)
watch(() => props.messages.length, () => { if (stickToBottom.value) scrollBottom('auto') })

onMounted(() => {
  listEl.value?.addEventListener('scroll', onScroll, { passive: true })
  scrollBottom('auto')
})
onBeforeUnmount(() => listEl.value?.removeEventListener('scroll', onScroll))

defineExpose({ scrollBottom })
</script>

<template>
  <div class="relative min-h-0 flex-1 overflow-hidden">
    <div
      ref="listEl"
      class="flex h-full flex-col gap-3 overflow-y-auto overscroll-contain bg-base/50 px-5 pb-3 pt-3.5"
      aria-live="polite"
    >
      <!-- 空态 -->
      <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center gap-2 px-6 text-center">
        <p class="serif text-base text-dim">💡 试试：</p>
        <button
          v-for="s in emptyHint"
          :key="s"
          class="max-w-full cursor-pointer truncate rounded-xl border border-line bg-surface px-3.5 py-1.5 text-xs text-dim transition hover:border-primary hover:text-ink"
          @click="emit('send', s)"
        >{{ s }}</button>
      </div>

      <template v-for="row in rows" :key="row.id">
        <p v-if="row.type === 'date'" class="self-center">
          <span class="rounded-full border border-line bg-surface px-3 py-0.5 font-mono text-[10px] tracking-[0.14em] text-faint shadow-sm">{{ row.label }}</span>
        </p>
        <MessageBubble
          v-else
          :msg="row.msg"
          :confirming="confirming"
          @confirm="(pending, approve) => emit('confirm', pending, approve)"
          @retry="(text) => emit('retry', text)"
        />
      </template>

      <!-- 三点 loading -->
      <div v-if="waitingFirst" class="flex w-[72px] items-center justify-center gap-1.5 self-start rounded-2xl rounded-bl-sm border border-line bg-white px-3 py-2.5 shadow-sm">
        <span class="assistant-dot" style="animation-delay: 0s" />
        <span class="assistant-dot" style="animation-delay: .15s" />
        <span class="assistant-dot" style="animation-delay: .3s" />
      </div>
    </div>

    <!-- 回到底部浮标 -->
    <button
      v-if="!stickToBottom"
      class="absolute bottom-4 right-4 z-10 flex h-8 w-8 cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-dim shadow-md transition hover:text-ink"
      aria-label="回到底部"
      @click="scrollBottom(); stickToBottom = true"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 9l6 6 6-6" /></svg>
    </button>
  </div>
</template>
