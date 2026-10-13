<script setup lang="ts">
/**
 * 美食详情
 * - 首屏 Hero（约 50vh）：封面大图 + 名称 + 分类/城市/人均 + 辣度标识
 * - 正文两栏（60 / 40）：左为简介 / 历史故事 / 视频；右为信息卡 + 代表店铺卡
 * - 底部：同分类其他美食推荐（3 张）
 * - 数据来自 /api/food/{id}、/api/food/list
 * - 复用公共设计系统（variables / mixins / GoldButton）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getFoodDetail, listFood, type FoodDetailVO, type FoodVO } from '@/api/food'
import StateBlock from '@/components/common/StateBlock.vue'
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()

const detail = ref<FoodDetailVO | null>(null)
const sameCategory = ref<FoodVO[]>([])
const loading = ref(true)
const notFound = ref(false)

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

/**
 * 视频嵌入地址：
 * - 已是 player.bilibili.com / youtube embed → 原样
 * - B站普通视频页 → https://player.bilibili.com/player.html?bvid=BVxxx
 * - YouTube watch → embed
 * - 其它 → 空
 */
const embedUrl = computed(() => {
  const url = detail.value?.videoUrl || ''
  if (!url) {
    return ''
  }
  if (/player\.bilibili\.com|youtube\.com\/embed/.test(url)) {
    return url
  }
  const bili = url.match(/bilibili\.com\/video\/(BV[0-9A-Za-z]+)/)
  if (bili) {
    return `https://player.bilibili.com/player.html?bvid=${bili[1]}&high_quality=1&danmaku=0`
  }
  const yt = url.match(/youtube\.com\/watch\?v=([\w-]+)/)
  if (yt) {
    return `https://www.youtube.com/embed/${yt[1]}`
  }
  return ''
})

/** 店铺是否有坐标 */
const hasShopLocation = computed(
  () => detail.value?.shopLongitude != null && detail.value?.shopLatitude != null,
)

/** 高德 URI（无需 Key，直接跳转） */
const mapLink = computed(() => {
  const d = detail.value
  if (!d) {
    return ''
  }
  if (d.shopLongitude != null && d.shopLatitude != null) {
    return `https://uri.amap.com/marker?position=${d.shopLongitude},${d.shopLatitude}&name=${encodeURIComponent(d.shopName || d.name)}`
  }
  return `https://uri.amap.com/search?keyword=${encodeURIComponent(d.shopAddress || d.name)}`
})

function goDetail(id: number) {
  router.push(`/food/${id}`)
}

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadSameCategory(category?: string, currentId?: number) {
  if (!category) {
    sameCategory.value = []
    return
  }
  try {
    const data = await listFood({ page: 1, size: 4, category })
    const arr = Array.isArray(data?.records) ? data!.records : []
    sameCategory.value = arr.filter((f) => f.id !== currentId).slice(0, 3)
  } catch {
    sameCategory.value = []
  }
}

async function loadDetail(id: number) {
  loading.value = true
  notFound.value = false
  try {
    const data = await getFoodDetail(id)
    detail.value = data ?? null
    notFound.value = !detail.value
    if (detail.value) {
      await loadSameCategory(detail.value.category, detail.value.id)
    }
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDetail(Number(route.params.id))
})

// 切换同分类美食时重新加载
watch(
  () => route.params.id,
  (id) => {
    if (id) {
      window.scrollTo({ top: 0 })
      loadDetail(Number(id))
    }
  },
)
</script>

