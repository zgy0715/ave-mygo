<script setup>
import { RouterLink } from 'vue-router'
/** 页脚：粉丝站免责声明是硬要求（需求 5 节红线） */
const year = new Date().getFullYear()
</script>

<template>
  <footer class="border-t border-line bg-surface">
    <div class="wrap flex flex-col gap-2 py-8 text-center sm:flex-row sm:items-center sm:justify-between sm:text-left">
      <div>
        <p class="text-sm font-semibold">BANDORI / LIVE</p>
        <p class="mt-0.5 text-xs text-faint">MyGO!!!!! × Ave Mujica 粉丝站</p>
      </div>
      <p class="max-w-md text-xs leading-relaxed text-faint">
        本站为粉丝自制的个人学习项目，与 BanG Dream! 项目及武士道官方无关。
        所有歌曲仅提供官方 MV / 试听外链，不托管任何音频文件。© {{ year }}
      </p>
    </div>
  </footer>
</template>
