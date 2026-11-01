<script setup>
/**
 * 小灯面板头部：头像/名称/在线 + 最大化/关闭 + 人格 chips
 * docs/15：删除「⚙ 运营」开关——能力由后端按 token 身份决定，前端不再分模式
 */
defineProps({
  persona: { type: Object, required: true },
  personas: { type: Array, required: true },
  personaId: { type: String, required: true },
  threads: { type: Object, required: true },
  maximized: { type: Boolean, default: false },
})
const emit = defineEmits(['update:personaId', 'toggle-max', 'close', 'clear'])
</script>

<template>
  <div class="shrink-0 border-b border-line px-4 pb-2.5 pt-3">
    <div class="flex items-center justify-between gap-2">
      <div class="flex min-w-0 items-center gap-3">
        <div
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-white shadow-sm"
          :style="{ background: persona.gradient }"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
            <rect x="9.4" y="18.8" width="5.2" height="1.7" rx="0.85" />
            <rect x="10.2" y="21.1" width="3.6" height="1.4" rx="0.7" />
          </svg>
        </div>
        <div class="min-w-0">
          <p class="flex min-w-0 items-baseline gap-1.5">
            <span class="truncate text-sm font-bold leading-tight">{{ persona.chip }}</span>
            <span class="h-2.5 w-px shrink-0 bg-line-strong" aria-hidden="true" />
            <span class="shrink-0 font-mono text-[9px] uppercase tracking-[0.14em] text-faint">AI 助手</span>
          </p>
          <p class="mt-0.5 flex items-center gap-1.5 font-mono text-[9px] uppercase tracking-[0.14em] text-faint">
            <span class="inline-block h-1 w-1 rounded-full bg-green-500" />在线 ONLINE
          </p>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-1.5">
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
        <button
          class="flex h-7 w-7 cursor-pointer items-center justify-center rounded text-dim transition hover:bg-base hover:text-ink"
          aria-label="收起"
          @click="emit('close')"
        >✕</button>
      </div>
    </div>

    <!-- 人格 chips -->
    <div class="mt-2 flex items-center gap-1.5">
      <button
        v-for="p in personas"
        :key="p.id"
        class="flex cursor-pointer items-center rounded-full border px-2.5 py-[3px] text-[11px] font-medium transition-colors duration-200"
        :class="personaId === p.id
          ? 'border-primary bg-primary text-white'
          : 'border-line bg-surface text-dim hover:border-line-strong hover:text-ink'"
        @click="emit('update:personaId', p.id)"
      >
        {{ p.chip }}
        <span
          v-if="threads[p.id]?.messages.length > 1"
          class="ml-1.5 inline-block h-1 w-1 rounded-full"
          :class="personaId === p.id ? 'bg-white/80' : 'bg-primary'"
        />
      </button>
      <button
        class="ml-auto cursor-pointer text-[11px] text-faint transition hover:text-ink"
        title="清空当前对话"
        aria-label="清空当前对话"
        @click="emit('clear')"
      >
        ↺ 清空
      </button>
    </div>
  </div>
</template>
