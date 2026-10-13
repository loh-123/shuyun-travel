import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

import { getLenis } from '@/composables/useLenis'
import { adminRoutes } from '@/admin/router/admin'
import { ADMIN_TOKEN_KEY } from '@/admin/api/adminRequest'
import { userConsoleRoutes } from '@/user/router/userConsole'
import { TOKEN_KEY } from '@/api/request'
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
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' },
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
    path: '/food',
    name: 'food',
    component: () => import('@/views/Food.vue'),
    meta: { title: '美食' },
  },
  {
    path: '/food/:id',
    name: 'food-detail',
    component: () => import('@/views/FoodDetail.vue'),
    meta: { title: '美食详情' },
  },
  {
    path: '/heritage',
    name: 'heritage',
    component: () => import('@/views/Heritage.vue'),
    meta: { title: '非遗' },
  },
  {
    path: '/heritage/:id',
    name: 'heritage-detail',
    component: () => import('@/views/HeritageDetail.vue'),
    meta: { title: '非遗详情' },
  },
  {
    path: '/person',
    name: 'person',
    component: () => import('@/views/Persons.vue'),
    meta: { title: '传承人' },
  },
  {
    path: '/person/:id',
    name: 'person-detail',
    component: () => import('@/views/PersonDetail.vue'),
    meta: { title: '传承人详情' },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('@/views/Assistant.vue'),
    // hideFooter：隐藏全站页脚；fullscreen：整页锁定视口高度，仅对话区内部滚动
    meta: { title: 'AI管家', hideFooter: true, fullscreen: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '个人中心' },
  },
  // 3D 四川数字地图（全屏沉浸式）
  {
    path: '/map3d',
    name: 'map3d',
    component: () => import('@/views/Map3D.vue'),
    meta: { title: '3D数据大屏' },
  },
  // 城市 3D 全景（点击省级地图中的市州名称标签进入）
  {
    path: '/city/:cityName',
    name: 'City3D',
    component: () => import('@/views/City3D.vue'),
    meta: { title: '城市 3D 全景' },
  },
  // 数据大屏（独立全屏布局，不套前台布局）
  {
    path: '/screen',
    component: () => import('@/screen/ScreenLayout.vue'),
    children: [
      {
        path: '',
        name: 'screen',
        component: () => import('@/screen/DataScreen.vue'),
        meta: { title: '数据大屏' },
      },
    ],
  },
  // 用户后台（普通用户自己的数据）
  ...userConsoleRoutes,
  // 后台管理端路由
  ...adminRoutes,
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

  // 后台路由鉴权：使用独立的后台 token
  if (to.path.startsWith('/admin')) {
    const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
    if (to.meta?.adminPublic) {
      // 已登录时访问登录页，直接进入看板
      if (adminToken && to.path === '/admin/login') {
        next('/admin/dashboard')
        return
      }
      next()
      return
    }
    if (!adminToken) {
      next({ path: '/admin/login', query: { redirect: to.fullPath } })
      return
    }
  }

  // 用户后台：需前台登录
  if (to.path.startsWith('/user') && !localStorage.getItem(TOKEN_KEY)) {
    next('/')
    return
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
