<script setup>
import { computed, nextTick, ref, watch } from 'vue'

/**
 * 输入区：快捷提问 chips（按模式）+ 自适应输入框（1~5 行）+ 字数 + 发送/停止 + 运营态标识
 * docs/12 §AssistantComposer
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  mode: { type: String, default: 'chat' },          // 'chat' | 'ops'
  personaChip: { type: String, default: '小灯' },
  slow: { type: Boolean, default: false },          // 运营模式 >20s
})
const emit = defineEmits(['update:modelValue', 'send', 'stop'])

const MAX = 500
const ta = ref(null)

const CHAT_CHIPS = ['推荐演出', '春日影是哪张专辑', 'MyGO 叫怎么读']
const OPS_CHIPS = ['列出所有演出', '把 id=1 的 MV 换成 https://example.com/x']
const chips = computed(() => (props.mode === 'ops' ? OPS_CHIPS : CHAT_CHIPS))

const over = computed(() => props.modelValue.length > MAX)
const canSend = computed(() => !props.loading && !!props.modelValue.trim() && !over.value)

/** 自适应高度 1~5 行 */
function autoGrow() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}
watch(() => props.modelValue, () => nextTick(autoGrow))
onMounted?.()

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

function useChip(s) {
  emit('update:modelValue', s)
  nextTick(() => { ta.value?.focus(); autoGrow() })
}
</script>

<template>
  <!-- 快捷提问：按模式切换文案 -->
  <div class="flex flex-wrap items-center gap-1.5 px-3 pt-2.5">
    <span class="text-[11px] text-faint">💡 {{ mode === 'ops' ? '运营示例：' : '推荐提问：' }}</span>
    <button
      v-for="s in chips"
      :key="s"
      class="max-w-[16rem] cursor-pointer truncate rounded-full border border-line px-2.5 py-1 text-[11px] text-dim transition hover:border-primary hover:text-primary"
      @click="useChip(s)"
    >{{ s }}</button>
  </div>

  <div
    class="flex items-end gap-2 border-t p-3 transition-colors"
    :class="mode === 'ops' ? 'border-amber-300' : 'border-line'"
  >
    <div class="relative min-w-0 flex-1">
      <textarea
        ref="ta"
        :value="modelValue"
        rows="1"
        maxlength="600"
        class="input max-h-[120px] w-full resize-none pr-12"
        :placeholder="mode === 'ops' ? '下达运营指令…（Enter 发送 / Shift+Enter 换行）' : `问${personaChip}点什么…（Enter 发送 / Shift+Enter 换行）`"
        @input="onInput"
        @keydown="onKeydown"
      />
      <span class="tabular absolute bottom-1.5 right-2.5 text-[10px]" :class="over ? 'text-red-500' : 'text-faint'">
        {{ modelValue.length }}/{{ MAX }}
      </span>
    </div>

    <button
      v-if="loading && mode === 'chat'"
      class="btn-ghost h-9 shrink-0 !px-3 !text-xs"
      title="停止生成"
      @click="emit('stop')"
    >停止</button>
    <button
      v-else
      class="btn-primary h-9 w-9 shrink-0 !px-0"
      :class="mode === 'ops' && '!bg-amber-500 hover:!bg-amber-600'"
      :disabled="!canSend"
      aria-label="发送"
      @click="emit('send')"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M2 21 23 12 2 3l4.5 7L14 12l-7.5 2z" /></svg>
    </button>

    <div class="absolute bottom-1 right-14 flex items-center gap-2 sm:static sm:right-auto">
      <span v-if="mode === 'ops'" class="rounded-full border border-amber-400 bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-700">⚙ 运营中</span>
    </div>
  </div>

  <!-- 运营模式慢提示 -->
  <p v-if="slow && mode === 'ops' && loading" class="px-3 pb-2 text-[11px] text-faint">
    本地模型较慢，请稍候（约 30~60s）
  </p>
</template>
