<script setup lang="ts">
/**
 * 篆刻印章装饰
 * - 绝对定位于最近的定位祖先（页面 Hero 设为 position: relative 即可）
 * - 红色方印 + 金色「蜀」字；纯装饰、不接收事件
 */
</script>

<template>
  <div class="seal-mark" aria-hidden="true">
    <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <rect x="2.5" y="2.5" width="59" height="59" rx="7" fill="#7A1A22" stroke="#D4B374" stroke-width="1.5" />
      <text
        x="32"
        y="45"
        text-anchor="middle"
        font-size="30"
        font-weight="900"
        fill="#D4B374"
        font-family="'Source Han Serif SC', 'Noto Serif SC', serif"
      >
        蜀
      </text>
    </svg>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.seal-mark {
  position: absolute;
  top: clamp(88px, 15vh, 168px);
  right: clamp(20px, 5vw, 96px);
  z-index: $z-content;
  width: clamp(52px, 4.4vw, 64px);
  pointer-events: none;
  opacity: 0.92;
  filter: drop-shadow(0 6px 20px rgba(0, 0, 0, 0.35));

  svg {
    display: block;
    width: 100%;
    height: auto;
  }
}
</style>
