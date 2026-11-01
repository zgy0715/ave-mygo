<script setup>
import { computed, reactive, ref } from 'vue'
import RichText from './RichText.vue'

/**
 * 单条消息气泡（docs/12/15 · text / tool / confirm 三类同条渲染）
 * - 经典聊天布局：助手消息带人格渐变头像；用户消息右对齐渐变气泡
 * - tools：状态胶囊（RUNNING 呼吸 / OK 淡 / FAIL 红），点击展开摘要
 * - pending 确认卡片：绑定自己的 pending 对象（禁全局查找）
 * - 长回复 >480 字折叠；hover 出复制/重试
 */
const props = defineProps({
  msg: { type: Object, required: true },
  confirming: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'retry'])

const copied = ref(false)
const copyTimer = ref(null)
const expanded = ref(false)

/** 人格名 → 渐变（与 AssistantWidget.PERSONAS 保持一致） */
const NAME_AVATAR = {
  'Anon': '/img/member-mygo-2.png',
  '小祥': '/img/member-mujica-5.png',
  '睦子米': '/img/member-mujica-3.png',
}
const NAME_BG = {
  '小灯': 'linear-gradient(135deg, #2c7da0, #6b4fa3)',
  'Anon': 'linear-gradient(135deg, #e8618c, #f89)',
  '小祥': 'linear-gradient(135deg, #5d1031, #aa1656)',
  '睦子米': 'linear-gradient(135deg, #4a5568, #86a397)',
}

const isUser = computed(() => props.msg.role === 'user')
const isSystem = computed(() => props.msg.role === 'system')
const isAssistant = computed(() => props.msg.role === 'assistant')

