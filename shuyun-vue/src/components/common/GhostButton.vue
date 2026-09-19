<script setup lang="ts">
/**
 * 幽灵次按钮
 * - 复用全局 .sy-btn-text：纯文字 + 下划线 hover 从左向右展开
 * - 默认插槽放文字
 */
interface Props {
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{ (e: 'click', ev: MouseEvent): void }>()
</script>

<template>
  <button
    type="button"
    class="ghost-button sy-btn-text"
    :disabled="props.disabled"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped lang="scss">
.ghost-button {
  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}
</style>
