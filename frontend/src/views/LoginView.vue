<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register } from '@/api/auth'

const router = useRouter()
const auth = useAuthStore()

// mode: login / register 双模式切换
const mode = ref('login')
const form = reactive({ username: '', password: '', nickname: '' })
const errMsg = ref('')
const submitting = ref(false)

async function submit() {
  errMsg.value = ''
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(form.username, form.password)
    } else {
      await register({ username: form.username, password: form.password, nickname: form.nickname || undefined })
      await auth.login(form.username, form.password)   // 注册成功自动登录
    }
    router.push('/')                                    // 回首页
  } catch (e) {
    errMsg.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex max-w-md flex-col items-center px-4 py-16">
    <div class="w-full rounded-xl border border-line bg-surface p-8">
      <h1 class="mb-1 text-center text-xl font-bold">{{ mode === 'login' ? '欢迎回来' : '加入 Bandori Live' }}</h1>
      <p class="mb-6 text-center text-sm text-gray-500">{{ mode === 'login' ? '登录后可购票、收藏与点歌' : '注册一个账号，去看第一场 Live' }}</p>

      <form class="space-y-4" @submit.prevent="submit">
        <input
          v-model.trim="form.username"
          placeholder="用户名（3~32位字母/数字/下划线）"
          class="w-full rounded-lg border border-line bg-elevated px-3 py-2.5 text-sm outline-none focus:border-primary"
          required
        />
        <input
          v-model.trim="form.password"
          type="password"
          placeholder="密码（6~32位，含字母和数字）"
          class="w-full rounded-lg border border-line bg-elevated px-3 py-2.5 text-sm outline-none focus:border-primary"
          required
        />
        <input
          v-if="mode === 'register'"
          v-model.trim="form.nickname"
          placeholder="昵称（选填）"
          class="w-full rounded-lg border border-line bg-elevated px-3 py-2.5 text-sm outline-none focus:border-primary"
        />

        <p v-if="errMsg" class="text-sm text-accent">{{ errMsg }}</p>

        <button
          type="submit"
          :disabled="submitting"
          class="w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-white hover:bg-violet-500 disabled:opacity-50"
        >
          {{ submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="mt-4 text-center text-xs text-gray-500">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <button class="text-primary hover:underline" @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '去注册' : '去登录' }}
        </button>
      </p>
    </div>
  </main>
</template>
