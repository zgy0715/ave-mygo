<script setup>
import { RouterLink } from 'vue-router'

/**
 * 页脚：品牌区 + 外链矩阵 + 免责声明。
 * 外链样式 = 参考站（Mujica 官网 / anontokyo / 个人站）的「文字 + ↗」语言：
 * mono 大写、细线分隔、hover 时箭头向右上探出（见 main.css .link-ext）。
 */
const year = new Date().getFullYear()

const externalLinks = [
  { label: 'MyGO!!!!! OFFICIAL', href: 'https://mygo-org.bang-dream.com/' },
  { label: 'AVE MUJICA OFFICIAL', href: 'https://avemujica.bang-dream.com/' },
  { label: '劇場版 prima aurora', href: 'https://avemujica-movie.bang-dream.com/' },
  { label: 'BESTDORI!', href: 'https://bestdori.com/' },
  { label: 'AUTHOR ↝ ZGY0715', href: 'https://zgy0715.github.io/' },
]
</script>

<template>
  <footer class="border-t border-line bg-surface">
    <div class="wrap py-10">
      <div class="flex flex-col gap-8 md:flex-row md:items-start md:justify-between">
        <!-- 品牌 -->
        <div>
          <p class="serif text-lg font-semibold">Bandori <span class="micro-accent !text-[9px]">/ LIVE</span></p>
          <p class="mt-2 max-w-sm text-xs leading-relaxed text-faint">
            MyGO!!!!! × Ave Mujica 粉丝自制站点。本站与 BanG Dream! 项目及武士道官方无关；
            所有歌曲仅提供官方 MV / 试听外链，不托管任何音频文件。
          </p>
        </div>

        <!-- 外链矩阵：mono 大写 + ↗ 探出 -->
        <nav class="grid grid-cols-1 gap-x-10 gap-y-0 sm:grid-cols-2" aria-label="外部链接">
          <a
            v-for="l in externalLinks"
            :key="l.href"
            :href="l.href"
            target="_blank"
            rel="noopener noreferrer"
            class="link-ext border-b border-line py-2.5 text-xs tracking-[0.14em]"
          >
            {{ l.label }} <span class="arrow">↗</span>
          </a>
        </nav>
      </div>

      <div class="mt-8 flex items-center justify-between border-t border-line pt-5">
        <p class="micro">© {{ year }} BANDORI / LIVE — FAN PROJECT</p>
        <p class="micro">BUILT WITH ♥ &amp; VUE 3</p>
      </div>
    </div>
  </footer>
</template>
