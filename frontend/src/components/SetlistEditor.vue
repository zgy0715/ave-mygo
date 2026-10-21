<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getSessionSetlist, saveSessionSetlist } from '@/api/admin'
import { listSongs } from '@/api/song'

/**
 * 场次歌单（Setlist）录入
 * - 左：歌曲搜索（GET /songs?keyword=）→ 点击加入
 * - 右：已选曲目，拖拽排序 / ↑↓ 微调 / 勾选安可 / 移除
 * - 保存：PUT 全量替换（orderNo 按当前顺序 1..n 重排）
 * - 有未保存改动时关闭需二次确认
 */
const props = defineProps({
  sessionId: { type: [Number, String], required: true },
  sessionName: { type: String, default: '' },
})
const emit = defineEmits(['close', 'saved'])

const selected = ref([])          // [{songId, title, duration, encore}]
const dirty = ref(false)
const loading = ref(true)
const saving = ref(false)
const errMsg = ref('')
const okMsg = ref('')

// 左侧搜索
const keyword = ref('')
const results = ref([])
const searching = ref(false)
let searchTimer = null
let seq = 0

const dragIndex = ref(-1)

async function loadSetlist() {
  loading.value = true
  errMsg.value = ''
  try {
    const list = await getSessionSetlist(props.sessionId)
    selected.value = (list || []).map((i) => ({
      songId: i.songId,
      title: i.songTitle,
      duration: i.duration,
      encore: !!i.encore,
    }))
    dirty.value = false
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(loadSetlist)

async function runSearch() {
  const q = keyword.value.trim()
  if (!q) { results.value = []; return }
  const my = ++seq
  searching.value = true
  try {
    const data = await listSongs({ keyword: q, page: 1, pageSize: 8 })
    if (my === seq) results.value = data.content || []
  } catch {
    if (my === seq) results.value = []
  } finally {
    if (my === seq) searching.value = false
  }
}
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(runSearch, 300)
})

const has = (id) => selected.value.some((s) => s.songId === id)

function add(song) {
  if (has(song.id)) return
  selected.value.push({ songId: song.id, title: song.title, duration: song.duration, encore: false })
  dirty.value = true
  okMsg.value = ''
}
function remove(i) {
  selected.value.splice(i, 1)
  dirty.value = true
}
function move(i, dir) {
  const j = i + dir
  if (j < 0 || j >= selected.value.length) return
  const arr = selected.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  dirty.value = true
}

/* ---- 原生拖拽排序 ---- */
function onDragStart(i, e) {
  dragIndex.value = i
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(i))
}
function onDragOver(i, e) {
  e.preventDefault()
  const from = dragIndex.value
  if (from === -1 || from === i) return
  const arr = selected.value
  const [item] = arr.splice(from, 1)
  arr.splice(i, 0, item)
  dragIndex.value = i
  dirty.value = true
}
function onDrop() { dragIndex.value = -1 }

async function save() {
  if (saving.value) return
  saving.value = true
  errMsg.value = ''
  okMsg.value = ''
  try {
    const items = selected.value.map((s, i) => ({ songId: s.songId, orderNo: i + 1, encore: !!s.encore }))
    const saved = await saveSessionSetlist(props.sessionId, items)
    selected.value = (saved || []).map((i) => ({ songId: i.songId, title: i.songTitle, duration: i.duration, encore: !!i.encore }))
    dirty.value = false
    okMsg.value = '已保存，前台歌单同步更新'
    emit('saved')
  } catch (e) {
    errMsg.value = e.message
  } finally {
    saving.value = false
  }
}

function close() {
  if (dirty.value && !confirm('有未保存的修改，确定关闭？')) return
  emit('close')
}

