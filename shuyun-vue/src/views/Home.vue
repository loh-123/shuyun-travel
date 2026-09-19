<script setup lang="ts">
/**
 * 首页容器
 * - 5 个 100vh 分节（Lenis 平滑滚动 + lenis/snap 整屏吸附）
 * - fixed 背景舞台：5 层背景叠放，按当前屏交叉淡入（真正的「淡入淡出切换背景图」）
 * - 四季屏把当前轮播图上报给舞台，因此它的背景同样参与交叉淡入
 * - GSAP + ScrollTrigger 负责各分节进入动画；右侧指示器 / 鼠标光晕 / 蜀锦加载动画
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Lenis from 'lenis'
import Snap from 'lenis/snap'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import 'lenis/dist/lenis.css'

import CursorGlow from '@/components/common/CursorGlow.vue'
import HeroSection from '@/components/home/HeroSection.vue'
import DataSection from '@/components/home/DataSection.vue'
import SeasonSection from '@/components/home/SeasonSection.vue'
import CultureSection from '@/components/home/CultureSection.vue'
import AiSection from '@/components/home/AiSection.vue'

gsap.registerPlugin(ScrollTrigger)

/** 是否启用整屏吸附：若觉得吸附过强可置为 false */
const SNAP_ENABLED = true
/** 加载动画时长（ms，3s 内） */
const LOADER_MS = 1200

/**
 * 背景图（占位为 Unsplash 实拍照片）
 * 换成自己的四川实景图：改成本地路径，详见 public/images/README.md
 */
const IMG = {
  hero: 'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1920&q=80',
  hero2x:
    'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=2560&q=80',
  data: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=1920&q=80',
  data2x:
    'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=2560&q=80',
  season: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=1920&q=80',
  season2x:
    'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=2560&q=80',
  ai: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1920&q=80',
  ai2x:
    'https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=2560&q=80',
}

interface StageLayer {
  key: string
  /** 背景图地址；为空时用 fill 纯色 */
  src?: string
  src2x?: string
  /** 无图时的底色/渐变 */
  fill?: string
  /** 叠层色调：dark 用墨蓝渐变，light 用浅色，none 不加渐变 */
  tone: 'dark' | 'light' | 'none'
}

const rootEl = ref<HTMLElement | null>(null)
const loaderEl = ref<HTMLElement | null>(null)

/** 当前屏下标 */
const activeIndex = ref(0)
/** 四季屏上报的当前背景图 */
const seasonBg = ref(IMG.season)
const seasonBg2x = ref(IMG.season2x)

/** 加载完成后通知首屏开始「书写」 */
const heroActive = ref(false)

const sections = [
  { key: 'hero', label: '蜀韵智游' },
  { key: 'data', label: '蜀地家底' },
  { key: 'season', label: '四季蜀韵' },
  { key: 'culture', label: '八大文化' },
  { key: 'ai', label: 'AI 管家' },
]

/** 背景舞台：每屏一层 */
const stageLayers = computed<StageLayer[]>(() => [
  { key: 'hero', src: IMG.hero, src2x: IMG.hero2x, tone: 'dark' },
  { key: 'data', src: IMG.data, src2x: IMG.data2x, tone: 'dark' },
  { key: 'season', src: seasonBg.value, src2x: seasonBg2x.value, tone: 'dark' },
  {
    key: 'culture',
    fill: 'radial-gradient(120% 90% at 12% 0%, rgba(201,168,106,0.22) 0%, rgba(232,227,216,0) 60%), #E8E3D8',
    tone: 'none',
  },
  { key: 'ai', src: IMG.ai, src2x: IMG.ai2x, tone: 'dark' },
])

let lenis: Lenis | null = null
let snap: Snap | null = null
let ctx: gsap.Context | null = null
let loaderTimer = 0

/** GSAP ticker 驱动 Lenis（官方推荐接法） */
function tickerFn(time: number) {
  lenis?.raf(time * 1000)
}

function goTo(index: number) {
  activeIndex.value = index
  if (snap) {
    snap.goTo(index)
  } else {
    lenis?.scrollTo(`#sec-${index}`, { duration: 1.2 })
  }
}

