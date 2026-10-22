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
  { path: '/favorites', name: 'favorites', component: () => import('@/views/FavoritesView.vue'), meta: { title: '我的收藏', requiresAuth: true } },
  { path: '/playlists', name: 'playlists', component: () => import('@/views/PlaylistsView.vue'), meta: { title: '我的歌单', requiresAuth: true } },
  { path: '/playlists/:id', name: 'playlist-detail', component: () => import('@/views/PlaylistDetailView.vue'), meta: { title: '歌单详情', requiresAuth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人中心', requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { title: '歌曲管理', requiresAuth: true, requiresAdmin: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

/**
 * 滚动位置管理（不依赖 vue-router 内部的 savedPosition 时序）：
 * - before 导航：把「离开的页面 + 当时的 scrollY」记进自己的 Map
 * - popstate（浏览器/PageBack 返回）：用 Map 里的位置恢复
 * - 恢复用 rAF 逐帧重试 2.5s：目标页数据异步到达前高度不足，一次性 scrollTo 会被 clamp 成 0
 * - 用户一旦滚轮/触摸，立刻放弃恢复，不抢方向盘
 */
const scrollMemo = new Map()
let isPopNavigation = false
let pendingRestoreTop = null

// 后退/前进的标志：popstate 先于路由回调触发
window.addEventListener('popstate', () => { isPopNavigation = true })

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 只有真的后退/前进才记录位置；新导航一律清空，避免遗留值污染下一次落位
    pendingRestoreTop = isPopNavigation ? (savedPosition?.top ?? null) : null
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

let cancelRestore = null

/** 逐帧落位：高度不够时持续重试，到位或超时/用户干预即停 */
function restoreScroll(target) {
  cancelRestore?.()
  const lenis = window.__lenis
  if (!target || target <= 0) {
    lenis?.scrollTo(0, { immediate: true, force: true })
    return
  }

  let stopped = false
  const stop = () => { stopped = true; cleanup() }
  const cleanup = () => {
    window.removeEventListener('wheel', stop)
    window.removeEventListener('touchstart', stop)
    window.removeEventListener('keydown', stop)
    cancelRestore = null
  }
  cancelRestore = stop
  window.addEventListener('wheel', stop, { passive: true })
  window.addEventListener('touchstart', stop, { passive: true })
  window.addEventListener('keydown', stop)

  const start = performance.now()
  const tick = () => {
    if (stopped) return
    lenis?.scrollTo(target, { immediate: true, force: true })
    window.scrollTo(0, target)
    if (Math.abs(window.scrollY - target) <= 4 || performance.now() - start > 2500) { cleanup(); return }
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

// 离开前记录滚动位置（返回时用它还原，不依赖 vue-router savedPosition 的时序）
router.beforeEach((to, from) => {
  if (from.name && from.fullPath !== to.fullPath) scrollMemo.set(from.fullPath, window.scrollY)
})

// 登录守卫：无 token 跳 /login 并带 redirect，登录成功后回原页
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
  document.title = to.meta.title ? `${to.meta.title} — BanG Dream! / LIVE` : 'BanG Dream! / LIVE'

  const pop = isPopNavigation
  isPopNavigation = false
  const saved = pendingRestoreTop
  pendingRestoreTop = null

  if (pop) {
    // 后退/前进：优先自己的缓存，其次路由给的位置
    restoreScroll(scrollMemo.get(to.fullPath) ?? saved ?? 0)
  } else {
    // 新导航：无条件回顶部。
    // 注意不能用 saved/pendingRestoreTop —— afterEach 常早于 scrollBehavior 执行，
    // 读到的可能是上一次后退恢复的遗留值，会把新页面甩到最底部。
    restoreScroll(0)
  }
})

export default router
