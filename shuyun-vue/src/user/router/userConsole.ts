import type { RouteRecordRaw } from 'vue-router'

/**
 * 用户后台路由（普通用户的「后台管理」）
 *
 * 与管理员后台 /admin/** 并列：这里只承载当前用户自己的数据页面。
 * 全部页面需登录，且统一使用 UserConsoleLayout。
 */
export const userConsoleRoutes: RouteRecordRaw[] = [
  {
    path: '/user',
    component: () => import('@/user/layouts/UserConsoleLayout.vue'),
    redirect: '/user/overview',
    children: [
      {
        path: 'overview',
        name: 'user-overview',
        component: () => import('@/user/views/UserOverview.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'trips',
        name: 'user-trips',
        component: () => import('@/user/views/MyTrips.vue'),
        meta: { title: '我的行程' },
      },
      {
        path: 'collections',
        name: 'user-collections',
        component: () => import('@/user/views/MyCollections.vue'),
        meta: { title: '我的收藏' },
      },
      {
        path: 'notes',
        name: 'user-notes',
        component: () => import('@/user/views/MyNotes.vue'),
        meta: { title: '我的笔记' },
      },
      {
        path: 'history',
        name: 'user-history',
        component: () => import('@/user/views/MyHistory.vue'),
        meta: { title: '浏览历史' },
      },
      {
        path: 'feedback',
        name: 'user-feedback',
        component: () => import('@/user/views/MyFeedback.vue'),
        meta: { title: '问题反馈' },
      },
      {
        path: 'settings',
        name: 'user-settings',
        component: () => import('@/user/views/AccountSettings.vue'),
        meta: { title: '账号设置' },
      },
    ],
  },
]
