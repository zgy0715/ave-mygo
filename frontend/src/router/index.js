import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/events', name: 'events', component: () => import('@/views/EventView.vue') },
    { path: '/events/:id', name: 'event-detail', component: () => import('@/views/EventDetailView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrdersView.vue'),
      // 登录守卫：未登录访问受保护页面 → 踢回登录页（前端侧的 anyRequest().authenticated()）
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !useAuthStore().isLogin) {
    return { path: '/login' }
  }
})

export default router
