<script setup lang="ts">
/**
 * 用户后台布局（UserConsoleLayout）
 *
 * 普通用户的「后台管理」：仅承载与本人相关的数据页面。
 * 与管理员后台（深色、监控全站）在视觉与权限上完全分开。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useUserStore } from '@/stores/user'
import BrandLogo from '@/components/common/BrandLogo.vue'
import ProfileMenuIcon from '@/components/common/ProfileMenuIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 侧边菜单 */
const MENUS = [
  { label: '概览', path: '/user/overview', icon: 'overview' },
  { label: '我的行程', path: '/user/trips', icon: 'trips' },
  { label: '我的收藏', path: '/user/collections', icon: 'collections' },
  { label: '我的笔记', path: '/user/notes', icon: 'notes' },
  { label: '浏览历史', path: '/user/history', icon: 'history' },
  { label: '问题反馈', path: '/user/feedback', icon: 'feedback' },
  { label: '账号设置', path: '/user/settings', icon: 'settings' },
]

/** 当前页标题 */
const title = computed(() => (route.meta?.title as string) || '用户中心')

/** 用户名首字母 */
const initial = computed(() => userStore.user?.username?.charAt(0)?.toUpperCase() || '蜀')

function isActive(path: string) {
  return route.path === path
}

/** 返回前台 */
function goHome() {
  router.push('/')
}

/** 退出登录 */
function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<template>
  <div class="uc">
    <!-- ============ 侧边栏 ============ -->
    <aside class="uc-side">
      <div class="uc-side__logo">
        <BrandLogo :size="64" />
        <span class="uc-side__name">我的空间</span>
      </div>

      <nav class="uc-side__menu">
        <button
          v-for="m in MENUS"
          :key="m.path"
          type="button"
          class="uc-side__item"
          :class="{ 'is-active': isActive(m.path) }"
          @click="router.push(m.path)"
        >
          <ProfileMenuIcon class="uc-side__icon" :name="m.icon" :size="28" />
          <span>{{ m.label }}</span>
        </button>
      </nav>

      <div class="uc-side__foot">
        <div class="uc-side__user">
          <span class="uc-side__avatar">{{ initial }}</span>
          <span class="uc-side__meta">
            <b>{{ userStore.user?.username || '未登录' }}</b>
            <em>{{ userStore.user?.email || '' }}</em>
          </span>
        </div>
        <button type="button" class="uc-side__back" @click="goHome">← 返回前台</button>
        <button type="button" class="uc-side__logout" @click="logout">安全退出</button>
      </div>
    </aside>

    <!-- ============ 主区 ============ -->
    <div class="uc-main">
      <header class="uc-head">
        <h1 class="uc-head__title">{{ title }}</h1>
        <span class="uc-head__tip">仅你可查看，数据仅属于你自己</span>
      </header>

      <main class="uc-body">
        <router-view v-slot="{ Component, route: r }">
          <transition name="uc-fade" mode="out-in">
            <component :is="Component" :key="r.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.uc {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background: $color-paper;
  color: $color-text-dark;
}

/* ---------------- 侧边栏 ---------------- */
.uc-side {
  display: flex;
  flex: 0 0 314px;
  flex-direction: column;
  height: 100vh;
  border-right: 1px solid $color-border-gold;
  background: linear-gradient(180deg, #14202e 0%, $color-ink 100%);
  color: $color-text-light;
}

.uc-side__logo {
  display: flex;
  gap: 14px;
  align-items: center;
  height: 96px;
  padding: 0 26px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.24);
}

.uc-side__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 26px;
  letter-spacing: 0.14em;
  color: $color-text-light;
}

.uc-side__menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 22px 16px;
  overflow-y: auto;
}

.uc-side__item {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: center;
  height: 58px;
  padding: 0 18px;
  border: none;
  border-radius: $radius-md;
  background: transparent;
  font-size: 21px;
  letter-spacing: 0.06em;
  color: rgba(245, 241, 232, 0.72);
  text-align: left;
  cursor: pointer;
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 0;
    width: 0;
    height: 28px;
    margin-top: -14px;
    border-radius: 0 2px 2px 0;
    background: $color-gold;
    transition: width $duration-fast $ease-out-expo;
  }

  &:hover {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.12);

    &::before {
      width: 3px;
    }
  }

  &.is-active {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.18);

    &::before {
      width: 3px;
    }
  }
}

.uc-side__icon {
  flex-shrink: 0;
}

.uc-side__foot {
  padding: 22px;
  border-top: 1px solid rgba(212, 179, 116, 0.24);
}

.uc-side__user {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 20px;
}

.uc-side__avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, $color-primary, #5c1219);
  font-size: 24px;
  font-weight: 700;
  color: $color-gold;
}

.uc-side__meta {
  display: flex;
  flex-direction: column;
  min-width: 0;

  b {
    font-size: 21px;
    color: $color-text-light;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 18px;
    color: rgba(245, 241, 232, 0.5);
    @include text-ellipsis(1);
  }
}

.uc-side__back,
.uc-side__logout {
  width: 100%;
  padding: 14px 0;
  margin-bottom: 10px;
  border-radius: $radius-md;
  font-size: 20px;
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;
}

.uc-side__back {
  border: 1px solid rgba(212, 179, 116, 0.4);
  background: transparent;
  color: rgba(245, 241, 232, 0.8);

  &:hover {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.12);
  }
}

.uc-side__logout {
  border: none;
  background: rgba(122, 26, 34, 0.5);
  color: #ffd2d2;

  &:hover {
    background: rgba(122, 26, 34, 0.75);
  }
}

/* ---------------- 主区 ---------------- */
.uc-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  /* 主内容整体缩小 0.5×（侧栏不受影响） */
  zoom: 0.5;
}

.uc-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 26px 32px 18px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.3);
}

.uc-head__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(20px, 2vw, 28px);
  letter-spacing: 0.1em;
  color: var(--sy-accent);
}

.uc-head__tip {
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.5);
}

.uc-body {
  flex: 1;
  padding: 26px 32px 48px;
  overflow-y: auto;
}

/* 过渡 */
.uc-fade-enter-active,
.uc-fade-leave-active {
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

.uc-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.uc-fade-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 900px) {
  .uc-side {
    flex-basis: 96px;

    .uc-side__name,
    .uc-side__item span:not(.uc-side__icon),
    .uc-side__meta,
    .uc-side__back,
    .uc-side__logout {
      display: none;
    }

    .uc-side__item {
      justify-content: center;
    }
  }

  .uc-head,
  .uc-body {
    padding-inline: 16px;
  }
}
</style>
