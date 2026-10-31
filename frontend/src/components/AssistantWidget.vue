<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useAuthStore } from '@/stores/auth'
import { chatStream, chatWithAssistant } from '@/api/assistant'
import { agentChat, agentConfirm } from '@/api/agent'
import AssistantHeader from '@/components/assistant/AssistantHeader.vue'
import MessageList from '@/components/assistant/MessageList.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'

/**
 * 「小灯」AI 助手 v2（docs/12）：悬浮球 + 一个窗口融合问答与运营
 * - 问答（默认）：SSE 流式，/assistant/chat/stream，可停止
 * - 运营（仅 ADMIN 手动开）：/admin/agent/chat 非流式（20~60s），写操作出确认卡片
 * - 切模式不清空消息；消息用 mode 标记所属链路
 * - 尺寸三态：常规 720×82vh / 最大化 / 收起；Esc 先退最大化再关闭
 */

/* ---- 人格 ---- */
const PERSONAS = [
  { id: 'xiaodeng', chip: '小灯', gradient: 'linear-gradient(135deg, #2c7da0, #6b4fa3)', greeting: '我是小灯，本站的智能助手✳ 你可以问我站内的歌曲、乐队成员，或者让推荐演出哦。' },
  { id: 'anon', chip: 'Anon', gradient: 'linear-gradient(135deg, #e8618c, #f89)', greeting: '嗨嗨～我是千早愛音✳ 本站的歌曲、成员、演出，想聊什么都可以哦！先说好，MyGO 的票可是超——推荐的说！' },
  { id: 'sakiko', chip: '小祥', gradient: 'linear-gradient(135deg, #5d1031, #aa1656)', greeting: '……豊川祥子。本站的事，问我便是。歌曲、成员、演出——吧台后面的事，也可以问哦。' },
  { id: 'mutsumi', chip: '睦子米', gradient: 'linear-gradient(135deg, #4a5568, #86a397)', greeting: '……睦。歌曲的事，演出的事……想知道的话，说吧。我都在。' },
]

const player = usePlayerStore()
const auth = useAuthStore()
const open = ref(false)
const input = ref('')
const maximized = ref(false)
const opsMode = ref(false)
const confirming = ref(false)
const opsSlow = ref(false)

const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const mode = computed(() => (opsMode.value && isAdmin.value ? 'ops' : 'chat'))

/* ---- 线程：按人格分；消息带 mode 标记 ---- */
let msgSeq = 0
const mkGreeting = (p) => ({ id: ++msgSeq, role: 'assistant', mode: 'chat', name: p.chip, text: p.greeting, at: Date.now() })

const threads = reactive(Object.fromEntries(PERSONAS.map((p) => [p.id, {
  messages: [mkGreeting(p)],
  sessionId: '',
  loading: false,
  error: '',
}])))
const persona = ref('xiaodeng')
const thread = computed(() => threads[persona.value])
const messages = computed(() => thread.value.messages)
const loading = computed(() => thread.value.loading)
const error = computed(() => thread.value.error)
const currentPersona = computed(() => PERSONAS.find((p) => p.id === persona.value) || PERSONAS[0])
const chipById = (id) => PERSONAS.find((p) => p.id === id)?.chip || '小灯'

/* ---- 持久化（P1）：按人格存最近 50 条到 sessionStorage（不存 token） ---- */
const PERSIST_KEY = (pid) => `assistant_thread:${pid}`
function persist(pid) {
  try {
    const t = threads[pid]
    sessionStorage.setItem(PERSIST_KEY(pid), JSON.stringify({ messages: t.messages.slice(-50), sessionId: t.sessionId }))
  } catch { /* 存储满等静默 */ }
}
function restore() {
  for (const p of PERSONAS) {
    try {
      const raw = sessionStorage.getItem(PERSIST_KEY(p.id))
      if (!raw) continue
      const saved = JSON.parse(raw)
      if (Array.isArray(saved.messages) && saved.messages.length) {
        threads[p.id].messages = saved.messages
        msgSeq = Math.max(msgSeq, ...saved.messages.map((m) => m.id || 0))
        threads[p.id].sessionId = saved.sessionId || ''
      }
    } catch { /* 损坏则忽略 */ }
  }
}
restore()
let persistTimer = null
watch(threads, () => {
  clearTimeout(persistTimer)
  persistTimer = setTimeout(() => PERSONAS.forEach((p) => persist(p.id)), 400)
}, { deep: true })

