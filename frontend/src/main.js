import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// v2 起不再引入 Lenis/GSAP：动效退回 CSS transition，
// 首屏性能与可维护性优先，等核心功能闭环后再按需引入。
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
