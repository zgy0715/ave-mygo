<script setup>
import { RouterLink } from 'vue-router'

/**
 * 页脚 v4.5：anontokyo 式 CONTACT 区——
 * 整块主色底，居中大字标题 + 一排纯白实心品牌图标 + 版权行。
 * 图标为实心 fill（挖空部分用主色回填），hover 上浮微透。
 */
const year = new Date().getFullYear()

/** 实心图标库：24 viewBox，fill currentColor；挖空细节用主色（footer 同底色） */
const icons = {
  bilibili:
    '<path d="M8.9 5.6 6.6 2.9a.9.9 0 0 1 1.4-1.2l2.6 3.1h2.8l2.6-3.1a.9.9 0 0 1 1.4 1.2l-2.3 2.7H19A3.4 3.4 0 0 1 22.4 9v8.6A3.4 3.4 0 0 1 19 21H5a3.4 3.4 0 0 1-3.4-3.4V9A3.4 3.4 0 0 1 5 5.6h3.9z"/><rect x="7.3" y="10.8" width="2.3" height="4.4" rx="1.15" fill="var(--color-primary)"/><rect x="14.4" y="10.8" width="2.3" height="4.4" rx="1.15" fill="var(--color-primary)"/>',
  douyin:
    '<path d="M16.7 2.8c.4 2.7 2 4.4 4.5 4.7v3.1c-1.7 0-3.2-.5-4.5-1.4v5.7a5.8 5.8 0 1 1-5.8-5.8c.34 0 .67.03 1 .1v3.2a2.7 2.7 0 1 0 1.9 2.5V2.8h2.9z"/>',
  mygo:
    '<circle cx="12" cy="12" r="9.2"/><g fill="none" stroke="var(--color-primary)" stroke-width="1.5"><path d="M3.2 12h17.6"/><ellipse cx="12" cy="12" rx="4.2" ry="9.2"/></g>',
  mujica:
    '<path d="M14.9 2.9A9.6 9.6 0 1 0 21.4 15 8 8 0 0 1 14.9 2.9z"/>',
  movie:
    '<rect x="2.4" y="4.8" width="19.2" height="14.4" rx="2.6"/><path d="M10 9v6l5.4-3z" fill="var(--color-primary)"/>',
  bestdori:
    '<path d="M12 2.6l2.8 5.9 6.4.8-4.7 4.4 1.3 6.4L12 17l-5.8 3.1 1.3-6.4L2.8 9.3l6.4-.8z"/>',
  author:
    '<path d="M12 2.9 2.7 11.2a1.05 1.05 0 0 0 1.4 1.6l.9-.8V19a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7l.9.8a1.05 1.05 0 0 0 1.4-1.6L12 2.9z"/><path d="M9.9 21v-5.6h4.2V21" fill="var(--color-primary)"/>',
}

const externalLinks = [
  { label: '哔哩哔哩 BILIBILI', href: 'https://space.bilibili.com/531846477', icon: 'bilibili' },
  { label: '抖音 DOUYIN', href: 'https://www.douyin.com/user/MS4wLjABAAAA6dxS4T4bSlZKc5WaiQ55DJ7IShZuouv5EeV2O', icon: 'douyin' },
  { label: 'MyGO!!!!! 官网', href: 'https://mygo-org.bang-dream.com/', icon: 'mygo' },
  { label: 'Ave Mujica 官网', href: 'https://avemujica.bang-dream.com/', icon: 'mujica' },
  { label: '劇場版 prima aurora', href: 'https://avemujica-movie.bang-dream.com/', icon: 'movie' },
  { label: 'Bestdori!', href: 'https://bestdori.com/', icon: 'bestdori' },
  { label: '站长个人站 ZGY0715', href: 'https://zgy0715.github.io/', icon: 'author' },
]
</script>

<template>
  <footer class="bg-primary text-white">
    <div class="wrap py-14 text-center">
      <p class="display text-4xl tracking-[0.12em] sm:text-5xl">LINKS</p>

      <!-- 图标行：实心白，hover 上浮微透 -->
      <nav class="mt-8 flex flex-wrap items-center justify-center gap-x-8 gap-y-5 sm:gap-x-10" aria-label="外部链接">
        <a
          v-for="l in externalLinks"
          :key="l.href"
          :href="l.href"
          :title="l.label"
          :aria-label="l.label"
          target="_blank"
          rel="noopener noreferrer"
          class="text-white opacity-95 transition-all duration-300 hover:-translate-y-1 hover:opacity-70"
          style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)"
        >
          <svg viewBox="0 0 24 24" class="h-7 w-7 sm:h-8 sm:w-8" fill="currentColor" v-html="icons[l.icon]" />
        </a>
      </nav>

      <!-- 版权 + 免责 -->
      <p class="display mt-10 text-sm tracking-[0.18em] sm:text-base">
        COPYRIGHT © {{ year }} BANDORI / LIVE. ALL RIGHTS RESERVED.
      </p>
      <p class="micro mt-3 !text-white/60">
        粉丝自制站点 · 与 BAN!DREAM! 项目及武士道官方无关 · 不托管任何音频文件
      </p>
      <p class="micro mt-1.5 !text-white/40">POWERED BY VUE 3 · <RouterLink to="/" class="hover:!text-white/80">BACK TO TOP ↑</RouterLink></p>
    </div>
  </footer>
</template>
