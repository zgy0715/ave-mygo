<script setup>
import { RouterLink } from 'vue-router'

/**
 * 页脚 v4.4：品牌区 + 外链芯片矩阵 + 免责声明。
 * 每条外链 = 线性 SVG 图标芯片（手绘 path，零图标库依赖），
 * hover 上浮 + 描边/图标变主色（.link-chip 见 main.css）。
 */
const year = new Date().getFullYear()

/** 线性图标库：24 viewBox / stroke currentColor，注入 <svg v-html> */
const icons = {
  // B 站小电视：双天线 + 机身 + 双眼
  bilibili:
    '<path d="M8 6 5.5 3M16 6l2.5-3" stroke-linecap="round"/><rect x="3" y="6" width="18" height="13" rx="3.2"/><path d="M8.6 11v3M15.4 11v3" stroke-linecap="round"/>',
  // 抖音音符：符干 + 附杆弧线 + 符头圆
  douyin:
    '<path d="M13.8 4v10.6a3.6 3.6 0 1 1-2.6-3.46" stroke-linecap="round"/><path d="M13.8 4c.35 2.8 2.5 4.9 5.2 5.2" stroke-linecap="round"/>',
  // 官网：地球
  globe:
    '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/><path d="M12 3.5c2.5 2.4 3.9 5.3 3.9 8.5s-1.4 6.1-3.9 8.5c-2.5-2.4-3.9-5.3-3.9-8.5s1.4-6.1 3.9-8.5z"/>',
  // 剧场版：放映幕布 + 播放
  film:
    '<rect x="3" y="4.5" width="18" height="15" rx="2.5"/><path d="M10 9.2v5.6l4.8-2.8z" stroke-linejoin="round"/>',
  // Bestdori：五角星
  star:
    '<path d="M12 3.5l2.5 5.4 5.9.7-4.4 4 1.2 5.9L12 16.6l-5.2 2.9 1.2-5.9-4.4-4 5.9-.7z" stroke-linejoin="round"/>',
  // 个人站：小房子
  home:
    '<path d="M4 11.2 12 4l8 7.2" stroke-linecap="round" stroke-linejoin="round"/><path d="M6.2 9.8V20h11.6V9.8"/>',
}

const externalLinks = [
  { label: 'BILIBILI · 哔哩哔哩', href: 'https://space.bilibili.com/531846477', icon: 'bilibili' },
  { label: 'DOUYIN · 抖音', href: 'https://www.douyin.com/user/MS4wLjABAAAA6dxS4T4bSlZKc5WaiQ55DJ7IShZuouv5EeV2O', icon: 'douyin' },
  { label: 'MYGO!!!!! OFFICIAL', href: 'https://mygo-org.bang-dream.com/', icon: 'globe' },
  { label: 'AVE MUJICA OFFICIAL', href: 'https://avemujica.bang-dream.com/', icon: 'globe' },
  { label: '劇場版 PRIMA AURORA', href: 'https://avemujica-movie.bang-dream.com/', icon: 'film' },
  { label: 'BESTDORI!', href: 'https://bestdori.com/', icon: 'star' },
  { label: 'AUTHOR · ZGY0715', href: 'https://zgy0715.github.io/', icon: 'home' },
]
</script>

<template>
  <footer class="border-t border-line bg-surface">
    <div class="wrap py-10">
      <div class="flex flex-col gap-8 lg:flex-row lg:items-start lg:justify-between">
        <!-- 品牌与免责 -->
        <div class="max-w-sm">
          <p class="serif text-lg font-semibold">Bandori <span class="micro-accent !text-[9px]">/ LIVE</span></p>
          <p class="mt-2 text-xs leading-relaxed text-faint">
            MyGO!!!!! × Ave Mujica 粉丝自制站点。本站与 BanG Dream! 项目及武士道官方无关；
            所有歌曲仅提供官方 MV / 试听外链，不托管任何音频文件。
          </p>
        </div>

        <!-- 外链芯片矩阵：图标 + mono 标签 + ↗ -->
        <nav class="grid w-full max-w-xl grid-cols-2 gap-2.5 sm:grid-cols-3" aria-label="外部链接">
          <a
            v-for="l in externalLinks"
            :key="l.href"
            :href="l.href"
            target="_blank"
            rel="noopener noreferrer"
            class="link-chip"
            :title="l.label"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" v-html="icons[l.icon]" />
            <span class="min-w-0 flex-1 truncate text-[11px] font-medium tracking-[0.08em]">{{ l.label }}</span>
            <span class="arrow text-[10px] text-faint transition-transform duration-300" style="transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1)">↗</span>
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
