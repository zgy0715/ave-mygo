<script setup>
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import CoverArt from '@/components/CoverArt.vue'
import { slugByBandId } from '@/utils/band'

/**
 * 全局底部播放条（v2）：
 * - 原生 input[range] 换成自定义轨道：可点击、可拖拽、hover 出滑块（原生控件跨浏览器样式不可控）
 * - 音量：hover 展开滑块 + 静音；记忆到 localStorage
 * - 播放模式：顺序 / 单曲循环 / 随机
 * - 关闭按钮：清空队列并收起播放条
 */
const player = usePlayerStore()
const showQueue = ref(false)

const currentSlug = computed(() => slugByBandId(player.current?.bandId))

const fmt = (sec) => {
  const s = Math.max(0, Math.floor(sec || 0))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}
const curText = computed(() => fmt(player.position))
const durText = computed(() => (player.current?.duration ? fmt(player.current.duration) : '—'))

/* ===== 进度条：自定义拖拽（桌面/移动端各一条轨道，统一用 currentTarget 计算） ===== */
const dragging = ref(false)
const dragPct = ref(0)
const shownPct = computed(() => (dragging.value ? dragPct.value : player.progressPct))
const hasDuration = computed(() => !!player.current?.duration)

function pctFromEvent(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  if (!rect.width) return 0
  return Math.max(0, Math.min(100, ((e.clientX - rect.left) / rect.width) * 100))
}
function onSeekDown(e) {
  if (!hasDuration.value) return
  dragging.value = true
  dragPct.value = pctFromEvent(e)
  e.currentTarget.setPointerCapture?.(e.pointerId)
}
function onSeekMove(e) {
  if (dragging.value) dragPct.value = pctFromEvent(e)
}
function onSeekUp(e) {
  if (!dragging.value) return
  player.seek(dragPct.value)
  dragging.value = false
  e.currentTarget.releasePointerCapture?.(e.pointerId)
}

/* ===== 音量：同样自定义，hover 展开 ===== */
const volDragging = ref(false)

function volPctFromEvent(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  if (!rect.width) return 0
  return Math.max(0, Math.min(100, ((e.clientX - rect.left) / rect.width) * 100))
}
function onVolDown(e) {
  volDragging.value = true
  player.setVolume(volPctFromEvent(e) / 100)
  e.currentTarget.setPointerCapture?.(e.pointerId)
}
function onVolMove(e) {
  if (volDragging.value) player.setVolume(volPctFromEvent(e) / 100)
}
function onVolUp(e) {
  volDragging.value = false
  e.currentTarget.releasePointerCapture?.(e.pointerId)
}

const modeLabel = computed(() => ({ order: '顺序播放', repeat: '单曲循环', shuffle: '随机播放' }[player.mode]))
</script>

