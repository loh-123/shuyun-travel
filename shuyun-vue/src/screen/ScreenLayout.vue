<script setup lang="ts">
/**
 * 数据大屏独立布局（ScreenLayout）
 *
 * 全屏、无导航栏、无页脚，仅承载大屏内容（子路由）；右下角常驻「蜀韵管家」智能体入口。
 */
import AiFab from '@/components/common/AiFab.vue'
</script>

<template>
  <div class="screen-layout">
    <router-view />
    <AiFab />
  </div>
</template>

<style scoped>
.screen-layout {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #060e1a;
}
</style>
