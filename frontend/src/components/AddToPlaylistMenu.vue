<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePlaylistStore } from '@/stores/playlists'
import { addSongToPlaylist } from '@/api/playlist'
import LoginGate from '@/components/LoginGate.vue'

/**
 * 「加入歌单」下拉菜单
 * - 游客点击 → 登录引导
 * - 面板用 Teleport + position:fixed 渲染到 body：
 *   避免被列表容器的 overflow:hidden / 卡片圆角裁剪（之前就是这个 bug）
 * - 打开时按触发按钮的位置计算坐标，上方空间不足自动向下弹
 * - 支持就地「新建歌单」并直接把当前歌加进去
 */
const props = defineProps({
  songId: { type: [Number, String], required: true },
  variant: { type: String, default: 'icon' },   // icon=列表行紧凑按钮 / button=详情页按钮
})
const emit = defineEmits(['added'])

const auth = useAuthStore()
const store = usePlaylistStore()

const triggerEl = ref(null)
const panelEl = ref(null)
const open = ref(false)
const panelStyle = ref({})
const busy = ref(false)
const msg = ref('')
const msgStyle = ref({})
const gateOpen = ref(false)
const creating = ref(false)
const newName = ref('')
let msgTimer = null

const PANEL_W = 224
const items = computed(() => store.items)

/** 按触发按钮位置算面板坐标（右对齐、上方优先、空间不足向下） */
function placePanel() {
  const r = triggerEl.value?.getBoundingClientRect()
  if (!r) return
  const gap = 6
  const estimatedH = Math.min(280, 96 + items.value.length * 34)
  const openUp = r.top > estimatedH + gap + 8
  const left = Math.min(Math.max(8, r.right - PANEL_W), window.innerWidth - PANEL_W - 8)

  panelStyle.value = {
    position: 'fixed',
    width: `${PANEL_W}px`,
    left: `${left}px`,
    top: openUp ? `${r.top - gap}px` : `${r.bottom + gap}px`,
    transform: openUp ? 'translateY(-100%)' : 'none',
    zIndex: 90,
  }
  msgStyle.value = {
    position: 'fixed',
    left: `${left + PANEL_W / 2}px`,
    top: openUp ? `${r.top - estimatedH - 30}px` : `${r.bottom + estimatedH + 18}px`,
    transform: 'translateX(-50%)',
    zIndex: 91,
  }
}

async function toggle(e) {
  e.preventDefault()
  e.stopPropagation()
  if (!auth.isLogin) { gateOpen.value = true; return }
  if (open.value) { close(); return }
  open.value = true
  creating.value = false
  newName.value = ''
  await store.load()
  await nextTick()
  placePanel()
  window.addEventListener('scroll', onScrollClose, true)
  window.addEventListener('resize', close)
}

function close() {
  open.value = false
  window.removeEventListener('scroll', onScrollClose, true)
  window.removeEventListener('resize', close)
}

/** 页面滚动/窗口变化时收起（fixed 定位不跟随，避免错位） */
function onScrollClose(e) {
  if (panelEl.value && e.target instanceof Node && panelEl.value.contains(e.target)) return
  close()
}

async function pick(p) {
  if (busy.value) return
  busy.value = true
  try {
    const detail = await addSongToPlaylist(p.id, props.songId)
    store.patchSummary(detail)
    flash(`已加入「${p.name}」`)
    close()
    emit('added', p)
  } catch (e) {
    flash(e.message || '加入失败')
  } finally {
    busy.value = false
  }
}

async function createAndAdd() {
  const name = newName.value.trim()
  if (!name) return flash('先给歌单起个名字')
  busy.value = true
  try {
    const p = await store.create(name)
    const detail = await addSongToPlaylist(p.id, props.songId)
    store.patchSummary(detail)
    flash(`已新建「${name}」并加入`)
    creating.value = false
    newName.value = ''
    close()
  } catch (e) {
    flash(e.message || '新建失败')
  } finally {
    busy.value = false
  }
}

function flash(m) {
  msg.value = m
  clearTimeout(msgTimer)
  msgTimer = setTimeout(() => (msg.value = ''), 2400)
}

/** 点面板/按钮以外区域收起（Teleport 后不在同一 DOM 子树，需单独判断） */
function onDocDown(e) {
  if (!open.value) return
  const t = e.target
  if (panelEl.value?.contains(t) || triggerEl.value?.contains(t)) return
  close()
}
onMounted(() => document.addEventListener('pointerdown', onDocDown))

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocDown)
  close()
  clearTimeout(msgTimer)
})
</script>

<template>
  <span ref="triggerEl" class="inline-flex">
    <button
      v-if="variant === 'icon'"
      class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-faint opacity-0 transition group-hover:opacity-100 hover:bg-base hover:text-primary"
      :class="open && 'opacity-100 !text-primary'"
      aria-label="加入歌单"
      aria-haspopup="menu"
      :aria-expanded="open"
      title="加入歌单"
      @click="toggle"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 7h9M4 12h9M4 17h6M17 17V8M17 8l3-1v9" />
      </svg>
    </button>
    <button v-else class="btn-ghost !px-2 !py-1.5 !text-xs" title="加入歌单" aria-haspopup="menu" :aria-expanded="open" @click="toggle">
      ♫ 加入歌单
    </button>
  </span>

  <!-- 面板：Teleport 到 body，不受任何父级 overflow/圆角裁剪 -->
  <Teleport to="body">
    <div
      v-if="open"
      ref="panelEl"
      class="overflow-hidden rounded-lg border border-line bg-surface shadow-xl"
      :style="panelStyle"
      role="menu"
    >
      <p class="border-b border-line px-3 py-2 text-[11px] text-faint">加入歌单</p>

      <ul class="max-h-52 overflow-y-auto">
        <li v-for="p in items" :key="p.id">
          <button
            class="flex w-full cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left text-xs transition hover:bg-base"
            :disabled="busy"
            @click="pick(p)"
          >
            <span class="truncate">{{ p.name }}</span>
            <span class="tabular shrink-0 text-[10px] text-faint">{{ p.songCount }}</span>
          </button>
        </li>
        <li v-if="!items.length && !store.loading" class="px-3 py-3 text-center text-[11px] text-faint">还没有歌单</li>
      </ul>

      <div class="border-t border-line p-2">
        <template v-if="!creating">
          <button class="w-full cursor-pointer rounded px-2 py-1.5 text-left text-xs text-primary transition hover:bg-base" @click="creating = true">
            ＋ 新建歌单
          </button>
        </template>
        <template v-else>
          <input v-model="newName" class="input !py-1 !text-xs" maxlength="50" placeholder="歌单名" @keydown.enter.prevent="createAndAdd">
          <div class="mt-1.5 flex gap-1.5">
            <button class="btn-primary flex-1 !px-2 !py-1 !text-[11px]" :disabled="busy" @click="createAndAdd">新建并加入</button>
            <button class="btn-ghost !px-2 !py-1 !text-[11px]" @click="creating = false">取消</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 结果提示 -->
    <p v-if="msg" :style="msgStyle" class="whitespace-nowrap rounded bg-ink/85 px-2 py-1 text-[10px] text-white">{{ msg }}</p>

    <LoginGate v-model:open="gateOpen" action="歌单" />
  </Teleport>
</template>
