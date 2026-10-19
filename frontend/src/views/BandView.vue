<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CoverArt from '@/components/CoverArt.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getBand } from '@/api/song'
import { listSongs } from '@/api/song'

/**
 * 乐队主页 v3.2：成员区对标 Mujica 剧场版官网人物介绍页——
 * 一排竖长立牌（约 1:2），衬线名字叠于立牌底部居中，职位标签在上，
 * 编号（01…）做档案感装饰，hover 触发扫光（.shine）。
 * 真接口版：GET /bands/{id} → {band, members, albums} + GET /songs?bandId=。
 * 头图：滚动展开（Apple 式 zoom-out reveal）——初始放大裁切，下拉一屏逐渐完整呈现。
 */
const route = useRoute()
const router = useRouter()

const band = ref(null)
const members = ref([])
const albums = ref([])
const songs = ref([])
const loading = ref(true)
const errMsg = ref('')

// ===== 头图滚动展开（丝滑版）：lerp 插值 + 纯 transform 合成 =====
const heroWrap = ref(null)
const target = ref(0)               // 滚动目标进度（即时）
const eased = ref(0)                // 渲染进度（向 target 缓动）
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

function measure() {
  const el = heroWrap.value
  if (!el) return
  const total = el.offsetHeight - window.innerHeight
  target.value = total > 0 ? Math.min(1, Math.max(0, -el.getBoundingClientRect().top / total)) : 0
}

let rafId = null
function tick() {
  // 每帧向目标靠近 9% —— 惯性"跟手但不生硬"的来源
  eased.value += (target.value - eased.value) * 0.09
  // smoothstep 缓动曲线：两端慢中间快，更有电影感
  const p = eased.value * eased.value * (3 - 2 * eased.value)
  const img = imgEl.value
  if (img) {
    img.style.transform = `translate3d(0, ${(1 - p) * -7}%, 0) scale(${1.28 - 0.28 * p})`
    img.style.opacity = String(0.6 + 0.4 * p)
  }
  const txt = heroTextEl.value
  if (txt) {
    txt.style.opacity = String(Math.max(0, 1 - p * 1.8))
    txt.style.transform = `translate3d(0, ${p * 3.5}rem, 0)`
  }
  const shade = shadeEl.value
  if (shade) shade.style.opacity = String(1 - p * 0.4)
  rafId = requestAnimationFrame(tick)
}

const imgEl = ref(null)
const heroTextEl = ref(null)
const shadeEl = ref(null)