/** 四季屏轮播切换时上报当前背景图，让舞台同步做交叉淡入 */
function onSeasonBgChange(payload: { src: string; src2x: string }) {
  seasonBg.value = payload.src
  seasonBg2x.value = payload.src2x
}

onMounted(() => {
  const root = rootEl.value
  if (!root) {
    return
  }

  // ---------- 1. Lenis 平滑滚动 ----------
  lenis = new Lenis({ duration: 1.1, smoothWheel: true })
  lenis.on('scroll', ScrollTrigger.update)
  gsap.ticker.add(tickerFn)
  gsap.ticker.lagSmoothing(0)

  const sectionEls = Array.from(root.querySelectorAll<HTMLElement>('.home__section'))

  // ---------- 2. 整屏吸附（吸收异常也不影响可用性）----------
  if (SNAP_ENABLED && window.innerWidth > 900) {
    try {
      snap = new Snap(lenis, {
        type: 'mandatory',
        onSnapComplete: (item: { index?: number }) => {
          if (typeof item?.index === 'number') {
            activeIndex.value = item.index
          }
        },
      })
      snap.addElements(sectionEls)
    } catch (err) {
      console.warn('[home] 整屏吸附初始化失败，已退回平滑滚动', err)
      snap = null
    }
  }

  // ---------- 3. 当前屏跟踪 ----------
  ctx = gsap.context(() => {
    sectionEls.forEach((el, i) => {
      ScrollTrigger.create({
        trigger: el,
        start: 'top 50%',
        end: 'bottom 50%',
        onToggle: (self) => {
          if (self.isActive) {
            activeIndex.value = i
          }
        },
      })
    })
  }, root)

  // ---------- 4. 加载动画结束 → 首屏入场 ----------
  loaderTimer = window.setTimeout(() => {
    heroActive.value = true
    const loader = loaderEl.value
    if (loader) {
      gsap.to(loader, {
        opacity: 0,
        duration: 0.6,
        ease: 'power2.out',
        onComplete: () => {
          loader.style.display = 'none'
        },
      })
    }
  }, LOADER_MS)

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  window.clearTimeout(loaderTimer)
  gsap.ticker.remove(tickerFn)
  ctx?.revert()
  snap?.destroy()
  lenis?.destroy()
  lenis = null
  snap = null
})
</script>

<template>
  <div ref="rootEl" class="home">
    <!-- ================= 固定背景舞台：5 层交叉淡入 ================= -->
    <div class="home__stage" aria-hidden="true">
      <div
        v-for="(layer, i) in stageLayers"
        :key="layer.key"
        class="home__stage-layer"
        :class="{ 'is-active': i === activeIndex }"
      >
        <img
          v-if="layer.src"
          class="home__stage-img"
          :src="layer.src"
          :srcset="`${layer.src} 1920w, ${layer.src2x} 2560w`"
          sizes="100vw"
          alt=""
          :fetchpriority="i === 0 ? 'high' : 'auto'"
          decoding="async"
        />
        <div v-else class="home__stage-fill" :style="{ background: layer.fill }" />

        <div class="sy-overlay">
          <div v-if="layer.tone !== 'none'" class="sy-overlay__gradient" />
          <div v-if="layer.tone === 'light'" class="home__stage-light" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>
      </div>
    </div>

    <!-- ================= 5 个整屏分节 ================= -->
    <section id="sec-0" class="home__section">
      <HeroSection :active="heroActive" />
    </section>
    <section id="sec-1" class="home__section">
      <DataSection />
    </section>
    <section id="sec-2" class="home__section">
      <SeasonSection @bg-change="onSeasonBgChange" />
    </section>
    <section id="sec-3" class="home__section">
      <CultureSection />
    </section>
    <section id="sec-4" class="home__section">
      <AiSection />
    </section>

    <!-- ================= 右侧滚动指示器 ================= -->
    <nav class="home__dots" aria-label="分屏导航">
      <button
        v-for="(s, i) in sections"
        :key="s.key"
        class="home__dot"
        :class="{ 'is-active': i === activeIndex }"
        :aria-label="s.label"
        @click="goTo(i)"
      >
        <span class="home__dot-label">{{ s.label }}</span>
        <span class="home__dot-mark" />
      </button>
    </nav>

    <!-- ================= 鼠标跟随光晕 ================= -->
    <CursorGlow />

    <!-- ================= 蜀锦纹样加载动画 ================= -->
    <div ref="loaderEl" class="home__loader">
      <div class="home__loader-mark">
        <span class="home__loader-char">蜀</span>
      </div>
      <p class="home__loader-text">蜀韵 · 智游</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.home {
  position: relative;
  width: 100%;
  background: $color-ink;
}

