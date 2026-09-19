<script setup lang="ts">
/**
 * 蜀锦纹样加载动画
 * - 旋转的蜀锦菱形纹 + 中心「蜀」字
 * - duration 毫秒后开始淡出，并在淡出开始的同一刻派发 finish（父级据此启动首屏入场）
 */
import { onMounted, onUnmounted, ref } from 'vue'
import gsap from 'gsap'

interface Props {
  /** 展示时长（ms，默认 1200） */
  duration?: number
  /** 中心字 */
  char?: string
  /** 底部文案 */
  text?: string
}

const props = withDefaults(defineProps<Props>(), {
  duration: 1200,
  char: '蜀',
  text: '蜀韵 · 智游',
})

const emit = defineEmits<{ (e: 'finish'): void }>()

const rootEl = ref<HTMLElement | null>(null)
let timer = 0

onMounted(() => {
  timer = window.setTimeout(() => {
    // 先通知父级：可以开始首屏入场了
    emit('finish')

    const el = rootEl.value
    if (!el) {
      return
    }
    gsap.to(el, {
      opacity: 0,
      duration: 0.6,
      ease: 'power2.out',
      onComplete: () => {
        el.style.display = 'none'
      },
    })
  }, props.duration)
})

onUnmounted(() => {
  window.clearTimeout(timer)
})
</script>

<template>
  <div ref="rootEl" class="loading">
    <div class="loading__mark">
      <span class="loading__char">{{ props.char }}</span>
    </div>
    <p class="loading__text">{{ props.text }}</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.loading {
  position: fixed;
  inset: 0;
  z-index: $z-loader;
  @include flex-center(column);
  gap: 22px;
  background: $color-ink;
}

.loading__mark {
  display: grid;
  place-items: center;
  width: clamp(84px, 9vw, 128px);
  height: clamp(84px, 9vw, 128px);
  border: 1px solid rgba(201, 168, 106, 0.6);
  background-color: rgba(201, 168, 106, 0.06);
  background-image:
    repeating-linear-gradient(45deg, #{$color-gold} 0 1px, transparent 1px 12px),
    repeating-linear-gradient(-45deg, #{$color-gold} 0 1px, transparent 1px 12px);
  animation: loading-spin 3.4s linear infinite;
}

.loading__char {
  padding: 6px 10px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(30px, 3.6vw, 48px);
  color: $color-gold;
  background: $color-ink;
}

.loading__text {
  font-family: $font-title;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.5em;
  color: rgba(245, 241, 232, 0.7);
}

@keyframes loading-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
