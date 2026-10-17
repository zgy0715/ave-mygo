<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 登录 / 注册（双 Tab 一页）。
 * - 支持登录后跳回来源页（走查缺口②）：?redirect=/xxx
 * - 后端未就绪：本地 mock 登录（任意非空输入即通过），接口就绪后换 authStore.login 真调用
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref(route.query.mode === 'register' ? 'register' : 'login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')

function submit() {
  if (!username.value.trim() || !password.value.trim()) {
    error.value = '用户名和密码不能为空'
    return
  }
  error.value = ''
  // mock：直接发 token；真实实现 = auth.login() 调 POST /auth/login
  auth.token = 'mock-token-' + Date.now()
  auth.user = { username: username.value.trim(), nickname: nickname.value.trim() || username.value.trim(), role: 'USER' }
  sessionStorage.setItem('token', auth.token)
  sessionStorage.setItem('user', JSON.stringify(auth.user))
  router.push(route.query.redirect || '/')
}
</script>

<template>
  <div class="wrap flex justify-center py-14">
    <div class="card w-full max-w-sm p-7">
      <!-- Tab 切换 -->
      <div class="mb-6 grid grid-cols-2 rounded-lg bg-base p-1 text-sm font-medium">
        <button class="rounded-md py-2 transition" :class="mode === 'login' ? 'bg-surface text-ink shadow-sm' : 'text-dim'" @click="mode = 'login'">登录</button>
        <button class="rounded-md py-2 transition" :class="mode === 'register' ? 'bg-surface text-ink shadow-sm' : 'text-dim'" @click="mode = 'register'">注册</button>
      </div>

      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="mb-1 block text-xs font-medium text-dim">用户名</label>
          <input v-model="username" type="text" class="input" placeholder="字母 / 数字组合">
        </div>
        <div v-if="mode === 'register'">
          <label class="mb-1 block text-xs font-medium text-dim">昵称</label>
          <input v-model="nickname" type="text" class="input" placeholder="展示在订单与评论中的名字">
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-dim">密码</label>
          <input v-model="password" type="password" class="input" placeholder="至少 8 位">
        </div>
        <p v-if="error" class="text-xs text-red-500">{{ error }}</p>
        <button type="submit" class="btn-primary w-full">
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="mt-4 text-center text-xs text-faint">演示环境：任意输入即可登录 · 后端就绪后接入 JWT</p>
    </div>
  </div>
</template>
