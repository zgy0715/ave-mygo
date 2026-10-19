<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listBands } from '@/api/song'

/** 乐队总览：双乐队入口卡（真接口版） */
const bands = ref([])
const loading = ref(true)
const errMsg = ref('')

onMounted(async () => {
  try {
    bands.value = await listBands()
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
})

const gradientOf = (slug) =>
  ({
    'ave-mujica': 'linear-gradient(135deg,#6b4fa3,#4a3573)',
    sumimi: 'linear-gradient(135deg, #d4789f 0%, #b95 100%)',
  }[slug] || 'linear-gradient(135deg,#2c7da0,#1f6485)')
</script>

<template>
  <div class="wrap py-8">
    <div class="section-head">
      <h1 class="text-2xl font-black">乐队</h1>
    </div>

    <div v-if="loading" class="grid gap-6 md:grid-cols-2">
      <div v-for="i in 2" :key="i" class="h-56 animate-pulse rounded-xl bg-surface"></div>
    </div>
    <div v-else-if="errMsg" class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></div>

    <div v-else class="grid gap-6 md:grid-cols-2">
      <RouterLink
        v-for="b in bands"
        :key="b.id"
        :to="`/bands/${b.id}`"
        class="card group overflow-hidden hover:shadow-md"
      >
        <!-- 官方图优先，缺图回退乐队渐变 -->
        <div class="shine relative h-32 overflow-hidden" :style="{ background: gradientOf(b.slug) }">
          <img
            v-if="b.logoUrl"
            :src="b.logoUrl"
            :alt="b.name"
            class="zoomable absolute inset-0 h-full w-full object-cover"
            loading="lazy"
          >
          <div v-if="b.logoUrl" class="absolute inset-0 bg-gradient-to-t from-black/55 via-black/10 to-transparent" />
          <p class="display absolute bottom-5 left-5 text-3xl tracking-wide text-white" style="text-shadow: 0 1px 12px rgba(0,0,0,.5)">{{ b.name }}</p>
        </div>
        <div class="p-5">
          <p class="text-sm leading-relaxed text-dim">{{ b.description }}</p>
        </div>
      </RouterLink>
    </div>
  </div>
</template>
