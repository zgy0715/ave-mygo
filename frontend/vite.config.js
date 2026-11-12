import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  // 默认构建/开发不提供本地未授权素材；仅在确认拥有素材使用权后显式开启。
  publicDir: process.env.INCLUDE_LOCAL_ASSETS === '1' ? 'public' : false,
  plugins: [
    vue(),
    vueDevTools(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // Vitest：组件测试用 jsdom；用例放各目录 __tests__ 下
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/__tests__/**/*.spec.js'],
    restoreMocks: true,
  },
})
