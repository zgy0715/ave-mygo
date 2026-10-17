<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { bands, members, songs } from '@/data/mock'

/**
 * 乐队主页 v3.2：成员区对标 Mujica 剧场版官网人物介绍页——
 * 一排竖长立牌（约 1:2），衬线名字叠于立牌底部居中，职位标签在上，
 * 编号（01…）做档案感装饰，hover 触发扫光（.shine）。
 */
const route = useRoute()
const router = useRouter()
const band = bands.find((b) => b.id === route.params.id)
const bandMembers = computed(() => members.filter((m) => m.band === band?.id))
const bandSongs = computed(() => songs.filter((s) => s.band === band?.id))
</script>

<template>
  <div v-if="band" class="wrap py-8">
    <!-- 乐队头图 -->
    <div class="shine flex h-52 flex-col justify-end rounded-xl p-7 text-white sm:h-64" :style="{ background: band.gradient }">
      <p class="mono text-xs tracking-[0.25em] text-white/70">{{ band.nameCn }}</p>
      <h1 class="serif mt-2 text-5xl font-semibold sm:text-6xl" style="text-shadow: 0 2px 18px rgba(0,0,0,.45)">{{ band.name }}</h1>
    </div>
    <p class="mt-5 max-w-2xl text-sm leading-relaxed text-dim">{{ band.intro }}</p>

    <!-- 成员：竖长立牌（人物页风格） -->
    <section class="mt-14">
      <div class="section-head">
        <div>
          <p class="micro-accent mb-1.5">CHARACTER</p>
          <h2 class="serif text-2xl font-semibold">成员</h2>
        </div>
        <p class="micro">{{ bandMembers.length }} MEMBERS</p>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5 lg:gap-5">
        <div
          v-for="(m, i) in bandMembers"
          :key="m.id"
          v-reveal="i * 90"
          class="group relative overflow-hidden rounded-lg"
          style="aspect-ratio: 1 / 1.9"
        >
          <!-- 立牌面：乐队渐变 + 大衬线首字 -->
          <div class="shine absolute inset-0 flex flex-col" :style="{ background: band.gradient }">
            <div class="absolute inset-0 opacity-25" style="background: linear-gradient(115deg, transparent 35%, rgba(255,255,255,.4) 48%, transparent 60%)" />
            <span class="mono absolute left-3 top-3 text-[10px] tracking-[0.25em] text-white/55">
              {{ String(i + 1).padStart(2, '0') }}
            </span>
            <span class="serif relative mt-auto px-4 pb-24 text-7xl font-semibold text-white/90">
              {{ m.name.slice(1, 2) }}
            </span>
          </div>

          <!-- 底部信息：职位标签 + 衬线名 + CV（官网 .name 的居中叠放语言） -->
          <div class="absolute inset-x-0 bottom-0 z-10 bg-gradient-to-t from-black/70 via-black/35 to-transparent px-3 pb-4 pt-10 text-center text-white">
            <p class="micro !text-white/70">{{ m.position }}</p>
            <p class="serif mt-1.5 text-xl font-semibold">{{ m.name }}</p>
            <p class="mt-1 text-[11px] text-white/60">CV. {{ m.cv }}</p>
            <p class="mt-2 max-h-0 overflow-hidden text-[11px] leading-relaxed text-white/75 opacity-0 transition-all duration-500 group-hover:max-h-16 group-hover:opacity-100" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">
              {{ m.intro }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 收录歌曲 -->
    <section class="mt-14">
      <div class="section-head">
        <div>
          <p class="micro-accent mb-1.5">DISCOGRAPHY</p>
          <h2 class="serif text-2xl font-semibold">收录歌曲</h2>
        </div>
        <RouterLink :to="{ path: '/songs', query: { band: band.id } }" class="micro py-1 transition hover:text-primary">歌曲库 →</RouterLink>
      </div>
      <ul class="card divide-y divide-line overflow-hidden">
        <li v-for="(s, i) in bandSongs" :key="s.id" v-reveal="i * 50">
          <RouterLink :to="`/songs/${s.id}`" class="flex items-center gap-3 p-3 transition hover:bg-elevated">
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
