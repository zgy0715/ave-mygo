import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  // 本地开发读取现有素材；构建默认不打包，可用 1/0 显式开启或关闭。
  publicDir: process.env.INCLUDE_LOCAL_ASSETS === '1'
    || (command === 'serve' && process.env.INCLUDE_LOCAL_ASSETS !== '0') ? 'public' : false,
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
}))
