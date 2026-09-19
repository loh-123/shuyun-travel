import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

import { getLenis } from '@/composables/useLenis'
import { applyTitle } from '@/i18n'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/scenic',
    name: 'scenic',
    component: () => import('@/views/Scenic.vue'),
    meta: { title: '景点' },
  },
  {
    path: '/scenic/:id',
    name: 'scenic-detail',
    component: () => import('@/views/ScenicDetail.vue'),
    meta: { title: '景点详情' },
  },
  {
    path: '/culture',
    name: 'culture',
    component: () => import('@/views/Culture.vue'),
    meta: { title: '文化' },
  },
  {
    path: '/culture/:id',
    name: 'culture-detail',
    component: () => import('@/views/CultureDetail.vue'),
    meta: { title: '文化详情' },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('@/views/Assistant.vue'),
    meta: { title: 'AI管家' },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '个人中心' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  /**
   * 切换路由时回到顶部。
   * 注：页面里挂载了 Lenis 全局平滑滚动，优先交给它处理，避免两边抢滚动。
   */
  scrollBehavior: () => {
    const lenis = getLenis()
    if (lenis) {
      lenis.scrollTo(0, { immediate: true })
    } else {
      window.scrollTo({ top: 0 })
    }
    return false
  },
})

/* ---------------- 顶部金色进度条（NProgress） ---------------- */
NProgress.configure({
  showSpinner: false,
  easing: 'ease',
  speed: 400,
  minimum: 0.12,
  trickleSpeed: 160,
})

router.beforeEach((to, from, next) => {
  // 同页面（仅参数变化）不重复出进度条，避免闪烁
  if (to.path !== from.path) {
    NProgress.start()
  }
  next()
})

router.afterEach((to) => {
  NProgress.done()
  // 标题走 i18n，切换语言时会自动重写（见 src/i18n/index.ts）
  applyTitle(String(to.name ?? ''))
})

router.onError(() => {
  NProgress.done()
})

export default router
