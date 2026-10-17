<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getBand, listSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'

const route = useRoute()
const player = usePlayerStore()

const band = ref(null)
const members = ref([])
const albums = ref([])
const songs = ref([])
const loading = ref(true)
const errMsg = ref('')

onMounted(async () => {
  try {
    const data = await getBand(route.params.id)
    band.value = data.band
    members.value = data.members
    albums.value = data.albums
    const so = await listSongs({ bandId: route.params.id, pageSize: 50 })
    songs.value = so.content
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 pb-24 pt-8">
    <div v-if="loading" class="h-64 animate-pulse rounded-xl bg-surface"></div>
    <div v-else-if="errMsg" class="rounded-lg bg-red-950/50 p-4 text-sm text-red-400">{{ errMsg }}</div>

    <template v-else-if="band">
      <!-- 乐队头部 -->
      <p class="mono mb-3 text-xs text-primary">BAND / {{ String(band.id).padStart(2, '0') }}</p>
      <h1 class="hero-type !text-5xl md:!text-7xl">{{ band.name }}</h1>
      <p class="mt-6 max-w-2xl text-sm leading-7 text-gray-500">{{ band.description }}</p>

      <!-- 成员 -->
      <section class="mt-16">
        <div class="section-head">
          <h2 class="text-lg font-bold text-gray-900"><span class="mono mr-2 text-xs text-primary">_M1_</span>成员 <span class="mono ml-2 text-[10px] text-dim">MEMBERS</span></h2>
        </div>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <div v-for="m in members" :key="m.id" class="group rounded-xl border border-line bg-surface p-5 transition hover:border-primary/50">
            <div class="mb-4 flex h-24 items-center justify-center rounded-lg bg-gradient-to-br from-primary/15 to-transparent text-3xl group-hover:scale-105 transition">👤</div>
            <p class="mono text-[10px] text-primary">{{ m.position }}</p>
            <p class="mt-1 text-lg font-bold text-gray-900">{{ m.nameJa }}</p>
            <p class="mono text-[10px] text-dim">{{ m.nameRomaji }}</p>
            <p class="mt-2 text-xs text-gray-500">CV. {{ m.cv }}</p>
            <p class="mt-2 text-xs leading-5 text-gray-600">{{ m.intro }}</p>
          </div>
        </div>
      </section>

      <!-- 专辑 -->
      <section class="mt-16">
        <div class="section-head">
          <h2 class="text-lg font-bold text-gray-900"><span class="mono mr-2 text-xs text-primary">_M2_</span>作品 <span class="mono ml-2 text-[10px] text-dim">DISCOGRAPHY</span></h2>
        </div>
        <div class="grid gap-4 sm:grid-cols-3">
          <div v-for="a in albums" :key="a.id" class="rounded-xl border border-line bg-surface p-5">
            <div class="mb-3 flex h-28 items-center justify-center rounded-lg bg-gradient-to-br from-violet-900/40 to-transparent text-3xl">💿</div>
            <p class="font-semibold text-gray-900">{{ a.name }}</p>
            <p class="mono mt-1 text-[10px] text-dim">{{ a.type }} · {{ a.releaseDate || 'TBD' }}</p>
          </div>
        </div>
      </section>

      <!-- 歌曲 -->
      <section class="mt-16">
        <div class="section-head">
          <h2 class="text-lg font-bold text-gray-900"><span class="mono mr-2 text-xs text-primary">_M3_</span>歌曲 <span class="mono ml-2 text-[10px] text-dim">SONGS</span></h2>
        </div>
        <div class="grid gap-2">
          <div
            v-for="(s, i) in songs" :key="s.id"
            class="group flex cursor-pointer items-center gap-4 border-b border-line px-2 py-3 transition hover:bg-elevated/60"
            @click="player.play(s)"
          >
            <span class="mono w-8 text-xs text-dim">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="flex-1 truncate text-sm text-gray-900 group-hover:text-gray-900">{{ s.title }}</span>
            <span class="hidden text-xs text-dim md:inline" v-if="s.albumName">{{ s.albumName }}</span>
            <span class="mono w-16 text-right text-xs text-primary">{{ player.current?.id === s.id && player.isPlaying ? '▶ PLAYING' : '' }}</span>
          </div>
        </div>
      </section>
    </template>
  </main>
</template>
