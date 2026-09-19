<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import request from '@/api/request'

gsap.registerPlugin(ScrollTrigger)

const router = useRouter()

/** 根元素 / 各屏元素 */
const rootEl = ref<HTMLElement | null>(null)
const heroEl = ref<HTMLElement | null>(null)
const heroBgEl = ref<HTMLElement | null>(null)
const heroContentEl = ref<HTMLElement | null>(null)
const statsEl = ref<HTMLElement | null>(null)
const hotEl = ref<HTMLElement | null>(null)
const cultureEl = ref<HTMLElement | null>(null)

const HERO_BG =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1920&q=80'

/** 兜底占位图（Unsplash），接口不可用时使用 */
const UNSPLASH = [
  'https://images.unsplash.com/photo-1506905925346-21bda4d32df4',
  'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05',
  'https://images.unsplash.com/photo-1441974231531-c6227db76b6e',
  'https://images.unsplash.com/photo-1469474968028-56623f02e42e',
]

function fb(i: number, w = 900) {
  return `${UNSPLASH[i % UNSPLASH.length]}?auto=format&fit=crop&w=${w}&q=80`
}

interface HotItem {
  id: number
  name: string
  cityName?: string
  score?: number
  coverImage?: string
}

interface CultureItem {
  id: number
  name: string
  category?: string
  coverImage?: string
}

/** 数据概览：num 会被 GSAP 做数字滚动 */
const stats = ref([
  { num: 0, target: 21, label: '市州' },
  { num: 0, target: 10, label: '大景点' },
  { num: 0, target: 8, label: '大文化' },
  { num: 0, target: 365, label: '天陪伴' },
])

const fallbackHot: HotItem[] = [
  { id: 1, name: '九寨沟', cityName: '阿坝', score: 4.9, coverImage: fb(0) },
  { id: 2, name: '峨眉山', cityName: '乐山', score: 4.8, coverImage: fb(1) },
  { id: 3, name: '稻城亚丁', cityName: '甘孜', score: 4.9, coverImage: fb(2) },
  { id: 4, name: '都江堰', cityName: '成都', score: 4.7, coverImage: fb(3) },
  { id: 5, name: '乐山大佛', cityName: '乐山', score: 4.7, coverImage: fb(0) },
  { id: 6, name: '青城山', cityName: '成都', score: 4.6, coverImage: fb(1) },
]

const fallbackCulture: CultureItem[] = [
  { id: 1, name: '川剧变脸', category: '戏曲', coverImage: fb(0) },
  { id: 2, name: '蜀绣', category: '传统技艺', coverImage: fb(1) },
  { id: 3, name: '川菜', category: '饮食文化', coverImage: fb(2) },
  { id: 4, name: '茶马古道', category: '历史文化', coverImage: fb(3) },
  { id: 5, name: '三国文化', category: '历史文化', coverImage: fb(0) },
  { id: 6, name: '三星堆', category: '考古文明', coverImage: fb(1) },
  { id: 7, name: '大熊猫', category: '自然生态', coverImage: fb(2) },
  { id: 8, name: '酒文化', category: '饮食文化', coverImage: fb(3) },
]

/** 先用兜底数据渲染，接口成功后再替换（游客未登录时也不会空屏） */
const hotList = ref<HotItem[]>([...fallbackHot])
const cultureList = ref<CultureItem[]>([...fallbackCulture])

let ctx: gsap.Context | null = null

onMounted(async () => {
  await loadData()
  await nextTick()
  setupAnimation()
})

onUnmounted(() => {
  ctx?.revert()
})

async function loadData() {
  try {
    const res = await request.get('/scenic/hot', { silent: true })
    if (Array.isArray(res.data) && res.data.length) {
      hotList.value = res.data
    }
  } catch {
    // 保持兜底数据
  }

  try {
    const res = await request.get('/culture/list', { silent: true })
    if (Array.isArray(res.data) && res.data.length) {
      cultureList.value = res.data
    }
  } catch {
    // 保持兜底数据
  }
}