/* ---------------- 背景舞台 ---------------- */
.home__stage {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.home__stage-layer {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 1s $ease-in-out-soft;
  will-change: opacity;

  &.is-active {
    opacity: 1;

    // 只有当前层的图会做缓慢推近，避免多张图同时动画
    .home__stage-img {
      animation: kenburns 20s ease-in-out infinite alternate;
    }
  }
}

.home__stage-img,
.home__stage-fill {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 浅色层（宣纸屏）上不加墨蓝渐变，避免压暗 */
.home__stage-light {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.24) 0%, rgba(232, 227, 216, 0) 100%);
}

@keyframes kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

/* ---------------- 分节（浮在舞台之上） ---------------- */
.home__section {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* ---------------- 右侧指示器 ---------------- */
.home__dots {
  position: fixed;
  top: 50%;
  right: clamp(12px, 2vw, 40px);
  z-index: $z-dots;
  display: flex;
  flex-direction: column;
  gap: 18px;
  transform: translateY(-50%);
}

.home__dot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 2px 0;

  &:hover .home__dot-label,
  &.is-active .home__dot-label {
    opacity: 1;
    transform: translateX(0);
  }

  &:hover .home__dot-mark,
  &.is-active .home__dot-mark {
    background: $color-gold;
    border-color: $color-gold;
    transform: scale(1.35);
  }
}

.home__dot-label {
  font-family: $font-body;
  font-size: 12px;
  letter-spacing: 0.2em;
  white-space: nowrap;
  color: $color-text-light;
  text-shadow: $shadow-text-soft;
  opacity: 0;
  transform: translateX(8px);
  transition:
    opacity $dur-fast $ease-out-expo,
    transform $dur-fast $ease-out-expo;
}

.home__dot-mark {
  width: 8px;
  height: 8px;
  border: 1px solid rgba(201, 168, 106, 0.7);
  border-radius: 50%;
  background: transparent;
  transition:
    background-color $dur-fast $ease-out-expo,
    transform $dur-base $ease-out-expo,
    border-color $dur-fast $ease-out-expo;
}

/* ---------------- 加载动画 ---------------- */
.home__loader {
  position: fixed;
  inset: 0;
  z-index: $z-loader;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  background: $color-ink;
}

.home__loader-mark {
  display: grid;
  place-items: center;
  width: clamp(84px, 9vw, 128px);
  height: clamp(84px, 9vw, 128px);
  border: 1px solid rgba(201, 168, 106, 0.6);
  background-color: rgba(201, 168, 106, 0.06);
  background-image:
    repeating-linear-gradient(45deg, #{$color-gold} 0 1px, transparent 1px 12px),
    repeating-linear-gradient(-45deg, #{$color-gold} 0 1px, transparent 1px 12px);
  animation: loaderSpin 3.4s linear infinite;
}

.home__loader-char {
  padding: 6px 10px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(30px, 3.6vw, 48px);
  color: $color-gold;
  background: $color-ink;
}

.home__loader-text {
  font-family: $font-title;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.5em;
  color: rgba(245, 241, 232, 0.7);
}

@keyframes loaderSpin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  // 小屏取消整屏锁定，让内容自然高度（避免长内容被裁切）
  .home__section {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .home__dots {
    right: 8px;
    gap: 14px;
  }

  .home__dot-label {
    display: none;
  }
}
</style>
