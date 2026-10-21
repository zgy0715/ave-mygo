<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchAll } from '@/api/search'
import { slugByBandId } from '@/utils/band'

/**
 * 顶栏全局搜索：
 * - 桌面：图标 → 展开输入框；移动端：点图标后整行展开
 * - 300ms 防抖；请求带序号，丢弃过期响应（打字快时不乱序）
 * - 下拉分「歌曲 / 演出」两组，↑↓ 选择、Enter 跳转、Esc 关闭
 * - 底部「查看全部结果 →」→ /songs?keyword=xxx
 */
const router = useRouter()
const open = ref(false)
const keyword = ref('')
const songs = ref([])
const events = ref([])
const loading = ref(false)
const active = ref(-1)
const boxEl = ref(null)
const inputEl = ref(null)

let timer = null
let seq = 0

const hasResult = computed(() => songs.value.length > 0 || events.value.length > 0)
const showPanel = computed(() => open.value && keyword.value.trim().length > 0)

/** 扁平化用于键盘导航：先歌曲后演出 */
const flat = computed(() => [
  ...songs.value.map((s) => ({ type: 'song', id: s.id, to: `/songs/${s.id}` })),
  ...events.value.map((e) => ({ type: 'event', id: e.id, to: `/events/${e.id}` })),
])

async function runSearch() {
  const q = keyword.value.trim()
  if (!q) { songs.value = []; events.value = []; return }
  const my = ++seq
  loading.value = true
  try {
    const data = await searchAll(q, 5)
    if (my !== seq) return
    songs.value = data.songs || []
    events.value = data.events || []
    active.value = flat.value.length ? 0 : -1
  } catch {
    if (my === seq) { songs.value = []; events.value = [] }
  } finally {
    if (my === seq) loading.value = false
  }
}

function onInput() {
  clearTimeout(timer)
  active.value = -1
  timer = setTimeout(runSearch, 300)
}

function toggle() {
  open.value = !open.value
  if (open.value) nextTick(() => inputEl.value?.focus())
  else reset()
}

function reset() {
  keyword.value = ''
  songs.value = []
  events.value = []
  active.value = -1
  clearTimeout(timer)
}

function go(to) {
  open.value = false
  reset()
  router.push(to)
}

function viewAll() {
  const q = keyword.value.trim()
  go(`/songs?keyword=${encodeURIComponent(q)}`)
}

function onKeydown(e) {
  if (e.key === 'Escape') { open.value = false; reset(); inputEl.value?.blur(); return }
  if (!showPanel.value) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (flat.value.length) active.value = (active.value + 1) % flat.value.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (flat.value.length) active.value = (active.value - 1 + flat.value.length) % flat.value.length
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = flat.value[active.value]
    if (item) go(item.to)
    else if (keyword.value.trim()) viewAll()
  }
}

// 点外部关闭
function onDocDown(e) {
  if (open.value && boxEl.value && !boxEl.value.contains(e.target)) { open.value = false; reset() }
}
onMounted(() => document.addEventListener('pointerdown', onDocDown))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocDown)
  clearTimeout(timer)
})

const songActive = (i) => active.value === i
const eventActive = (i) => active.value === songs.value.length + i
</script>

<template>
  <div ref="boxEl" class="relative">
    <!-- 触发：图标 + 可选输入框 -->
    <div class="flex items-center" :class="open ? 'w-[min(22rem,60vw)]' : ''">
      <button
        class="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-dim transition hover:bg-base hover:text-ink"
        :aria-label="open ? '关闭搜索' : '搜索'"
        :aria-expanded="open"
        @click="toggle"
      >
        <svg v-if="!open" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
          <circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" />
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
      </button>

      <input
        v-show="open"
        ref="inputEl"
        v-model="keyword"
        type="search"
        class="ml-1 h-9 w-full rounded-full border border-line bg-surface px-3 text-sm outline-none transition focus:border-primary"
        placeholder="搜索歌曲 / 演出…"
        @input="onInput"
        @keydown="onKeydown"
      >
    </div>

    <!-- 结果面板 -->
    <div
      v-if="showPanel"
      class="absolute right-0 top-full z-40 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-line bg-surface shadow-xl"
    >
      <p v-if="loading" class="px-4 py-5 text-center text-xs text-faint">搜索中…</p>

      <template v-else-if="hasResult">
        <ul class="max-h-80 overflow-y-auto">
          <!-- 歌曲组 -->
          <li v-if="songs.length">
            <p class="micro bg-base/50 px-4 py-1.5 !text-[9px]">歌曲</p>
            <button
              v-for="(s, i) in songs"
              :key="`s-${s.id}`"
              class="flex w-full cursor-pointer items-center gap-3 px-4 py-2 text-left transition"
              :class="songActive(i) ? 'bg-base' : 'hover:bg-base/60'"
              @mouseenter="active = i"
              @click="go(`/songs/${s.id}`)"
            >
              <span class="h-8 w-8 shrink-0 overflow-hidden rounded" :style="{ background: 'linear-gradient(135deg,#2c7da0,#6b4fa3)' }">
                <img v-if="s.coverUrl" :src="s.coverUrl" class="h-full w-full object-cover" :alt="s.title" loading="lazy">
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm">{{ s.title }}</span>
                <span class="block truncate text-[11px] text-faint">{{ s.bandName }} · {{ s.albumName || '未收录单曲' }}</span>
              </span>
              <span class="tabular shrink-0 text-[11px] text-faint">{{ s.duration ? `${Math.floor(s.duration / 60)}:${String(s.duration % 60).padStart(2, '0')}` : '—' }}</span>
            </button>
          </li>

          <!-- 演出组 -->
          <li v-if="events.length">
            <p class="micro bg-base/50 px-4 py-1.5 !text-[9px]">演出</p>
            <button
              v-for="(e, i) in events"
              :key="`e-${e.id}`"
              class="flex w-full cursor-pointer items-center gap-3 px-4 py-2 text-left transition"
              :class="eventActive(i) ? 'bg-base' : 'hover:bg-base/60'"
              @mouseenter="active = songs.length + i"
              @click="go(`/events/${e.id}`)"
            >
              <span class="h-8 w-8 shrink-0 overflow-hidden rounded" :style="{ background: 'linear-gradient(135deg,#6b4fa3,#4a3573)' }">
                <img v-if="e.posterUrl" :src="e.posterUrl" class="h-full w-full object-cover" :alt="e.title" loading="lazy">
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm">{{ e.title }}</span>
                <span class="block truncate text-[11px] text-faint">{{ e.venue }} · {{ (e.nextStartTime || '').slice(5, 16).replace('T', ' ') }}</span>
              </span>
            </button>
          </li>
        </ul>

        <button class="flex w-full cursor-pointer items-center justify-between border-t border-line px-4 py-2.5 text-xs text-primary transition hover:bg-base" @click="viewAll">
          <span>查看全部结果</span><span>→</span>
        </button>
      </template>

      <p v-else class="px-4 py-6 text-center text-xs text-faint">
        没找到「{{ keyword }}」，试试乐队名或日文原名
      </p>
    </div>
  </div>
</template>
