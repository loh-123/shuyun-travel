<script setup lang="ts">
/**
 * 鼠标跟随光晕（全局公共组件）
 * - 直径 400px 的径向渐变圆，soft-light 混色，营造「灯下看绢」的暖调
 * - 位置追踪交给 useCursor（rAF + lerp 缓动），直接写 transform，不触发组件重渲染
 * - 触屏设备自动不启用
 */
import { onMounted, ref } from 'vue'
import { useCursor } from '@/composables/useCursor'

const glowEl = ref<HTMLElement | null>(null)

const { bind, enabled } = useCursor({ lerp: 0.12 })

onMounted(() => {
  // useCursor 的 onMounted 先于本组件的 onMounted 执行，此时 enabled 已就绪
  if (enabled.value) {
    bind(glowEl.value)
  }
})
</script>

<template>
  <div ref="glowEl" class="cursor-glow" aria-hidden="true" />
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.cursor-glow {
  position: fixed;
  top: 0;
  left: 0;
  z-index: $z-cursor;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  pointer-events: none;
  will-change: transform;
  background: radial-gradient(
    circle,
    $color-gold-soft 0%,
    rgba(201, 168, 106, 0.06) 45%,
    rgba(201, 168, 106, 0) 70%
  );
  mix-blend-mode: soft-light;
}
</style>
