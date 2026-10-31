<script setup>
/**
 * 小灯面板头部：头像/名称/在线 + 运营开关(仅 ADMIN) + 最大化/关闭 + 人格 chips
 * docs/12-前端提示词-小灯助手界面升级.md
 */
defineProps({
  persona: { type: Object, required: true },      // 当前人格对象 {id, chip, gradient}
  personas: { type: Array, required: true },
  personaId: { type: String, required: true },
  threads: { type: Object, required: true },      // 用于人格 chip 上的「有历史」圆点
  isAdmin: { type: Boolean, default: false },
  opsMode: { type: Boolean, default: false },
  maximized: { type: Boolean, default: false },
})
const emit = defineEmits(['update:personaId', 'update:opsMode', 'toggle-max', 'close', 'clear'])

function switchPersona(p) {
  if (p.id !== '') emit('update:personaId', p.id)
}
</script>

<template>
  <div class="border-b border-line px-4 py-3">
    <div class="flex items-center justify-between gap-2">
      <div class="flex min-w-0 items-center gap-3">
        <div
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-white shadow-sm"
          :style="{ background: persona.gradient }"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
            <rect x="9.4" y="18.8" width="5.2" height="1.7" rx="0.85" />
            <rect x="10.2" y="21.1" width="3.6" height="1.4" rx="0.7" />
          </svg>
        </div>
        <div class="min-w-0">
          <p class="truncate text-sm font-bold leading-tight">
            {{ opsMode ? '小灯 · 运营' : persona.chip }}
            <span class="micro-accent !text-[9px]">✳ AI 助手</span>
          </p>
          <p class="micro mt-0.5 flex items-center gap-1.5">
            <span class="inline-block h-1.5 w-1.5 rounded-full bg-green-500" />在线 ONLINE
          </p>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-1.5">
        <!-- 运营开关：仅 ADMIN 渲染（docs/12 能力矩阵） -->
        <button
          v-if="isAdmin"
          class="cursor-pointer rounded-full border px-2.5 py-1 text-[11px] font-medium transition-colors"
          :class="opsMode
            ? 'border-amber-400 bg-amber-50 text-amber-700'
            : 'border-line bg-transparent text-dim hover:text-ink'"
          :aria-pressed="opsMode"
          :title="opsMode ? '关闭运营模式' : '开启运营模式（写操作需人工确认）'"
          @click="emit('update:opsMode', !opsMode)"
        >
          ⚙ {{ opsMode ? '运营中' : '运营' }}
        </button>
        <button
          class="hidden h-7 w-7 cursor-pointer items-center justify-center rounded text-dim transition hover:bg-base hover:text-ink sm:flex"
          :title="maximized ? '还原尺寸' : '最大化'"
          :aria-label="maximized ? '还原尺寸' : '最大化'"
          @click="emit('toggle-max')"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 3v18" />
          </svg>
        </button>
        <button class="flex h-7 w-7 cursor-pointer items-center justify-center rounded text-dim transition hover:bg-base hover:text-ink" aria-label="收起" @click="emit('close')">✕</button>
      </div>
    </div>

    <!-- 人格 chips：运营模式下置灰（Agent 固定小灯语气，不收 persona） -->
    <div class="mt-2.5 flex items-center gap-1.5" :class="opsMode && 'pointer-events-none opacity-40'" :title="opsMode ? '运营模式下由小灯统一处理' : ''">
      <button
        v-for="p in personas"
        :key="p.id"
        class="flex cursor-pointer items-center rounded-full px-2.5 py-1 text-xs font-medium transition-colors duration-200"
        :class="personaId === p.id ? 'bg-primary text-white' : 'bg-base text-dim hover:text-ink'"
        @click="switchPersona(p)"
      >
        {{ p.chip }}
        <span
          v-if="threads[p.id]?.messages.length > 1"
          class="ml-1 inline-block h-1.5 w-1.5 rounded-full"
          :class="personaId === p.id ? 'bg-white/80' : 'bg-primary'"
        />
      </button>
      <button
        class="ml-auto cursor-pointer text-xs text-faint transition hover:text-ink"
        title="清空当前对话"
        aria-label="清空当前对话"
        @click="emit('clear')"
      >
        ↺ 清空
      </button>
    </div>
  </div>
</template>