const time = computed(() => {
  if (!props.msg.at) return ''
  const d = new Date(props.msg.at)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

const longText = computed(() => isAssistant.value && (props.msg.text || '').length > 480)
const shownText = computed(() => {
  const raw = props.msg.text || ''
  if (!longText.value || expanded.value) return raw
  return raw.slice(0, 480).replace(/\*{1,2}$/, '') + '…'
})

async function copyText() {
  try {
    await navigator.clipboard.writeText(props.msg.text || '')
    copied.value = true
    clearTimeout(copyTimer.value)
    copyTimer.value = setTimeout(() => (copied.value = false), 1500)
  } catch { /* 剪贴板不可用静默 */ }
}

/** 工具状态 → 颜色（v2：RUNNING 进行中 / OK 成功 / FAIL 失败） */
const toolCls = (s) =>
  s === 'FAIL' || s === 'FAILED' ? 'text-red-500'
    : s === 'RUNNING' || s === 'PENDING' ? 'text-dim animate-pulse'
      : 'text-faint'

const cardCls = computed(() => {
  const st = props.msg.pending?.state
  return {
    PENDING: 'border-amber-300 bg-amber-50',
    EXECUTED: 'border-green-200 bg-green-50',
    REJECTED: 'border-gray-200 bg-gray-50',
    FAILED: 'border-red-200 bg-red-50',
  }[st] || 'border-line bg-surface'
})

const openTools = reactive(new Set())
const toolKey = (i) => `${props.msg.id}:${i}`
const toolOpen = (i) => openTools.has(toolKey(i))
function toggleTool(i) {
  const k = toolKey(i)
  openTools.has(k) ? openTools.delete(k) : openTools.add(k)
}
</script>

<template>
  <!-- 系统提示 -->
  <div v-if="isSystem" class="msg-in my-1 flex flex-col items-center gap-1">
    <p class="text-[11px] text-faint">{{ msg.text }}</p>
    <button
      v-if="msg.action === 'retry'"
      class="cursor-pointer rounded-full border border-line px-3 py-1 text-[11px] text-dim transition hover:border-primary hover:text-primary"
      @click="emit('retry', msg.payload)"
    >↺ 重试</button>
    <button
      v-else-if="msg.action === 'chat'"
      class="cursor-pointer rounded-full border border-line px-3 py-1 text-[11px] text-dim transition hover:border-primary hover:text-primary"
      @click="emit('confirm', null, 'chat')"
    >切回问答模式</button>
  </div>

  <!-- 用户 -->
  <div v-else-if="isUser" class="msg-in flex flex-col items-end gap-0.5">
    <p class="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-[linear-gradient(135deg,#2c7da0,#1f6485)] px-3.5 py-2.5 text-sm leading-relaxed text-white shadow-sm">{{ msg.text }}</p>
    <span v-if="time" class="tabular pr-0.5 text-[10px] text-faint">{{ time }}</span>
  </div>

  <!-- 助手：头像 + 气泡 -->
  <div v-else class="msg-in group/msg flex w-full items-start gap-2">
    <span
      class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center overflow-hidden rounded-full text-white shadow-sm"
      :style="msg.name && NAME_AVATAR[msg.name] ? {} : { background: NAME_BG[msg.name] || NAME_BG['小灯'] }"
    >
      <img v-if="msg.name && NAME_AVATAR[msg.name]" :src="NAME_AVATAR[msg.name]" :alt="msg.name" class="h-full w-full object-cover" />
      <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
      </svg>
    </span>

    <div class="flex min-w-0 flex-1 flex-col items-start gap-1">
      <div v-if="msg.name" data-msg-meta class="flex items-baseline gap-1.5">
        <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-primary/80">{{ msg.name }}</span>
        <span v-if="time && !msg.streaming" class="tabular text-[10px] text-faint">{{ time }}</span>
      </div>

      <!-- 工具胶囊 -->
      <div v-if="msg.tools?.length" class="flex w-full max-w-[85%] flex-col gap-1">
        <p
          v-for="(tc, i) in msg.tools"
          :key="i"
          class="flex cursor-pointer items-baseline gap-1 rounded-lg border border-line bg-surface px-2 py-1 text-[11px] leading-5"
          :class="toolCls(tc.status)"
          @click="toggleTool(i)"
        >
          <span class="shrink-0">🔧</span>
          <span class="mono shrink-0">{{ tc.name }}</span>
          <span class="shrink-0 opacity-50">·</span>
          <span class="shrink-0 font-medium">{{ tc.status }}</span>
          <span
            v-if="tc.summary"
            data-tool-summary
            class="ml-1 min-w-0 flex-1 opacity-80"
            :class="toolOpen(i) ? 'whitespace-normal break-words' : 'truncate'"
          >{{ tc.summary }}</span>

            <span v-if="tc.summary" class="shrink-0 opacity-60">{{ toolOpen(i) ? '▴' : '▾' }}</span>
        </p>
      </div>

      <!-- 文本气泡：没吐首字时不渲染（三点 loading 代表） -->
      <div
        v-if="msg.text"
        data-bubble
        class="relative max-w-[85%] rounded-2xl rounded-bl-sm border border-line bg-white px-3.5 py-2.5 text-sm leading-relaxed shadow-sm"
      >
        <RichText :text="shownText" :streaming="msg.streaming" />
        <button
          v-if="longText && !expanded"
          class="mt-1 cursor-pointer text-[11px] text-primary"
          @click="expanded = true"
        >展开全文</button>

        <div
          data-msg-actions
          class="pointer-events-none absolute -top-2.5 right-2 flex items-center gap-1 opacity-0 transition-opacity duration-150 group-focus-within/msg:pointer-events-auto group-focus-within/msg:opacity-100 group-hover/msg:pointer-events-auto group-hover/msg:opacity-100 max-sm:!pointer-events-auto max-sm:!opacity-100"
        >
          <button
            class="cursor-pointer rounded border border-line bg-surface px-1.5 py-0.5 text-[10px] text-faint shadow-sm transition hover:text-ink"
            :title="copied ? '已复制' : '复制'"
            @click="copyText"
          >{{ copied ? '已复制 ✓' : '⧉' }}</button>
          <button
            v-if="msg.failed"
            class="cursor-pointer rounded border border-line bg-surface px-1.5 py-0.5 text-[10px] text-faint shadow-sm transition hover:text-red-500"
            title="重试"
            @click="emit('retry', msg.retryText)"
          >↺</button>
        </div>
      </div>

      <!-- 确认卡片 -->
      <div v-if="msg.pending" class="w-full max-w-xl">
        <div class="rounded-2xl border p-3 shadow-sm" :class="cardCls">
          <p class="text-sm font-semibold">
            <template v-if="msg.pending.state === 'PENDING'">⚠️ 待确认：{{ msg.pending.title || msg.pending.toolName }}</template>
            <template v-else-if="msg.pending.state === 'EXECUTED'">✅ 已执行</template>
            <template v-else-if="msg.pending.state === 'REJECTED'">🚫 已取消</template>
            <template v-else>❌ 执行失败</template>
          </p>
          <dl class="mt-2 space-y-1 text-xs">
            <div v-for="(v, k) in msg.pending.arguments" :key="k" class="flex gap-2">
              <dt class="w-24 shrink-0 text-faint">{{ k }}</dt>
              <dd class="min-w-0 flex-1 break-all font-medium">{{ v }}</dd>
            </div>
          </dl>
          <p
            v-if="msg.pending.result"
            class="mt-2 break-words text-xs"
            :class="msg.pending.state === 'FAILED' ? 'text-red-600' : 'text-dim'"
          >{{ msg.pending.result }}</p>
          <div v-if="msg.pending.state === 'PENDING'" class="mt-3 flex justify-end gap-2">
            <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="confirming" @click="emit('confirm', msg.pending, false)">取消</button>
            <button
              class="btn-primary !bg-red-500 !px-3 !py-1.5 !text-xs hover:!bg-red-600"
              :disabled="confirming"
              @click="emit('confirm', msg.pending, true)"
            >确认执行</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-in { animation: msg-in 0.22s ease both; }
@keyframes msg-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}
</style>
