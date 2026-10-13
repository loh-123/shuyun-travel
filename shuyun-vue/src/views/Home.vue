<script setup lang="ts">
/**
 * 首页容器
 * - 6 个 100vh 分节（Lenis 平滑滚动 + lenis/snap 整屏吸附）
 * - fixed 背景舞台：6 层背景叠放，按当前屏交叉淡入（真正的「淡入淡出切换背景图」）
 * - 四季屏把当前轮播图上报给舞台，因此它的背景同样参与交叉淡入
 * - 公共能力全部复用：useLenis（平滑滚动）/ ScrollIndicator（右侧指示器）/ Loading（加载动画）
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Snap from 'lenis/snap'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import { initLenis, lenisScrollTo } from '@/composables/useLenis'
import ScrollIndicator from '@/components/common/ScrollIndicator.vue'
import Loading from '@/components/common/Loading.vue'
import HeroSection from '@/components/home/HeroSection.vue'
import DataSection from '@/components/home/DataSection.vue'
import SeasonSection from '@/components/home/SeasonSection.vue'
import HotScenicSection from '@/components/home/HotScenicSection.vue'
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

/** 当前屏下标 */
const activeIndex = ref(0)
/** 四季屏上报的当前背景图 */
const seasonBg = ref(IMG.season)
const seasonBg2x = ref(IMG.season2x)

/** 加载完成后通知首屏开始「书写」 */
const heroActive = ref(false)

const sections = [
  { key: 'hero', label: '蜀韵管家' },
  { key: 'data', label: '蜀地家底' },
  { key: 'season', label: '四季蜀韵' },
  { key: 'hot', label: '热门景点' },
  { key: 'culture', label: '八大文化' },
  { key: 'ai', label: 'AI 管家' },
]

/** 背景舞台：每屏一层 */
const stageLayers = computed<StageLayer[]>(() => [
  { key: 'hero', src: IMG.hero, src2x: IMG.hero2x, tone: 'dark' },
  { key: 'data', src: IMG.data, src2x: IMG.data2x, tone: 'dark' },
  { key: 'season', src: seasonBg.value, src2x: seasonBg2x.value, tone: 'dark' },
  // 热门景点 / 八大文化：四川剪影整屏背景（背景样式见 .home__stage-fill--hot / --culture）
  { key: 'hot', tone: 'none' },
  { key: 'culture', tone: 'none' },
  { key: 'ai', src: IMG.ai, src2x: IMG.ai2x, tone: 'dark' },
])

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let snap: any = null
let sectionCtx: gsap.Context | null = null

/** 点击右侧指示器跳转到第 index 屏 */
function goTo(index: number) {
  activeIndex.value = index
  if (snap) {
    snap.goTo(index)
  } else {
    lenisScrollTo(`#sec-${index}`, { duration: 1.2 })
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

  // 平滑滚动单例（main.ts 已初始化，这里再取一次，保证单独打开本页也可用）
  const lenis = initLenis()
  const sectionEls = Array.from(root.querySelectorAll<HTMLElement>('.home__section'))

  // ---------- 整屏吸附（吸收异常也不影响可用性）----------
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

  // ---------- 当前屏跟踪 ----------
  sectionCtx = gsap.context(() => {
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

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  sectionCtx?.revert()
  snap?.destroy()
  snap = null
})
</script>

<template>
  <div ref="rootEl" class="home">
    <!-- ================= 固定背景舞台：6 层交叉淡入 ================= -->
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
        <div
          v-else
          class="home__stage-fill"
          :class="`home__stage-fill--${layer.key}`"
          :style="{ background: layer.fill }"
        />

        <div class="sy-overlay">
          <div v-if="layer.tone !== 'none'" class="sy-overlay__gradient" />
          <div v-if="layer.tone === 'light'" class="home__stage-light" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>
      </div>
    </div>

    <!-- ================= 6 个整屏分节 ================= -->
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
      <HotScenicSection />
    </section>
    <section id="sec-4" class="home__section">
      <CultureSection />
    </section>
    <section id="sec-5" class="home__section">
      <AiSection />
    </section>

    <!-- ================= 右侧滚动指示器（公共组件） ================= -->
    <ScrollIndicator :items="sections" :active="activeIndex" @select="goTo" />

    <!-- ================= 蜀锦纹样加载动画（公共组件） ================= -->
    <Loading :duration="LOADER_MS" @finish="heroActive = true" />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

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
  transition: opacity 1s $ease-in-out;
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

/* 热门景点 / 八大文化两屏：四川剪影整屏背景 */
.home__stage-fill--hot,
.home__stage-fill--culture {
  background-color: $color-shuxiu;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;

  // 柔化层：顶部略提亮保证深色标题可读，向下渐隐让底图通透
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(
      180deg,
      rgba(247, 243, 234, 0.42) 0%,
      rgba(247, 243, 234, 0.12) 55%,
      rgba(247, 243, 234, 0.03) 100%
    );
  }
}

/* 热门景点屏：蓝色四川剪影 */
.home__stage-fill--hot {
  background-image: url('/images/scenic-bg.png');
}

/* 八大文化屏：红色四川剪影 */
.home__stage-fill--culture {
  background-image: url('/images/culture-bg.png');
}

/* 浅色层（宣纸屏）上不加墨蓝渐变，避免压暗 */
.home__stage-light {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(var(--sy-surface-rgb), 0.24) 0%, rgba(var(--sy-paper-rgb), 0) 100%);
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

/* ---------------- 响应式 ---------------- */
@include responsive(900px) {
  // 小屏取消整屏锁定，让内容自然高度（避免长内容被裁切）
  .home__section {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }
}
</style>
