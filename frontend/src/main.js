import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Lenis from 'lenis'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import App from './App.vue'
import router from './router'

// ===== 获奖站标配动效栈：Lenis 平滑滚动 + GSAP 滚动编排 =====
gsap.registerPlugin(ScrollTrigger)

// 尊重系统"减少动态效果"设置：关闭平滑滚动
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
if (!prefersReducedMotion) {
  const lenis = new Lenis({ duration: 1.2 })
  lenis.on('scroll', ScrollTrigger.update)
  gsap.ticker.add((time) => lenis.raf(time * 1000))
  gsap.ticker.lagSmoothing(0)
  window.__lenis = lenis
}

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
