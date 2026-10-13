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

import { listCulture, listCultureByCategory, type CultureVO } from '@/api/culture'
import GoldButton from '@/components/common/GoldButton.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import { t } from '@/i18n'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型与常量
 * ---------------------------------------------------------- */
// 类型统一来自 @/api/culture（CultureVO）

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

const all = ref<CultureVO[]>([])
const list = ref<CultureVO[]>([])
const categories = ref<string[]>([])
const activeCategory = ref('')
const loading = ref(true)
/** 接口是否加载失败 */
const loadFailed = ref(false)
const listEl = ref<HTMLElement | null>(null)

/** 图片兜底占位图（内联 SVG） */
const IMG_FALLBACK =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="450"><rect width="100%" height="100%" fill="#F0EBE0"/><text x="50%" y="50%" fill="#D4B374" font-size="60" font-family="serif" text-anchor="middle" dominant-baseline="middle">蜀</text></svg>',
  )

/** 图片加载失败：换成占位图（每张只处理一次） */
function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') {
    return
  }
  img.dataset.fallback = '1'
  img.src = IMG_FALLBACK
}

/** 收集卡片 DOM，供滚动定位与入场动画使用 */
function registerCard(el: unknown, index: number) {
  if (el instanceof HTMLElement) {
    cardEls.value[index] = el
  }
}

