<script setup lang="ts">
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import CursorGlow from '@/components/common/CursorGlow.vue'
</script>

<template>
  <DefaultLayout>
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </DefaultLayout>

  <!-- 鼠标跟随光晕：全局挂载，所有页面共用 -->
  <CursorGlow />
</template>

<style>
/* 页面切换淡入淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
