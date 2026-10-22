import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 配置（Vite 8 + Vue 3 项目）
 * - baseURL 走环境变量，默认本地 dev server 5173
 * - 用系统 Edge（channel: 'msedge'），免去下载 Chromium 内核
 * - 只跑 chromium 系：safari/firefox 需要额外内核，CI 里再开
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,          // 购票用例会写数据，串行更稳
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'off',
    screenshot: 'only-on-failure',
    viewport: { width: 1366, height: 900 },
  },
  projects: [
    {
      name: 'edge',
      use: { ...devices['Desktop Edge'], channel: 'msedge' },
    },
  ],
})