function setupAnimation() {
  const root = rootEl.value
  if (!root) {
    return
  }

  ctx = gsap.context(() => {
    // 首屏背景视差
    if (heroBgEl.value && heroEl.value) {
      gsap.to(heroBgEl.value, {
        yPercent: 16,
        ease: 'none',
        scrollTrigger: {
          trigger: heroEl.value,
          start: 'top top',
          end: 'bottom top',
          scrub: true,
        },
      })
    }

    // 首屏文字入场
    if (heroContentEl.value) {
      gsap.from(heroContentEl.value, {
        y: 40,
        opacity: 0,
        duration: 1,
        ease: 'power3.out',
        delay: 0.15,
      })
    }

    // 数字滚动
    stats.value.forEach((item) => {
      gsap.fromTo(
        item,
        { num: 0 },
        {
          num: item.target,
          duration: 1.8,
          ease: 'power2.out',
          snap: { num: 1 },
          scrollTrigger: { trigger: statsEl.value, start: 'top 80%', once: true },
        },
      )
    })

    // 各屏内容淡入
    fadeIn(statsEl.value, '.stat-card')
    fadeIn(hotEl.value, '.hot-card')
    fadeIn(cultureEl.value, '.culture-card')
  }, root)

  ScrollTrigger.refresh()
}

/** 让某屏内的卡片依次淡入 */
function fadeIn(section: HTMLElement | null, selector: string) {
  if (!section) {
    return
  }
  const items = Array.from(section.querySelectorAll<HTMLElement>(selector))
  gsap.from(items.length ? items : [section], {
    y: 44,
    opacity: 0,
    duration: 0.85,
    ease: 'power3.out',
    stagger: 0.08,
    scrollTrigger: { trigger: section, start: 'top 82%', once: true },
  })
}

/** 向下箭头：滚动到第二屏 */
function scrollToNext() {
  statsEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goAssistant() {
  router.push('/assistant')
}
</script>

<template>
  <div ref="rootEl" class="home">
    <!-- ================= 第一屏：主视觉 ================= -->
    <section ref="heroEl" class="hero">
      <div ref="heroBgEl" class="hero__bg" :style="{ backgroundImage: `url(${HERO_BG})` }" />
      <div class="hero__mask" />

      <div ref="heroContentEl" class="hero__content">
        <h1 class="hero__title">蜀韵·智游</h1>
        <p class="hero__subtitle">AI 懂你的巴蜀旅行管家</p>
        <button class="hero__arrow" @click="scrollToNext">
          <span class="hero__arrow-icon">↓</span>
        </button>
      </div>
    </section>

    <!-- ================= 第二屏：数据概览 ================= -->
    <section ref="statsEl" class="stats">
      <div class="stats__grid">
        <div v-for="item in stats" :key="item.label" class="stat-card">
          <div class="stat-card__num">{{ item.num }}</div>
          <div class="stat-card__label">{{ item.label }}</div>
        </div>
      </div>
    </section>

    <!-- ================= 第三屏：热门景点 ================= -->
    <section ref="hotEl" class="section">
      <header class="section__head">
        <h2 class="section__title">热门景点</h2>
        <span class="section__more" @click="router.push('/scenic')">全部景点 →</span>
      </header>

      <div class="hot-row">
        <article
          v-for="item in hotList"
          :key="item.id"
          class="hot-card"
          @click="router.push(`/scenic/${item.id}`)"
        >
          <div class="hot-card__img" :style="{ backgroundImage: `url(${item.coverImage})` }" />
          <div class="hot-card__body">
            <h3 class="hot-card__name">{{ item.name }}</h3>
            <div class="hot-card__meta">
              <span>{{ item.cityName }}</span>
              <span class="hot-card__score">{{ item.score }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- ================= 第四屏：八大文化 ================= -->
    <section ref="cultureEl" class="section section--last">
      <header class="section__head">
        <h2 class="section__title">八大文化</h2>
        <span class="section__more" @click="router.push('/culture')">全部文化 →</span>
      </header>

      <div class="culture-grid">
        <article
          v-for="item in cultureList"
          :key="item.id"
          class="culture-card"
          @click="router.push(`/culture/${item.id}`)"
        >
          <div class="culture-card__img" :style="{ backgroundImage: `url(${item.coverImage})` }" />
          <div class="culture-card__info">
            <h3 class="culture-card__name">{{ item.name }}</h3>
            <span class="culture-card__cat">{{ item.category }}</span>
          </div>
        </article>
      </div>
    </section>

    <!-- ================= 悬浮 AI 管家入口 ================= -->
    <button class="ai-fab" title="AI 管家" @click="goAssistant">
      <span class="ai-fab__icon">AI</span>
      <span class="ai-fab__text">管家</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
/* ================= 第一屏 ================= */
.hero {
  position: relative;
  height: calc(100vh - 68px);
  min-height: 560px;
  overflow: hidden;
}

.hero__bg {
  position: absolute;
  inset: -8% 0;
  background-size: cover;
  background-position: center;
  will-change: transform;
}

.hero__mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(36, 26, 27, 0.35) 0%,
    rgba(36, 26, 27, 0.55) 60%,
    rgba(232, 227, 216, 0.9) 100%
  );
}

