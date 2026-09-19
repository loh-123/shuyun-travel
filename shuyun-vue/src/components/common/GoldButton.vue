<script setup lang="ts">
/**
 * 金色主按钮
 * - 复用全局 .sy-btn：金色描边 + 透明底 + 金字；hover 金色填充 + 墨蓝字
 * - 默认插槽放文字，icon-left / icon-right 放左右图标
 * - 尺寸 md 与全局 .sy-btn 完全一致，保证既有页面外观不变
 */
interface Props {
  /** 尺寸 */
  size?: 'sm' | 'md' | 'lg'
  /** 是否占满整行 */
  block?: boolean
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  block: false,
  disabled: false,
})

const emit = defineEmits<{ (e: 'click', ev: MouseEvent): void }>()
</script>

<template>
  <button
    type="button"
    class="gold-button sy-btn"
    :class="[`gold-button--${props.size}`, { 'is-block': props.block }]"
    :disabled="props.disabled"
    @click="emit('click', $event)"
  >
    <slot name="icon-left" />
    <slot />
    <slot name="icon-right" />
  </button>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.gold-button {
  /* md 沿用 .sy-btn 默认值（14px 34px / $fs-small），不做任何覆盖 */

  &--sm {
    padding: 9px 22px;
    font-size: $fs-small;
  }

  &--lg {
    padding: 17px 42px;
    font-size: $fs-body;
  }

  &.is-block {
    width: 100%;
    justify-content: center;
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}
</style>
