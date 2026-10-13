<script setup lang="ts">
/**
 * 公共三态组件（loading / empty / error）
 * - 三态共用同一容器：居中布局，上下内边距 80px
 * - loading：金黄绸缎底 + 项目 LOGO + 文案
 * - empty：蜀锦纹样图标 + 主文案 + 可选副文案 + 可选操作按钮
 * - error：警示图标（金色，不刺眼）+ 主文案 + 可选副文案 + 重试按钮
 *
 * @example
 * <StateBlock state="loading" />
 * <StateBlock state="empty" empty-text="暂无美食" action-text="返回" :action="goBack" />
 * <StateBlock state="error" :retry="loadList" />
 */
import BrandLogo from '@/components/common/BrandLogo.vue'
import GoldButton from '@/components/common/GoldButton.vue'

interface Props {
  /** 状态类型 */
  state: 'loading' | 'empty' | 'error'
  /** 加载中文案 */
  loadingText?: string
  /** 空数据主文案 */
  emptyText?: string
  /** 错误主文案 */
  errorText?: string
  /** 空数据副文案（可选） */
  emptySubText?: string
  /** 错误副文案（可选） */
  errorSubText?: string
  /** 空数据图标文字（可选，默认「蜀」） */
  emptyIcon?: string
  /** 重试回调（error 状态显示「重试」按钮） */
  retry?: (() => void) | null
  /** 操作按钮文案（empty 状态显示，如「返回列表」） */
  actionText?: string
  /** 操作按钮回调 */
  action?: (() => void) | null
}

const props = withDefaults(defineProps<Props>(), {
  loadingText: '玩命加载中',
  emptyText: '暂无数据',
  errorText: '加载失败，请稍后重试',
  emptySubText: '',
  errorSubText: '',
  emptyIcon: '',
  retry: null,
  actionText: '',
  action: null,
})

function onRetry() {
  props.retry?.()
}

function onAction() {
  props.action?.()
}
</script>

<template>
  <div class="state-block" :class="`state-block--${props.state}`">
    <!-- 加载中：金黄绸缎底 + 项目 LOGO -->
    <template v-if="props.state === 'loading'">
      <div class="state-block__logo">
        <BrandLogo :size="96" />
      </div>
      <p class="state-block__loading-text">{{ props.loadingText }}</p>
    </template>

    <!-- 空数据 -->
    <template v-else-if="props.state === 'empty'">
      <div class="state-block__mark">{{ props.emptyIcon || '蜀' }}</div>
      <p class="state-block__text">{{ props.emptyText }}</p>
      <p v-if="props.emptySubText" class="state-block__sub">{{ props.emptySubText }}</p>
      <GoldButton v-if="props.actionText" size="sm" @click="onAction">
        {{ props.actionText }}
      </GoldButton>
    </template>

    <!-- 错误 -->
    <template v-else>
      <div class="state-block__mark state-block__mark--error">
        <svg
          viewBox="0 0 24 24"
          width="30"
          height="30"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 3.4 21 19.8H3z" />
          <path d="M12 9.6v4.2M12 16.6v.1" />
        </svg>
      </div>
      <p class="state-block__text">{{ props.errorText }}</p>
      <p v-if="props.errorSubText" class="state-block__sub">{{ props.errorSubText }}</p>
      <GoldButton v-if="props.retry" size="sm" @click="onRetry">重试</GoldButton>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-block: 80px;
  text-align: center;
}

/* 蜀锦纹样图标 */
.state-block__mark {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background-color: rgba(212, 179, 116, 0.08);
  background-image: $pattern-brocade;
  font-family: $font-title;
  font-weight: 900;
  font-size: 30px;
  color: $color-gold;

  &--error {
    background-image: none;
    background-color: rgba(212, 179, 116, 0.1);
  }
}

.state-block__text {
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-h4;
  letter-spacing: 0.12em;
  color: $color-text-dark;
}

.state-block__sub {
  margin-top: -6px;
  font-family: $font-body;
  font-size: $fs-caption;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.55);
}

/* 加载中：金黄绸缎底 + 项目 LOGO */
.state-block--loading {
  gap: 22px;
  min-height: clamp(300px, 46vh, 520px);
  padding-block: clamp(40px, 8vh, 96px);
  border-radius: $radius-lg;
  overflow: hidden;
  @include silk-gold-bg;
}

.state-block__logo {
  display: grid;
  place-items: center;
  animation: sb-loading-spin 4.6s linear infinite;
}

/* 金色底上用柔和暗影让 LOGO 从绸缎上「浮」起来 */
.state-block__logo :deep(.brand-logo__mark) {
  filter: drop-shadow(0 8px 20px rgba(74, 52, 24, 0.42));
}

.state-block__loading-text {
  /* letter-spacing 会在末字后留白，用等量 padding-left 抵消，使文字视觉居中 */
  padding-left: 0.5em;
  font-family: $font-title;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.5em;
  color: rgba(58, 39, 17, 0.8);
  text-shadow: 0 1px 0 rgba(255, 246, 214, 0.55);
}

@keyframes sb-loading-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .state-block__logo {
    animation: none;
  }
}
</style>