/* ---- 会话 ID：每个人格独立；运营 conversationId 整页一个 ---- */
function getSessionId(pid) {
  const key = `assistant_session:${pid}`
  let id = sessionStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID?.() || `s-${Date.now()}-${Math.random().toString(36).slice(2)}`
    sessionStorage.setItem(key, id)
  }
  return id
}
const opsConversationId = crypto.randomUUID?.() || `ops-${Date.now()}`

/* ---- 清空 / 切人格 ---- */
function clearThread() {
  const t = thread.value
  t.messages.splice(0, t.messages.length, mkGreeting(currentPersona.value))
  t.sessionId = ''
  t.error = ''
}
function switchPersona(p) {
  if (persona.value === p.id) return
  persona.value = p.id
}

/* ---- waitingFirst：只在该吐第一个字前显示三点 ---- */
const waitingFirst = computed(() => {
  if (!loading.value) return false
  const ms = messages.value
  const last = ms[ms.length - 1]
  return !!last && last.role === 'assistant' && !last.text && !last.pending
})

/* ---- 发送：按模式路由 ---- */
let opsTimer = null
async function send(text, opts = {}) {
  const t = thread.value
  const content = (text ?? input.value).trim()
  if (!content || t.loading) return
  if (content.length > 500) { t.error = '消息太长啦，控制在 500 字以内'; return }

  const sentPersona = persona.value
  const sentThread = t
  const m = mode.value

  if (!opts.retry) t.messages.push({ id: ++msgSeq, role: 'user', mode: m, text: content, at: Date.now() })
  input.value = ''
  t.error = ''
  t.loading = true

  if (m === 'ops') {
    /* ===== 运营链路：非流式，一次性返回 ===== */
    const bubble = { id: ++msgSeq, role: 'assistant', mode: 'ops', name: '小灯', text: '', at: Date.now() }
    t.messages.push(bubble)
    opsSlow.value = false
    clearTimeout(opsTimer)
    opsTimer = setTimeout(() => (opsSlow.value = true), 20_000)
    try {
      const data = await agentChat(content, opsConversationId)
      bubble.text = data.content || ''
      bubble.tools = data.toolCalls || []
      if (data.type === 'pending_confirmation') {
        bubble.pending = { token: data.token, toolName: data.toolName, arguments: data.arguments || {}, state: 'PENDING', result: '' }
      }
    } catch (e) {
      const msg = String(e.message)
      t.messages.splice(t.messages.indexOf(bubble), 1)
      if (/LLM|50301/.test(msg)) {
        pushSystem('运营模式需要 LLM 服务（Ollama 未启动）。', 'ops', { action: 'chat' })
      } else if (/403|管理员/.test(msg)) {
        pushSystem('需要管理员权限。', 'ops')
        opsMode.value = false
      } else {
        pushSystem(`出错了：${msg}`)
      }
    } finally {
      clearTimeout(opsTimer)
      opsSlow.value = false
      t.loading = false
    }
    return
  }

  /* ===== 问答链路：SSE 流式 ===== */
  const bubble = { id: ++msgSeq, role: 'assistant', mode: 'chat', name: chipById(sentPersona), text: '', at: Date.now(), streaming: true }
  t.messages.push(bubble)
  const controller = new AbortController()
  t.controller = controller
  try {
    const meta = await chatStream({
      message: content,
      sessionId: getSessionId(sentPersona),
      persona: sentPersona,
      signal: controller.signal,
      onDelta: (d) => { bubble.text += d },
    })
    if (meta?.personaName) bubble.name = meta.personaName
  } catch (e) {
    if (e?.name === 'AbortError' || controller.signal.aborted) {
      if (!bubble.text) bubble.text = '（已停止生成）'
    } else {
      // SSE 不可用 → 降级一次性；再失败 → 系统提示 + 重试
      try {
        const data = await chatWithAssistant({ message: content, sessionId: getSessionId(sentPersona), persona: sentPersona })
        bubble.text = data.reply
        if (data.personaName) bubble.name = data.personaName
      } catch {
        t.messages.splice(t.messages.indexOf(bubble), 1)
        pushSystem('小灯走神了，发送失败。', 'chat', { action: 'retry', payload: content })
      }
    }
  } finally {
    bubble.streaming = false
    sentThread.loading = false
    sentThread.controller = null
  }
}

