import type { RouteRecordRaw } from 'vue-router'

/**
 * 后台管理端路由
 *
 * - /admin/login：登录页（独立全屏，不套后台布局）
 * - /admin/*：后台主体，统一使用 AdminLayout（侧边栏 + 顶栏）
 *
 * meta.adminPublic 标记无需登录即可访问的页面；其余在全局守卫中校验 admin_token。
 */
export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/admin/views/AdminLogin.vue'),
    meta: { title: '后台登录', adminPublic: true },
  },
  {
    path: '/admin',
    component: () => import('@/admin/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/admin/views/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/admin/views/UserManage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'scenic',
        name: 'admin-scenic',
        component: () => import('@/admin/views/ScenicManage.vue'),
        meta: { title: '景点管理' },
      },
      {
        path: 'culture',
        name: 'admin-culture',
        component: () => import('@/admin/views/CultureManage.vue'),
        meta: { title: '文化管理' },
      },
      {
        path: 'trips',
        name: 'admin-trips',
        component: () => import('@/admin/views/TripManage.vue'),
        meta: { title: '行程管理' },
      },
      {
        path: 'ai-chat',
        name: 'admin-ai-chat',
        component: () => import('@/admin/views/AiChatManage.vue'),
        meta: { title: 'AI 对话管理' },
      },
      {
        path: 'behavior',
        name: 'admin-behavior',
        component: () => import('@/admin/views/UserBehavior.vue'),
        meta: { title: '用户行为统计' },
      },
      {
        path: 'feedback',
        name: 'admin-feedback',
        component: () => import('@/admin/views/FeedbackManage.vue'),
        meta: { title: '问题反馈' },
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: () => import('@/admin/views/ContentAudit.vue'),
        meta: { title: '内容审核' },
      },
      {
        path: 'backup',
        name: 'admin-backup',
        component: () => import('@/admin/views/DataBackup.vue'),
        meta: { title: '数据备份' },
      },
    ],
  },
]
