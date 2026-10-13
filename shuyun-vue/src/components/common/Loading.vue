<script setup lang="ts">
/**
 * 加载动画
 * - 中心展示项目 LOGO（太阳神鸟 · 玉璧团章 BrandLogo），并缓慢旋转
 * - duration 毫秒后开始淡出，并在淡出开始的同一刻派发 finish（父级据此启动首屏入场）
 */
import { onMounted, onUnmounted, ref } from 'vue'
import gsap from 'gsap'
import BrandLogo from '@/components/common/BrandLogo.vue'

interface Props {
  /** 展示时长（ms，默认 1200） */
  duration?: number
  /** 项目 LOGO 边长（px） */
  size?: number
  /** 底部文案 */
  text?: string
}

const props = withDefaults(defineProps<Props>(), {
  duration: 1200,
  size: 128,
  text: '玩命加载中',
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
    <!-- 项目 LOGO（太阳神鸟 · 玉璧团章） -->
    <div class="loading__mark">
      <BrandLogo :size="props.size" />
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
  /* 高级金黄绸缎底 */
  @include silk-gold-bg;
}

/* 项目 LOGO 缓慢旋转，营造加载感 */
.loading__mark {
  display: grid;
  place-items: center;
  animation: loading-spin 4.6s linear infinite;
}

/* 金色底上用柔和暗影让 LOGO 从绸缎上「浮」起来，避免金对金发糊 */
.loading__mark :deep(.brand-logo__mark) {
  filter: drop-shadow(0 8px 20px rgba(74, 52, 24, 0.42));
}

.loading__text {
  /* letter-spacing 会在末字后留白，用等量 padding-left 抵消，使文字视觉居中 */
  padding-left: 0.5em;
  font-family: $font-title;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.5em;
  color: rgba(58, 39, 17, 0.8);
  text-shadow: 0 1px 0 rgba(255, 246, 214, 0.55);
}

@keyframes loading-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .loading__mark {
    animation: none;
  }
}
</style>
