<script setup lang="ts">
/**
 * 文化寻踪（列表页）
 * - 60vh Hero（大字艺术排版 + 滚动淡出）
 * - 横向文化导航（胶囊，选中金色填充，点击滚动到对应卡片）
 * - 大卡片墙：整行卡片、左右交错；图片自右滑入、文字自左滑入
 * - 底部横向文化时间轴：轴线滚动到此处时从左到右绘制
 * 全部样式复用公共设计系统（variables / mixins / GoldButton / CultureIcon）
 */
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import request from '@/api/request'
import { getLenis } from '@/composables/useLenis'
import GoldButton from '@/components/common/GoldButton.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import { t } from '@/i18n'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型与常量
 * ---------------------------------------------------------- */
interface CultureItem {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  content?: string
  tags?: string
}

/** 换成本地大图：/images/culture-hero.webp（川剧 / 蜀绣类实景） */
const HERO_BG =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1920&q=80'
const HERO_BG_2X =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=2560&q=80'

/** 文化时间轴（横向，静态展示） */
const TIMELINE = [
  { era: '古蜀', year: '距今约 3000–5000 年', text: '三星堆与金沙，青铜神树、纵目面具勾勒出古蜀文明的神秘轮廓。' },
  { era: '秦汉', year: '公元前 221 年 – 公元 263 年', text: '都江堰灌溉天府，蜀汉定都成都，三国故事自此有了舞台。' },
  { era: '唐宋', year: '唐宋以降', text: '茶马古道以茶易马，穿越川滇藏的高山峡谷，商路亦是文化走廊。' },
  { era: '明清', year: '清代中叶', text: '川剧五腔合一而成，变脸、吐火、滚灯绝技自成一体。' },
  { era: '现代', year: '今天', text: '蜀绣、川菜、大熊猫与川酒，成为走向世界的巴蜀名片。' },
]

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const router = useRouter()

const heroEl = ref<HTMLElement | null>(null)
const rootEl = ref<HTMLElement | null>(null)
const cardEls = ref<HTMLElement[]>([])
const timelineEl = ref<HTMLElement | null>(null)

const list = ref<CultureItem[]>([])
const loading = ref(true)
const activeId = ref<number | null>(null)

/** 收集卡片 DOM，供滚动定位与入场动画使用 */
function registerCard(el: unknown, index: number) {
  if (el instanceof HTMLElement) {
    cardEls.value[index] = el
  }
}

/** 卡片简介：正文两段拼接（约 150 字，由 CSS 限 3 行） */
function introOf(item: CultureItem) {
  const text = (item.content || item.summary || '').replace(/\n/g, '')
  return text || item.summary || ''
}

/** 标签拆分 */
function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/culture/list', { silent: true })
    list.value = Array.isArray(res.data) ? res.data : []
    activeId.value = list.value[0]?.id ?? null
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 点击导航胶囊：滚动到对应卡片（让开固定导航 + 吸顶胶囊条） */
function gotoCulture(id: number) {
  activeId.value = id
  const index = list.value.findIndex((c) => c.id === id)
  const el = cardEls.value[index]
  if (!el) {
    return
  }
  const lenis = getLenis()
  if (lenis) {
    lenis.scrollTo(el, { offset: -150, duration: 1.1 })
  } else {
    window.scrollTo({ top: el.offsetTop - 150, behavior: 'smooth' })
  }
}

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

