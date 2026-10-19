import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Lenis from 'lenis'

import App from './App.vue'
import router from './router'

/**
 * Lenis 平滑滚动：iOS 般惯性质感（依赖已在 package.json）。
 * 尊重系统「减少动态效果」设置。
 */
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
if (!prefersReducedMotion) {
  const lenis = new Lenis({
    duration: 1.1,
    easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)), // easeOutExpo：先快后缓的苹果味
  })
  const raf = (time) => {
    lenis.raf(time)
    requestAnimationFrame(raf)
  }
  requestAnimationFrame(raf)
  // 暴露给 router：页面切换时同步重置 Lenis 内部滚动目标，否则会被拉回旧位置
  window.__lenis = lenis
}

/**
 * v-reveal 滚动入场指令：
 *   <div v-reveal>            → 进入视口时浮现
 *   <div v-reveal="150">      → 延迟 150ms（做卡片交错节奏）
 * 实现：IntersectionObserver 打标 + CSS 过渡（见 main.css .reveal）。
 * 只在支持 IO 的浏览器启用；触发一次后停止观察，零运行时开销。
 */
const revealObserver = new IntersectionObserver(
  (entries) => {
    for (const e of entries) {
      if (e.isIntersecting) {
        e.target.classList.add('is-inview')
        revealObserver.unobserve(e.target)
      }
    }
  },
  { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
)

const app = createApp(App)

app.directive('reveal', {
  mounted(el, binding) {
    if (prefersReducedMotion) return
    el.classList.add('reveal')
    if (binding.value) el.style.setProperty('--reveal-delay', `${binding.value}ms`)
    revealObserver.observe(el)
  },
})

app.use(createPinia())
app.use(router)

app.mount('#app')
