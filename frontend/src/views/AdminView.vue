<script setup>
import { defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue'
import CoverArt from '@/components/CoverArt.vue'
import { listAlbums, listBands } from '@/api/song'
import { createAdminSong, deleteAdminSong, listAdminSongs, updateAdminSong, uploadHeroVideo } from '@/api/admin'
import AdminEventsPanel from './AdminEventsPanel.vue'
import AdminOrdersPanel from './AdminOrdersPanel.vue'
import AdminUsersPanel from './AdminUsersPanel.vue'

// 看板用到 echarts（体积大），改成切到该标签页时才异步加载
const AdminStatsPanel = defineAsyncComponent(() => import('./AdminStatsPanel.vue'))

/**
 * 管理端 · 歌曲管理（仅 ADMIN，路由守卫拦截）。
 * - 筛选：bandId / keyword（防抖 300ms），服务端分页
 * - 行内编辑：mvUrl / mvPlatform / audioUrl / tags / status → PATCH（null 不更新）
 * - 新建：弹窗表单 → POST；删除：两步确认 → DELETE（软删）
 * - 成功后就地更新行数据；错误红字提示
 */
const bands = ref([])
const rows = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const pageErr = ref('')

/* ---- 标签页：songs 歌曲 / events 演出 ---- */
const tab = ref('stats')

/* ---- 筛选 ---- */
const bandId = ref('')
const keyword = ref('')
let kwTimer = null
// 筛选变化 → 回第 1 页；改 page 时交给 watch(page)，避免双请求
function applyFilter() {
  if (page.value === 1) load()
  else page.value = 1
}

watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(applyFilter, 300)
})
watch(bandId, applyFilter)
watch(page, load)

async function load() {
  loading.value = true
  pageErr.value = ''
  try {
    const params = { page: page.value, pageSize }
    if (bandId.value) params.bandId = bandId.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const data = await listAdminSongs(params)
    rows.value = data.content
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    pageErr.value = e.message
  } finally {
    loading.value = false
  }
}

/* ---- 首页 Hero 背景视频管理（两支 MV，覆盖 /assets/band-{slot}.mp4） ---- */
const HERO_SLOTS = [
  { id: 'mygo', label: 'MyGO!!!!! MV（壱雫空）', path: '/assets/band-mygo.mp4' },
  { id: 'mujica', label: 'Ave Mujica MV（KiLLKiSS）', path: '/assets/band-mujica.mp4' },
]
const heroStatus = reactive({})
const heroUploading = reactive({})
const heroErr = reactive({})

// 就绪检测：GET 该路径，SPA fallback 会把缺失文件回成 200+text/html，
// 所以按 content-type 判断而非状态码
async function checkHero(slot) {
  const s = HERO_SLOTS.find((h) => h.id === slot)
  heroStatus[slot] = 'checking'
  try {
    const r = await fetch(s.path)
    heroStatus[slot] = (r.headers.get('content-type') || '').includes('video') ? 'ok' : 'missing'
  } catch {
    heroStatus[slot] = 'missing'
  }
}

async function uploadHero(slot, e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.type !== 'video/mp4') { heroErr[slot] = '只支持 MP4 格式'; return }
  if (file.size > 100 * 1024 * 1024) { heroErr[slot] = '文件超过 100MB'; return }
  heroErr[slot] = ''
  heroUploading[slot] = true
  try {
    await uploadHeroVideo(slot, file)
    heroErr[slot] = ''
  } catch (err) {
    heroErr[slot] = err.message
  } finally {
    heroUploading[slot] = false
    checkHero(slot)
  }
}

onMounted(async () => {
  load()
  try { bands.value = await listBands() } catch { /* 下拉失败不阻塞 */ }
  HERO_SLOTS.forEach((s) => checkHero(s.id))
})

const bandName = (bid) => bands.value.find((b) => b.id === bid)?.name || bid
const fmtDuration = (sec) => (sec ? `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` : '—')

