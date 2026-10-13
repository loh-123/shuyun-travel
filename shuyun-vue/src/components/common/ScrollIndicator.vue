<script setup lang="ts">
/**
 * 右侧滚动指示器
 * - 竖排圆点，当前屏金色高亮；hover 时展开中文标签
 * - 点击派发 select 事件，由父级决定跳转方式（Lenis / lenis-snap）
 */
interface IndicatorItem {
  /** 唯一标识 */
  key: string
  /** hover 显示的中文标签 */
  label: string
}

interface Props {
  items: IndicatorItem[]
  /** 当前高亮下标 */
  active: number
}

const props = defineProps<Props>()

const emit = defineEmits<{ (e: 'select', index: number): void }>()
</script>

<template>
  <nav class="scroll-indicator" aria-label="分屏导航">
    <button
      v-for="(item, i) in props.items"
      :key="item.key"
      type="button"
      class="scroll-indicator__item"
      :class="{ 'is-active': i === props.active }"
      :aria-label="item.label"
      @click="emit('select', i)"
    >
      <span class="scroll-indicator__label">{{ item.label }}</span>
      <span class="scroll-indicator__mark" />
    </button>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.scroll-indicator {
  position: fixed;
  top: 50%;
  right: clamp(12px, 2vw, 40px);
  z-index: $z-dots;
  display: flex;
  flex-direction: column;
  /* 列间距（较上一版缩小 0.5×） */
  gap: 20px;
  transform: translateY(-50%);
}

.scroll-indicator__item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 2px 0;

  &:hover .scroll-indicator__label,
  &.is-active .scroll-indicator__label {
    opacity: 1;
    transform: translateX(0);
  }

  &:hover .scroll-indicator__mark,
  &.is-active .scroll-indicator__mark {
    background: $color-gold;
    border-color: $color-gold;
    transform: scale(1.35);
  }
}

.scroll-indicator__label {
  font-family: $font-body;
  /* 字号（较上一版放大 0.5×） */
  font-size: 20.25px;
  letter-spacing: 0.2em;
  white-space: nowrap;
  color: $color-text-light;
  text-shadow: $shadow-text-soft;
  opacity: 0;
  transform: translateX(8px);
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

.scroll-indicator__mark {
  /* 空心圆（较上一版放大 0.5×） */
  width: 18px;
  height: 18px;
  border: 2.25px solid rgba(212, 179, 116, 0.7);
  border-radius: 50%;
  background: transparent;
  transition:
    background-color $duration-fast $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    border-color $duration-fast $ease-out-expo;
}

@include responsive(900px) {
  .scroll-indicator {
    right: 8px;
    gap: 12px;
  }

  .scroll-indicator__label {
    display: none;
  }
}
</style>
