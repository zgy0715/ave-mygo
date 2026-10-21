<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import { usePlaylistStore } from '@/stores/playlists'

/** 我的歌单：卡片网格 + 新建/改名/删除 */
const store = usePlaylistStore()
const items = computed(() => store.items)

const modal = ref(null)          // { mode:'create'|'rename', id?, name, description }
const busy = ref(false)
const errMsg = ref('')
const confirmDelete = ref(null)

onMounted(() => store.load(true))

function openCreate() {
  errMsg.value = ''
  modal.value = { mode: 'create', name: '', description: '' }
}
function openRename(p) {
  errMsg.value = ''
  modal.value = { mode: 'rename', id: p.id, name: p.name, description: p.description || '' }
}

async function submit() {
  const name = modal.value.name.trim()
  if (!name) { errMsg.value = '歌单名不能为空'; return }
  busy.value = true
  errMsg.value = ''
  try {
    if (modal.value.mode === 'create') await store.create(name, modal.value.description.trim() || undefined)
    else await store.update(modal.value.id, { name, description: modal.value.description.trim() || undefined })
    modal.value = null
  } catch (e) {
    errMsg.value = e.message
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  busy.value = true
  try {
    await store.remove(confirmDelete.value.id)
    confirmDelete.value = null
  } catch (e) {
    errMsg.value = e.message
  } finally {
    busy.value = false
  }
}

const fmtDate = (t) => (t ? t.slice(0, 10) : '')
/** 无封面时按名字生成稳定渐变色 */
const gradientOf = (name = '') => {
  const palette = [
    'linear-gradient(135deg,#2c7da0,#1f6485)',
    'linear-gradient(135deg,#6b4fa3,#4a3573)',
    'linear-gradient(135deg,#d4789f,#b95)',
    'linear-gradient(135deg,#4a5568,#86a397)',
  ]
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) % 997
  return palette[h % palette.length]
}
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">MY — PLAYLISTS</p>
        <h1 class="serif text-2xl font-semibold">我的歌单</h1>
      </div>
      <button class="btn-primary !px-4 !py-2 !text-xs" @click="openCreate">＋ 新建歌单</button>
    </div>

    <div v-if="store.loading && !items.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="i in 4" :key="i" class="h-44 animate-pulse rounded-xl bg-surface"></div>
    </div>

    <div v-else-if="items.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="p in items" :key="p.id" class="card group relative overflow-hidden p-3 hover:shadow-md">
        <!-- 行内操作 -->
        <div class="absolute right-3 top-3 z-10 flex gap-1 opacity-0 transition group-hover:opacity-100">
          <button class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full bg-white/85 text-dim shadow-sm backdrop-blur hover:text-primary" title="改名" @click.prevent="openRename(p)">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 20h4l10-10-4-4L4 16v4zM14 6l4 4" /></svg>
          </button>
          <button class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full bg-white/85 text-dim shadow-sm backdrop-blur hover:text-red-500" title="删除" @click.prevent="confirmDelete = p">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13" /></svg>
          </button>
        </div>

        <RouterLink :to="`/playlists/${p.id}`" class="block">
          <div class="relative aspect-square w-full overflow-hidden rounded-lg" :style="{ background: gradientOf(p.name) }">
            <img v-if="p.coverUrl" :src="p.coverUrl" :alt="p.name" class="h-full w-full object-cover" loading="lazy">
            <span v-else class="absolute inset-0 flex items-center justify-center px-3 text-center text-lg font-black text-white/90">{{ p.name.slice(0, 4) }}</span>
          </div>
          <p class="mt-2.5 truncate text-sm font-semibold">{{ p.name }}</p>
          <p class="mt-0.5 truncate text-xs text-dim">{{ p.songCount }} 首 · 更新于 {{ fmtDate(p.updatedAt) }}</p>
        </RouterLink>
      </div>
    </div>

    <EmptyState
      v-else
      icon="♫"
      text="还没有歌单"
      hint="建一个歌单，把喜欢的歌收在一起"
      action-text="新建歌单"
      @action="openCreate"
    />

    <!-- 新建/改名弹窗 -->
    <Teleport to="body">
      <div v-if="modal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="modal = null">
        <div class="card w-full max-w-sm p-6">
          <h2 class="serif mb-4 text-lg font-semibold">{{ modal.mode === 'create' ? '新建歌单' : '重命名歌单' }}</h2>
          <label class="mb-1 block text-xs font-medium text-dim">歌单名 *</label>
          <input v-model="modal.name" class="input" maxlength="50" placeholder="如：练琴歌单 / 深夜循环" @keydown.enter.prevent="submit">
          <label class="mb-1 mt-3 block text-xs font-medium text-dim">简介（可选）</label>
          <input v-model="modal.description" class="input" maxlength="200" placeholder="一句话说明">
          <p v-if="errMsg" class="mt-2 text-xs text-red-500">{{ errMsg }}</p>
          <div class="mt-4 flex justify-end gap-2">
            <button class="btn-ghost" @click="modal = null">取消</button>
            <button class="btn-primary" :disabled="busy" @click="submit">{{ busy ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <div v-if="confirmDelete" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="confirmDelete = null">
        <div class="card w-full max-w-sm p-6 text-center">
          <p class="text-sm">确定删除歌单「{{ confirmDelete.name }}」？</p>
          <p class="mt-1 text-xs text-faint">歌单内的歌曲不会被删除</p>
          <p v-if="errMsg" class="mt-2 text-xs text-red-500">{{ errMsg }}</p>
          <div class="mt-5 flex justify-center gap-2">
            <button class="btn-ghost" @click="confirmDelete = null">取消</button>
            <button class="btn-primary !bg-red-500 hover:!bg-red-600" :disabled="busy" @click="doDelete">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
