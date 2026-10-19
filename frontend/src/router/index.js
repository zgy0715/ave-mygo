import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// meta.title：浏览器标签/历史条目的可读名称（afterEach 统一写入 document.title）
const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '首页' } },
  { path: '/songs', name: 'songs', component: () => import('@/views/SongsView.vue'), meta: { title: '歌曲库' } },
  { path: '/songs/:id', name: 'song-detail', component: () => import('@/views/SongDetailView.vue'), meta: { title: '歌曲详情' } },
  { path: '/bands', name: 'bands', component: () => import('@/views/BandsView.vue'), meta: { title: '乐队' } },
  { path: '/bands/:id', name: 'band', component: () => import('@/views/BandView.vue'), meta: { title: '乐队主页' } },
  { path: '/events', name: 'events', component: () => import('@/views/EventsView.vue'), meta: { title: '演出日程' } },
  { path: '/events/:id', name: 'event-detail', component: () => import('@/views/EventDetailView.vue'), meta: { title: '演出详情' } },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录 / 注册' } },
  { path: '/orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { title: '我的订单', requiresAuth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人中心', requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { title: '歌曲管理', requiresAuth: true, requiresAdmin: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // 滚动行为：新页面回顶部，浏览器前进/后退恢复原位置。
  // Lenis 接管了滚动，必须同步重置它的内部目标，否则导航后会被拉回旧位置。
  scrollBehavior(to, from, savedPosition) {
    const target = savedPosition?.top ?? 0
    window.__lenis?.scrollTo(target, { immediate: true, force: true })
    return { top: target }
  },
})

// 登录守卫：无 token 跳 /login 并带 redirect，登录成功后回原页（需求走查缺口②）
// 管理守卫：requiresAdmin 且非 ADMIN → 回首页（前端侧拦截，后端接口另有双重校验）
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
    return { path: '/' }
  }
})

// 页面标题：历史记录里每一跳都可辨认
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} — Bandori / LIVE` : 'Bandori / LIVE'
})

export default router
