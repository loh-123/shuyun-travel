<script setup lang="ts">
/**
 * 后台侧边栏（AdminSidebar）
 *
 * - 顶部：品牌徽标 + 品牌名
 * - 菜单：默认次要色，hover / 选中金色，左侧 3px 金色竖条
 * - 底部：管理员头像 + 用户名 + 退出登录
 * - 折叠态：仅显示图标
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminIcon from './AdminIcon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import { ADMIN_TOKEN_KEY, ADMIN_USER_KEY } from '@/admin/api/adminRequest'

/** 叶子菜单项 */
interface MenuLeaf {
  label: string
  path: string
  icon: string
}

/** 可展开的菜单分组 */
interface MenuGroup {
  key: string
  label: string
  icon: string
  children: MenuLeaf[]
}

const props = defineProps<{
  /** 是否折叠 */
  collapsed: boolean
}>()

const route = useRoute()
const router = useRouter()

/** 「功能管理」之前的一级菜单 */
const MENUS_TOP: MenuLeaf[] = [{ label: '用户管理', path: '/admin/users', icon: 'users' }]

/** 「功能管理」分组：聚合各管理看板，点击展开后选择其中一个 */
const FEATURE_GROUP: MenuGroup = {
  key: 'feature',
  label: '功能管理',
  icon: 'menu',
  children: [
    { label: '景点管理', path: '/admin/scenic', icon: 'scenic' },
    { label: '文化管理', path: '/admin/culture', icon: 'culture' },
    { label: '行程管理', path: '/admin/trips', icon: 'trip' },
    { label: 'AI 对话管理', path: '/admin/ai-chat', icon: 'ai' },
  ],
}

/** 「功能管理」分组之后的一级菜单（仪表盘放在分组下方） */
const MENUS_BOTTOM: MenuLeaf[] = [
  { label: '仪表盘', path: '/admin/dashboard', icon: 'dashboard' },
  { label: '用户行为统计', path: '/admin/behavior', icon: 'behavior' },
  { label: '问题反馈', path: '/admin/feedback', icon: 'bell' },
  { label: '内容审核', path: '/admin/audit', icon: 'audit' },
  { label: '数据备份', path: '/admin/backup', icon: 'backup' },
]

/** 已展开的分组 key */
const openKeys = ref<string[]>([])

/** 分组是否展开 */
function isOpen(key: string) {
  return openKeys.value.includes(key)
}

/** 切换分组展开 / 收起；折叠态下点击直接进入第一个看板 */
function onGroupClick(m: MenuGroup) {
  if (props.collapsed) {
    go(m.children[0].path)
    return
  }
  openKeys.value = isOpen(m.key)
    ? openKeys.value.filter((k) => k !== m.key)
    : [...openKeys.value, m.key]
}

/** 分组内是否有当前激活项 */
function isGroupActive(m: MenuGroup) {
  return m.children.some((c) => isActive(c.path))
}

/** 进入某看板时自动展开所属分组 */
watch(
  () => route.path,
  (p) => {
    const inside = FEATURE_GROUP.children.some((c) => p === c.path || p.startsWith(`${c.path}/`))
    if (inside && !openKeys.value.includes(FEATURE_GROUP.key)) {
      openKeys.value = [...openKeys.value, FEATURE_GROUP.key]
    }
  },
  { immediate: true },
)

/** 读取本地保存的管理员信息 */
const adminUser = computed<{ username?: string }>(() => {
  try {
    return JSON.parse(localStorage.getItem(ADMIN_USER_KEY) || '{}') as { username?: string }
  } catch {
    return {}
  }
})

/** 管理员名 */
const adminName = computed(() => adminUser.value.username || '管理员')

/** 头像首字母 */
const initial = computed(() => adminName.value.charAt(0).toUpperCase())

/** 当前菜单是否选中（含子路由） */
function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

/** 跳转 */
function go(path: string) {
  if (route.path !== path) {
    router.push(path)
  }
}

/** 退出登录 */
function logout() {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  localStorage.removeItem(ADMIN_USER_KEY)
  ElMessage.success('已退出后台')
  router.push('/admin/login')
}
</script>