<template>
  <transition enter-active-class="transition duration-200" enter-from-class="translate-y-full">
    <div v-if="player.current && !player.barCollapsed" class="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 backdrop-blur">
      <!-- 队列操作提示 -->
      <transition enter-active-class="transition duration-200" enter-from-class="opacity-0 translate-y-1" leave-active-class="transition duration-200" leave-to-class="opacity-0">
        <p
          v-if="player.notice"
          class="pointer-events-none absolute -top-10 left-1/2 -translate-x-1/2 rounded-full bg-ink/85 px-3.5 py-1.5 text-xs text-white shadow-lg"
        >
          {{ player.notice }}
        </p>
      </transition>
      <!-- 队列抽屉 -->
      <div v-if="showQueue" class="border-b border-line bg-surface">
        <div class="wrap flex items-center justify-between py-2">
          <p class="text-sm font-semibold">播放队列 <span class="text-faint">（{{ player.queue.length }}）</span></p>
          <div class="flex items-center gap-3">
            <button class="cursor-pointer text-xs text-faint transition hover:text-red-500" title="清空队列并停止播放" @click="player.stop()">清空</button>
            <button class="cursor-pointer text-xs text-faint transition hover:text-dim" @click="showQueue = false">收起</button>
          </div>
        </div>
        <ul class="wrap max-h-56 overflow-y-auto pb-3">
          <li
            v-for="(s, i) in player.queue"
            :key="s.id"
            class="group flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 hover:bg-base"
            :class="i === player.currentIndex && 'bg-base/70 text-primary'"
            @click="player.playQueue(player.queue, i)"
          >
            <span class="mono w-6 text-center text-xs text-faint">
              <template v-if="i === player.currentIndex && player.isPlaying">▶</template>
              <template v-else>{{ i + 1 }}</template>
            </span>
            <span class="flex-1 truncate text-sm">{{ s.title }}</span>
            <span class="hidden text-xs text-faint sm:block">{{ s.albumName || '未收录单曲' }}</span>
            <button class="cursor-pointer text-xs text-faint opacity-0 group-hover:opacity-100 hover:text-red-500" @click.stop="player.removeAt(i)">✕</button>
          </li>
        </ul>
      </div>

      <div class="px-4 py-2.5 sm:px-8">
        <div class="flex items-center gap-3 sm:gap-5">
          <!-- 封面 + 曲目信息：整块可点，进歌曲详情 -->
          <RouterLink
            :to="`/songs/${player.current.id}`"
            class="group/info flex min-w-0 shrink-0 items-center gap-3 transition"
            :title="`查看《${player.current.title}》详情`"
          >
            <CoverArt
              :title="player.current.title"
              :band="currentSlug"
              :cover-url="player.current.coverUrl || ''"
              class="hidden h-11 w-11 shrink-0 transition-opacity group-hover/info:opacity-85 sm:flex"
              rounded="rounded-md"
            />
            <div class="w-32 min-w-0 sm:w-48">
              <p class="flex items-center gap-1.5 truncate text-sm font-semibold transition-colors group-hover/info:text-primary">
                <span class="truncate">{{ player.current.title }}</span>
                <span
                  v-if="!player.current.audioUrl"
                  class="shrink-0 rounded border border-amber-200 bg-amber-50 px-1 py-0.5 text-[9px] font-normal text-amber-700"
                >试听待补充</span>
              </p>
              <p class="truncate text-xs text-faint">{{ player.current.albumName || player.current.bandName }}</p>
            </div>
          </RouterLink>

          <!-- 传输控制 -->
          <div class="flex shrink-0 items-center gap-0.5 sm:gap-1.5">
            <button class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:text-ink" aria-label="上一首" @click="player.prev">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" /></svg>
            </button>
            <button
              class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-sm transition hover:bg-primary-strong"
              :aria-label="player.isPlaying ? '暂停' : '播放'"
              @click="player.toggle"
            >
              <svg v-if="!player.isPlaying" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
            </button>
            <button class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:text-ink" aria-label="下一首" @click="player.next">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z" /></svg>
            </button>
          </div>

          <!-- 进度条（桌面） -->
          <div class="hidden flex-1 items-center gap-3 md:flex">
            <span class="tabular w-10 text-right text-[11px] text-faint">{{ dragging ? fmt((dragPct / 100) * (player.current.duration || 0)) : curText }}</span>

            <div
             
              class="group relative flex h-6 flex-1 items-center"
              :class="hasDuration ? 'cursor-pointer' : 'cursor-default'"
              @pointerdown="onSeekDown"
              @pointermove="onSeekMove"
              @pointerup="onSeekUp"
              @pointercancel="onSeekUp"
            >
              <div class="h-[3px] w-full rounded-full bg-line-strong/60">
                <div class="h-[3px] rounded-full bg-primary transition-[width] duration-100" :style="{ width: shownPct + '%' }" />
              </div>
              <span
                class="absolute h-3 w-3 -translate-x-1/2 rounded-full bg-primary shadow transition-opacity"
                :class="dragging ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'"
                :style="{ left: shownPct + '%' }"
              />
            </div>

            <span class="tabular w-10 text-[11px] text-faint">{{ durText }}</span>
          </div>

          <!-- 右侧工具 -->
          <div class="ml-auto flex shrink-0 items-center gap-0.5 sm:gap-1">
            <!-- 播放模式 -->
            <button
              class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:text-ink"
              :title="modeLabel"
              :aria-label="modeLabel"
              @click="player.cycleMode"
            >
              <svg v-if="player.mode === 'order'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 8h11l-2.5-2.5M20 16H9l2.5 2.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
              <span v-else-if="player.mode === 'repeat'" class="relative flex items-center">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 8h11l-2.5-2.5M20 16H9l2.5 2.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                <span class="absolute -right-1.5 -bottom-1 text-[8px] font-bold text-primary">1</span>
              </span>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7h4l3 10 3-14 3 10h5" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </button>

            <!-- 音量：hover 展开 -->
            <div class="group/vol relative hidden items-center lg:flex">
              <button
                class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-dim transition hover:text-ink"
                :aria-label="player.muted ? '取消静音' : '静音'"
                @click="player.toggleMute"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M4 9v6h4l5 4V5L8 9H4z" />
                  <path v-if="!player.muted && player.volume > 0.05" d="M16 8.5a5 5 0 0 1 0 7" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  <path v-if="!player.muted && player.volume > 0.45" d="M18.5 6a8 8 0 0 1 0 12" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  <path v-if="player.muted || player.volume <= 0.05" d="M16 9l5 6M21 9l-5 6" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                </svg>
              </button>

              <div class="pointer-events-none absolute right-0 bottom-full mb-2 w-24 rounded-lg border border-line bg-surface p-2 opacity-0 shadow-md transition-opacity group-hover/vol:pointer-events-auto group-hover/vol:opacity-100">
                <div
                 
                  class="relative flex h-5 cursor-pointer items-center"
                  @pointerdown="onVolDown"
                  @pointermove="onVolMove"
                  @pointerup="onVolUp"
                  @pointercancel="onVolUp"
                >
                  <div class="h-[3px] w-full rounded-full bg-line-strong/60">
                    <div class="h-[3px] rounded-full bg-primary" :style="{ width: (player.muted ? 0 : player.volume * 100) + '%' }" />
                  </div>
                  <span
                    class="absolute h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-primary"
                    :style="{ left: (player.muted ? 0 : player.volume * 100) + '%' }"
                  />
                </div>
              </div>
            </div>

            <!-- 队列 -->
            <button
              class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full transition"
              :class="showQueue ? 'text-primary' : 'text-dim hover:text-ink'"
              aria-label="播放队列"
              @click="showQueue = !showQueue"
            >
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 7h11M4 12h11M4 17h7M19 8v8M17 14l2 2 2-2" /></svg>
            </button>

            <!-- 收起：只隐藏播放条，音乐继续（清空队列在抽屉里） -->
            <button
              class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-faint transition hover:text-ink"
              aria-label="收起播放条"
              title="收起播放条（音乐继续）"
              @click="player.collapseBar()"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6" /></svg>
            </button>
          </div>
        </div>

        <!-- 进度条（移动端第二行） -->
        <div class="mt-1.5 flex items-center gap-2 md:hidden">
          <span class="tabular text-[10px] text-faint">{{ curText }}</span>
          <div
           
            class="relative flex h-5 flex-1 items-center"
            :class="hasDuration ? 'cursor-pointer' : 'cursor-default'"
            @pointerdown="onSeekDown"
            @pointermove="onSeekMove"
            @pointerup="onSeekUp"
            @pointercancel="onSeekUp"
          >
            <div class="h-[3px] w-full rounded-full bg-line-strong/60">
              <div class="h-[3px] rounded-full bg-primary" :style="{ width: shownPct + '%' }" />
            </div>
            <span class="absolute h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-primary shadow" :class="dragging ? 'opacity-100' : 'opacity-0'" :style="{ left: shownPct + '%' }" />
          </div>
          <span class="tabular text-[10px] text-faint">{{ durText }}</span>
        </div>
      </div>
    </div>
  </transition>

  <!-- 收起态：左下角「上拉展开」小条（音乐继续播放） -->
  <transition enter-active-class="transition duration-200" enter-from-class="opacity-0 translate-y-2">
    <button
      v-if="player.current && player.barCollapsed"
      class="group fixed bottom-4 left-4 z-40 flex max-w-[min(22rem,calc(100vw-2rem))] cursor-pointer items-center gap-2.5 rounded-full border border-line bg-surface/95 py-2 pl-2 pr-4 shadow-lg backdrop-blur transition hover:shadow-xl"
      title="展开播放条"
      aria-label="展开播放条"
      @click="player.expandBar()"
    >
      <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-white transition group-hover:-translate-y-0.5">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 15l6-6 6 6" /></svg>
      </span>
      <CoverArt
        :title="player.current.title"
        :band="currentSlug"
        :cover-url="player.current.coverUrl || ''"
        class="hidden h-7 w-7 shrink-0 sm:flex"
        rounded="rounded"
      />
      <span class="min-w-0 text-left">
        <span class="block truncate text-xs font-medium leading-tight">{{ player.current.title }}</span>
        <span class="tabular block text-[10px] leading-tight text-faint">
          {{ player.isPlaying ? `播放中 ${curText}` : `已暂停 ${curText}` }}
        </span>
      </span>
    </button>
  </transition>
</template>
