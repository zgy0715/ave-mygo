<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listAdminUsers, updateAdminUserStatus } from '@/api/admin'

/**
 * 管理端 · 用户管理
 * 接口：GET /admin/users?keyword&page&pageSize → Page<UserView>
 *      PATCH /admin/users/{id}/status {status: 0|1}（0 封禁 / 1 正常）
 * 规则：不能封禁自己（后端 40301，前端先置灰）
 */
const auth = useAuthStore()
const keyword = ref('')
const page = ref(1)
const pageSize = 15
const rows = ref([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const errMsg = ref('')
const actingId = ref(0)
const confirmTarget = ref(null)
const toast = ref('')
let toastTimer = null

const selfId = computed(() => Number(auth.user?.id || 0))

let seq = 0
async function load() {
  const my = ++seq
  loading.value = true
  errMsg.value = ''
  try {
    const params = { page: page.value, pageSize }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const data = await listAdminUsers(params)
    if (my !== seq) return
    rows.value = data.content
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    if (my === seq) errMsg.value = e.message
  } finally {
    if (my === seq) loading.value = false
  }
}
onMounted(load)
watch(page, load)

let kwTimer = null
watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => { page.value = 1; page.value === 1 ? load() : null }, 300)
})

function showToast(m) {
  toast.value = m
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2400)
}
onBeforeUnmount(() => clearTimeout(toastTimer))

async function toggleStatus(u) {
  actingId.value = u.id
  const next = u.status === 1 ? 0 : 1
  try {
    const updated = await updateAdminUserStatus(u.id, next)
    const i = rows.value.findIndex((r) => r.id === u.id)
    if (i >= 0) rows.value[i] = { ...rows.value[i], ...updated, status: next }
    showToast(next === 0 ? `已封禁 @${u.username}` : `已解封 @${u.username}`)
  } catch (e) {
    showToast(e.message || '操作失败')
  } finally {
    actingId.value = 0
    confirmTarget.value = null
  }
}

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '—')
const fmtMoney = (n) => `¥${Number(n || 0).toFixed(2)}`
const isSelf = (u) => u.id === selfId.value
</script>

<template>
  <div>
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <input v-model="keyword" type="search" class="input !w-60 !py-1.5 !text-xs" placeholder="搜索用户名 / 昵称">
      <p class="ml-auto text-sm text-faint tabular">共 {{ totalElements }} 位用户</p>
    </div>

    <p v-if="errMsg" class="card mb-4 p-4 text-sm text-red-500">{{ errMsg }} <button class="ml-2 underline" @click="load">重试</button></p>
    <div v-if="loading" class="card p-4"><div class="h-56 animate-pulse rounded-lg bg-base"></div></div>

    <div v-else-if="rows.length" class="card overflow-x-auto">
      <table class="w-full min-w-[52rem] text-sm">
        <thead>
          <tr class="border-b border-line text-left text-xs text-faint">
            <th class="px-3 py-2.5 font-medium">ID</th>
            <th class="px-3 py-2.5 font-medium">用户名</th>
            <th class="px-3 py-2.5 font-medium">昵称</th>
            <th class="px-3 py-2.5 font-medium">角色</th>
            <th class="px-3 py-2.5 font-medium">状态</th>
            <th class="px-3 py-2.5 font-medium">订单数</th>
            <th class="px-3 py-2.5 font-medium">消费额</th>
            <th class="px-3 py-2.5 font-medium">注册时间</th>
            <th class="px-3 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in rows" :key="u.id" class="border-b border-line last:border-0">
            <td class="tabular px-3 py-2.5 text-xs text-faint">{{ u.id }}</td>
            <td class="px-3 py-2.5 font-medium">
              {{ u.username }}
              <span v-if="isSelf(u)" class="ml-1 rounded bg-primary/10 px-1 py-0.5 text-[10px] text-primary">你</span>
            </td>
            <td class="max-w-40 truncate px-3 py-2.5 text-dim">{{ u.nickname || '—' }}</td>
            <td class="px-3 py-2.5">
              <span
                class="rounded px-1.5 py-0.5 text-[11px] font-medium"
                :class="u.role === 'ADMIN' ? 'bg-primary/10 text-primary' : 'bg-base text-dim'"
              >{{ u.role === 'ADMIN' ? '管理员' : '用户' }}</span>
            </td>
            <td class="px-3 py-2.5">
              <span
                class="rounded border px-1.5 py-0.5 text-[11px]"
                :class="u.status === 1 ? 'border-green-200 bg-green-50 text-green-700' : 'border-red-200 bg-red-50 text-red-500'"
              >{{ u.status === 1 ? '正常' : '已封禁' }}</span>
            </td>
            <td class="tabular px-3 py-2.5">{{ u.orderCount }}</td>
            <td class="tabular px-3 py-2.5">{{ fmtMoney(u.totalSpent) }}</td>
            <td class="tabular px-3 py-2.5 text-xs text-dim">{{ fmtTime(u.createdAt) }}</td>
            <td class="px-3 py-2.5">
              <button
                class="btn-ghost !px-2.5 !py-1 !text-xs"
                :class="u.status === 1 && 'hover:!border-red-300 hover:!text-red-500'"
                :disabled="isSelf(u) || actingId === u.id"
                :title="isSelf(u) ? '不能封禁自己' : (u.status === 1 ? '封禁该用户' : '解封该用户')"
                @click="confirmTarget = u"
              >
                {{ actingId === u.id ? '处理中…' : (u.status === 1 ? '封禁' : '解封') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else class="card p-6 text-center text-sm text-dim">没有匹配的用户</p>

    <div v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-3">
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page <= 1" @click="page--">← 上一页</button>
      <span class="tabular text-xs text-faint">{{ page }} / {{ totalPages }}</span>
      <button class="btn-ghost !px-3 !py-1.5 !text-xs" :disabled="page >= totalPages" @click="page++">下一页 →</button>
    </div>

    <!-- 二次确认 -->
    <Teleport to="body">
      <div v-if="confirmTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" @click.self="confirmTarget = null">
        <div class="card w-full max-w-sm p-6 text-center">
          <p class="text-sm">
            确定{{ confirmTarget.status === 1 ? '封禁' : '解封' }}用户
            <span class="font-semibold">@{{ confirmTarget.username }}</span>？
          </p>
          <p class="mt-1 text-xs text-faint">
            {{ confirmTarget.status === 1 ? '封禁后该用户将无法登录、下单与收藏' : '解封后该用户可正常登录使用' }}
          </p>
          <div class="mt-5 flex justify-center gap-2">
            <button class="btn-ghost" @click="confirmTarget = null">取消</button>
            <button
              class="btn-primary"
              :class="confirmTarget.status === 1 && '!bg-red-500 hover:!bg-red-600'"
              :disabled="actingId === confirmTarget.id"
              @click="toggleStatus(confirmTarget)"
            >确认{{ confirmTarget.status === 1 ? '封禁' : '解封' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <p v-if="toast" class="fixed bottom-8 left-1/2 z-[60] -translate-x-1/2 rounded-lg border border-line bg-surface px-4 py-2 text-xs shadow-lg">{{ toast }}</p>
  </div>
</template>