/* ---- 行内编辑 ---- */
const editingId = ref(null)
const draft = ref({})
const rowErr = ref({})
const savingId = ref(null)

function startEdit(row) {
  editingId.value = row.id
  rowErr.value[row.id] = ''
  draft.value = {
    mvUrl: row.mvUrl || '',
    mvPlatform: row.mvPlatform || '',
    audioUrl: row.audioUrl || '',
    tags: row.tags || '',
    status: row.status ?? 1,
  }
}
function cancelEdit() { editingId.value = null }

async function saveRow(row) {
  savingId.value = row.id
  rowErr.value[row.id] = ''
  try {
    await updateAdminSong(row.id, {
      mvUrl: draft.value.mvUrl || null,
      mvPlatform: draft.value.mvPlatform || null,
      audioUrl: draft.value.audioUrl || null,
      tags: draft.value.tags || null,
      status: draft.value.status,
    })
    // 就地更新：空值发的是 null（后端不更新），本地同样保留旧值
    if (draft.value.mvUrl) row.mvUrl = draft.value.mvUrl
    if (draft.value.mvPlatform) row.mvPlatform = draft.value.mvPlatform
    if (draft.value.audioUrl) row.audioUrl = draft.value.audioUrl
    if (draft.value.tags) row.tags = draft.value.tags
    row.status = draft.value.status
    editingId.value = null
  } catch (e) {
    rowErr.value[row.id] = e.message
  } finally {
    savingId.value = null
  }
}

/* ---- 删除（两步确认） ---- */
const confirmId = ref(null)

async function removeRow(row) {
  if (confirmId.value !== row.id) { confirmId.value = row.id; return }
  rowErr.value[row.id] = ''
  try {
    await deleteAdminSong(row.id)
    rows.value = rows.value.filter((r) => r.id !== row.id)
    totalElements.value--
  } catch (e) {
    rowErr.value[row.id] = e.message
  } finally {
    confirmId.value = null
  }
}

/* ---- 新建弹窗 ---- */
const showCreate = ref(false)
const createErr = ref('')
const submitting = ref(false)
const albums = ref([])
const form = reactive(defaultForm())

function defaultForm() {
  return { title: '', bandId: '', albumId: '', trackNo: '', releaseDate: '', duration: '', coverUrl: '', audioUrl: '', mvUrl: '', mvPlatform: 'BILIBILI', tags: '', status: 1 }
}

// 选乐队 → 拉其专辑供归属选择
watch(() => form.bandId, async (bid) => {
  albums.value = []
  form.albumId = ''
  if (bid) { try { albums.value = await listAlbums(bid) } catch { /* 专辑拉取失败不阻塞 */ } }
})

function openCreate() {
  Object.assign(form, defaultForm())
  createErr.value = ''
  showCreate.value = true
}

