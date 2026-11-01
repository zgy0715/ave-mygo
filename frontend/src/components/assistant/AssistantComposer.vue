<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

/**
 * 输入区：快捷提问 chips + 自适应输入框（1~5 行）+ 发送/停止（docs/15：不再分模式）
 * docs/12 §AssistantComposer
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  personaChip: { type: String, default: '小灯' },
})
const emit = defineEmits(['update:modelValue', 'send', 'stop'])

const MAX = 500
const ta = ref(null)

// docs/15：不再分模式，固定一组快捷提问
const CHAT_CHIPS = ['推荐演出', '春日影是哪张专辑', 'MyGO 有哪些成员']

const over = computed(() => props.modelValue.length > MAX)
const canSend = computed(() => !props.loading && !!props.modelValue.trim() && !over.value)

/** 自适应高度 1~5 行；补 2px 上下边框（scrollHeight 不含边框，border-box 下会溢出出滚动条） */
const MAX_H = 120
const BORDER = 2
function autoGrow() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  const need = el.scrollHeight + BORDER
  el.style.height = Math.min(need, MAX_H) + 'px'
  el.style.overflowY = need > MAX_H ? 'auto' : 'hidden'
}
watch(() => props.modelValue, () => nextTick(autoGrow))

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  autoGrow()
}

onMounted(() => nextTick(autoGrow))

onMounted(() => nextTick(autoGrow))

function useChip(s) {
  emit('update:modelValue', s)
  nextTick(() => { ta.value?.focus(); autoGrow() })
}
</script>

<template>
  <!-- 快捷提问 -->
  <div class="flex shrink-0 flex-wrap items-center gap-1.5 px-3 pb-2 pt-2.5">
    <span class="text-[11px] text-faint">💡 推荐提问：</span>
    <button
      v-for="s in CHAT_CHIPS"
      :key="s"
      class="max-w-[16rem] cursor-pointer truncate rounded-full border border-line bg-surface px-2.5 py-1 text-[11px] text-dim transition hover:border-primary hover:text-primary"
      @click="useChip(s)"
    >{{ s }}</button>
  </div>

  <div class="flex shrink-0 items-end gap-2 border-t border-line px-3 pt-2.5 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
    <div class="relative min-w-0 flex-1">
      <textarea
        ref="ta"
        :value="modelValue"
        rows="1"
        :maxlength="MAX"
        class="input max-h-[120px] w-full resize-none overflow-hidden !rounded-lg"
        :placeholder="`问${personaChip}点什么…（Enter 发送 / Shift+Enter 换行）`"
        @input="onInput"
        @keydown="onKeydown"
      />
    </div>

    <button
      v-if="loading"
      class="btn-ghost flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center !px-0"
      title="停止生成"
      aria-label="停止生成"
      @click="emit('stop')"
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2" /></svg>
    </button>
    <button
      v-else
      class="btn-primary h-9 w-9 shrink-0 !px-0 disabled:!bg-elevated disabled:!text-faint disabled:!opacity-100 disabled:!shadow-none"
      :disabled="!canSend"
      aria-label="发送"
      @click="emit('send')"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M2 21 23 12 2 3l4.5 7L14 12l-7.5 2z" /></svg>
    </button>

  </div>

</template>
