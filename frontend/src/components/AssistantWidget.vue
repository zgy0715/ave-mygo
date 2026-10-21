<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { chatWithAssistant } from '@/api/assistant'

/**
 * 「小灯」AI 助手：右下角悬浮球 + 聊天抽屉。
 * - 悬浮球 fixed 常驻（播放条出现时上移避开进度条）
 * - sessionId：crypto.randomUUID() 首次生成存 sessionStorage，刷新续会话（历史在后端）
 * - 发送 → 立即上屏用户消息 + 三点 loading → 响应替换为 reply；失败红字可重试
 */
const player = usePlayerStore()
const open = ref(false)
const input = ref('')
const listEl = ref(null)

/* ---- 人格：xiaodeng 缺省；切换保留会话，各自口吻的问候语 ---- */
const PERSONAS = [
  {
    id: 'xiaodeng', chip: '小灯',
    gradient: 'linear-gradient(135deg, #2c7da0, #6b4fa3)',
    greeting: '我是小灯，本站的智能助手✳ 你可以问我站内的歌曲、乐队成员，或者让推荐演出哦。',
  },
  {
    id: 'anon', chip: 'Anon',
    gradient: 'linear-gradient(135deg, #e8618c, #f89)',
    greeting: '嗨嗨～我是千早愛音✳ 本站的歌曲、成员、演出，想聊什么都可以哦！先说好，MyGO 的票可是超——推荐的说！',
  },
  {
    id: 'sakiko', chip: '小祥',
    gradient: 'linear-gradient(135deg, #5d1031, #aa1656)',
    greeting: '……豊川祥子。本站的事，问我便是。歌曲、成员、演出——吧台后面的事，也可以问哦。',
  },
  {
    id: 'mutsumi', chip: '睦子米',
    gradient: 'linear-gradient(135deg, #4a5568, #86a397)',
    greeting: '……睦。歌曲的事，演出的事……想知道的话，说吧。我都在。',
  },
]
const persona = ref('xiaodeng')

/**
 * 每个人格一条独立对话线程（相当于各自一个会话页面）：
 * 切换人格 = 切线程，历史互不串台；sessionId 也各自独立（后端记忆不混淆）
 */
const threads = reactive(
  Object.fromEntries(PERSONAS.map((p) => [p.id, {
    messages: [{ role: 'assistant', name: p.chip, text: p.greeting }],
    sessionId: '',
    loading: false,
    error: '',
  }])),
)
const thread = computed(() => threads[persona.value])
const messages = computed(() => thread.value.messages)
const loading = computed(() => thread.value.loading)
const error = computed(() => thread.value.error)

const currentPersona = computed(() => PERSONAS.find((p) => p.id === persona.value) || PERSONAS[0])
const chipById = (id) => PERSONAS.find((p) => p.id === id)?.chip || '小灯'

function switchPersona(p) {
  if (persona.value === p.id) return
  persona.value = p.id
  scrollBottom()
}

/** 清空当前人格的对话（重新打招呼 + 换新 session） */
function clearThread() {
  const t = thread.value
  t.messages.splice(0, t.messages.length, { role: 'assistant', name: currentPersona.value.chip, text: currentPersona.value.greeting })
  t.sessionId = ''
  t.error = ''
  scrollBottom()
}

/* ---- 会话 ID：每个人格独立，首次生成，sessionStorage 持久 ---- */
function getSessionId(pid) {
  const key = `assistant_session:${pid}`
  let id = sessionStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID?.() || `s-${Date.now()}-${Math.random().toString(36).slice(2)}`
    sessionStorage.setItem(key, id)
  }
  return id
}

/* ---- 自动滚底 ---- */
function scrollBottom() {
  nextTick(() => listEl.value?.scrollTo({ top: listEl.value.scrollHeight, behavior: 'smooth' }))
}
watch(() => [messages.value.length, loading.value], scrollBottom)

