<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { agentChat, agentConfirm } from '@/api/agent'

/**
 * 小灯 · Agent 运营台（管理端 M4）
 * 自然语言指挥小灯调用管理端工具；写操作先出确认卡片，人工确认才执行。
 * 文档：docs/11-前端提示词-Agent运营台.md
 *
 * 消息结构：{ role:'user'|'assistant'|'system', content, toolCalls:[{name,status,summary}],
 *            pending:{token,toolName,arguments,state:'PENDING'|'EXECUTED'|'REJECTED'|'FAILED',result}|null }
 */

// toolName → 中文动作（确认卡片标题用）
const TOOL_LABEL = {
  update_song_mv: '修改歌曲 MV',
  set_ticket_status: '票档上下架',
  delete_session: '删除场次',
  delete_event: '删除演出',
}

const SUGGESTIONS = [
  '列出所有演出',
  '查一下迷星叫的信息',
  '把迷星叫的 MV 换成 https://www.bilibili.com/video/BV1GJ411x7h7',
]

const messages = ref([])
const input = ref('')
const loading = ref(false)
const errMsg = ref('')
const confirming = ref(false)
const listEl = ref(null)
const conversationId = crypto.randomUUID()
let msgSeq = 0

const lastPending = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'assistant' && m.pending) return { msg: m, idx: i }
  }
  return null
})

function scrollBottom() {
  nextTick(() => {
    const el = listEl.value
    if (!el) return
    if (typeof el.scrollTo === 'function') el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    else el.scrollTop = el.scrollHeight       // jsdom/旧环境兜底
  })
}

function clearConversation() {
  messages.value = []
  errMsg.value = ''
}

function pushSystem(text) {
  messages.value.push({ id: ++msgSeq, role: 'system', content: text })
  scrollBottom()
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || loading.value) return
  if (content.length > 500) { errMsg.value = '指令太长啦，控制在 500 字以内'; return }

  messages.value.push({ id: ++msgSeq, role: 'user', content })
  input.value = ''
  errMsg.value = ''
  loading.value = true
  scrollBottom()

  try {
    const data = await agentChat(content, conversationId)
    messages.value.push({
      id: ++msgSeq,
      role: 'assistant',
      content: data.content || '',
      toolCalls: data.toolCalls || [],
      pending: data.type === 'pending_confirmation'
        ? { token: data.token, toolName: data.toolName, arguments: data.arguments || {}, state: 'PENDING', result: '' }
        : null,
    })
  } catch (e) {
    // 50301 = LLM 不可用：引导去只读智能问答，不弹整页报错
    if (String(e.message).includes('LLM') || String(e.message).includes('50301')) {
      pushSystem('小灯暂时离线（需要 LLM 服务）。可以先用右上角悬浮球的「智能问答」做只读咨询。')
    } else {
      pushSystem(`出错了：${e.message}`)
    }
  } finally {
    loading.value = false
    scrollBottom()
  }
}

/** 确认/取消待执行操作；结果三态就地更新到气泡上 */
async function confirmPending(approve) {
  const target = lastPending.value
  if (!target || confirming.value || target.msg.pending.state !== 'PENDING') return
  confirming.value = true
  try {
    const data = await agentConfirm(target.msg.pending.token, approve)
    target.msg.pending.state = data.status        // EXECUTED / REJECTED / FAILED
    target.msg.pending.result = data.result || ''
    // 失败/已取消后允许重新下指令；成功提示就地展示
  } catch (e) {
    // 40902 token 已处理/过期、40004 不存在 → 引导重新下达
    if (/40902|已失效|已处理|过期|40004|不存在/.test(e.message)) {
      target.msg.pending.state = 'FAILED'
      target.msg.pending.result = '这次确认已失效，请重新下达指令'
    } else {
      pushSystem(`确认出错：${e.message}`)
    }
  } finally {
    confirming.value = false
    scrollBottom()
  }
}

onMounted(() => {
  // 面板滚动用不到全局监听；此处仅为占位语义（无副作用）
})
onBeforeUnmount(() => {})
</script>

