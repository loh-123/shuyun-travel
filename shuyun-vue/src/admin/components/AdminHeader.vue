<script setup lang="ts">
/**
 * 后台顶栏（AdminHeader）
 *
 * - 左侧：折叠按钮 + 面包屑
 * - 右侧：全局搜索框 / 通知铃铛（红点）/ 管理员头像下拉
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminIcon from './AdminIcon.vue'
import { ADMIN_TOKEN_KEY, ADMIN_USER_KEY } from '@/admin/api/adminRequest'

defineProps<{
  /** 侧边栏是否折叠 */
  collapsed: boolean
}>()

defineEmits<{
  /** 切换折叠 */
  (e: 'toggle'): void
}>()

const route = useRoute()
const router = useRouter()

/** 搜索关键词 */
const keyword = ref('')
/** 头像下拉是否展开 */
const menuOpen = ref(false)

/** 管理员名 */
const adminName = computed(() => {
  try {
    const info = JSON.parse(localStorage.getItem(ADMIN_USER_KEY) || '{}') as { username?: string }
    return info.username || '管理员'
  } catch {
    return '管理员'
  }
})

/** 头像首字母 */
const initial = computed(() => adminName.value.charAt(0).toUpperCase())

/** 面包屑：首页 / 当前页 */
const breadcrumb = computed(() => {
  const title = (route.meta?.title as string) || '后台'
  return ['首页', title]
})

/** 搜索（暂为占位） */
function onSearch() {
  const text = keyword.value.trim()
  if (!text) {
    return
  }
  ElMessage.info(`「${text}」全局搜索功能建设中`)
}

/** 返回前台首页 */
function goHome() {
  menuOpen.value = false
  router.push('/')
}

/** 退出登录 */
function logout() {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  localStorage.removeItem(ADMIN_USER_KEY)
  ElMessage.success('已退出后台')
  router.push('/admin/login')
}

/** 个人设置（占位） */
function onSetting() {
  menuOpen.value = false
  ElMessage.info('个人设置建设中')
}