async function submitCreate() {
  if (!form.title.trim() || !form.bandId) { createErr.value = '标题和乐队为必填项'; return }
  submitting.value = true
  createErr.value = ''
  try {
    await createAdminSong({
      title: form.title.trim(),
      bandId: Number(form.bandId),
      albumId: form.albumId ? Number(form.albumId) : null,
      trackNo: form.trackNo ? Number(form.trackNo) : null,
      releaseDate: form.releaseDate || null,
      duration: form.duration ? Number(form.duration) : null,
      coverUrl: form.coverUrl || null,
      audioUrl: form.audioUrl || null,
      mvUrl: form.mvUrl || null,
      mvPlatform: form.mvPlatform || null,
      tags: form.tags || null,
      status: form.status,
    })
    showCreate.value = false
    page.value = 1
    await load()
  } catch (e) {
    createErr.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wrap py-8">
    <!-- 标签页：歌曲管理 / 演出管理 -->
    <div class="mb-6 flex gap-1.5 border-b border-line pb-3">
      <button
        v-for="t in [{ id: 'stats', label: '数据看板' }, { id: 'songs', label: '歌曲管理' }, { id: 'events', label: '演出管理' }, { id: 'orders', label: '订单管理' }, { id: 'users', label: '用户管理' }}]"
        :key="t.id"
        class="cursor-pointer rounded-full px-4 py-1.5 text-sm font-medium transition-colors duration-200"
        :class="tab === t.id ? 'bg-primary text-white' : 'text-dim hover:text-ink'"
        @click="tab = t.id"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-show="tab === 'songs'">
    <!-- 头部 -->
    <div class="section-head">
      <div>
        <p class="micro-accent mb-1.5">ADMIN — SONGS</p>
        <h1 class="serif text-2xl font-semibold">歌曲管理</h1>
      </div>
      <button class="btn-primary" @click="openCreate">＋ 新建歌曲</button>
    </div>

    <!-- 筛选栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <select v-model="bandId" class="input max-w-40">
        <option value="">全部乐队</option>
        <option v-for="b in bands" :key="b.id" :value="b.id">{{ b.name }}</option>
      </select>
      <input v-model="keyword" type="search" placeholder="搜索标题…" class="input max-w-56">
      <p class="ml-auto text-sm text-faint tabular">共 {{ totalElements }} 首 · 第 {{ page }}/{{ totalPages }} 页</p>
    </div>

    <!-- 列表 -->
    <p v-if="pageErr" class="card mb-4 p-4 text-sm text-red-500">{{ pageErr }} <button class="ml-2 underline" @click="load">重试</button></p>

    <div v-if="loading" class="card p-4"><div class="h-64 animate-pulse rounded-lg bg-base"></div></div>

    <div v-else-if="rows.length" class="card overflow-x-auto">
      <table class="w-full min-w-[68rem] text-sm">
        <thead>
          <tr class="border-b border-line text-left text-xs text-faint">
            <th class="px-3 py-2.5 font-medium">封面</th>
            <th class="px-3 py-2.5 font-medium">标题 / ID</th>
            <th class="px-3 py-2.5 font-medium">乐队</th>
            <th class="px-3 py-2.5 font-medium">专辑 / 曲目</th>
            <th class="px-3 py-2.5 font-medium">发行 / 时长</th>
            <th class="px-3 py-2.5 font-medium">MV 链接</th>
            <th class="px-3 py-2.5 font-medium">平台</th>
            <th class="px-3 py-2.5 font-medium">音源</th>
            <th class="px-3 py-2.5 font-medium">标签</th>
            <th class="px-3 py-2.5 font-medium">状态</th>
            <th class="px-3 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in rows" :key="row.id">
            <tr class="border-b border-line align-middle transition hover:bg-elevated">
              <td class="px-3 py-2.5">
                <CoverArt :title="row.title" :band="row.bandId === 1 ? 'mygo' : row.bandId === 2 ? 'ave-mujica' : 'sumimi'" :cover-url="row.coverUrl || ''" class="h-10 w-10" rounded="rounded-md" />
              </td>
              <td class="max-w-40 px-3 py-2.5">
                <p class="truncate font-medium">{{ row.title }}</p>
                <p class="mono text-[10px] text-faint">#{{ row.id }}</p>
              </td>
              <td class="px-3 py-2.5 text-dim">{{ bandName(row.bandId) }}</td>
              <td class="max-w-36 px-3 py-2.5">
                <p class="truncate text-dim">{{ row.albumName || '—' }}</p>
                <p class="text-xs text-faint">{{ row.trackNo ? `Track ${row.trackNo}` : '' }}</p>
              </td>
              <td class="px-3 py-2.5">
                <p class="tabular text-dim">{{ row.releaseDate || '—' }}</p>
                <p class="tabular text-xs text-faint">{{ fmtDuration(row.duration) }}</p>
              </td>
              <!-- 可编辑五列：编辑态换输入控件 -->
              <td class="px-3 py-2.5">
                <input v-if="editingId === row.id" v-model="draft.mvUrl" class="input !py-1 !text-xs w-44" placeholder="B 站链接">
                <span v-else class="block max-w-44 truncate text-dim" :title="row.mvUrl">{{ row.mvUrl || '—' }}</span>
              </td>
              <td class="px-3 py-2.5">
                <select v-if="editingId === row.id" v-model="draft.mvPlatform" class="input !py-1 !text-xs w-28">
                  <option value="">无</option>
                  <option value="BILIBILI">BILIBILI</option>
                </select>
                <span v-else class="micro">{{ row.mvPlatform || '—' }}</span>
              </td>
              <td class="px-3 py-2.5">
                <input v-if="editingId === row.id" v-model="draft.audioUrl" class="input !py-1 !text-xs w-40" placeholder="音源外链">
                <span v-else class="block max-w-36 truncate text-dim" :title="row.audioUrl">{{ row.audioUrl ? '已配置' : '—' }}</span>
              </td>
              <td class="px-3 py-2.5">
                <input v-if="editingId === row.id" v-model="draft.tags" class="input !py-1 !text-xs w-28" placeholder="逗号分隔">
                <span v-else class="block max-w-28 truncate text-dim">{{ row.tags || '—' }}</span>
              </td>
              <td class="px-3 py-2.5">
                <select v-if="editingId === row.id" v-model="draft.status" class="input !py-1 !text-xs w-20">
                  <option :value="1">上架</option>
                  <option :value="0">下架</option>
                </select>
                <span v-else class="micro" :class="row.status === 1 ? '!text-green-700' : '!text-faint'">
                  {{ row.status === 1 ? '上架' : '下架' }}
                </span>
              </td>
              <td class="px-3 py-2.5">
                <div class="flex items-center gap-1.5 whitespace-nowrap">
                  <template v-if="editingId === row.id">
                    <button class="btn-primary !px-2.5 !py-1 !text-xs" :disabled="savingId === row.id" @click="saveRow(row)">
                      {{ savingId === row.id ? '保存中…' : '保存' }}
                    </button>
                    <button class="btn-ghost !px-2.5 !py-1 !text-xs" @click="cancelEdit">取消</button>
                  </template>
                  <template v-else>
                    <button class="btn-ghost !px-2.5 !py-1 !text-xs" @click="startEdit(row)">编辑</button>
                    <button
                      class="btn-ghost !px-2.5 !py-1 !text-xs"
                      :class="confirmId === row.id && '!border-red-300 !text-red-500'"
                      @click="removeRow(row)"
                    >
                      {{ confirmId === row.id ? '确认删除？' : '删除' }}
                    </button>
                  </template>
                </div>
                <p v-if="rowErr[row.id]" class="mt-1 max-w-40 text-xs text-red-500">{{ rowErr[row.id] }}</p>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <p v-else class="card p-6 text-center text-sm text-dim">没有匹配的歌曲</p>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-3">
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page <= 1" @click="page--">← 上一页</button>
      <span class="tabular text-xs text-faint">{{ page }} / {{ totalPages }}</span>
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page >= totalPages" @click="page++">下一页 →</button>
    </div>

    <!-- 首页 Hero 背景视频管理 -->
    <div class="card mt-8 p-5">
      <p class="micro-accent mb-1">ADMIN — HERO VIDEO</p>
      <h2 class="serif text-lg font-semibold">首页背景视频</h2>
      <p class="mt-1 text-xs text-faint">首页满屏 Hero 的两支背景 MV，上传 MP4 后立即覆盖生效（页面刷新可见）。</p>
      <div class="mt-4 grid gap-4 sm:grid-cols-2">
        <div v-for="s in HERO_SLOTS" :key="s.id" class="rounded-lg border border-line p-4">
          <div class="flex items-center justify-between gap-2">
            <p class="text-sm font-semibold">{{ s.label }}</p>
            <span
              class="micro"
              :class="heroStatus[s.id] === 'ok' ? '!text-green-700' : heroStatus[s.id] === 'missing' ? '!text-red-500' : ''"
            >
              {{ heroStatus[s.id] === 'ok' ? '✓ 已就位' : heroStatus[s.id] === 'missing' ? '✗ 缺失（回退 aurora）' : '检测中…' }}
            </span>
          </div>
          <input
            type="file"
            accept="video/mp4"
            class="mt-3 block w-full text-xs text-dim file:mr-3 file:cursor-pointer file:rounded-md file:border-0 file:bg-primary file:px-3 file:py-1.5 file:text-xs file:font-medium file:text-white"
            :disabled="heroUploading[s.id]"
            @change="uploadHero(s.id, $event)"
          >
          <p v-if="heroUploading[s.id]" class="mt-1.5 text-xs text-dim">上传中…</p>
          <p v-if="heroErr[s.id]" class="mt-1 text-xs text-red-500">{{ heroErr[s.id] }}</p>
        </div>
      </div>
    </div>

    </div>
    <!-- /歌曲管理标签页 -->

    <!-- 演出管理标签页 -->
    <AdminEventsPanel v-if="tab === 'events'" />

    <!-- 订单管理标签页 -->
    <AdminOrdersPanel v-if="tab === 'orders'" />

    <!-- 数据看板标签页 -->
    <AdminStatsPanel v-if="tab === 'stats'" />

    <!-- 用户管理标签页 -->
    <AdminUsersPanel v-if="tab === 'users'" />


    <!-- 新建弹窗 -->
    <Teleport to="body">
      <div v-if="showCreate" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="showCreate = false">
        <div class="card max-h-[90vh] w-full max-w-lg overflow-y-auto p-6">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="serif text-lg font-semibold">新建歌曲</h2>
            <button class="text-dim hover:text-ink" @click="showCreate = false">✕</button>
          </div>

          <form class="grid grid-cols-2 gap-3" @submit.prevent="submitCreate">
            <div class="col-span-2">
              <label class="mb-1 block text-xs font-medium text-dim">标题 *</label>
              <input v-model="form.title" type="text" class="input" placeholder="歌曲标题">
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">乐队 *</label>
              <select v-model="form.bandId" class="input">
                <option value="" disabled>选择乐队</option>
                <option v-for="b in bands" :key="b.id" :value="b.id">{{ b.name }}</option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">归属专辑</label>
              <select v-model="form.albumId" class="input" :disabled="!form.bandId">
                <option value="">不归属</option>
                <option v-for="a in albums" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">曲目号</label>
              <input v-model="form.trackNo" type="number" min="1" class="input" placeholder="选填">
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">发行日期</label>
              <input v-model="form.releaseDate" type="date" class="input">
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">时长（秒）</label>
              <input v-model="form.duration" type="number" min="0" class="input" placeholder="选填">
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">状态</label>
              <select v-model="form.status" class="input">
                <option :value="1">上架</option>
                <option :value="0">下架</option>
              </select>
            </div>
            <div class="col-span-2">
              <label class="mb-1 block text-xs font-medium text-dim">封面 URL</label>
              <input v-model="form.coverUrl" type="text" class="input" placeholder="/img/covers/… 或外链，留空用渐变">
            </div>
            <div class="col-span-2">
              <label class="mb-1 block text-xs font-medium text-dim">MV 链接（B 站）</label>
              <input v-model="form.mvUrl" type="text" class="input" placeholder="player.bilibili.com 链接，选填">
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">MV 平台</label>
              <select v-model="form.mvPlatform" class="input">
                <option value="">无</option>
                <option value="BILIBILI">BILIBILI</option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-dim">音源外链</label>
              <input v-model="form.audioUrl" type="text" class="input" placeholder="选填">
            </div>
            <div class="col-span-2">
              <label class="mb-1 block text-xs font-medium text-dim">标签</label>
              <input v-model="form.tags" type="text" class="input" placeholder="逗号分隔，如：燃,主题曲">
            </div>

            <p v-if="createErr" class="col-span-2 text-xs text-red-500">{{ createErr }}</p>
            <div class="col-span-2 mt-1 flex justify-end gap-2">
              <button type="button" class="btn-ghost" @click="showCreate = false">取消</button>
              <button type="submit" class="btn-primary" :disabled="submitting">
                {{ submitting ? '提交中…' : '创建' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>
