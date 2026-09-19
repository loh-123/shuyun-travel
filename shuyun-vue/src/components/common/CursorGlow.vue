<script setup lang="ts">
/**
 * 鼠标跟随光晕
 * - 直径 400px 的径向渐变圆，混色模式 soft-light，营造「灯下看绢」的暖调
 * - 用 rAF + lerp 做缓动跟随，避免生硬
 * - 触屏设备（无 hover）自动不启用
 */
import { onMounted, onUnmounted, ref } from 'vue'

const glowEl = ref<HTMLElement | null>(null)

/** 目标位置（鼠标） */
let targetX = 0
let targetY = 0
/** 当前位置（缓动结果） */
let currentX = 0
let currentY = 0
let rafId = 0
let enabled = false

function onPointerMove(e: MouseEvent) {
  targetX = e.clientX
  targetY = e.clientY
}

function loop() {
  const el = glowEl.value
  if (el) {
    // 线性插值：每帧靠近目标 12%
    currentX += (targetX - currentX) * 0.12
    currentY += (targetY - currentY) * 0.12
    el.style.transform = `translate3d(${currentX}px, ${currentY}px, 0) translate(-50%, -50%)`
  }
  rafId = requestAnimationFrame(loop)
}

onMounted(() => {
  // 触屏设备不启用光晕
  if (window.matchMedia('(hover: none)').matches) {
    return
  }
  enabled = true
  targetX = currentX = window.innerWidth / 2
  targetY = currentY = window.innerHeight / 2
  window.addEventListener('mousemove', onPointerMove, { passive: true })
  rafId = requestAnimationFrame(loop)
})

onUnmounted(() => {
  if (enabled) {
    window.removeEventListener('mousemove', onPointerMove)
  }
  if (rafId) {
    cancelAnimationFrame(rafId)
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
