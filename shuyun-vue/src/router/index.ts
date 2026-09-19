import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

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
  scrollBehavior: () => ({ top: 0 }),
})

export default router
