<script setup lang="ts">
/**
 * 后台占位页（AdminPlaceholder）
 *
 * 用于尚未实现的模块：统一展示页面标题 + 副标题 + 「建设中」提示。
 * 后续实现具体页面时，直接替换该视图内容即可。
 */
import AdminIcon from './AdminIcon.vue'

defineProps<{
  /** 页面标题 */
  title: string
  /** 副标题 */
  subtitle?: string
  /** 图标名 */
  icon?: string
}>()
</script>

<template>
  <div class="stub">
    <div class="stub__head">
      <h2 class="stub__title">{{ title }}</h2>
      <p v-if="subtitle" class="stub__sub">{{ subtitle }}</p>
    </div>

    <div class="stub__panel">
      <span class="stub__icon">
        <AdminIcon :name="icon || 'settings'" :size="40" :stroke="1.3" />
      </span>
      <p class="stub__text">该模块建设中</p>
      <p class="stub__hint">接口与页面将在后续迭代中完善，敬请期待。</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.stub__head {
  margin-bottom: 24px;
}

.stub__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.stub__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.stub__panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 40px;

  @include admin-card;
}

.stub__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  margin-bottom: 22px;
  border-radius: 50%;
  background: $admin-gold-soft;
  color: $admin-gold;
}

.stub__text {
  font-size: 18px;
  font-weight: 600;
  color: $admin-text;
}

.stub__hint {
  margin-top: 10px;
  font-size: 14px;
  color: $admin-text-muted;
}
</style>