/* ---- 发送（线程隔离：回复永远回到发送时那条线程） ---- */
async function send() {
  const t = thread.value
  const text = input.value.trim()
  if (!text || t.loading) return
  if (text.length > 500) { t.error = '消息太长啦，控制在 500 字以内'; return }

  const sentPersona = persona.value
  const sentThread = t          // 飞行中切人格：回复仍落回原线程，不串台
  t.messages.push({ role: 'user', text })
  input.value = ''
  t.error = ''
  t.loading = true

  try {
    const data = await chatWithAssistant({ message: text, sessionId: getSessionId(sentPersona), persona: sentPersona })
    sentThread.messages.push({ role: 'assistant', text: data.reply, name: data.personaName || chipById(sentPersona) })
  } catch {
    sentThread.error = '小灯走神了，再问一次好不好？'
  } finally {
    sentThread.loading = false
    if (sentPersona === persona.value) scrollBottom()
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
}
</script>

<template>
  <!-- 悬浮球：播放条出现时上移避开进度条 -->
  <button
    class="fixed right-6 z-[42] flex h-14 w-14 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-xl transition-all duration-300 hover:scale-105"
    :class="player.current ? 'bottom-24' : 'bottom-6'"
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

  <!-- 聊天抽屉：从底部弹起 -->
  <Transition name="assistant">
    <div
      v-if="open"
      class="fixed right-6 z-[42] flex w-[380px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-2xl"
      :class="player.current ? 'bottom-[10.5rem]' : 'bottom-[6.5rem]'"
      style="height: min(560px, 70vh)"
    >
      <!-- 顶栏：徽章 + 名称/状态 + 人格 chips + 关闭 -->
      <div class="border-b border-line px-4 py-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="flex h-9 w-9 items-center justify-center rounded-full text-white shadow-sm" :style="{ background: currentPersona.gradient }">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
                <rect x="9.4" y="18.8" width="5.2" height="1.7" rx="0.85" />
                <rect x="10.2" y="21.1" width="3.6" height="1.4" rx="0.7" />
              </svg>
            </div>
            <div>
              <p class="text-sm font-bold leading-tight">{{ currentPersona.chip }} <span class="micro-accent !text-[9px]">✳ AI 助手</span></p>
              <p class="micro mt-0.5 flex items-center gap-1.5">
                <span class="inline-block h-1.5 w-1.5 rounded-full bg-green-500" />在线 ONLINE
              </p>
            </div>
          </div>
          <button class="cursor-pointer text-dim transition hover:text-ink" aria-label="收起" @click="open = false">✕</button>
        </div>
        <!-- 人格切换：每个人格一条独立对话（右侧 ↺ 清空当前线程） -->
        <div class="mt-2.5 flex items-center gap-1.5">
          <button
            v-for="p in PERSONAS"
            :key="p.id"
            class="flex cursor-pointer items-center rounded-full px-2.5 py-1 text-xs font-medium transition-colors duration-200"
            :class="persona === p.id ? 'bg-primary text-white' : 'bg-base text-dim hover:text-ink'"
            @click="switchPersona(p)"
          >
            {{ p.chip }}
            <span
              v-if="threads[p.id].messages.length > 1"
              class="ml-1 inline-block h-1.5 w-1.5 rounded-full"
              :class="persona === p.id ? 'bg-white/80' : 'bg-primary'"
            />
          </button>
          <button
            class="ml-auto cursor-pointer text-xs text-faint transition hover:text-ink"
            title="清空当前对话"
            aria-label="清空当前对话"
            @click="clearThread"
          >
            ↺ 清空
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div ref="listEl" class="flex flex-1 flex-col gap-3 overflow-y-auto px-4 py-4">
        <template v-for="(m, i) in messages" :key="i">
          <!-- 系统提示：换人格 -->
          <p v-if="m.role === 'system'" class="micro self-center !text-[9px]">{{ m.text }}</p>
          <!-- 用户：右对齐主色底 -->
          <p v-else-if="m.role === 'user'" class="self-end rounded-2xl rounded-br-sm bg-primary px-3.5 py-2 text-sm leading-relaxed text-white">
            {{ m.text }}
          </p>
          <!-- 助手：左对齐 card 底，顶部显示当前角色名 -->
          <div v-else class="card self-start max-w-[85%] rounded-2xl rounded-bl-sm px-3.5 py-2">
            <p v-if="m.name" class="micro-accent mb-1 !text-[9px]">{{ m.name }}</p>
            <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ m.text }}</p>
          </div>
        </template>

        <!-- 三点 loading -->
        <div v-if="loading" class="card flex items-center gap-1.5 self-start rounded-2xl rounded-bl-sm px-4 py-3">
          <span class="assistant-dot" style="animation-delay: 0s" />
          <span class="assistant-dot" style="animation-delay: .15s" />
          <span class="assistant-dot" style="animation-delay: .3s" />
        </div>

        <!-- 错误红字（不阻塞后续提问） -->
        <p v-if="error" class="self-start text-xs text-red-500">{{ error }}</p>
      </div>

      <!-- 输入区 -->
      <div class="flex items-end gap-2 border-t border-line p-3">
        <textarea
          v-model="input"
          rows="1"
          class="input max-h-24 flex-1 resize-none"
          :placeholder="`问${currentPersona.chip}点什么…（回车发送）`"
          @keydown="onKeydown"
        />
        <button
          class="btn-primary h-9 w-9 shrink-0 !px-0"
          :disabled="loading || !input.trim()"
          aria-label="发送"
          @click="send"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M2 21 23 12 2 3l4.5 7L14 12l-7.5 2z" /></svg>
        </button>
      </div>
    </div>
  </Transition>
</template>