<template>
  <div class="flex flex-col" style="height: calc(100vh - 13rem); min-height: 28rem">
    <!-- 头部 -->
    <div class="mb-3 flex items-center justify-between gap-3">
      <div>
        <p class="micro-accent mb-1.5">XIAODENG — AGENT CONSOLE</p>
        <h1 class="serif text-2xl font-semibold">小灯 · 运营台</h1>
        <p class="mt-1 text-xs text-faint">用自然语言下指令；写操作必须人工确认才会执行，全程留痕可审计</p>
      </div>
      <button class="btn-ghost shrink-0 !px-3 !py-1.5 !text-xs" :disabled="loading" @click="clearConversation">清空对话</button>
    </div>

    <!-- 消息区 -->
    <div ref="listEl" class="card min-h-0 flex-1 overflow-y-auto p-4">
      <!-- 空态：示例指令 -->
      <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center gap-3 text-center">
        <p class="serif text-lg text-dim">💡 试试对我说：</p>
        <div class="flex flex-col gap-2">
          <button
            v-for="s in SUGGESTIONS"
            :key="s"
            class="cursor-pointer rounded-lg border border-line bg-surface px-4 py-2 text-left text-sm text-dim transition hover:border-primary hover:text-ink"
            @click="send(s)"
          >
            {{ s }}
          </button>
        </div>
      </div>

      <template v-for="m in messages" :key="m.id">
        <!-- 系统提示（灰字，不抢戏） -->
        <p v-if="m.role === 'system'" class="my-2 text-center text-[11px] text-faint">{{ m.content }}</p>

        <!-- 用户 -->
        <div v-else-if="m.role === 'user'" class="mb-3 flex justify-end">
          <p class="max-w-[80%] whitespace-pre-wrap rounded-2xl rounded-br-sm bg-primary px-4 py-2.5 text-sm text-white">{{ m.content }}</p>
        </div>

        <!-- 助手 -->
        <div v-else class="mb-3 flex flex-col items-start gap-1.5">
          <p class="micro !text-[9px] text-faint">小灯</p>

          <!-- 工具调用条目：灰色小字 -->
          <div v-if="m.toolCalls?.length" class="w-full space-y-1">
            <p v-for="(tc, i) in m.toolCalls" :key="i" class="text-[11px] text-faint">
              🔧 <span class="mono">{{ tc.name }}</span>
              <span class="mx-1 text-line-strong">·</span>{{ tc.status }}
              <template v-if="tc.summary"> — {{ tc.summary }}</template>
            </p>
          </div>

          <!-- 正文 -->
          <p v-if="m.content" class="card max-w-full whitespace-pre-wrap break-all px-4 py-2.5 text-sm leading-relaxed">{{ m.content }}</p>

          <!-- 待确认 / 已决策 卡片 -->
          <div v-if="m.pending" class="w-full max-w-xl">
            <div
              class="rounded-xl border p-4"
              :class="{
                'border-amber-300 bg-amber-50': m.pending.state === 'PENDING',
                'border-green-200 bg-green-50': m.pending.state === 'EXECUTED',
                'border-gray-200 bg-gray-50': m.pending.state === 'REJECTED',
                'border-red-200 bg-red-50': m.pending.state === 'FAILED',
              }"
            >
              <p class="text-sm font-semibold">
                <template v-if="m.pending.state === 'PENDING'">⚠️ 待确认：{{ TOOL_LABEL[m.pending.toolName] || m.pending.toolName }}</template>
                <template v-else-if="m.pending.state === 'EXECUTED'">✅ 已执行</template>
                <template v-else-if="m.pending.state === 'REJECTED'">🚫 已取消</template>
                <template v-else>❌ 执行失败</template>
              </p>

              <!-- 参数表 -->
              <dl class="mt-2 space-y-1 text-xs">
                <div v-for="(v, k) in m.pending.arguments" :key="k" class="flex gap-2">
                  <dt class="w-24 shrink-0 text-faint">{{ k }}</dt>
                  <dd class="min-w-0 flex-1 break-all font-medium">{{ v }}</dd>
                </div>
              </dl>

              <p v-if="m.pending.result" class="mt-2 text-xs" :class="m.pending.state === 'FAILED' ? 'text-red-600' : 'text-dim'">
                {{ m.pending.result }}
              </p>

              <div v-if="m.pending.state === 'PENDING'" class="mt-3 flex justify-end gap-2">
                <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="confirming" @click="confirmPending(false)">取消</button>
                <button
                  class="btn-primary !bg-red-500 !px-3 !py-1.5 !text-xs hover:!bg-red-600"
                  :disabled="confirming"
                  @click="confirmPending(true)"
                >
                  {{ confirming ? '确认中…' : '确认执行' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 思考中 -->
      <div v-if="loading" class="card flex w-24 items-center justify-center gap-1.5 rounded-2xl rounded-bl-sm px-4 py-3">
        <span class="assistant-dot" style="animation-delay: 0s" />
        <span class="assistant-dot" style="animation-delay: .15s" />
        <span class="assistant-dot" style="animation-delay: .3s" />
      </div>
    </div>

    <!-- 错误条 -->
    <p v-if="errMsg" class="mt-2 text-xs text-red-500">{{ errMsg }}</p>

    <!-- 输入区 -->
    <div class="mt-3 flex items-end gap-2">
      <textarea
        v-model="input"
        rows="1"
        maxlength="500"
        class="input max-h-24 flex-1 resize-none"
        placeholder="输入指令…（Enter 发送，Shift+Enter 换行）"
        @keydown="onKeydown"
      />
      <button class="btn-primary h-10 shrink-0 !px-5" :disabled="loading || !input.trim()" @click="send()">
        {{ loading ? '思考中…' : '发送' }}
      </button>
    </div>
  </div>
</template>
