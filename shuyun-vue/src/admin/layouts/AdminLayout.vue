<script setup lang="ts">
/**
 * 后台整体布局（AdminLayout）
 *
 * 左侧固定侧边栏（可折叠）+ 右侧顶栏与内容区。
 * 小屏（<=1100px）自动折叠为图标模式。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import AdminSidebar from '@/admin/components/AdminSidebar.vue'
import AdminHeader from '@/admin/components/AdminHeader.vue'

/** 侧边栏是否折叠 */
const collapsed = ref(false)

/** 折叠断点 */
const COLLAPSE_WIDTH = 1100

function onResize() {
  if (window.innerWidth <= COLLAPSE_WIDTH) {
    collapsed.value = true
  }
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="admin">
    <AdminSidebar :collapsed="collapsed" />

    <div class="admin__body">
      <AdminHeader :collapsed="collapsed" @toggle="collapsed = !collapsed" />
      <main class="admin__content">
        <router-view v-slot="{ Component, route }">
          <transition name="admin-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.admin {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background: $admin-bg;
  color: $admin-text;
  /* 后台整体（含字号 / 图标 / 间距）放大 0.5× */
  zoom: 1.5;
}

.admin__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
}

.admin__content {
  flex: 1;
  padding: 32px;
  overflow-y: auto;
}

/* 内容切换过渡 */
.admin-fade-enter-active,
.admin-fade-leave-active {
  transition:
    opacity 0.3s $admin-ease,
    transform 0.3s $admin-ease;
}

.admin-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.admin-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 小屏：内容内边距收窄 */
@media (max-width: 768px) {
  .admin__content {
    padding: 20px 16px;
  }
}
</style>
