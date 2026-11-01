<script setup>
import { computed, reactive, ref } from 'vue'

/**
 * 单条消息气泡（docs/12 · 消息三类型：text / tool / confirm 全在这一条里渲染）
 * - 用户：右对齐主色底；助手：左对齐 card
 * - 助手气泡：角色名 + 文本（流式光标）+ 时间 + hover 复制 + 失败重试
 * - tools：灰色小字条目，状态色 EXECUTED 灰 / FAILED 红 / PENDING 琥珀
 * - pending 确认卡片：绑定自己的 pending 对象（v1 教训：禁用 lastPending 全局查找）
 * - 长回复 >12 行折叠
 */
const props = defineProps({
  msg: { type: Object, required: true },
  confirming: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'retry'])

const copied = ref(false)
const copyTimer = ref(null)
const expanded = ref(false)

const isUser = computed(() => props.msg.role === 'user')
const isSystem = computed(() => props.msg.role === 'system')
const isAssistant = computed(() => props.msg.role === 'assistant')

const time = computed(() => {
  if (!props.msg.at) return ''
  const d = new Date(props.msg.at)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

/** 长回复折叠：超过 12 行（约 480 字符）默认收起 */
const longText = computed(() => isAssistant.value && (props.msg.text || '').length > 480)
const shownText = computed(() => {
  const raw = props.msg.text || ''
  if (!longText.value || expanded.value) return raw
  // 截断处若刚好落在 ** 标记上，先清掉再补省略号，避免露出半个星号
  return raw.slice(0, 480).replace(/\*{1,2}$/, '') + '…'
})

/** 工具条目展开状态：key 带 msg.id，长对话里不串台 */
const openTools = reactive(new Set())
const toolKey = (i) => `${props.msg.id}:${i}`
const toolOpen = (i) => openTools.has(toolKey(i))
function toggleTool(i) {
  const k = toolKey(i)
  openTools.has(k) ? openTools.delete(k) : openTools.add(k)
}

async function copyText() {
  try {
    await navigator.clipboard.writeText(props.msg.text || '')
    copied.value = true
    clearTimeout(copyTimer.value)
    copyTimer.value = setTimeout(() => (copied.value = false), 1500)
  } catch { /* 剪贴板不可用（非 https 等）静默 */ }
}

/** 工具状态 → 颜色（v2 事件：RUNNING 进行中 / OK 成功 / FAIL 失败） */
const toolCls = (s) =>
  s === 'FAIL' || s === 'FAILED' ? 'text-red-500'
    : s === 'RUNNING' || s === 'PENDING' ? 'text-dim animate-pulse'
      : 'text-faint'

/** 确认卡片状态 → 配色 */
const cardCls = computed(() => {
  const st = props.msg.pending?.state
  return {
    PENDING: 'border-amber-300 bg-amber-50',
    EXECUTED: 'border-green-200 bg-green-50',
    REJECTED: 'border-gray-200 bg-gray-50',
    FAILED: 'border-red-200 bg-red-50',
  }[st] || 'border-line bg-surface'
})

</script>

<template>
  <!-- 系统提示：居中灰字，可带行动按钮（重试 / 切回问答） -->
  <div v-if="isSystem" class="my-1 flex flex-col items-center gap-1">
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
  <div v-else-if="isUser" class="flex flex-col items-end gap-0.5">
    <p class="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-primary px-3.5 py-2.5 text-sm leading-relaxed text-white">{{ msg.text }}</p>
    <span v-if="time" class="tabular pr-0.5 text-[10px] text-faint">{{ time }}</span>
  </div>

  <!-- 助手 -->
  <div v-else class="group/msg flex w-full flex-col items-start gap-1">
    <div v-if="msg.name" data-msg-meta class="flex items-baseline gap-1.5 pl-0.5">
      <span class="font-mono text-[10px] uppercase tracking-[0.12em] text-primary/80">{{ msg.name }}</span>
      <span v-if="time && !msg.streaming" class="tabular text-[10px] text-faint">{{ time }}</span>
    </div>

    <!-- 工具条目 -->
    <div v-if="msg.tools?.length" class="flex w-full max-w-[85%] flex-col gap-0.5">
      <p
        v-for="(tc, i) in msg.tools"
        :key="i"
        class="flex cursor-pointer items-baseline gap-1 text-[11px] leading-5"
        :class="toolCls(tc.status)"
        :title="tc.summary || tc.name"
        @click="toggleTool(i)"
      >
        <span class="shrink-0">🔧</span>
        <span class="mono shrink-0">{{ tc.name }}</span>
        <span class="shrink-0 opacity-50">·</span>
        <span class="shrink-0">{{ tc.status }}</span>
        <span
          v-if="tc.summary"
          data-tool-summary
          class="ml-1 min-w-0 flex-1 opacity-80"
          :class="toolOpen(i) ? 'whitespace-normal break-words' : 'truncate'"
        >{{ tc.summary }}</span>
      </p>
    </div>

    <!-- 文本气泡：没吐出第一个字时不渲染（由三点 loading 代表），避免空框 + 三点双框 -->
    <div
      v-if="msg.text"
      data-bubble
      class="relative max-w-[85%] rounded-2xl rounded-bl-sm border border-line bg-base px-3.5 py-2.5 text-sm leading-relaxed"
    >
      <p class="whitespace-pre-wrap break-words">
        {{ shownText }}<span
          v-if="msg.streaming"
          class="ml-0.5 inline-block w-[2px] animate-pulse bg-primary align-middle"
          style="height: 1em"
        />
      </p>
      <button
        v-if="longText && !expanded"
        class="mt-1 cursor-pointer text-[11px] text-primary"
        @click="expanded = true"
      >展开全文</button>

      <!-- hover 操作条：复制 / 失败重试 -->
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

    <!-- 确认卡片：绑自己的 pending（v-for 多卡不串台） -->
    <div v-if="msg.pending" class="w-full max-w-xl">
      <div class="rounded-2xl border p-3" :class="cardCls">
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
          >{{ confirming ? '确认中…' : '确认执行' }}</button>
        </div>
        <p v-if="msg.pending.state === 'FAILED' && /40902|已失效|过期/.test(msg.pending.result || '')" class="mt-1 text-[11px] text-faint">
          请重新下达指令
        </p>
      </div>
    </div>

  </div>
</template>
