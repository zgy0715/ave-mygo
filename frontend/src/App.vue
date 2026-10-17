<script setup>
/**
 * 应用外壳：顶部导航 + 路由视图 + 全局播放条 + 页脚。
 * v2 结构调整：不再把业务逻辑放 App.vue，只做布局编排。
 */
import NavBar from '@/components/NavBar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import PlayerBar from '@/components/PlayerBar.vue'
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <NavBar />
    <!-- pb-28 给底部播放条留位，避免遮内容 -->
    <main class="flex-1 pb-28">
      <RouterView />
    </main>
    <SiteFooter />
    <PlayerBar />
  </div>
</template>
