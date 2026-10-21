<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageBack from '@/components/PageBack.vue'
import { register as apiRegister } from '@/api/auth'

/**
 * 登录 / 注册（双 Tab 一页）——真接口版。
 * - 支持登录后跳回来源页：?redirect=/xxx
 * - 后端契约：POST /auth/login → {token, expiresIn, user:{userId,username,nickname,role}}
 *            POST /auth/register → 业务错误码经 request.js 统一转成 Error(message)
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref(route.query.mode === 'register' ? 'register' : 'login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value.trim()) {
    error.value = '用户名和密码不能为空'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(username.value.trim(), password.value)
    } else {
      // 注册成功后自动登录（体验惯例，省一次输入）
      await apiRegister({
        username: username.value.trim(),
        password: password.value,
        nickname: nickname.value.trim() || undefined,
      })
      await auth.login(username.value.trim(), password.value)
    }
    // redirect 只接受站内路径（防 /\/evil.com 之类的开放跳转）；非法值回首页
    const raw = route.query.redirect
    const redirect = typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/'
    router.push(redirect)
  } catch (e) {
    // request.js 已把后端 message（如"用户名已存在"）转成可读 Error
    error.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wrap flex justify-center py-14">
    <div class="w-full max-w-sm">
      <PageBack label="返回" fallback="/" />
      <div class="card p-7">
      <!-- Tab 切换 -->
      <div class="mb-6 grid grid-cols-2 rounded-lg bg-base p-1 text-sm font-medium">
        <button class="rounded-md py-2 transition" :class="mode === 'login' ? 'bg-surface text-ink shadow-sm' : 'text-dim'" @click="mode = 'login'">登录</button>
        <button class="rounded-md py-2 transition" :class="mode === 'register' ? 'bg-surface text-ink shadow-sm' : 'text-dim'" @click="mode = 'register'">注册</button>
      </div>

      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="mb-1 block text-xs font-medium text-dim">用户名</label>
          <input v-model="username" type="text" class="input" placeholder="3~32 位字母 / 数字 / 下划线">
        </div>
        <div v-if="mode === 'register'">
          <label class="mb-1 block text-xs font-medium text-dim">昵称</label>
          <input v-model="nickname" type="text" class="input" placeholder="展示在订单与评论中的名字">
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-dim">密码</label>
          <input v-model="password" type="password" class="input" :placeholder="mode === 'register' ? '8~32 位，含字母和数字' : '至少 8 位'">
        </div>
        <p v-if="error" class="text-xs text-red-500">{{ error }}</p>
        <button type="submit" class="btn-primary w-full" :disabled="submitting">
          {{ submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="mt-4 text-center text-xs text-faint">已接入 JWT 认证 · 游客可浏览，登录后可购票</p>
      </div>
    </div>
  </div>
</template>
