<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useFavoriteStore } from '@/stores/favorites'
import LoginGate from '@/components/LoginGate.vue'

/**
 * 收藏心形按钮：已收藏实心 / 未收藏描边；游客点击弹登录引导。
 * 用法：<FavoriteButton type="SONG" :id="song.id" /> 或 type="EVENT"
 */
const props = defineProps({
  type: { type: String, required: true },       // SONG | EVENT
  id: { type: [Number, String], required: true },
  size: { type: Number, default: 18 },
  /** 悬浮态样式（卡片上使用时传 true：默认半透明，hover 才明显） */
  overlay: { type: Boolean, default: false },
})
const emit = defineEmits(['changed'])

const auth = useAuthStore()
const fav = useFavoriteStore()
const gateOpen = ref(false)
const errored = ref('')

const active = computed(() => fav.isFav(props.type, props.id))
const busy = computed(() => fav.pending.has(`${props.type}:${props.id}`))

async function onClick(e) {
  e.preventDefault()
  e.stopPropagation()
  errored.value = ''
  if (!auth.isLogin) { gateOpen.value = true; return }
  try {
    const nowFav = await fav.toggle(props.type, props.id)
    emit('changed', nowFav)
  } catch (err) {
    errored.value = err.message
    setTimeout(() => (errored.value = ''), 2200)
  }
}
</script>

<template>
  <span class="relative inline-flex">
    <button
      class="group/fav flex cursor-pointer items-center justify-center rounded-full transition"
      :class="overlay
        ? 'h-8 w-8 bg-white/80 text-dim shadow-sm backdrop-blur hover:scale-105 hover:text-red-500'
        : 'h-9 w-9 border border-line bg-surface text-dim hover:border-red-300 hover:text-red-500'"
      :aria-label="active ? '取消收藏' : '收藏'"
      :aria-pressed="active"
      :title="errored || (active ? '取消收藏' : '收藏')"
      :disabled="busy"
      @click="onClick"
    >
      <svg :width="size" :height="size" viewBox="0 0 24 24"
           :fill="active ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.8"
           stroke-linecap="round" stroke-linejoin="round"
           :class="active ? 'text-red-500' : ''">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1L12 21l7.7-7.6 1.1-1a5.5 5.5 0 0 0 0-7.8z" />
      </svg>
    </button>

    <span v-if="errored" class="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap rounded bg-ink/85 px-2 py-0.5 text-[10px] text-white">{{ errored }}</span>

    <!-- Teleport 到 body，放在 button 外层避免非法嵌套 -->
    <LoginGate v-model:open="gateOpen" action="收藏" />
  </span>
</template>