function pushSystem(text, m = 'chat', extra = {}) {
  thread.value.messages.push({ id: ++msgSeq, role: 'system', mode: m, text, at: Date.now(), ...extra })
}

/** 确认卡片：绑各自的 pending（不在全局找） */
async function confirm(pending, approve) {
  if (!pending || pending.state !== 'PENDING' || confirming.value) return
  confirming.value = true
  try {
    const data = await agentConfirm(pending.token, approve)
    pending.state = data.status
    pending.result = data.result || ''
  } catch (e) {
    const msg = String(e.message)
    if (/40902|已处理|已失效|过期|40004|不存在/.test(msg)) {
      pending.state = 'FAILED'
      pending.result = '这次确认已失效，请重新下达指令'
    } else {
      pushSystem(`确认出错：${msg}`)
    }
  } finally {
    confirming.value = false
  }
}

function stop() {
  thread.value.controller?.abort()
}

/* ---- Esc 逐级退出：先退最大化，再关面板 ---- */
function onGlobalKey(e) {
  if (!open.value || e.key !== 'Escape') return
  if (maximized.value) maximized.value = false
  else open.value = false
}
onMounted(() => window.addEventListener('keydown', onGlobalKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKey)
  clearTimeout(opsTimer)
  clearTimeout(persistTimer)
})
</script>

<template>
  <!-- 悬浮球：播放条出现时上移避开进度条 -->
  <button
    class="fixed right-6 z-[42] flex h-14 w-14 cursor-pointer items-center justify-center rounded-full text-white shadow-xl transition-all duration-300 hover:scale-105"
    :class="[player.current && !player.barCollapsed ? 'bottom-24' : 'bottom-6', opsMode ? 'bg-amber-500' : 'bg-primary']"
    :aria-expanded="open"
    :aria-label="currentPersona.chip + ' AI 助手'"
    style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)"
    @click="open = !open"
  >
    <svg v-if="!open" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
      <rect x="9.4" y="18.8" width="5.2" height="1.7" rx="0.85" />
      <rect x="10.2" y="21.1" width="3.6" height="1.4" rx="0.7" />
    </svg>
    <span v-else class="text-lg leading-none">✕</span>
  </button>

  <!-- 面板：常规 720 / 最大化 / 移动端全屏 -->
  <Transition name="assistant">
    <div
      v-if="open"
      class="fixed z-[42] flex flex-col overflow-hidden rounded-xl border bg-surface shadow-2xl"
      :class="[
        maximized
          ? 'bottom-[max(1.5rem,env(safe-area-inset-bottom))] right-1/2 h-[90vh] w-[min(1200px,92vw)] translate-x-1/2'
          : 'right-6 h-[min(820px,82vh)] w-[720px] max-w-[calc(100vw-2rem)]',
        !maximized && (player.current && !player.barCollapsed ? 'bottom-[10.5rem]' : 'bottom-[6.5rem]'),
        'max-sm:!bottom-0 max-sm:!right-0 max-sm:!h-[100dvh] max-sm:!w-full max-sm:!translate-x-0 max-sm:!rounded-none',
        opsMode ? 'border-amber-300' : 'border-line',
      ]"
      role="dialog"
      :aria-label="opsMode ? '小灯运营模式' : '小灯 AI 助手'"
    >
      <AssistantHeader
        :persona="currentPersona"
        :personas="PERSONAS"
        :persona-id="persona"
        :threads="threads"
        :is-admin="isAdmin"
        :ops-mode="opsMode"
        :maximized="maximized"
        @update:persona-id="switchPersona($event)"
        @update:ops-mode="opsMode = $event"
        @toggle-max="maximized = !maximized"
        @close="open = false"
        @clear="clearThread"
      />

      <MessageList
        :messages="messages"
        :loading="loading"
        :waiting-first="waitingFirst"
        :confirming="confirming"
        :empty-hint="mode === 'ops' ? ['列出所有演出'] : ['推荐演出', '春日影是哪张专辑']"
        @confirm="confirm"
        @retry="(text) => send(text, { retry: true })"
        @send="(s) => send(s)"
      />

      <AssistantComposer
        v-model="input"
        :loading="loading"
        :mode="mode"
        :persona-chip="currentPersona.chip"
        :slow="opsSlow"
        @send="send()"
        @stop="stop"
      />
    </div>
  </Transition>
</template>

<style scoped>
.assistant-enter-active,
.assistant-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.assistant-enter-from,
.assistant-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