/* ------------------------------------------------------------
 * 动效（GSAP + ScrollTrigger）
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null

onMounted(async () => {
  await loadList()
  await nextTick()

  ctx = gsap.context(() => {
    // Hero 向下淡出
    if (heroEl.value) {
      gsap.to('.cul-hero__content', {
        yPercent: -30,
        opacity: 0,
        ease: 'none',
        scrollTrigger: {
          trigger: heroEl.value,
          start: 'top top',
          end: 'bottom top',
          scrub: true,
        },
      })
    }

    // 卡片：图片自右滑入、文字自左滑入
    cardEls.value.forEach((card) => {
      const media = card.querySelector('.cul-card__media')
      const body = card.querySelector('.cul-card__body')
      const trigger = { trigger: card, start: 'top 80%', once: true }

      if (media) {
        gsap.from(media, { x: 90, opacity: 0, duration: 1.1, ease: 'power3.out', scrollTrigger: trigger })
      }
      if (body) {
        gsap.from(body, { x: -90, opacity: 0, duration: 1.1, ease: 'power3.out', scrollTrigger: trigger })
      }
    })

    // 时间轴：轴线从左到右绘制，节点依次点亮
    gsap.from('.cul-time__line-fill', {
      scaleX: 0,
      transformOrigin: 'left center',
      ease: 'none',
      scrollTrigger: {
        trigger: timelineEl.value,
        start: 'top 78%',
        end: 'bottom 60%',
        scrub: true,
      },
    })
    gsap.from('.cul-time__node', {
      scale: 0,
      opacity: 0,
      duration: 0.6,
      stagger: 0.12,
      ease: 'back.out(2)',
      scrollTrigger: { trigger: timelineEl.value, start: 'top 78%', once: true },
    })
  }, rootEl.value ?? undefined)

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  ctx?.revert()
  ctx = null
})
</script>

<template>
  <section ref="rootEl" class="culture-page">
    <!-- ================= 1. Hero（60vh） ================= -->
    <header ref="heroEl" class="cul-hero">
      <img
        class="cul-hero__bg"
        :src="HERO_BG"
        :srcset="`${HERO_BG} 1920w, ${HERO_BG_2X} 2560w`"
        sizes="100vw"
        :alt="t('culture.heroAlt')"
        fetchpriority="high"
        decoding="async"
      />

      <div class="sy-overlay">
        <div class="sy-overlay__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="sy-container cul-hero__content">
        <nav class="cul-hero__crumb">
          <router-link to="/">{{ t('nav.home') }}</router-link>
          <span class="cul-hero__sep">/</span>
          <span>{{ t('nav.culture') }}</span>
        </nav>

        <h1 class="cul-hero__title">{{ t('culture.heroTitle1') }}<span>{{ t('culture.heroTitle2') }}</span></h1>
        <p class="cul-hero__sub">{{ t('culture.heroSub') }}</p>
      </div>
    </header>

    <!-- ================= 2. 横向文化导航 ================= -->
    <nav class="cul-nav" :aria-label="t('culture.navAria')">
      <div class="sy-container cul-nav__inner">
        <button
          v-for="item in list"
          :key="item.id"
          type="button"
          class="cul-nav__pill"
          :class="{ 'is-active': item.id === activeId }"
          @click="gotoCulture(item.id)"
        >
          {{ item.name }}
        </button>
      </div>
    </nav>

    <!-- ================= 3. 文化卡片墙 ================= -->
    <div v-loading="loading" class="sy-container cul-list">
      <article
        v-for="(item, i) in list"
        :key="item.id"
        :ref="(el) => registerCard(el, i)"
        class="cul-card"
        :class="{ 'is-reverse': i % 2 === 1 }"
      >
        <!-- 大图 -->
        <div class="cul-card__media">
          <img class="cul-card__img" :src="item.coverImage" alt="" loading="lazy" decoding="async" />
          <span class="cul-card__index">{{ String(i + 1).padStart(2, '0') }}</span>
        </div>

        <!-- 文字 -->
        <div class="cul-card__body">
          <div class="cul-card__meta">
            <CultureIcon class="cul-card__icon" :name="item.name" />
            <span class="cul-card__category">{{ item.category }}</span>
          </div>

          <h2 class="cul-card__name">{{ item.name }}</h2>
          <p class="cul-card__intro">{{ introOf(item) }}</p>

          <div v-if="splitTags(item.tags).length" class="cul-card__tags">
            <span v-for="t in splitTags(item.tags)" :key="t" class="cul-card__tag">{{ t }}</span>
          </div>

          <GoldButton class="cul-card__more" @click="goDetail(item.id)">
            {{ t('culture.more') }}
            <template #icon-right>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </template>
          </GoldButton>
        </div>
      </article>
    </div>

    <!-- ================= 4. 文化时间轴 ================= -->
    <section ref="timelineEl" class="cul-time">
      <div class="sy-container">
        <p class="sy-eyebrow">Timeline</p>
        <h2 class="sy-page-title">{{ t('culture.timelineTitle') }}</h2>

        <div class="cul-time__track">
          <span class="cul-time__line" />
          <span class="cul-time__line-fill" />

          <div v-for="node in TIMELINE" :key="node.era" class="cul-time__item">
            <span class="cul-time__node" />
            <p class="cul-time__era">{{ node.era }}</p>
            <p class="cul-time__year">{{ node.year }}</p>
            <p class="cul-time__text">{{ node.text }}</p>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;
/* ============================================================
 * 1. Hero（60vh）
 * ========================================================== */
.cul-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: 60vh;
  overflow: hidden;
  isolation: isolate;
}

.cul-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: cul-kenburns 26s ease-in-out infinite alternate;
}

@keyframes cul-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.cul-hero__content {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(72px, 12vh, 150px) clamp(36px, 6vh, 72px);
}

.cul-hero__crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.78);

  a:hover {
    color: $color-gold;
  }
}

.cul-hero__sep {
  color: $color-gold;
}

/* 主标题：后半句金色描边 */
.cul-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero;
  line-height: 1;
  letter-spacing: -0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;

  span {
    display: inline-block;
    margin-left: 0.06em;
    color: transparent;
    -webkit-text-stroke: 1.5px $color-gold;
    text-shadow: none;
  }
}

.cul-hero__sub {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(15px, 1.4vw, 22px);
  letter-spacing: 0.4em;
  color: rgba(245, 241, 232, 0.88);
  text-shadow: $shadow-text-soft;
}

