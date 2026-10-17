<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { bands, members, songs } from '@/data/mock'

/** 乐队主页：简介 + 成员档案 + 收录歌曲 */
const route = useRoute()
const router = useRouter()
const band = bands.find((b) => b.id === route.params.id)
const bandMembers = computed(() => members.filter((m) => m.band === band?.id))
const bandSongs = computed(() => songs.filter((s) => s.band === band?.id))
</script>

<template>
  <div v-if="band" class="wrap py-8">
    <!-- 乐队头图 -->
    <div class="flex h-44 flex-col justify-end rounded-2xl p-6 text-white sm:h-56" :style="{ background: band.gradient }">
      <p class="mono text-xs tracking-[0.25em] opacity-80">{{ band.nameCn }}</p>
      <h1 class="mt-1 text-4xl font-black" style="text-shadow: 0 1px 10px rgba(0,0,0,.3)">{{ band.name }}</h1>
    </div>
    <p class="mt-4 max-w-2xl text-sm leading-relaxed text-dim">{{ band.intro }}</p>

    <!-- 成员 -->
    <section class="mt-10">
      <h2 class="section-head text-xl font-bold">成员</h2>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        <div v-for="m in bandMembers" :key="m.id" class="card p-4 text-center">
          <div
            class="mx-auto flex h-16 w-16 items-center justify-center rounded-full text-xl font-black text-white"
            :style="{ background: band.gradient }"
          >
            {{ m.name.slice(1, 2) }}
          </div>
          <p class="mt-3 font-semibold">{{ m.name }}</p>
          <p class="mt-0.5 text-xs" :style="{ color: band.color }">{{ m.position }}</p>
          <p class="mt-0.5 text-xs text-faint">CV. {{ m.cv }}</p>
        </div>
      </div>
    </section>

    <!-- 收录歌曲 -->
    <section class="mt-10">
      <div class="section-head">
        <h2 class="text-xl font-bold">收录歌曲</h2>
        <RouterLink :to="{ path: '/songs', query: { band: band.id } }" class="text-sm text-dim hover:text-primary">在歌曲库中查看 →</RouterLink>
      </div>
      <ul class="card divide-y divide-line overflow-hidden">
        <li v-for="s in bandSongs" :key="s.id">
          <RouterLink :to="`/songs/${s.id}`" class="flex items-center gap-3 p-3 hover:bg-base">
            <CoverArt :title="s.title" :band="s.band" class="h-10 w-10" rounded="rounded-md" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ s.title }}</p>
              <p class="truncate text-xs text-faint">{{ s.album }}</p>
            </div>
            <span class="tabular text-xs text-faint">{{ s.releaseDate }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>

  <div v-else class="wrap py-16">
    <EmptyState icon="🎸" text="乐队不存在" action-text="查看全部乐队" @action="router.push('/bands')" />
  </div>
</template>
