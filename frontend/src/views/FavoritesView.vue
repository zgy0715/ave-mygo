<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import FavoriteButton from '@/components/FavoriteButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listFavorites } from '@/api/favorite'
import { slugByName } from '@/utils/band'

/**
 * 我的收藏：歌曲 / 演出 两个 Tab。
 * 数据直接用后端 FavoriteItem（title/coverUrl/extra），不再逐条查歌曲或演出。
 */
const TABS = [
  { v: 'SONG', t: '歌曲' },
  { v: 'EVENT', t: '演出' },
]
const tab = ref('SONG')
const items = ref([])
const loading = ref(true)
const errMsg = ref('')

let seq = 0
async function load() {
  const my = ++seq
  loading.value = true
  errMsg.value = ''
  try {
    const data = await listFavorites(tab.value)
    if (my !== seq) return
    items.value = data
  } catch (e) {
    if (my === seq) errMsg.value = e.message
  } finally {
    if (my === seq) loading.value = false
  }
}
onMounted(load)
watch(tab, load)

/** 取消收藏后直接把卡片移除（列表本身就是收藏结果） */
function onChanged(targetId, nowFav) {
  if (!nowFav) items.value = items.value.filter((i) => i.targetId !== targetId)
}

const count = computed(() => items.value.length)
const isSong = computed(() => tab.value === 'SONG')
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">MY — FAVORITES</p>
        <h1 class="serif text-2xl font-semibold">我的收藏</h1>
      </div>
      <p class="tabular text-sm text-faint">{{ count }} 项</p>
    </div>

    <div class="mb-5 flex gap-2">
      <button
        v-for="t in TABS"
        :key="t.v"
        class="cursor-pointer rounded-full border px-4 py-1.5 text-sm transition"
        :class="tab === t.v ? 'border-primary bg-primary !text-[11px] text-white' : 'border-line bg-transparent text-dim hover:border-line-strong'"
        @click="tab = t.v"
      >
        {{ t.t }}
      </button>
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="i in 4" :key="i" class="h-48 animate-pulse rounded-xl bg-surface"></div>
    </div>

    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">
      {{ errMsg }} <button class="ml-2 underline" @click="load">重试</button>
    </div>

    <div v-else-if="items.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="it in items" :key="`${it.targetType}-${it.targetId}`" class="card group relative overflow-hidden p-3 hover:shadow-md">
        <div class="absolute right-4 top-4 z-10">
          <FavoriteButton :type="it.targetType" :id="it.targetId" overlay :size="15" @changed="(v) => onChanged(it.targetId, v)" />
        </div>

        <RouterLink :to="isSong ? `/songs/${it.targetId}` : `/events/${it.targetId}`" class="block">
          <template v-if="isSong">
            <CoverArt :title="it.title" :band="slugByName(it.extra)" :cover-url="it.coverUrl || ''" class="aspect-square w-full" rounded="rounded-lg" />
            <p class="mt-2.5 truncate text-sm font-semibold" :title="it.title">{{ it.title }}</p>
            <p class="mt-0.5 truncate text-xs text-dim">{{ it.extra || '未收录乐队' }}</p>
          </template>
          <template v-else>
            <div class="relative aspect-square w-full overflow-hidden rounded-lg" :style="{ background: 'linear-gradient(135deg,#6b4fa3,#4a3573)' }">
              <img v-if="it.coverUrl" :src="it.coverUrl" :alt="it.title" class="h-full w-full object-cover" loading="lazy">
              <span v-else class="absolute inset-0 flex items-center justify-center p-3 text-center text-sm font-semibold text-white">{{ it.title }}</span>
            </div>
            <p class="mt-2.5 line-clamp-2 text-sm font-semibold">{{ it.title }}</p>
            <p class="mt-0.5 truncate text-xs text-dim">{{ it.extra || '场馆待定' }}</p>
          </template>
        </RouterLink>
      </div>
    </div>

    <EmptyState
      v-else
      icon="♡"
      :text="isSong ? '还没有收藏的歌曲' : '还没有收藏的演出'"
      :hint="isSong ? '在歌曲卡片或详情页点心形即可收藏' : '在演出卡片或详情页点心形即可收藏'"
      action-text="去听几首 →"
      @action="$router.push(isSong ? '/songs' : '/events')"
    />
  </div>
</template>
