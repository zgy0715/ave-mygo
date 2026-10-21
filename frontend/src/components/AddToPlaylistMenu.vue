<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePlaylistStore } from '@/stores/playlists'
import { addSongToPlaylist } from '@/api/playlist'
import LoginGate from '@/components/LoginGate.vue'

/**
 * 「加入歌单」下拉菜单：
 * - 游客点击 → 登录引导
 * - 列表来自 playlistStore（首次点击时懒加载）
 * - 支持就地「新建歌单」并直接把当前歌加进去
 */
const props = defineProps({
  songId: { type: [Number, String], required: true },
  /** 'icon' 紧凑图标（列表行）/ 'button' 常规按钮（详情页） */
  variant: { type: String, default: 'icon' },
})
const emit = defineEmits(['added'])

const auth = useAuthStore()
const store = usePlaylistStore()
const open = ref(false)
const busy = ref(false)
const msg = ref('')
const gateOpen = ref(false)
const creating = ref(false)
const newName = ref('')
const root = ref(null)
let msgTimer = null

const items = computed(() => store.items)

async function toggle(e) {
  e.preventDefault()
  e.stopPropagation()
  if (!auth.isLogin) { gateOpen.value = true; return }
  open.value = !open.value
  if (open.value) {
    creating.value = false
    newName.value = ''
    store.load()
  }
}

async function pick(p) {
  if (busy.value) return
  busy.value = true
  try {
    const detail = await addSongToPlaylist(p.id, props.songId)
    store.patchSummary(detail)
    flash(`已加入「${p.name}」`)
    open.value = false
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
    open.value = false
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

// 点外部关闭
function onDocDown(e) {
  if (open.value && root.value && !root.value.contains(e.target)) open.value = false
}
onMounted(() => document.addEventListener('pointerdown', onDocDown))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocDown)
  clearTimeout(msgTimer)
})
</script>

<template>
  <span ref="root" class="relative inline-flex">
    <button
      v-if="variant === 'icon'"
      class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-faint opacity-0 transition group-hover:opacity-100 hover:bg-base hover:text-primary"
      aria-label="加入歌单"
      title="加入歌单"
      @click="toggle"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 7h9M4 12h9M4 17h6M17 17V8M17 8l3-1v9" />
      </svg>
    </button>
    <button v-else class="btn-ghost !px-2 !py-1.5 !text-xs" title="加入歌单" @click="toggle">
      ♫ 加入歌单
    </button>

    <!-- 下拉 -->
    <div
      v-if="open"
      class="absolute bottom-full right-0 z-50 mb-1 w-52 overflow-hidden rounded-lg border border-line bg-surface shadow-lg"
    >
      <p class="border-b border-line px-3 py-2 text-[11px] text-faint">加入歌单</p>

      <ul class="max-h-48 overflow-y-auto">
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

      <!-- 就地新建 -->
      <div class="border-t border-line p-2">
        <template v-if="!creating">
          <button class="w-full cursor-pointer rounded px-2 py-1.5 text-left text-xs text-primary transition hover:bg-base" @click="creating = true">
            ＋ 新建歌单
          </button>
        </template>
        <template v-else>
          <input
            v-model="newName"
            class="input !py-1 !text-xs"
            maxlength="50"
            placeholder="歌单名"
            @keydown.enter.prevent="createAndAdd"
          >
          <div class="mt-1.5 flex gap-1.5">
            <button class="btn-primary flex-1 !px-2 !py-1 !text-[11px]" :disabled="busy" @click="createAndAdd">新建并加入</button>
            <button class="btn-ghost !px-2 !py-1 !text-[11px]" @click="creating = false">取消</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 结果提示 -->
    <span
      v-if="msg"
      class="absolute bottom-full right-0 mb-1 whitespace-nowrap rounded bg-ink/85 px-2 py-1 text-[10px] text-white"
    >{{ msg }}</span>

    <LoginGate v-model:open="gateOpen" action="歌单" />
  </span>
</template>
