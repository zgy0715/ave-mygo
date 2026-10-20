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

/**
 * 滚动恢复：浏览器前进/后退（popstate）时 savedPosition 才有值。
 * 关键坑：目标页数据是异步拉的，恢复那一刻页面可能只有骨架屏 → 高度不够 →
 * 目标位置被 clamp 成 0（表现为「返回后跳到顶部」）。所以这里只记录目标，
 * 真正的恢复放到 afterEach 里带重试地执行，等布局高度长出来再落位。
 */
let pendingRestoreTop = null

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    pendingRestoreTop = savedPosition?.top ?? null
    if (savedPosition) return savedPosition   // 先让路由落一次位（能成最好）
    return { top: 0 }                          // 新导航：回顶部
  },
})

/** 重试恢复：高度不足时连续尝试，直到到位或超时（~1.2s） */
function restoreScroll(target) {
  let tries = 0
  const apply = () => {
    window.__lenis?.scrollTo(target, { immediate: true, force: true })
    window.scrollTo({ top: target })
    // 被 clamp（高度不够）时重试；到位即停
    if (Math.abs(window.scrollY - target) > 4 && tries++ < 15) setTimeout(apply, 80)
  }
  apply()
}

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

// 页面标题 + 滚动落位
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} — Bandori / LIVE` : 'Bandori / LIVE'

  const target = pendingRestoreTop
  pendingRestoreTop = null
  if (target != null) restoreScroll(target)
  else window.__lenis?.scrollTo(0, { immediate: true, force: true })
})

export default router