<template>
  <section class="fd-page">
    <StateBlock v-if="loading" state="loading" />

    <template v-if="detail">
      <!-- ================= 1. Hero（约 50vh） ================= -->
      <header class="fd-hero">
        <img
          class="fd-hero__bg"
          :src="detail.coverImage"
          :alt="detail.name"
          fetchpriority="high"
          decoding="async"
          @error="onImgError"
        />
        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container fd-hero__inner">
          <nav class="fd-hero__crumb">
            <router-link to="/">{{ t('nav.home') }}</router-link>
            <span class="fd-hero__sep">/</span>
            <router-link to="/food">{{ t('nav.food') }}</router-link>
            <span class="fd-hero__sep">/</span>
            <span>{{ detail.name }}</span>
          </nav>

          <h1 class="fd-hero__title">{{ detail.name }}</h1>

          <div class="fd-hero__meta">
            <span v-if="detail.category">{{ detail.category }}</span>
            <i class="fd-hero__dot" />
            <span v-if="detail.cityName">{{ detail.cityName }}</span>
            <i v-if="detail.avgPrice != null" class="fd-hero__dot" />
            <span v-if="detail.avgPrice != null" class="fd-hero__price">人均 ¥{{ detail.avgPrice }}</span>
          </div>

          <div v-if="detail.spicyLevel" class="fd-hero__spicy">
            <span class="fd-hero__spicy-label">辣度</span>
            <i
              v-for="n in 5"
              :key="n"
              class="fd-chili"
              :class="{ 'is-on': n <= (detail.spicyLevel || 0) }"
            >🌶</i>
          </div>
        </div>
      </header>

      <!-- ================= 2. 正文两栏 ================= -->
      <div class="sy-container fd-body">
        <!-- 左 60% -->
        <article class="fd-main">
          <div class="fd-cols">
            <section v-if="detail.description" class="fd-block">
              <h2 class="fd-block__title">简介</h2>
              <p class="fd-text">{{ detail.description }}</p>
            </section>

            <section v-if="detail.history" class="fd-block fd-block--quote">
              <h2 class="fd-block__title">历史故事</h2>
              <p class="fd-text">{{ detail.history }}</p>
            </section>
          </div>

          <section class="fd-block">
            <h2 class="fd-block__title">相关视频</h2>
            <div v-if="embedUrl" class="fd-video">
              <iframe :src="embedUrl" allowfullscreen frameborder="0" :title="detail.name" />
            </div>
            <a
              v-else-if="detail.videoUrl"
              class="fd-video__link"
              :href="detail.videoUrl"
              target="_blank"
              rel="noopener"
            >
              在新窗口打开视频 →
            </a>
            <p v-else class="fd-text fd-text--muted">暂无视频</p>
          </section>
        </article>

        <!-- 右 40% -->
        <aside class="fd-aside">
          <!-- 信息卡 -->
          <div class="fd-panel">
            <h3 class="fd-panel__title">基本信息</h3>
            <ul class="fd-info">
              <li><span>分类</span><b>{{ detail.category || '—' }}</b></li>
              <li><span>城市</span><b>{{ detail.cityName || '—' }}</b></li>
              <li><span>人均</span><b>{{ detail.avgPrice != null ? `¥${detail.avgPrice}` : '—' }}</b></li>
              <li>
                <span>推荐指数</span>
                <b class="fd-info__stars">
                  <template v-for="n in 5" :key="n">
                    <i class="fd-star" :class="{ 'is-on': n <= (detail.recommendIndex || 0) }">★</i>
                  </template>
                </b>
              </li>
            </ul>
          </div>

          <!-- 代表店铺卡 -->
          <div v-if="detail.shopName" class="fd-panel">
            <h3 class="fd-panel__title">代表店铺</h3>
            <img
              v-if="detail.shopImage"
              class="fd-shop__img"
              :src="detail.shopImage"
              :alt="detail.shopName"
              loading="lazy"
              @error="onImgError"
            />
            <p class="fd-shop__name">{{ detail.shopName }}</p>
            <p class="fd-shop__addr">{{ detail.shopAddress || '地址待补充' }}</p>

            <div class="fd-map">
              <template v-if="hasShopLocation">
                <span class="fd-map__coord">
                  {{ detail.shopLongitude }}, {{ detail.shopLatitude }}
                </span>
                <a class="fd-map__link" :href="mapLink" target="_blank" rel="noopener">
                  在地图中打开 →
                </a>
              </template>
              <span v-else class="fd-map__coord">暂无坐标</span>
            </div>
          </div>
        </aside>
      </div>

      <!-- ================= 3. 同分类推荐 ================= -->
      <section v-if="sameCategory.length" class="sy-container fd-reco">
        <p class="sy-eyebrow">More Food</p>
        <h2 class="sy-page-title">同类川味</h2>
        <div class="fd-reco__grid">
          <article
            v-for="item in sameCategory"
            :key="item.id"
            class="fd-reco__card"
            @click="goDetail(item.id)"
          >
            <img
              class="fd-reco__img"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              @error="onImgError"
            />
            <div class="fd-reco__body">
              <h3 class="fd-reco__name">{{ item.name }}</h3>
              <p class="fd-reco__meta">{{ item.cityName || '' }} · ¥{{ item.avgPrice ?? '—' }}/人</p>
            </div>
          </article>
        </div>
      </section>
    </template>

    <!-- 不存在 / 报错 -->
    <StateBlock
      v-else-if="!loading"
      state="error"
      error-text="美食信息不存在"
      action-text="返回美食列表"
      :action="() => router.push('/food')"
    />
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.fd-page {
  background: $color-paper;
}

