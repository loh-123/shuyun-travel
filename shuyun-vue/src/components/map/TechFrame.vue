<script setup lang="ts">
/**
 * 科技切角框架（TechFrame）
 *
 * 参考《本草纲目》数据大屏风格：
 * - 四角切掉 12px（clip-path）
 * - 双层描边：外层 rgba(58,140,220,0.4) + 内层 #0F2540
 * - 内层 inset 4px 再描一圈 rgba(58,140,220,0.15)
 * - 四角小三角装饰（#4AD8F0）
 * - 顶部 40px 标题栏：背景 rgba(58,140,220,0.12) + 底部分隔线 + 左侧 3px 竖线 + 右侧小图标
 * - 标题栏右侧可选插槽 #extra（放刷新等操作按钮）
 */
defineProps<{
  /** 标题栏文字 */
  title: string
}>()
</script>

<template>
  <section class="frame">
    <div class="frame__inner">
      <i class="frame__corner frame__corner--tl" />
      <i class="frame__corner frame__corner--tr" />
      <i class="frame__corner frame__corner--bl" />
      <i class="frame__corner frame__corner--br" />

      <header class="frame__head">
        <span class="frame__bar" />
        <h3>{{ title }}</h3>
        <svg
          class="frame__ico"
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
        >
          <path d="M4 12a8 8 0 1 1 2.34 5.66" />
          <path d="M4 20v-4h4" />
        </svg>

        <!-- 标题栏右侧操作区（可选） -->
        <slot name="extra" />
      </header>

      <div class="frame__body">
        <slot />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* 外层：只负责 1px 描边色；内层：深蓝底 + 光晕 */
.frame {
  position: relative;
  flex: none;
  clip-path: polygon(
    12px 0,
    calc(100% - 12px) 0,
    100% 12px,
    100% calc(100% - 12px),
    calc(100% - 12px) 100%,
    12px 100%,
    0 calc(100% - 12px),
    0 12px
  );
  background: rgba(58, 140, 220, 0.4);
}

.frame__inner {
  position: relative;
  margin: 1px;
  clip-path: polygon(
    12px 0,
    calc(100% - 12px) 0,
    100% 12px,
    100% calc(100% - 12px),
    calc(100% - 12px) 100%,
    12px 100%,
    0 calc(100% - 12px),
    0 12px
  );
  background: #0f2540;
  box-shadow: inset 0 0 30px rgba(58, 140, 220, 0.1);
}

/* 内层再描一圈（距边框 4px） */
.frame__inner::after {
  content: '';
  position: absolute;
  inset: 4px;
  border: 1px solid rgba(58, 140, 220, 0.15);
  pointer-events: none;
}

/* 四角小三角 */
.frame__corner {
  position: absolute;
  width: 0;
  height: 0;
  border: 4px solid transparent;
  pointer-events: none;

  &--tl {
    top: 6px;
    left: 6px;
    border-top-color: #4ad8f0;
    border-left-color: #4ad8f0;
  }

  &--tr {
    top: 6px;
    right: 6px;
    border-top-color: #4ad8f0;
    border-right-color: #4ad8f0;
  }

  &--bl {
    bottom: 6px;
    left: 6px;
    border-bottom-color: #4ad8f0;
    border-left-color: #4ad8f0;
  }

  &--br {
    right: 6px;
    bottom: 6px;
    border-right-color: #4ad8f0;
    border-bottom-color: #4ad8f0;
  }
}

/* 顶部标题栏 */
.frame__head {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(58, 140, 220, 0.3);
  background: rgba(58, 140, 220, 0.12);
}

.frame__bar {
  flex: none;
  width: 4px;
  height: 18px;
  background: #4ad8f0;
}

.frame__head h3 {
  flex: 1;
  overflow: hidden;
  font-family: $font-body;
  font-size: 25px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #ffffff;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.frame__ico {
  flex: none;
  color: #4ad8f0;
}

.frame__body {
  padding: 8px 14px 12px;
}
</style>
