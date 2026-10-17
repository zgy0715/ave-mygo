<script setup>
/**
 * 应用外壳：顶部导航 + 路由视图 + 全局播放条 + 页脚。
 * - grain：全站噪点纹理层（浅底纸感）
 * - RouterView 包 <Transition name="page">：路由切换淡入上移
 */
import NavBar from '@/components/NavBar.vue'
import SideMenu from '@/components/SideMenu.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import PlayerBar from '@/components/PlayerBar.vue'
</script>

<template>
  <div class="grain flex min-h-screen flex-col">
    <NavBar />
    <SideMenu />
    <!-- pb-28 给底部播放条留位，避免遮内容 -->
    <main class="flex-1 pb-28">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
    <SiteFooter />
    <PlayerBar />
  </div>
</template>