/** 点击空白收起下拉 */
function onDocClick(e: MouseEvent) {
  if (!(e.target as HTMLElement).closest('.hdr-user')) {
    menuOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <header class="hdr">
    <!-- 左侧 -->
    <div class="hdr__left">
      <button type="button" class="hdr__collapse" :title="collapsed ? '展开侧边栏' : '折叠侧边栏'" @click="$emit('toggle')">
        <AdminIcon name="menu" :size="20" />
      </button>
      <nav class="hdr__crumb">
        <span v-for="(item, i) in breadcrumb" :key="i" class="hdr__crumb-item" :class="{ 'is-last': i === breadcrumb.length - 1 }">
          {{ item }}
          <em v-if="i < breadcrumb.length - 1">/</em>
        </span>
      </nav>
    </div>

    <!-- 右侧 -->
    <div class="hdr__right">
      <!-- 返回前台首页 -->
      <button type="button" class="hdr__icon" title="返回前台首页" @click="goHome">
        <AdminIcon name="home" :size="19" />
      </button>

      <!-- 全局搜索 -->
      <label class="hdr__search">
        <AdminIcon name="search" :size="18" />
        <input v-model="keyword" type="text" placeholder="搜索用户 / 景点 / 文化…" @keyup.enter="onSearch" />
      </label>

      <!-- 通知 -->
      <button type="button" class="hdr__icon" title="通知" @click="ElMessage.info('暂无新通知')">
        <AdminIcon name="bell" :size="19" />
        <span class="hdr__dot" />
      </button>

      <!-- 管理员下拉 -->
      <div class="hdr-user">
        <button type="button" class="hdr-user__btn" @click.stop="menuOpen = !menuOpen">
          <span class="hdr-user__avatar">{{ initial }}</span>
          <span class="hdr-user__name">{{ adminName }}</span>
          <AdminIcon name="chevron" :size="14" />
        </button>

        <transition name="drop">
          <div v-if="menuOpen" class="hdr-user__menu">
            <div class="hdr-user__head">
              <span class="hdr-user__avatar hdr-user__avatar--lg">{{ initial }}</span>
              <span class="hdr-user__meta">
                <b>{{ adminName }}</b>
                <em>超级管理员</em>
              </span>
            </div>
            <button type="button" @click="onSetting">
              <AdminIcon name="settings" :size="16" /> 个人设置
            </button>
            <button type="button" class="is-danger" @click="logout">
              <AdminIcon name="logout" :size="16" /> 退出登录
            </button>
          </div>
        </transition>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.hdr {
  /* 顶栏使用 backdrop-filter 会生成层叠上下文，需显式提高层级，
     否则下方内容卡片会盖住头像下拉菜单 */
  position: relative;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $admin-header-h;
  padding: 0 24px;
  border-bottom: 1px solid $admin-border;

  @include admin-glass(rgba(var(--admin-glass-rgb), 0.72), 18px);
}

.hdr__left {
  display: flex;
  gap: 14px;
  align-items: center;
}

.hdr__collapse,
.hdr__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid transparent;
  border-radius: $admin-radius-sm;
  background: transparent;
  color: $admin-text-muted;
  cursor: pointer;
  transition:
    color 0.3s $admin-ease,
    background-color 0.3s $admin-ease,
    border-color 0.3s $admin-ease;

  &:hover {
    color: $admin-gold;
    border-color: $admin-border;
    background: $admin-gold-soft;
  }
}

.hdr__crumb {
  display: flex;
  gap: 8px;
  align-items: center;
}

.hdr__crumb-item {
  font-size: 14px;
  color: $admin-text-muted;

  em {
    margin-left: 8px;
    font-style: normal;
    color: rgba(122, 138, 160, 0.6);
  }

  &.is-last {
    color: $admin-text;
  }
}

.hdr__right {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 搜索框：圆角磨砂 */
.hdr__search {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 260px;
  height: 40px;
  padding: 0 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  color: $admin-text-muted;
  transition: border-color 0.3s $admin-ease;

  &:focus-within {
    border-color: $admin-gold-line;
  }

  input {
    flex: 1;
    min-width: 0;
    border: none;
    background: transparent;
    font-size: 14px;
    color: $admin-text;
    outline: none;

    &::placeholder {
      color: $admin-text-muted;
    }
  }
}

/* 铃铛红点 */
.hdr__icon {
  position: relative;
}

.hdr__dot {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: $admin-red-down;
  box-shadow: 0 0 0 2px rgba(var(--admin-glass-rgb), 0.9);
}

/* ---------------- 管理员下拉 ---------------- */
.hdr-user {
  position: relative;
}

.hdr-user__btn {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 40px;
  padding: 0 12px 0 6px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  color: $admin-text;
  cursor: pointer;
  transition: border-color 0.3s $admin-ease;

  &:hover {
    border-color: $admin-gold-line;
  }
}

.hdr-user__name {
  font-size: 14px;
}

.hdr-user__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, $admin-red, #5c1219);
  font-size: 13px;
  font-weight: 700;
  color: $admin-gold;

  &--lg {
    width: 42px;
    height: 42px;
    font-size: 17px;
  }
}

.hdr-user__menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 20;
  min-width: 200px;
  padding: 8px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  box-shadow: $admin-shadow;

  @include admin-glass(rgba(var(--admin-glass-rgb), 0.96), 18px);

  button {
    display: flex;
    gap: 10px;
    align-items: center;
    width: 100%;
    padding: 10px 12px;
    border: none;
    border-radius: 8px;
    background: transparent;
    font-size: 14px;
    color: $admin-text;
    text-align: left;
    cursor: pointer;
    transition: background-color 0.25s $admin-ease;

    &:hover {
      background: $admin-gold-soft;
    }

    &.is-danger:hover {
      color: $admin-red-down;
      background: rgba(208, 92, 98, 0.12);
    }
  }
}

.hdr-user__head {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 12px 12px;
  margin-bottom: 6px;
  border-bottom: 1px solid $admin-border;
}

.hdr-user__meta {
  display: flex;
  flex-direction: column;

  b {
    font-size: 14px;
    color: $admin-text;
  }

  em {
    font-style: normal;
    font-size: 12px;
    color: $admin-text-muted;
  }
}

/* 下拉过渡 */
.drop-enter-active,
.drop-leave-active {
  transition:
    opacity 0.22s $admin-ease,
    transform 0.22s $admin-ease;
}

.drop-enter-from,
.drop-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