.fd-loading {
  min-height: 4px;
}

/* ============================================================
 * 1. Hero（约 50vh）
 * ========================================================== */
.fd-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: $hero-detail-height;
  overflow: hidden;
  isolation: isolate;
}

.fd-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: fd-kenburns 26s ease-in-out infinite alternate;
}

@keyframes fd-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.fd-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(72px, 12vh, 150px) clamp(36px, 6vh, 72px);
}

.fd-hero__crumb {
  display: flex;
  flex-wrap: wrap;
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

.fd-hero__sep {
  color: $color-gold;
}

.fd-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero-xl;
  line-height: 1;
  letter-spacing: 0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.fd-hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(14px, 1.3vw, 20px);
  letter-spacing: 0.2em;
  color: rgba(245, 241, 232, 0.9);
  text-shadow: $shadow-text-soft;
}

.fd-hero__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: $color-gold;
}

.fd-hero__price {
  font-family: $font-en;
  color: $color-gold;
}

.fd-hero__spicy {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 18px;
  line-height: 1;
}

.fd-hero__spicy-label {
  margin-right: 4px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.2em;
  color: rgba(245, 241, 232, 0.85);
}

.fd-chili {
  filter: grayscale(1);
  opacity: 0.4;

  &.is-on {
    filter: none;
    opacity: 1;
  }
}

/* ============================================================
 * 2. 正文（整栏长文 + 下方卡片）
 * ========================================================== */
.fd-body {
  padding-block: clamp(48px, 7vw, 110px);
}

.fd-block + .fd-block {
  margin-top: clamp(28px, 3.4vw, 48px);
}

/* 简介 + 历史故事：两栏 */
.fd-cols {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(24px, 3vw, 48px);

  .fd-block {
    margin-top: 0;
  }
}

/* 历史故事：引号装饰 */
.fd-block--quote {
  position: relative;
  padding-left: clamp(22px, 2.2vw, 36px);

  &::before {
    content: '\201C';
    position: absolute;
    top: -0.12em;
    left: 0;
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(48px, 5vw, 84px);
    line-height: 1;
    color: rgba(212, 179, 116, 0.5);
    pointer-events: none;
  }
}

@include responsive(md) {
  .fd-cols {
    grid-template-columns: minmax(0, 1fr);
  }
}

.fd-block__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(18px, 1.6vw, 24px);
  letter-spacing: 0.1em;
  color: var(--sy-accent);

  &::before {
    content: '';
    position: absolute;
    top: 0.16em;
    left: 0;
    width: 3px;
    height: 0.84em;
    background: $color-gold;
  }
}