<template>
  <aside class="side" :class="{ 'is-collapsed': collapsed }">
    <!-- Logo -->
    <div class="side__logo">
      <BrandLogo :size="48" />
      <transition name="fade">
        <span v-if="!collapsed" class="side__brand sy-brand-text">蜀韵·智游后台</span>
      </transition>
    </div>

    <!-- 菜单 -->
    <nav class="side__menu">
      <!-- 「功能管理」之前 -->
      <button
        v-for="m in MENUS_TOP"
        :key="m.path"
        type="button"
        class="side__item"
        :class="{ 'is-active': isActive(m.path) }"
        :title="m.label"
        @click="go(m.path)"
      >
        <span class="side__bar" aria-hidden="true" />
        <AdminIcon :name="m.icon" :size="20" class="side__icon" />
        <transition name="fade">
          <span v-if="!collapsed" class="side__text">{{ m.label }}</span>
        </transition>
      </button>

      <!-- 「功能管理」分组：点击展开，选择其中一个看板 -->
      <div class="side__group">
        <button
          type="button"
          class="side__item side__item--group"
          :class="{ 'is-active': isGroupActive(FEATURE_GROUP) }"
          :title="FEATURE_GROUP.label"
          @click="onGroupClick(FEATURE_GROUP)"
        >
          <span class="side__bar" aria-hidden="true" />
          <AdminIcon :name="FEATURE_GROUP.icon" :size="20" class="side__icon" />
          <transition name="fade">
            <span v-if="!collapsed" class="side__text">{{ FEATURE_GROUP.label }}</span>
          </transition>
          <AdminIcon
            v-if="!collapsed"
            name="chevron"
            :size="16"
            class="side__caret"
            :class="{ 'is-open': isOpen(FEATURE_GROUP.key) }"
          />
        </button>

        <transition name="sub">
          <div v-if="!collapsed && isOpen(FEATURE_GROUP.key)" class="side__sub">
            <button
              v-for="c in FEATURE_GROUP.children"
              :key="c.path"
              type="button"
              class="side__item side__item--sub"
              :class="{ 'is-active': isActive(c.path) }"
              :title="c.label"
              @click="go(c.path)"
            >
              <span class="side__bar" aria-hidden="true" />
              <AdminIcon :name="c.icon" :size="18" class="side__icon" />
              <span class="side__text">{{ c.label }}</span>
            </button>
          </div>
        </transition>
      </div>

      <!-- 「功能管理」之后（仪表盘等） -->
      <button
        v-for="m in MENUS_BOTTOM"
        :key="m.path"
        type="button"
        class="side__item"
        :class="{ 'is-active': isActive(m.path) }"
        :title="m.label"
        @click="go(m.path)"
      >
        <span class="side__bar" aria-hidden="true" />
        <AdminIcon :name="m.icon" :size="20" class="side__icon" />
        <transition name="fade">
          <span v-if="!collapsed" class="side__text">{{ m.label }}</span>
        </transition>
      </button>
    </nav>

    <!-- 底部：管理员 -->
    <div class="side__foot">
      <span class="side__avatar">{{ initial }}</span>
      <transition name="fade">
        <span v-if="!collapsed" class="side__meta">
          <b>{{ adminName }}</b>
          <em>超级管理员</em>
        </span>
      </transition>
      <button type="button" class="side__logout" title="退出登录" @click="logout">
        <AdminIcon name="logout" :size="18" />
      </button>
    </div>
  </aside>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.side {
  display: flex;
  flex-direction: column;
  width: $admin-sidebar-w;
  height: 100vh;
  border-right: 1px solid $admin-border;
  transition: width 0.4s $admin-ease;

  @include admin-glass(rgba(var(--admin-glass-rgb), 0.85), 20px);

  &.is-collapsed {
    width: $admin-sidebar-w-collapsed;

    .side__logo {
      justify-content: center;
      padding: 0 12px;
    }

    .side__foot {
      justify-content: center;
    }
  }
}

/* ---------------- Logo ---------------- */
.side__logo {
  display: flex;
  gap: 12px;
  align-items: center;
  height: $admin-header-h;
  padding: 0 20px;
  border-bottom: 1px solid $admin-border;
  white-space: nowrap;
  overflow: hidden;
}

/* 徒标不参与收缩，避免折叠态被压扁 */
.side__logo :deep(.brand-logo) {
  flex: 0 0 auto;
}

.side__brand {
  /* 只定字号；金色立体效果全部交给全局 .sy-brand-text */
  font-size: 20px;
}

/* ---------------- 菜单 ---------------- */
.side__menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 12px;
  overflow-y: auto;
}

.side__item {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: center;
  height: 44px;
  padding: 0 12px;
  border: none;
  border-radius: $admin-radius-sm;
  background: transparent;
  font-size: 14px;
  color: $admin-text-muted;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  cursor: pointer;
  transition:
    color 0.3s $admin-ease,
    background-color 0.3s $admin-ease;

  &:hover,
  &.is-active {
    color: $admin-gold;
    background: $admin-gold-soft;
  }

  &.is-active {
    background: rgba(212, 179, 116, 0.16);
  }
}

.side__bar {
  position: absolute;
  top: 50%;
  left: 0;
  width: 0;
  height: 22px;
  margin-top: -11px;
  border-radius: 0 2px 2px 0;
  background: $admin-gold;
  transition: width 0.3s $admin-ease;
}

.side__item:hover .side__bar,
.side__item.is-active .side__bar {
  width: 3px;
}

.side__icon {
  flex: 0 0 auto;
}

.side__text {
  font-size: 14px;
}

/* ---------------- 分组与子菜单 ---------------- */
.side__group {
  display: flex;
  flex-direction: column;
}

/* 展开箭头 */
.side__caret {
  flex: 0 0 auto;
  margin-left: auto;
  transition: transform 0.3s $admin-ease;

  &.is-open {
    transform: rotate(180deg);
  }
}

/* 子菜单（缩进） */
.side__sub {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 18px;
  margin-top: 4px;
}

.side__item--sub {
  height: 38px;

  .side__text {
    font-size: 13px;
  }
}

/* 子菜单展开 / 收起过渡 */
.sub-enter-active,
.sub-leave-active {
  transition:
    opacity 0.25s $admin-ease,
    transform 0.25s $admin-ease;
}

.sub-enter-from,
.sub-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ---------------- 底部 ---------------- */
.side__foot {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px 18px;
  border-top: 1px solid $admin-border;
  overflow: hidden;
}

.side__avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(135deg, $admin-red, #5c1219);
  font-size: 16px;
  font-weight: 700;
  color: $admin-gold;
}

.side__meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;

  b {
    overflow: hidden;
    font-size: 14px;
    color: $admin-text;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    font-style: normal;
    font-size: 12px;
    color: $admin-text-muted;
  }
}

.side__logout {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: $admin-text-muted;
  cursor: pointer;
  transition:
    color 0.3s $admin-ease,
    background-color 0.3s $admin-ease;

  &:hover {
    color: $admin-red-down;
    background: rgba(208, 92, 98, 0.12);
  }
}

/* 折叠过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s $admin-ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
