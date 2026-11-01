<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { chatV2, chatWithAssistant } from '@/api/assistant'
import { agentConfirm } from '@/api/agent'
import AssistantHeader from '@/components/assistant/AssistantHeader.vue'
import MessageList from '@/components/assistant/MessageList.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'

/**
 * 「小灯」AI 助手 v2（docs/15）：悬浮球 + 单一窗口
 * - 只连 POST /assistant/chat/v2 一条链路（SSE over POST，首字 0.8~1.5s）
 * - 能力由后端按 token 身份决定：游客/普通用户只读；ADMIN 可直接下写指令 → confirm 卡片
 * - 前端不再有「运营开关 / 模式切换」
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
const open = ref(false)
const input = ref('')
const maximized = ref(false)
const confirming = ref(false)

/** 多轮上下文标识：整页一个（后端按它聚合会话） */
const conversationId = crypto.randomUUID?.() || `c-${Date.now()}`

/* ---- 线程：按人格分，各自独立 ---- */
let msgSeq = 0
const mkGreeting = (p) => ({ id: ++msgSeq, role: 'assistant', name: p.chip, text: p.greeting, at: Date.now() })

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

/* ---- 持久化：按人格存最近 50 条（不存 token） ---- */
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
    } catch { /* 损坏忽略 */ }
  }
}
restore()
let persistTimer = null
watch(threads, () => {
  clearTimeout(persistTimer)
  persistTimer = setTimeout(() => PERSONAS.forEach((p) => persist(p.id)), 400)
}, { deep: true })

/* ---- 人格线程的兜底 sessionId（老接口降级时用） ---- */
function getSessionId(pid) {
  const key = `assistant_session:${pid}`
  let id = sessionStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID?.() || `s-${Date.now()}`
    sessionStorage.setItem(key, id)
  }
  return id
}

/* ---- 清空 / 切人格 ---- */
function clearThread() {
  const t = thread.value
  t.messages.splice(0, t.messages.length, mkGreeting(currentPersona.value))
  t.sessionId = ''
  t.error = ''
}
function switchPersona(id) {
  if (!id || persona.value === id || !threads[id]) return
  persona.value = id
}

/* ---- 「思考中」三点：还没出首个字时显示 ---- */
const waitingFirst = computed(() => {
  if (!loading.value) return false
  const ms = messages.value
  const last = ms[ms.length - 1]
  return !!last && last.role === 'assistant' && !last.text && !last.pending
})

/** 工具事件：同名 RUNNING → 后续 OK/FAIL 就地更新，避免条目翻倍 */
function upsertTool(tools, evt) {
  if (evt.status === 'RUNNING') {
    tools.push({ name: evt.name, status: 'RUNNING', summary: evt.summary || '' })
    return
  }
  for (let i = tools.length - 1; i >= 0; i--) {
    if (tools[i].name === evt.name && tools[i].status === 'RUNNING') {
      tools[i].status = evt.status
      tools[i].summary = evt.summary || tools[i].summary
      return
    }
  }
  tools.push({ name: evt.name, status: evt.status, summary: evt.summary || '' })
}

function pushSystem(text, extra = {}) {
  thread.value.messages.push({ id: ++msgSeq, role: 'system', text, at: Date.now(), ...extra })
}

/* ---- 发送（唯一链路：v2 事件流） ---- */
async function send(text, opts = {}) {
  const t = thread.value
  const content = (text ?? input.value).trim()
  if (!content || t.loading) return

  const sentThread = t
  if (!opts.retry) t.messages.push({ id: ++msgSeq, role: 'user', text: content, at: Date.now() })
  input.value = ''
  t.error = ''
  t.loading = true

  const bubble = { id: ++msgSeq, role: 'assistant', name: chipById(persona.value), text: '', at: Date.now(), streaming: true, tools: [] }
  t.messages.push(bubble)
  // 关键：push 后取数组里的响应式代理，直接改裸对象不会触发渲染
  const rb = t.messages[t.messages.length - 1]

  const controller = new AbortController()
  t.controller = controller

  try {
    // v2 后端暂不接收 persona：非小灯人格时在消息前注入口吻提示（后端支持后移除）
    const cue = persona.value === 'xiaodeng'
      ? ''
      : `（请全程用「${chipById(persona.value)}」的口吻和人称回答）`
    await chatV2({
      message: cue + content,
      conversationId,
      signal: controller.signal,
      onEvent: (evt) => {
        switch (evt.type) {
          case 'delta':
            rb.text += evt.text || ''
            break
          case 'tool':
            upsertTool(rb.tools, evt)
            break
          case 'confirm':
            rb.pending = {
              token: evt.token,
              toolName: evt.toolName,
              title: evt.title || evt.toolName,
              arguments: evt.arguments || {},
              preview: evt.preview || '',
              state: 'PENDING',
              result: '',
            }
            break
          case 'error':
            // 50301 = LLM 未就绪：给明确提示，不白屏
            pushSystem(String(evt.code) === '50301' || evt.code === 50301
              ? '小灯暂时离线（LLM 服务未就绪），稍后再试。'
              : `出错了：${evt.message || evt.code || '未知错误'}`)
            break
          default:
            break
        }
      },
    })
  } catch (e) {
    if (e?.name === 'AbortError' || controller.signal.aborted) {
      if (!rb.text && !rb.pending) rb.text = '（已停止生成）'
    } else {
      // v2 流不可用（老后端 / 代理不支持流式）→ 降级 v1 一次性
      try {
        const data = await chatWithAssistant({ message: content, sessionId: getSessionId(persona.value), persona: persona.value })
        rb.text = data.reply
        if (data.personaName) rb.name = data.personaName
      } catch {
        t.messages.splice(t.messages.indexOf(rb), 1)
        pushSystem('小灯走神了，发送失败。', { action: 'retry', payload: content })
      }
    }
  } finally {
    rb.streaming = false
    sentThread.loading = false
    sentThread.controller = null
  }
}

