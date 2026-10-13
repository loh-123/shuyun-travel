<script setup lang="ts">
/**
 * 大屏面板容器（ScreenPanel）
 *
 * 统一的标题栏 + 四角金色装饰 + 半透明底；内容通过默认插槽传入。
 */
defineProps<{
  /** 面板标题 */
  title: string
  /** 次要说明（可选） */
  sub?: string
}>()
</script>

<template>
  <section class="sp">
    <header class="sp__head">
      <span class="sp__dot" />
      <h3 class="sp__title">{{ title }}</h3>
      <span v-if="sub" class="sp__sub">{{ sub }}</span>
      <span class="sp__extra"><slot name="extra" /></span>
    </header>

    <div class="sp__body">
      <slot />
    </div>

    <i class="sp__corner sp__corner--tl" />
    <i class="sp__corner sp__corner--tr" />
    <i class="sp__corner sp__corner--bl" />
    <i class="sp__corner sp__corner--br" />
  </section>
</template>

<style scoped lang="scss">
@use '@/screen/styles/screen-tokens' as *;

.sp {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px 14px 12px;

  @include screen-panel-box;
}

.sp__head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.16);
}

.sp__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $screen-cyan;
  box-shadow: 0 0 8px $screen-cyan;
}

.sp__title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: $screen-text;
}

.sp__sub {
  font-size: 18px;
  color: $screen-text-muted;
}

.sp__extra {
  margin-left: auto;
  font-size: 18px;
  color: $screen-text-muted;
}

.sp__body {
  flex: 1;
  min-height: 0;
}

/* 四角装饰 */
.sp__corner {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid $screen-gold;
  opacity: 0.85;

  &--tl {
    top: -1px;
    left: -1px;
    border-right: none;
    border-bottom: none;
  }

  &--tr {
    top: -1px;
    right: -1px;
    border-left: none;
    border-bottom: none;
  }

  &--bl {
    bottom: -1px;
    left: -1px;
    border-right: none;
    border-top: none;
  }

  &--br {
    bottom: -1px;
    right: -1px;
    border-left: none;
    border-top: none;
  }
}
</style>