onMounted(() => {
  if (!reducedMotion) {
    window.addEventListener('scroll', measure, { passive: true })
    window.addEventListener('resize', measure)
    measure()
    rafId = requestAnimationFrame(tick)
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', measure)
  window.removeEventListener('resize', measure)
  if (rafId) cancelAnimationFrame(rafId)
})

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

// 成员立牌首字：名最后的汉字（"高松 燈"→"燈"）
const lastChar = (nameJa) => (nameJa || '').replace(/\s/g, '').slice(-1)

const gradientOf = computed(() => {
  const g = {
    'ave-mujica': 'linear-gradient(135deg,#6b4fa3,#4a3573)',
    sumimi: 'linear-gradient(135deg, #d4789f 0%, #b95 100%)',
  }
  return g[band.value?.slug] || 'linear-gradient(135deg,#2c7da0,#1f6485)'
})

// 乐队 id → 封面渐变 slug（sumimi=3，V11 起第三支乐队）
const slugById = (id) => ({ 1: 'mygo', 2: 'ave-mujica', 3: 'sumimi' }[id] || 'mygo')
</script>

<template>
  <div v-if="loading" class="wrap py-8"><div class="h-64 animate-pulse rounded-xl bg-surface"></div></div>

  <div v-else-if="errMsg" class="wrap py-8">
    <div class="card p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="router.push('/bands')">返回乐队列表</button></div>
  </div>

  <div v-else-if="band" class="wrap py-8">
    <!-- 乐队头图：sticky 满屏画布，lerp 缓动 + 纯 transform（合成层，不掉帧） -->
    <div v-if="band.logoUrl" ref="heroWrap" class="relative h-[240vh]">
      <div class="sticky top-0 h-[100svh] overflow-hidden">
        <img
          ref="imgEl"
          :src="band.logoUrl"
          :alt="band.name"
          class="absolute inset-x-0 top-0 h-[118%] w-full object-cover will-change-transform"
        >
        <div ref="shadeEl" class="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent"></div>
        <div ref="heroTextEl" class="absolute bottom-14 left-7 will-change-transform sm:bottom-20 sm:left-10">
          <p class="mono text-xs tracking-[0.25em] text-white/70 [text-shadow:0_1px_6px_rgba(0,0,0,.5)]">{{ band.slug.toUpperCase() }}</p>
          <h1 class="mt-2 text-6xl tracking-wide sm:text-7xl" style="text-shadow: 0 2px 24px rgba(0,0,0,.55)">{{ band.name }}</h1>
          <p class="mono mt-4 text-[10px] tracking-[0.3em] text-white/60">SCROLL — 継続表示 ↧</p>
        </div>
      </div>
    </div>

    <!-- 无图回退：渐变头图（原样式） -->
    <div v-else class="shine font-display relative flex h-80 flex-col justify-end overflow-hidden rounded-xl p-7 text-white md:h-[26rem]" :style="{ background: gradientOf }">
      <p class="mono text-xs tracking-[0.25em] text-white/70">{{ band.slug.toUpperCase() }}</p>
      <h1 class="mt-2 text-5xl tracking-wide sm:text-6xl" style="text-shadow: 0 2px 18px rgba(0,0,0,.45)">{{ band.name }}</h1>
    </div>
    <p class="mt-5 max-w-2xl text-sm leading-relaxed text-dim">{{ band.description }}</p>

    <!-- 成员：竖长立牌（人物页风格） -->
    <section class="mt-14">
      <div class="section-head">
        <div>
          <p class="micro-accent mb-1.5">CHARACTER</p>
          <h2 class="serif text-2xl font-semibold">成员</h2>
        </div>
        <p class="micro">{{ members.length }} MEMBERS</p>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5 lg:gap-5">
        <div
          v-for="(m, i) in members"
          :key="m.id"
          v-reveal="i * 90"
          class="group relative overflow-hidden rounded-lg"
          style="aspect-ratio: 1 / 1.9"
        >
          <!-- 立牌面：渐变底永远渲染（官方立绘是透明底 PNG），立绘叠加其上
               立绘锚定：底边 = 文字块顶（7.25rem），left-1/2 + w-auto 保证水平真居中 -->
          <div class="shine absolute inset-0 flex flex-col" :style="{ background: gradientOf }">
            <img
              v-if="m.avatarUrl"
              :src="m.avatarUrl"
              :alt="m.nameJa"
              class="absolute bottom-[7.25rem] left-1/2 z-[1] h-[calc(100%-7.5rem)] w-auto max-w-none -translate-x-1/2 object-contain object-bottom"
            >
            <template v-if="!m.avatarUrl">
              <div class="absolute inset-0 opacity-25" style="background: linear-gradient(115deg, transparent 35%, rgba(255,255,255,.4) 48%, transparent 60%)" />
              <span class="serif relative mt-auto px-4 pb-24 text-7xl font-semibold text-white/90">
                {{ lastChar(m.nameJa) }}
              </span>
            </template>
            <span class="mono absolute left-3 top-3 z-10 text-[10px] tracking-[0.25em] text-white/75 [text-shadow:0_1px_4px_rgba(0,0,0,.5)]">
              {{ String(i + 1).padStart(2, '0') }}
            </span>
          </div>

          <!-- 底部信息：职位标签 + 衬线名 + CV（官网 .name 的居中叠放语言） -->
          <div class="absolute inset-x-0 bottom-0 z-10 bg-gradient-to-t from-black/70 via-black/35 to-transparent px-3 pb-4 pt-10 text-center text-white">
            <p class="micro !text-white/70">{{ m.position }}</p>
            <p class="serif mt-1.5 text-xl font-semibold">{{ m.nameJa }}</p>
            <p class="mt-1 text-[11px] text-white/60">{{ m.nameRomaji }} · CV. {{ m.cv }}</p>
            <p class="mt-2 max-h-0 overflow-hidden text-[11px] leading-relaxed text-white/75 opacity-0 transition-all duration-500 group-hover:max-h-20 group-hover:opacity-100" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">
              {{ m.intro }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 专辑 -->
    <section v-if="albums.length" class="mt-14">
      <div class="section-head">
        <div>
          <p class="micro-accent mb-1.5">SINGLE / ALBUM</p>
          <h2 class="serif text-2xl font-semibold">作品</h2>
        </div>
      </div>
      <div class="grid gap-4 sm:grid-cols-3">
        <div v-for="a in albums" :key="a.id" class="card p-4">
          <CoverArt :title="a.name" :band="band.slug" :cover-url="a.coverUrl || ''" class="h-28 w-full" rounded="rounded-lg" />
          <p class="mt-3 truncate text-sm font-semibold">{{ a.name }}</p>
          <p class="mono mt-0.5 text-[10px] text-faint">{{ a.type }} · {{ a.releaseDate || 'TBD' }}</p>
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
        <li v-for="(s, i) in songs" :key="s.id" v-reveal="i * 50">
          <RouterLink :to="`/songs/${s.id}`" class="flex items-center gap-3 p-3 transition hover:bg-elevated">
            <CoverArt :title="s.title" :band="slugById(s.bandId)" class="h-10 w-10" rounded="rounded-md" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ s.title }}</p>
              <p class="truncate text-xs text-faint">{{ s.albumName || '未收录单曲' }}</p>
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
