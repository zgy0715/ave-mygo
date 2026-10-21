<script setup>
import { computed, onMounted, ref } from 'vue'
import { createComment, deleteComment, listComments } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import LoginGate from '@/components/LoginGate.vue'

/**
 * 歌曲评论区：
 * - 公开可读（游客也能看），发布/删除需登录
 * - 发布 1-500 字，Enter 发送、Shift+Enter 换行
 * - 自己的评论与 ADMIN 显示删除（后端 own 字段已算好）
 */
const props = defineProps({ songId: { type: [Number, String], required: true } })

const auth = useAuthStore()
const items = ref([])
const total = ref(0)
const page = ref(1)
const totalPages = ref(0)
const loading = ref(true)
const errMsg = ref('')
const content = ref('')
const posting = ref(false)
const gateOpen = ref(false)
const deletingId = ref(0)
const PAGE_SIZE = 10

async function load() {
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listComments(props.songId, page.value, PAGE_SIZE)
    items.value = data.content
    total.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const canSubmit = computed(() => content.value.trim().length > 0 && content.value.trim().length <= 500)

async function submit() {
  if (!auth.isLogin) { gateOpen.value = true; return }
  const text = content.value.trim()
  if (!text || posting.value) return
  if (text.length > 500) { errMsg.value = '评论最多 500 字'; return }
  posting.value = true
  errMsg.value = ''
  try {
    const created = await createComment(props.songId, text)
    items.value = [created, ...items.value]
    total.value += 1
    content.value = ''
  } catch (e) {
    errMsg.value = e.message
  } finally {
    posting.value = false
  }
}

async function remove(c) {
  deletingId.value = c.id
  try {
    await deleteComment(c.id)
    items.value = items.value.filter((x) => x.id !== c.id)
    total.value = Math.max(0, total.value - 1)
  } catch (e) {
    errMsg.value = e.message
  } finally {
    deletingId.value = 0
  }
}

function changePage(p) {
  if (p < 1 || (totalPages.value && p > totalPages.value)) return
  page.value = p
  load()
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 日期 */
function relTime(t) {
  if (!t) return ''
  const then = new Date(t.replace(' ', 'T')).getTime()
  const diff = Date.now() - then
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return t.slice(0, 10)
}
const initial = (c) => (c.nickname || '迷').slice(0, 1).toUpperCase()
</script>

<template>
  <section class="mt-10">
    <div class="mb-3 flex items-baseline gap-3">
      <h2 class="serif text-lg font-semibold">评论</h2>
      <span class="text-xs text-faint">{{ total }} 条</span>
    </div>

    <!-- 输入 -->
    <div class="card p-3">
      <textarea
        v-model="content"
        rows="2"
        maxlength="500"
        class="w-full resize-none bg-transparent text-sm outline-none placeholder:text-faint"
        placeholder="说点什么…（Enter 发送，Shift+Enter 换行）"
        @keydown.enter.exact.prevent="submit"
      />
      <div class="mt-1 flex items-center justify-between">
        <span class="text-[11px] text-faint">{{ content.length }}/500</span>
        <button class="btn-primary !px-4 !py-1.5 !text-xs" :disabled="!canSubmit || posting" @click="submit">
          {{ posting ? '发送中…' : (auth.isLogin ? '发送' : '登录后评论') }}
        </button>
      </div>
    </div>

    <p v-if="errMsg" class="mt-2 text-xs text-red-500">{{ errMsg }}</p>

    <!-- 列表 -->
    <div v-if="loading" class="mt-4 space-y-2">
      <div v-for="i in 3" :key="i" class="h-16 animate-pulse rounded-lg bg-surface" />
    </div>

    <ul v-else-if="items.length" class="mt-4 space-y-3">
      <li v-for="c in items" :key="c.id" class="group flex gap-3">
        <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">{{ initial(c) }}</span>
        <div class="min-w-0 flex-1">
          <p class="flex items-baseline gap-2">
            <span class="text-sm font-medium">{{ c.nickname || `用户${c.userId}` }}</span>
            <span class="text-[11px] text-faint">{{ relTime(c.createdAt) }}</span>
            <button
              v-if="c.own"
              class="ml-auto cursor-pointer text-[11px] text-faint opacity-0 transition group-hover:opacity-100 hover:text-red-500 disabled:opacity-40"
              :disabled="deletingId === c.id"
              @click="remove(c)"
            >{{ deletingId === c.id ? '删除中…' : '删除' }}</button>
          </p>
          <p class="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-dim">{{ c.content }}</p>
        </div>
      </li>
    </ul>

    <p v-else class="mt-6 text-center text-sm text-dim">还没有评论，来说第一句 ✳</p>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="mt-5 flex items-center justify-center gap-3">
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page <= 1" @click="changePage(page - 1)">← 上一页</button>
      <span class="tabular text-xs text-faint">{{ page }} / {{ totalPages }}</span>
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页 →</button>
    </div>

    <LoginGate v-model:open="gateOpen" action="评论" />
  </section>
</template>
