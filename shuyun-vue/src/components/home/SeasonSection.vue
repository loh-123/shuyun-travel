<script setup lang="ts">
/**
 * 第三屏：四季蜀韵轮播
 * - Swiper 全屏淡入淡出轮播（春都江堰 / 夏九寨 / 秋稻城 / 冬西岭）
 * - 左侧大号季节字（金色描边），底部季节切换按钮与轮播联动
 */
import { onMounted, onUnmounted, ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, EffectFade, Navigation, Pagination } from 'swiper/modules'
import 'swiper/css'
import 'swiper/css/effect-fade'
import 'swiper/css/navigation'
import 'swiper/css/pagination'

gsap.registerPlugin(ScrollTrigger)

const swiperModules = [Autoplay, EffectFade, Navigation, Pagination]

interface Season {
  key: string
  season: string
  en: string
  place: string
  desc: string
  img: string
  img2x: string
}

/** 换成自己的图：把 img / img2x 改成 /images/season-xxx.webp 即可 */
const SEASONS: Season[] = [
  {
    key: 'spring',
    season: '春',
    en: 'Spring',
    place: '都江堰',
    desc: '岷江初暖，离堆公园的樱花与水声一同醒来。',
    img: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=1920&q=80',
    img2x: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=2560&q=80',
  },
  {
    key: 'summer',
    season: '夏',
    en: 'Summer',
    place: '九寨沟',
    desc: '翠海叠瀑，彩林之外是盛夏最清冽的一汪蓝。',
    img: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=1920&q=80',
    img2x: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=2560&q=80',
  },
  {
    key: 'autumn',
    season: '秋',
    en: 'Autumn',
    place: '稻城亚丁',
    desc: '雪山之下的草甸转金，是蓝色星球最后的净土。',
    img: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1920&q=80',
    img2x: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=2560&q=80',
  },
  {
    key: 'winter',
    season: '冬',
    en: 'Winter',
    place: '西岭雪山',
    desc: '窗含西岭千秋雪，成都人自己的雪山与雪场。',
    img: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=1920&q=80',
    img2x: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=2560&q=80',
  },
]

const rootEl = ref<HTMLElement | null>(null)
const activeIndex = ref(0)
/** Swiper 实例（用于点击季节按钮跳转） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let swiperRef: any = null

/** 把当前轮播图上报给父级（背景由 Home.vue 的 fixed 舞台统一渲染，才能交叉淡入） */
const emit = defineEmits<{
  (e: 'bg-change', payload: { src: string; src2x: string }): void
}>()

function announceBg(index: number) {
  const item = SEASONS[index] ?? SEASONS[0]
  emit('bg-change', { src: item.img, src2x: item.img2x })
}

let ctx: gsap.Context | null = null

function onSwiper(swiper: unknown) {
  swiperRef = swiper
}

function onSlideChange(swiper: { realIndex: number }) {
  activeIndex.value = swiper.realIndex
  announceBg(swiper.realIndex)
}

function goTo(index: number) {
  swiperRef?.slideTo?.(index)
}

onMounted(() => {
  const root = rootEl.value
  if (!root) {
    return
  }
  // 首屏即上报一次，避免舞台在进入本屏前没有对应背景
  announceBg(0)
  ctx = gsap.context(() => {
    gsap.from('.season__animate', {
      y: 40,
      opacity: 0,
      duration: 1,
      ease: 'power3.out',
      stagger: 0.1,
      scrollTrigger: { trigger: root, start: 'top 62%', once: true },
    })
  }, root)
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  ctx?.revert()
})
</script>

<template>
  <div ref="rootEl" class="season">
    <!-- 全屏淡入淡出轮播 -->
    <Swiper
      class="season__swiper"
      :modules="swiperModules"
      effect="fade"
      :fade-effect="{ crossFade: true }"
      :speed="1200"
      :loop="true"
      :autoplay="{ delay: 5200, disableOnInteraction: false }"
      :navigation="true"
      :pagination="{ clickable: true }"
      @swiper="onSwiper"
      @slide-change="onSlideChange"
    >
      <SwiperSlide v-for="s in SEASONS" :key="s.key">
        <!-- 图片统一由 Home.vue 的固定背景舞台渲染（才能与前后屏交叉淡入），
             这里只作「控制器」：滑动/按钮切屏时把当前图上报给父级 -->
        <div class="season__slide" :data-season="s.season" />
      </SwiperSlide>
    </Swiper>

    <!-- 文字层 -->
    <div class="sy-container season__inner">
      <p class="sy-eyebrow season__animate">Four Seasons</p>

      <div class="season__body">
        <div class="season__big season__animate">{{ SEASONS[activeIndex].season }}</div>
        <div class="season__info">
          <h2 class="sy-title sy-on-dark season__place season__animate">
            {{ SEASONS[activeIndex].place }}
          </h2>
          <p class="sy-text sy-on-dark season__desc season__animate">
            {{ SEASONS[activeIndex].desc }}
          </p>
        </div>
      </div>

      <!-- 季节切换 -->
      <div class="season__tabs season__animate">
        <button
          v-for="(s, i) in SEASONS"
          :key="s.key"
          class="season__tab"
          :class="{ 'is-active': i === activeIndex }"
          @click="goTo(i)"
        >
          <span class="season__tab-cn">{{ s.season }}</span>
          <span class="season__tab-en">{{ s.en }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.season {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.season__swiper {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;

  :deep(.swiper-button-next),
  :deep(.swiper-button-prev) {
    color: $color-gold;
  }

  :deep(.swiper-pagination-bullet) {
    background: $color-text-light;
    opacity: 0.45;
  }

  :deep(.swiper-pagination-bullet-active) {
    background: $color-gold;
    opacity: 1;
  }
}

.season__slide {
  width: 100%;
  height: 100%;
}

.season__inner {
  position: relative;
  z-index: $z-content;
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding-block: clamp(48px, 10vh, 140px);
}

.season__body {
  display: flex;
  align-items: center;
  gap: clamp(20px, 3vw, 56px);
  margin-top: clamp(16px, 3vh, 40px);
}

/* 大号季节字：金色描边 */
.season__big {
  font-family: $font-title;
  font-size: clamp(96px, 16vw, 260px);
  font-weight: 900;
  line-height: 0.9;
  color: transparent;
  -webkit-text-stroke: 1.5px $color-gold;
  text-shadow: $shadow-text;
}

.season__info {
  display: flex;
  flex-direction: column;
  gap: clamp(10px, 1.6vh, 20px);
}

.season__place {
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.season__desc {
  color: $color-text-light;
  opacity: 0.88;
  text-shadow: $shadow-text-soft;
  max-width: 46ch;
}

/* ---------------- 季节切换 ---------------- */
.season__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(12px, 2vw, 32px);
  margin-top: clamp(28px, 5vh, 64px);
  padding-top: clamp(16px, 2vh, 28px);
  border-top: 1px solid rgba(201, 168, 106, 0.28);
}

.season__tab {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 4px;
  color: rgba(245, 241, 232, 0.72);
  transition: color $dur-base $ease-out-expo;

  &:hover {
    color: $color-gold;
  }

  &.is-active {
    color: $color-gold;
  }
}

.season__tab-cn {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 24px);
  letter-spacing: 0.1em;
}

.season__tab-en {
  font-family: $font-en-body;
  font-size: $fs-small;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 768px) {
  .season__body {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .season__big {
    font-size: clamp(72px, 22vw, 140px);
  }

  .season__inner {
    justify-content: flex-end;
    padding-bottom: clamp(56px, 12vh, 120px);
  }
}
</style>