.fd-text {
  font-family: $font-body;
  font-size: $fs-body-lg;
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.82);
  text-align: justify;

  &--muted {
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.fd-video {
  width: 70%;
  margin-inline: auto;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  box-shadow: $shadow-md;

  iframe {
    width: 100%;
    height: 100%;
    border: 0;
  }
}

.fd-video__link {
  display: inline-block;
  margin-top: 8px;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
  text-decoration: underline;
  text-underline-offset: 4px;

  &:hover {
    color: $color-gold;
  }
}

/* ---------------- 下方卡片（随屏幕自适应列数） ---------------- */
.fd-aside {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: clamp(16px, 1.8vw, 24px);
  margin-top: clamp(32px, 4vw, 56px);
}

.fd-panel {
  @include sy-card-gold;
  padding: clamp(16px, 1.8vw, 24px);
}

.fd-panel__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.12em;
  color: var(--sy-accent);

  &::before {
    content: '';
    position: absolute;
    top: 0.14em;
    left: 0;
    width: 2px;
    height: 0.86em;
    background: $color-gold;
  }
}

.fd-info {
  display: flex;
  flex-direction: column;
  gap: 12px;

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 10px;
    border-bottom: 1px dashed rgba(212, 179, 116, 0.3);
    font-size: $fs-small;

    &:last-child {
      padding-bottom: 0;
      border-bottom: none;
    }

    span {
      color: rgba(var(--sy-text-rgb), 0.55);
      letter-spacing: 0.1em;
    }

    b {
      font-family: $font-title;
      letter-spacing: 0.06em;
      color: $color-text-dark;
    }
  }
}

.fd-info__stars {
  display: inline-flex;
  gap: 2px;
}

.fd-star {
  font-size: 14px;
  font-style: normal;
  color: rgba(122, 90, 30, 0.3);

  &.is-on {
    color: #a8721c;
  }
}

.fd-shop__img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: $radius-md;
  border: 1px solid rgba(212, 179, 116, 0.4);
}

.fd-shop__name {
  margin-top: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.2vw, 18px);
  letter-spacing: 0.06em;
  color: $color-text-dark;
}

.fd-shop__addr {
  margin-top: 6px;
  font-size: $fs-small;
  line-height: 1.7;
  color: rgba(var(--sy-text-rgb), 0.6);
}

.fd-map {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
  padding: 18px;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-md;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(212, 179, 116, 0.22) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(135deg, $color-ink, #16324f 60%, #1d2436);
  text-align: center;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image:
      repeating-linear-gradient(0deg, rgba(212, 179, 116, 0.12) 0 1px, transparent 1px 26px),
      repeating-linear-gradient(90deg, rgba(212, 179, 116, 0.12) 0 1px, transparent 1px 26px);
    pointer-events: none;
  }
}

.fd-map__coord {
  position: relative;
  z-index: 1;
  font-family: $font-en;
  font-size: $fs-caption;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.fd-map__link {
  position: relative;
  z-index: 1;
  font-size: $fs-caption;
  letter-spacing: 0.08em;
  color: $color-text-light;
  text-decoration: underline;
  text-underline-offset: 4px;

  &:hover {
    color: $color-gold;
  }
}

/* ============================================================
 * 3. 同分类推荐
 * ========================================================== */
.fd-reco {
  padding-block: clamp(48px, 7vw, 110px);
}

.fd-reco__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: clamp(18px, 2.4vw, 34px);
  margin-top: clamp(24px, 3.4vw, 44px);
}

.fd-reco__card {
  @include hover-lift;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover .fd-reco__img {
    transform: scale(1.06);
  }
}

.fd-reco__img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.fd-reco__body {
  padding: clamp(14px, 1.6vw, 20px);
}

.fd-reco__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.3vw, 20px);
  letter-spacing: 0.06em;
  color: $color-text-dark;
}

.fd-reco__meta {
  margin-top: 6px;
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.55);
}

/* ============================================================
 * 找不到
 * ========================================================== */
.fd-missing {
  @include flex-center(column);
  gap: 18px;
  padding-block: clamp(80px, 14vw, 180px);
  text-align: center;
}

.fd-missing__text {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  color: rgba(var(--sy-text-rgb), 0.6);
}

/* ============================================================
 * 响应式
 * ========================================================== */
</style>
