<script setup>
/**
 * 小灯面板头部：人格渐变横幅 + 人格 chips + 最大化/关闭
 * docs/15：无运营开关；能力由后端按 token 身份决定
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
  <div class="relative shrink-0 overflow-hidden" :style="{ background: persona.gradient }">
    <!-- 装饰光斑 -->
    <div class="pointer-events-none absolute -right-6 -top-10 h-28 w-28 rounded-full bg-white/10" />
    <div class="pointer-events-none absolute -left-10 -bottom-14 h-28 w-28 rounded-full bg-white/10" />

    <div class="relative px-4 pb-2.5 pt-3">
      <div class="flex items-center justify-between gap-2">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white/15 ring-2 ring-white/40">
            <img v-if="persona.avatar" :src="persona.avatar" :alt="persona.chip" class="h-full w-full object-cover" />
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor" class="text-white">
              <path d="M12 2a7 7 0 0 0-4.1 12.7c.6.5 1.1 1.4 1.1 2.3v.5h6v-.5c0-.9.5-1.8 1.1-2.3A7 7 0 0 0 12 2z" />
              <rect x="9.4" y="18.8" width="5.2" height="1.7" rx="0.85" />
              <rect x="10.2" y="21.1" width="3.6" height="1.4" rx="0.7" />
            </svg>
          </div>
          <div class="min-w-0">
            <p class="truncate text-sm font-bold leading-tight text-white">{{ persona.chip }}</p>
            <p class="mt-0.5 flex items-center gap-1.5 font-mono text-[9px] uppercase tracking-[0.14em] text-white/75">
              <span class="inline-block h-1 w-1 rounded-full bg-emerald-300" />在线 ONLINE
            </p>
          </div>
        </div>

        <div class="flex shrink-0 items-center gap-1">
          <button
            class="hidden h-7 w-7 cursor-pointer items-center justify-center rounded text-white/80 transition hover:bg-white/15 hover:text-white sm:flex"
            :title="maximized ? '还原尺寸' : '最大化'"
            :aria-label="maximized ? '还原尺寸' : '最大化'"
            @click="emit('toggle-max')"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 3v18" />
            </svg>
          </button>
          <button
            class="flex h-7 w-7 cursor-pointer items-center justify-center rounded text-white/80 transition hover:bg-white/15 hover:text-white"
            aria-label="收起"
            @click="emit('close')"
          >✕</button>
        </div>
      </div>

      <!-- 人格 chips（横幅内半透明胶囊） -->
      <div class="mt-2.5 flex items-center gap-1.5">
        <button
          v-for="p in personas"
          :key="p.id"
          class="flex cursor-pointer items-center rounded-full px-2.5 py-[3px] text-[11px] font-medium transition-colors duration-200"
          :class="personaId === p.id ? 'bg-white text-ink shadow-sm' : 'bg-white/15 text-white/90 hover:bg-white/25'"
          @click="emit('update:personaId', p.id)"
        >
          {{ p.chip }}
          <span
            v-if="threads[p.id]?.messages.length > 1"
            class="ml-1.5 inline-block h-1 w-1 rounded-full"
            :class="personaId === p.id ? 'bg-primary' : 'bg-white'"
          />
        </button>
        <button
          class="ml-auto cursor-pointer text-[11px] text-white/70 transition hover:text-white"
          title="清空当前对话"
          aria-label="清空当前对话"
          @click="emit('clear')"
        >
          ↺ 清空
        </button>
      </div>
    </div>
  </div>
</template>