.hero__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 18px;
  text-align: center;
}

.hero__title {
  font-size: 76px;
  letter-spacing: 20px;
  color: #fff;
  text-shadow: 0 6px 32px rgba(0, 0, 0, 0.4);
}

.hero__subtitle {
  font-size: 20px;
  letter-spacing: 5px;
  color: var(--color-gold);
}

.hero__arrow {
  margin-top: 26px;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(201, 168, 106, 0.7);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: var(--color-gold);
  cursor: pointer;
  animation: breathe 1.8s ease-in-out infinite;
}

.hero__arrow-icon {
  display: block;
  font-size: 20px;
  line-height: 1;
}

@keyframes breathe {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(8px);
    opacity: 1;
  }
}

/* ================= 第二屏：数据概览 ================= */
.stats {
  padding: 96px 24px;
}

.stats__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.stat-card {
  padding: 32px 16px;
  border-radius: 16px;
  text-align: center;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(201, 168, 106, 0.35);

  &__num {
    font-family: var(--font-title);
    font-size: 52px;
    font-weight: 600;
    line-height: 1.1;
    color: var(--color-primary);
  }

  &__label {
    margin-top: 6px;
    font-size: 15px;
    letter-spacing: 3px;
    color: #7a6a60;
  }
}

/* ================= 第三 / 四屏通用 ================= */
.section {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px 80px;

  &--last {
    padding-bottom: 120px;
  }

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 28px;
  }

  &__title {
    font-size: 30px;
    letter-spacing: 6px;
    color: var(--color-primary);
  }

  &__more {
    font-size: 14px;
    letter-spacing: 1px;
    color: var(--color-gold);
    cursor: pointer;

    &:hover {
      color: var(--color-primary);
    }
  }
}

/* ---------- 热门景点：横向卡片 ---------- */
.hot-row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 300px;
  gap: 20px;
  overflow-x: auto;
  padding-bottom: 12px;
  scroll-snap-type: x mandatory;
}

.hot-card {
  scroll-snap-align: start;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(201, 168, 106, 0.35);
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-6px);
    box-shadow: 0 18px 40px rgba(140, 31, 40, 0.16);
  }

  &__img {
    height: 190px;
    background-size: cover;
    background-position: center;
  }

  &__body {
    padding: 16px 18px;
  }

  &__name {
    font-size: 19px;
    letter-spacing: 2px;
    color: #2b2b2b;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 8px;
    font-size: 13px;
    color: #8a7a70;
  }

  &__score {
    font-weight: 600;
    color: var(--color-primary);
  }
}

/* ---------- 八大文化：网格 ---------- */
.culture-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.culture-card {
  position: relative;
  height: 190px;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;

  &__img {
    position: absolute;
    inset: 0;
    background-size: cover;
    background-position: center;
    transition: transform 0.5s ease;
  }

  &:hover &__img {
    transform: scale(1.08);
  }

  &__info {
    position: absolute;
    inset: auto 0 0 0;
    padding: 16px;
    background: linear-gradient(180deg, rgba(36, 26, 27, 0) 0%, rgba(36, 26, 27, 0.82) 100%);
    color: #fff;
  }

  &__name {
    font-size: 19px;
    letter-spacing: 2px;
  }

  &__cat {
    font-size: 12px;
    letter-spacing: 1px;
    color: var(--color-gold);
  }
}

/* ================= 悬浮 AI 管家 ================= */
.ai-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 90;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: 50%;
  background: linear-gradient(160deg, #8c1f28, #6e141c);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(140, 31, 40, 0.35);
  transition: transform 0.25s ease;

  &:hover {
    transform: translateY(-4px) scale(1.04);
  }

  &__icon {
    font-family: var(--font-title);
    font-size: 16px;
    line-height: 1;
  }

  &__text {
    font-size: 11px;
    letter-spacing: 1px;
    color: var(--color-gold);
  }
}

/* ================= 响应式 ================= */
@media (max-width: 900px) {
  .hero__title {
    font-size: 46px;
    letter-spacing: 10px;
  }

  .stats__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .culture-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