const fmt = (s) => (s ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` : '—')
const encoreCount = computed(() => selected.value.filter((s) => s.encore).length)
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4" @click.self="close">
      <div class="card flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden">
        <!-- 顶部 -->
        <div class="flex items-center justify-between border-b border-line px-5 py-3">
          <div class="min-w-0">
            <h2 class="serif truncate text-lg font-semibold">歌单录入 · {{ sessionName || `场次 #${sessionId}` }}</h2>
            <p class="mt-0.5 text-[11px] text-faint">共 {{ selected.length }} 首 · 安可 {{ encoreCount }} 首 · 拖拽可调整顺序</p>
          </div>
          <button class="cursor-pointer text-dim transition hover:text-ink" aria-label="关闭" @click="close">✕</button>
        </div>

        <div class="grid flex-1 grid-cols-1 gap-0 overflow-hidden sm:grid-cols-2">
          <!-- 左：搜索 -->
          <div class="flex min-h-0 flex-col border-b border-line sm:border-b-0 sm:border-r">
            <div class="p-3">
              <input v-model="keyword" class="input !py-1.5 !text-xs" placeholder="搜索歌曲名加入歌单…">
            </div>
            <ul class="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
              <li v-if="searching" class="px-2 py-3 text-center text-[11px] text-faint">搜索中…</li>
              <li v-else-if="!keyword.trim()" class="px-2 py-6 text-center text-[11px] text-faint">输入歌名搜索</li>
              <li v-else-if="!results.length" class="px-2 py-6 text-center text-[11px] text-faint">没有匹配的歌曲</li>
              <li v-for="s in results" :key="s.id">
                <button
                  class="flex w-full cursor-pointer items-center gap-2 rounded px-2 py-2 text-left text-xs transition hover:bg-base disabled:opacity-40"
                  :disabled="has(s.id)"
                  @click="add(s)"
                >
                  <span class="min-w-0 flex-1">
                    <span class="block truncate">{{ s.title }}</span>
                    <span class="block truncate text-[10px] text-faint">{{ s.bandName }} · {{ s.albumName || '未收录单曲' }}</span>
                  </span>
                  <span class="tabular shrink-0 text-[10px] text-faint">{{ fmt(s.duration) }}</span>
                  <span class="shrink-0 text-primary">{{ has(s.id) ? '已加' : '＋' }}</span>
                </button>
              </li>
            </ul>
          </div>

          <!-- 右：已选 -->
          <div class="flex min-h-0 flex-col">
            <div class="flex items-center justify-between px-3 py-2">
              <p class="micro !text-[9px]">已选曲目</p>
              <button v-if="selected.length" class="cursor-pointer text-[11px] text-faint hover:text-red-500" @click="selected = []; dirty = true">清空</button>
            </div>
            <ul class="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
              <li v-if="loading" class="px-2 py-6 text-center text-[11px] text-faint">读取中…</li>
              <li v-else-if="!selected.length" class="px-2 py-6 text-center text-[11px] text-faint">还没有曲目，从左侧搜索添加</li>
              <li
                v-for="(s, i) in selected"
                :key="s.songId"
                class="flex items-center gap-2 rounded px-2 py-1.5 text-xs transition hover:bg-base"
                :class="s.encore && 'bg-primary/5'"
                draggable="true"
                @dragstart="onDragStart(i, $event)"
                @dragover="onDragOver(i, $event)"
                @drop="onDrop"
                @dragend="onDrop"
              >
                <span class="tabular w-5 shrink-0 text-center text-[10px] text-faint">{{ i + 1 }}</span>
                <span class="min-w-0 flex-1 truncate">{{ s.title }}</span>
                <label class="flex shrink-0 cursor-pointer items-center gap-1 text-[10px] text-faint">
                  <input v-model="s.encore" type="checkbox" class="h-3 w-3 accent-[var(--color-primary)]" @change="dirty = true">
                  安可
                </label>
                <button class="cursor-pointer text-faint hover:text-ink" title="上移" @click="move(i, -1)">↑</button>
                <button class="cursor-pointer text-faint hover:text-ink" title="下移" @click="move(i, 1)">↓</button>
                <button class="cursor-pointer text-faint hover:text-red-500" title="移除" @click="remove(i)">✕</button>
              </li>
            </ul>
          </div>
        </div>

        <!-- 底部 -->
        <div class="flex items-center gap-3 border-t border-line px-5 py-3">
          <p v-if="errMsg" class="text-xs text-red-500">{{ errMsg }}</p>
          <p v-else-if="okMsg" class="text-xs text-green-600">{{ okMsg }}</p>
          <p v-else-if="dirty" class="text-xs text-amber-600">有未保存的修改</p>
          <div class="ml-auto flex gap-2">
            <button class="btn-ghost" @click="close">取消</button>
            <button class="btn-primary" :disabled="saving || !dirty" @click="save">{{ saving ? '保存中…' : '保存歌单' }}</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
