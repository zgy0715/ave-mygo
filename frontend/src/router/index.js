import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
  { path: '/songs', name: 'songs', component: () => import('@/views/SongsView.vue') },
  { path: '/songs/:id', name: 'song-detail', component: () => import('@/views/SongDetailView.vue') },
  { path: '/bands', name: 'bands', component: () => import('@/views/BandsView.vue') },
  { path: '/bands/:id', name: 'band', component: () => import('@/views/BandView.vue') },
  { path: '/events', name: 'events', component: () => import('@/views/EventsView.vue') },
  { path: '/events/:id', name: 'event-detail', component: () => import('@/views/EventDetailView.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 登录守卫：无 token 跳 /login 并带 redirect，登录成功后回原页（需求走查缺口②）
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !useAuthStore().isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
