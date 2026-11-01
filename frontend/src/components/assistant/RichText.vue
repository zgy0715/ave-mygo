<script setup>
import { computed } from 'vue'
import { parseRich } from '@/utils/richText'

/**
 * 助手回复的安全轻排版：只认 **加粗** 与列表，全程插值渲染（无 v-html）
 * 解析见 src/utils/richText.js，返回 block/runs 结构，模板只输出文本节点与 <strong>
 */
const props = defineProps({
  text: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
})

const blocks = computed(() => parseRich(props.text, { streaming: props.streaming }).blocks)
const isLast = (i) => props.streaming && i === blocks.value.length - 1
</script>

<template>
  <template v-for="(b, bi) in blocks" :key="bi">
    <!-- 段落：保留原始换行 -->
    <p v-if="b.type === 'p'" class="whitespace-pre-wrap break-words">
      <template v-for="(r, ri) in b.runs" :key="ri">
        <strong v-if="r.bold" class="font-semibold">{{ r.text }}</strong>
        <template v-else>{{ r.text }}</template>
      </template>
      <span
        v-if="isLast(bi)"
        class="ml-0.5 inline-block w-[2px] animate-pulse bg-primary align-middle"
        style="height: 1em"
      />
    </p>

    <!-- 列表项：marker + 内容，整块缩进 -->
    <div v-else class="flex gap-1.5 pl-0.5">
      <span class="shrink-0 select-none tabular text-dim" :class="b.ordered && 'mono'">{{ b.marker }}</span>
      <p class="min-w-0 flex-1 whitespace-pre-wrap break-words">
        <template v-for="(r, ri) in b.runs" :key="ri">
          <strong v-if="r.bold" class="font-semibold">{{ r.text }}</strong>
          <template v-else>{{ r.text }}</template>
        </template>
        <span
          v-if="isLast(bi)"
          class="ml-0.5 inline-block w-[2px] animate-pulse bg-primary align-middle"
          style="height: 1em"
        />
      </p>
    </div>
  </template>
</template>