/** 确认卡片：绑定各自的 pending 对象，不猜全局 */
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

/* ---- 面板打开时锁背景滚动（站点用 Lenis，必须 stop 才有效） ---- */
function setBodyScrollLock(lock) {
  try {
    const lenis = window.__lenis
    if (lenis) {
      if (lock) {
        lenis.stop?.()
        const y = lenis.actualScroll ?? lenis.scroll ?? window.scrollY
        lenis.scrollTo?.(y, { immediate: true, force: true })   // 掐掉惯性尾巴
      } else {
        lenis.start?.()
      }
    }
  } catch { /* 忽略 */ }
  const el = document.documentElement
  if (lock) {
    const sw = window.innerWidth - el.clientWidth
    el.style.overflow = 'hidden'
    if (sw > 0) document.body.style.paddingRight = `${sw}px`
  } else {
    el.style.overflow = ''
    document.body.style.paddingRight = ''
  }
}
watch(open, (v) => setBodyScrollLock(v))

/* ---- Esc 逐级退出：先退最大化，再关面板 ---- */
function onGlobalKey(e) {
  if (!open.value || e.key !== 'Escape') return
  if (maximized.value) maximized.value = false
  else open.value = false
}
onMounted(() => window.addEventListener('keydown', onGlobalKey))
onBeforeUnmount(() => {
  setBodyScrollLock(false)
  window.removeEventListener('keydown', onGlobalKey)
  clearTimeout(persistTimer)
})
</script>

<template>
  <!-- 悬浮球：播放条出现时上移避开进度条 -->
  <button
    class="fixed right-6 z-[60] flex h-14 w-14 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-xl transition-all duration-300 hover:scale-105"
    :class="player.current && !player.barCollapsed ? 'bottom-24' : 'bottom-6'"
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
      class="fixed z-[60] flex min-h-0 flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-2xl"
      :class="[
        maximized
          ? 'bottom-[max(1.5rem,env(safe-area-inset-bottom))] right-1/2 h-[90vh] w-[min(1200px,92vw)] translate-x-1/2'
          : 'right-6 h-[min(820px,82vh)] w-[720px] max-w-[calc(100vw-2rem)]',
        !maximized && (player.current && !player.barCollapsed ? 'bottom-[10.5rem]' : 'bottom-[6.5rem]'),
        'max-sm:!bottom-0 max-sm:!right-0 max-sm:!h-[100dvh] max-sm:!w-full max-sm:!max-w-none max-sm:!translate-x-0 max-sm:!rounded-none',
      ]"
      role="dialog"
      data-lenis-prevent
      :aria-label="currentPersona.chip + ' AI 助手'"
    >
      <AssistantHeader
        :persona="currentPersona"
        :personas="PERSONAS"
        :persona-id="persona"
        :threads="threads"
        :maximized="maximized"
        @update:persona-id="switchPersona($event)"
        @toggle-max="maximized = !maximized"
        @close="open = false"
        @clear="clearThread"
      />

      <MessageList
        :messages="messages"
        :loading="loading"
        :waiting-first="waitingFirst"
        :confirming="confirming"
        :empty-hint="['推荐演出', '春日影是哪张专辑', 'MyGO 有哪些成员']"
        @confirm="confirm"
        @retry="(text) => send(text, { retry: true })"
        @send="(s) => send(s)"
      />

      <AssistantComposer
        v-model="input"
        :loading="loading"
        :persona-chip="currentPersona.chip"
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
