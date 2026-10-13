<script setup lang="ts">
/**
 * 章节标题
 * - 结构：小标签（英文） + 主标题 + 描述
 * - variant：on-dark（深底浅字）/ on-light（浅底深字，标题用蜀锦红）
 * - decoration：标题上方金色装饰短横线（默认关闭，避免影响既有页面外观）
 * - animateClass：把入场动画类名透传到三个元素上，便于父级统一做 stagger 入场
 */
interface Props {
  /** 英文小标签 */
  eyebrow?: string
  /** 主标题 */
  title?: string
  /** 描述 */
  desc?: string
  /** 色调：深底 / 浅底 */
  variant?: 'on-dark' | 'on-light'
  /** 对齐方式 */
  align?: 'left' | 'center'
  /** 透传到三个元素上的动画类名 */
  animateClass?: string
  /** 是否显示标题上方的金色装饰线 */
  decoration?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'on-dark',
  align: 'left',
  decoration: false,
})

const toneClass = `is-${props.variant}`
</script>

<template>
  <div
    class="section-title"
    :class="[`section-title--${props.align}`, `section-title--${props.variant}`]"
  >
    <p v-if="props.eyebrow" class="sy-eyebrow" :class="props.animateClass">
      {{ props.eyebrow }}
    </p>

    <h2
      v-if="props.title || $slots.title"
      class="sy-title section-title__title"
      :class="[toneClass, props.animateClass]"
    >
      <span v-if="props.decoration" class="section-title__line" aria-hidden="true" />
      <!-- 需要换行/富文本时可自定义 title 插槽 -->
      <slot name="title">{{ props.title }}</slot>
    </h2>

    <p v-if="props.desc" class="sy-text section-title__desc" :class="[toneClass, props.animateClass]">
      {{ props.desc }}
    </p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.section-title {
  &--center {
    text-align: center;
  }
}

/* 主标题 */
.section-title__title {
  position: relative;
  margin-top: clamp(10px, 1.6vh, 22px);

  &.is-on-dark {
    color: $color-text-light;
    text-shadow: $shadow-text;
  }

  &.is-on-light {
    color: var(--sy-accent);
  }
}

/* 描述 */
.section-title__desc {
  margin-top: clamp(13px, 1.9vh, 24px);
  /* 字号在原基础上放大 0.5× */
  font-size: calc(#{$fs-body} * 1.5);

  &.is-on-dark {
    color: $color-text-light;
    opacity: 0.86;
    text-shadow: $shadow-text-soft;
  }

  &.is-on-light {
    color: $color-text-dark;
    opacity: 0.78;
  }
}

/* 金色装饰线：短横线 + 菱形收尾 */
.section-title__line {
  display: inline-block;
  width: clamp(28px, 3vw, 56px);
  height: 1px;
  margin-right: 14px;
  vertical-align: 0.32em;
  background: linear-gradient(90deg, $color-gold, rgba(212, 179, 116, 0.2));
  position: relative;

  &::after {
    content: '';
    position: absolute;
    top: 50%;
    left: -5px;
    width: 6px;
    height: 6px;
    transform: translateY(-50%) rotate(45deg);
    background: $color-gold;
  }
}

@include responsive(md) {
  .section-title--center {
    text-align: left;
  }
}
</style>
