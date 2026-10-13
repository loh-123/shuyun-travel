<script setup lang="ts">
/**
 * 应用根组件
 * - 全局布局（导航栏 + 页脚 + 回到顶部）
 * - 全局路由过渡：卷轴收起 / 展开
 * - 鼠标跟随光晕
 * - 后台/用户后台路由（/admin/**、/user/**）不套前台布局，由各自布局组件渲染
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import CursorGlow from '@/components/common/CursorGlow.vue'

const route = useRoute()

/** 是否使用自带布局的全屏页面（管理员后台 / 用户后台 / 数据大屏 / 城市 3D 全景） */
const isBare = computed(
  () =>
    route.path.startsWith('/admin') ||
    route.path.startsWith('/user') ||
    route.path === '/screen' ||
    route.path.startsWith('/city'),
)
</script>

<template>
  <!-- 前台：套用全局布局 -->
  <DefaultLayout v-if="!isBare">
    <router-view v-slot="{ Component, route }">
      <!--
        路由切换：以 path 作为 key，保证「同组件不同参数」也会重新挂载并播放动画；
        使用 mode="out-in"，旧页先收起、新页再展开，不会出现两页叠影。
      -->
      <transition name="scroll-swap" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </DefaultLayout>

  <!-- 后台 / 用户后台：路由自带布局，不套前台导航栏与页脚 -->
  <router-view v-else />

  <!-- 鼠标跟随光晕：仅前台展示 -->
  <CursorGlow v-if="!isBare" />
</template>

<style>
/* ============================================================
 * 路由过渡：卷轴收起 / 展开
 * 旧页：向右滑出 + 淡出 + scale(0.98)
 * 新页：从左滑入 + 淡入 + scale(1.02 → 1)
 * 缓动统一 cubic-bezier(0.22, 1, 0.36, 1)
 * ========================================================== */
.scroll-swap-enter-active,
.scroll-swap-leave-active {
  transition:
    opacity 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform, opacity;
}

/* 离开稍快一点，避免「等旧页演完」的拖沓感 */
.scroll-swap-leave-active {
  transition-duration: 0.28s;
}

.scroll-swap-enter-from {
  opacity: 0;
  transform: translateX(-32px) scale(1.02);
}

.scroll-swap-leave-to {
  opacity: 0;
  transform: translateX(32px) scale(0.98);
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .scroll-swap-enter-active,
  .scroll-swap-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>
