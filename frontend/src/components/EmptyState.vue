<script setup>
/** 空态/引导：所有列表页无数据时统一用它，附行动按钮 */
defineProps({
  icon: { type: String, default: '🎵' },
  text: { type: String, required: true },
  hint: { type: String, default: '' },
  actionText: { type: String, default: '' },
})
const emit = defineEmits(['action'])
</script>

<template>
  <div class="flex flex-col items-center justify-center rounded-xl border border-dashed border-line bg-surface px-6 py-16 text-center">
    <div class="mb-3 text-4xl opacity-80">{{ icon }}</div>
    <p class="font-medium text-ink">{{ text }}</p>
    <p v-if="hint" class="mt-1 text-sm text-dim">{{ hint }}</p>
    <button v-if="actionText" class="btn-ghost mt-5" @click="emit('action')">{{ actionText }}</button>
  </div>
</template>
