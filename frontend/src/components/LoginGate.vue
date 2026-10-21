<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dismissGate } from '@/utils/loginGate'

/**
 * 登录引导弹窗：游客做需要登录的操作（购票/收藏/评论）时弹出，
 * 给出「去登录（带 redirect 回当前页）」和「随便逛逛」两个出口。
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  action: { type: String, default: '继续操作' },   // 购票 / 收藏 / 评论
})
const emit = defineEmits(['update:open'])

const route = useRoute()
const router = useRouter()

const hint = computed(() => ({
  购票: '登录后即可选座下单、查看订单与倒计时支付。',
  收藏: '登录后即可收藏歌曲与演出，随时找回。',
  歌单: '登录后即可创建歌单，把喜欢的歌收在一起。',
  评论: '登录后即可参与评论，和大家一起聊歌。',
}[props.action] || '登录后即可使用该功能。'))

function close() {
  emit('update:open', false)
}

function goLogin() {
  close()
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function browse() {
  dismissGate()   // 本次会话不再提醒
  close()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/45 p-4" @click.self="close">
      <div class="card w-full max-w-sm p-6 text-center">
        <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 15v2m-6 4h12a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2zM8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
        </div>
        <h2 class="serif mt-4 text-lg font-semibold">登录后即可{{ action }}</h2>
        <p class="mt-1.5 text-sm text-dim">{{ hint }}</p>

        <div class="mt-6 space-y-2">
          <button class="btn-primary w-full" @click="goLogin">去登录</button>
          <button class="btn-ghost w-full" @click="browse">随便逛逛</button>
        </div>
        <button class="mt-4 cursor-pointer text-xs text-faint transition hover:text-dim" @click="close">先不用，关闭</button>
      </div>
    </div>
  </Teleport>
</template>