/* ============================================================
 * 2. 横向文化导航（吸顶）
 * ========================================================== */
.cul-nav {
  position: sticky;
  top: 68px;
  z-index: $z-header - 1;
  border-bottom: 1px solid rgba(201, 168, 106, 0.28);
  @include glass(rgba(232, 227, 216, 0.82), 18px);
}

.cul-nav__inner {
  display: flex;
  gap: 10px;
  padding-block: 14px;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.cul-nav__pill {
  flex-shrink: 0;
  padding: 9px 20px;
  border: 1px solid rgba(140, 31, 40, 0.22);
  border-radius: $radius-pill;
  background: transparent;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(26, 26, 26, 0.78);
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-primary;
    transform: translateY(-2px);
  }

  &.is-active {
    border-color: $color-gold;
    background: $color-gold;
    color: $color-ink;
  }
}

/* ============================================================
 * 3. 卡片墙（整行 + 左右交错）
 * ========================================================== */
.cul-list {
  display: flex;
  flex-direction: column;
  gap: clamp(48px, 7vw, 120px);
  padding-block: clamp(48px, 7vw, 110px);
}

.cul-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: clamp(28px, 4.5vw, 84px);

  // 奇数行图文互换
  &.is-reverse {
    .cul-card__media {
      order: 2;
    }

    .cul-card__body {
      order: 1;
    }
  }
}

.cul-card__media {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  box-shadow: $shadow-md;
  will-change: transform;

  &:hover .cul-card__img {
    transform: scale(1.05);
  }
}

.cul-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 1.2s $ease-out-expo;
}

.cul-card__index {
  position: absolute;
  left: 16px;
  bottom: 12px;
  font-family: $font-en;
  font-size: clamp(20px, 2vw, 30px);
  letter-spacing: 0.1em;
  color: rgba(245, 241, 232, 0.85);
  text-shadow: $shadow-text;
}

.cul-card__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: clamp(12px, 1.6vw, 20px);
  will-change: transform;
}

.cul-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cul-card__icon {
  width: 30px;
  color: $color-gold;
}

.cul-card__category {
  font-family: $font-en-body;
  font-size: $fs-small;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: $color-gold;
}

.cul-card__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-h2;
  line-height: 1.1;
  letter-spacing: 0.06em;
  color: $color-ink;
}

.cul-card__intro {
  font-family: $font-body;
  font-size: clamp(15px, 1.3vw, 19px);
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.72);
  @include text-ellipsis(3);
}

.cul-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cul-card__tag {
  padding: 3px 12px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-sm;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-bamboo;
}

.cul-card__more {
  margin-top: 6px;
}

/* ============================================================
 * 4. 文化时间轴
 * ========================================================== */
.cul-time {
  padding-block: clamp(56px, 8vw, 130px);
  background:
    radial-gradient(120% 130% at 15% 0%, rgba(201, 168, 106, 0.16) 0%, rgba(232, 227, 216, 0) 60%),
    $color-paper;
}

.cul-time__track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: clamp(16px, 2.4vw, 40px);
  margin-top: clamp(32px, 5vw, 70px);
  padding-top: 30px;
}

/* 轴线：底轨 + 从左到右绘制的填充 */
.cul-time__line,
.cul-time__line-fill {
  position: absolute;
  top: 30px;
  left: 0;
  width: 100%;
  height: 1px;
}

.cul-time__line {
  background: rgba(140, 31, 40, 0.15);
}

.cul-time__line-fill {
  background: linear-gradient(90deg, $color-gold, rgba(201, 168, 106, 0.35));
}

.cul-time__item {
  position: relative;
  padding-top: 22px;
}

.cul-time__node {
  position: absolute;
  top: -5px;
  left: 0;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: $color-gold;
  box-shadow: 0 0 0 4px rgba(201, 168, 106, 0.18);
}

.cul-time__era {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.5vw, 22px);
  letter-spacing: 0.12em;
  color: $color-primary;
}

.cul-time__year {
  margin-top: 4px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-gold;
}

.cul-time__text {
  margin-top: 8px;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.85;
  color: rgba(26, 26, 26, 0.68);
}

/* ============================================================
 * 响应式
 * ========================================================== */
@include responsive(lg) {
  .cul-card {
    grid-template-columns: minmax(0, 1fr);
    gap: clamp(16px, 3vw, 28px);

    &.is-reverse {
      .cul-card__media {
        order: 0;
      }

      .cul-card__body {
        order: 0;
      }
    }
  }
}

@include responsive(md) {
  .cul-hero__title span {
    -webkit-text-stroke-width: 1px;
  }

  .cul-hero__sub {
    letter-spacing: 0.2em;
  }

  // 时间轴改为横向可滚动，保持轴线与绘制效果
  .cul-time__track {
    grid-template-columns: repeat(5, minmax(210px, 1fr));
    overflow-x: auto;
  }
}
</style>