/** 卡片简介：取 summary，截断 150 字 */
function introOf(item: CultureVO) {
  const text = (item.summary || '').replace(/\n/g, '')
  return text.length > 150 ? `${text.slice(0, 150)}…` : text
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
async function loadAll() {
  loading.value = true
  loadFailed.value = false
  try {
    const data = await listCulture()
    const arr = Array.isArray(data) ? data : []
    all.value = arr
    list.value = arr
    // 从数据里提取去重分类
    categories.value = Array.from(new Set(arr.map((c) => c.category).filter(Boolean))) as string[]
  } catch {
    loadFailed.value = true
    all.value = []
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 切换分类：'' = 全部（全量已缓存；具体分类走接口） */
async function selectCategory(cat: string) {
  if (activeCategory.value === cat) {
    return
  }
  activeCategory.value = cat
  loading.value = true
  loadFailed.value = false
  try {
    if (!cat) {
      list.value = all.value
    } else {
      const data = await listCultureByCategory(cat)
      list.value = Array.isArray(data) ? data : []
    }
  } catch {
    loadFailed.value = true
    list.value = []
  } finally {
    loading.value = false
  }
  await nextTick()
  if (listEl.value) {
    gsap.fromTo(
      listEl.value,
      { opacity: 0, y: 12 },
      { opacity: 1, y: 0, duration: 0.45, ease: 'power2.out' },
    )
  }
}

// 分类切换见 selectCategory（原「按名称滚动定位」的导航已改为分类筛选）

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

/* ------------------------------------------------------------
 * 动效（GSAP + ScrollTrigger）
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null

onMounted(async () => {
  await loadAll()
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
          type="button"
          class="cul-nav__person"
          @click="router.push('/person')"
        >
          全部传承人
        </button>
        <button
          type="button"
          class="cul-nav__pill"
          :class="{ 'is-active': activeCategory === '' }"
          @click="selectCategory('')"
        >
          全部
        </button>
        <button
          v-for="cat in categories"
          :key="cat"
          type="button"
          class="cul-nav__pill"
          :class="{ 'is-active': activeCategory === cat }"
          @click="selectCategory(cat)"
        >
          {{ cat }}
        </button>
      </div>
    </nav>

    <!-- ================= 3. 文化卡片墙 ================= -->
    <!-- 加载中 -->
    <StateBlock v-if="loading" state="loading" />

    <div v-if="!loading" ref="listEl" class="sy-container cul-list">
      <article
        v-for="(item, i) in list"
        :key="item.id"
        :ref="(el) => registerCard(el, i)"
        class="cul-card"
        :class="{ 'is-reverse': i % 2 === 1 }"
      >
        <!-- 大图 -->
        <div class="cul-card__media">
          <img class="cul-card__img" :src="item.coverImage" alt="" loading="lazy" decoding="async" @error="onImgError" />
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

      <StateBlock v-if="!loading && loadFailed" state="error" :retry="loadAll" />
      <StateBlock v-else-if="!loading && !list.length" state="empty" empty-text="暂无内容" />
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
  min-height: $hero-list-height;
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
  font-size: $fs-hero-xl;
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
  top: $nav-height;
  z-index: $z-header - 1;
  border-bottom: 1px solid rgba(212, 179, 116, 0.28);
  @include glass(rgba(var(--sy-paper-rgb), 0.82), 18px);
}

.cul-nav__inner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-block: 16px;
  padding: 6px;
  border: 1px solid rgba(212, 179, 116, 0.35);
  border-radius: 16px;
  background: rgba(var(--sy-surface-rgb), 0.88);
  box-shadow: 0 10px 30px rgba(14, 42, 71, 0.06);
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.cul-nav__pill {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 54px;
  padding: 0 28px;
  border: none;
  border-radius: 11px;
  background: transparent;
  font-family: $font-body;
  font-size: 24px;
  letter-spacing: 0.04em;
  color: rgba(var(--sy-text-rgb), 0.72);
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.07);
    color: var(--sy-accent);
  }

  &.is-active {
    background: linear-gradient(120deg, $color-gold, #c39d52);
    color: $color-ink;
    box-shadow: 0 8px 18px rgba(212, 179, 116, 0.35);
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
  border: 1px solid rgba(212, 179, 116, 0.4);
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
  width: 40px;
  color: $color-gold;
}

.cul-card__category {
  font-family: $font-en-body;
  font-size: 24px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: $color-gold;
}

.cul-card__name {
  font-family: $font-title;
  font-weight: 700;
  font-size: clamp(40px, 3.4vw, 56px);
  line-height: 1.1;
  letter-spacing: 0.04em;
  color: $color-ink;
}

.cul-card__intro {
  font-family: $font-body;
  font-size: clamp(20px, 1.6vw, 24px);
  line-height: 1.8;
  color: rgba(var(--sy-text-rgb), 0.72);
  @include text-ellipsis(3);
}

.cul-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cul-card__tag {
  padding: 6px 18px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-sm;
  font-size: 18px;
  letter-spacing: 0.06em;
  color: $color-bamboo;
}

.cul-card__more {
  margin-top: 6px;
  font-size: 20px;
}

/* ============================================================
 * 4. 文化时间轴
 * ========================================================== */
.cul-time {
  padding-block: clamp(56px, 8vw, 130px);
  background:
    radial-gradient(120% 130% at 15% 0%, rgba(212, 179, 116, 0.16) 0%, rgba(var(--sy-paper-rgb), 0) 60%),
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
  background: rgba(122, 26, 34, 0.15);
}

.cul-time__line-fill {
  background: linear-gradient(90deg, $color-gold, rgba(212, 179, 116, 0.35));
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
  box-shadow: 0 0 0 4px rgba(212, 179, 116, 0.18);
}

.cul-time__era {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.5vw, 22px);
  letter-spacing: 0.12em;
  color: var(--sy-accent);
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
  color: rgba(var(--sy-text-rgb), 0.68);
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

/* 空状态 */
.cul-empty {
  padding-block: clamp(48px, 8vw, 110px);
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  text-align: center;
  color: rgba(var(--sy-text-rgb), 0.6);
}
/* 全部传承人入口按钮（分类导航左侧） */
.cul-nav__person {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 8px;
  padding: 8px 18px;
  border: 1px solid $color-gold;
  border-radius: $radius-pill;
  background: rgba(212, 179, 116, 0.1);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  white-space: nowrap;
  color: var(--sy-accent);
  cursor: pointer;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.22);
  }
}
</style>
