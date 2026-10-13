<script setup lang="ts">
/**
 * 非遗详情
 * - 首屏 Hero（60vh）：图片集首图，无图时纯色 + 蜀锦纹理 + 大号首字
 * - 信息卡区域：传承人 / 类别 / 级别 / 官网
 * - 正文：简介
 * - 图片集：网格展示
 * - 视频：B站 iframe 嵌入
 * - 底部：同类别其他非遗推荐（3 张）
 * - 数据来自 /api/heritage/{id}、/api/heritage/list
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getHeritageDetail, listHeritage, type HeritageDetailVO, type HeritageVO } from '@/api/heritage'
import { listAllPerson } from '@/api/person'
import StateBlock from '@/components/common/StateBlock.vue'
import PersonCards, { type PersonCardItem } from '@/components/common/PersonCards.vue'
import { t } from '@/i18n'

const route = useRoute()
const router = useRouter()

const detail = ref<HeritageDetailVO | null>(null)
const sameCategory = ref<HeritageVO[]>([])
const loading = ref(true)
const notFound = ref(false)

/** 传承人姓名 -> id 映射（用于跳转传承人详情） */
const personMap = ref<Record<string, number>>({})

/**
 * 当前非遗项目的传承人列表：inheritor 可能是单值或逗号分隔（如「道安,倪成玉」），
 * 按姓名解析到传承人 id，配合 PersonCards 点击查看传承人详情。
 */
const inheritorPeople = computed<PersonCardItem[]>(() =>
  (detail.value?.inheritor || '')
    .split(/[,，、]/)
    .map((n) => n.trim())
    .filter(Boolean)
    .map((name) => ({ id: personMap.value[name] ?? null, name })),
)

/** 图片兜底占位图 */
const IMG_FALLBACK =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="450"><rect width="100%" height="100%" fill="#F0EBE0"/><text x="50%" y="50%" fill="#D4B374" font-size="60" font-family="serif" text-anchor="middle" dominant-baseline="middle">蜀</text></svg>',
  )

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') {
    return
  }
  img.dataset.fallback = '1'
  img.src = IMG_FALLBACK
}

/** 图片集 */
const images = computed(() => (detail.value?.images ?? []).filter(Boolean) as string[])
/** 首屏大图：图片集第一张 */
const heroImage = computed(() => images.value[0] || '')
/** 无图时的首字 */
const firstChar = computed(() => (detail.value?.name || '遗').slice(0, 1))

/** 视频嵌入地址（B站普通视频页 → 播放器嵌入地址） */
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

function goDetail(id: number) {
  router.push(`/heritage/${id}`)
}

/** 加载全部传承人并建立「姓名 -> id」映射，失败静默 */
async function loadPersonMap() {
  try {
    const list = await listAllPerson(100)
    const map: Record<string, number> = {}
    for (const p of Array.isArray(list) ? list : []) {
      if (p?.name) {
        map[p.name] = p.id
      }
    }
    personMap.value = map
  } catch {
    personMap.value = {}
  }
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
    const data = await listHeritage({ page: 1, size: 4, category })
    const arr = Array.isArray(data?.records) ? data!.records : []
    sameCategory.value = arr.filter((h) => h.id !== currentId).slice(0, 3)
  } catch {
    sameCategory.value = []
  }
}

async function loadDetail(id: number) {
  loading.value = true
  notFound.value = false
  try {
    const data = await getHeritageDetail(id)
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
  loadPersonMap()
})

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
  <section class="hd-page">
    <StateBlock v-if="loading" state="loading" />

    <template v-if="detail">
      <!-- ================= 1. Hero（60vh） ================= -->
      <header class="hd-hero">
        <img
          v-if="heroImage"
          class="hd-hero__bg"
          :src="heroImage"
          :alt="detail.name"
          fetchpriority="high"
          decoding="async"
          @error="onImgError"
        />
        <div v-else class="hd-hero__plain">
          <span class="hd-hero__plain-char">{{ firstChar }}</span>
        </div>

        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container hd-hero__inner">
          <nav class="hd-hero__crumb">
            <router-link to="/">{{ t('nav.home') }}</router-link>
            <span class="hd-hero__sep">/</span>
            <router-link to="/heritage">{{ t('nav.heritage') }}</router-link>
            <span class="hd-hero__sep">/</span>
            <span>{{ detail.name }}</span>
          </nav>

          <h1 class="hd-hero__title">{{ detail.name }}</h1>

          <div class="hd-hero__meta">
            <span v-if="detail.level" class="hd-hero__level">{{ detail.level }}</span>
            <template v-if="detail.category"><i class="hd-hero__dot" /><span>{{ detail.category }}</span></template>
          </div>
        </div>
      </header>

      <!-- ================= 2. 信息卡区域 ================= -->
      <div class="sy-container hd-info">
        <div class="hd-info__item">
          <span class="hd-info__label">类别</span>
          <span class="hd-info__value">{{ detail.category || '—' }}</span>
        </div>
        <div class="hd-info__item">
          <span class="hd-info__label">级别</span>
          <span class="hd-info__value">{{ detail.level || '—' }}</span>
        </div>
        <div class="hd-info__item">
          <span class="hd-info__label">官网</span>
          <a v-if="detail.website" class="hd-info__link" :href="detail.website" target="_blank" rel="noopener">
            查看 →
          </a>
          <span v-else class="hd-info__value">—</span>
        </div>
      </div>

      <!-- ================= 2.5 传承人（卡片，点击查看详情） ================= -->
      <section v-if="inheritorPeople.length" class="sy-container hd-people-sec">
        <h2 class="hd-block__title">传承人</h2>
        <PersonCards :persons="inheritorPeople" />
      </section>

      <!-- ================= 3. 正文 ================= -->
      <div v-if="detail.summary" class="sy-container hd-body">
        <h2 class="hd-block__title">简介</h2>
        <p class="hd-text">{{ detail.summary }}</p>
      </div>

      <!-- ================= 4. 图片集 ================= -->
      <section v-if="images.length" class="sy-container hd-gallery-sec">
        <p class="sy-eyebrow">Gallery</p>
        <h2 class="sy-page-title">图片集</h2>
        <div class="hd-gallery">
          <img
            v-for="(img, i) in images"
            :key="img"
            class="hd-gallery__item"
            :src="img"
            :alt="`${detail.name} 图片 ${i + 1}`"
            loading="lazy"
            decoding="async"
            @error="onImgError"
          />
        </div>
      </section>

      <!-- ================= 5. 视频 ================= -->
      <section v-if="detail.videoUrl" class="sy-container hd-video-sec">
        <p class="sy-eyebrow">Video</p>
        <h2 class="sy-page-title">相关影像</h2>
        <div v-if="embedUrl" class="hd-video">
          <iframe :src="embedUrl" allowfullscreen frameborder="0" :title="detail.name" />
        </div>
        <a v-else class="hd-video__link" :href="detail.videoUrl" target="_blank" rel="noopener">
          在新窗口打开视频 →
        </a>
      </section>

      <!-- ================= 6. 同类别推荐 ================= -->
      <section v-if="sameCategory.length" class="sy-container hd-reco">
        <p class="sy-eyebrow">More Heritage</p>
        <h2 class="sy-page-title">同类非遗</h2>
        <div class="hd-reco__grid">
          <article v-for="item in sameCategory" :key="item.id" class="hd-reco__card" @click="goDetail(item.id)">
            <div class="hd-reco__cover">
              <img
                v-if="item.coverImage"
                class="hd-reco__img"
                :src="item.coverImage"
                :alt="item.name"
                loading="lazy"
                decoding="async"
                @error="onImgError"
              />
              <span v-else class="hd-reco__char">{{ (item.name || '遗').slice(0, 1) }}</span>
              <span v-if="item.level" class="hd-reco__level">{{ item.level }}</span>
            </div>
            <div class="hd-reco__body">
              <h3 class="hd-reco__name">{{ item.name }}</h3>
              <p class="hd-reco__meta">{{ item.category || '' }}</p>
            </div>
          </article>
        </div>
      </section>
    </template>

    <!-- 不存在 / 报错 -->
    <StateBlock
      v-else-if="!loading"
      state="error"
      error-text="非遗信息不存在"
      action-text="返回非遗列表"
      :action="() => router.push('/heritage')"
    />
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.hd-page {
  background: $color-paper;
}

.hd-loading {
  min-height: 4px;
}

/* ============================================================
 * 1. Hero（60vh）
 * ========================================================== */
.hd-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: $hero-detail-height;
  overflow: hidden;
  isolation: isolate;
}

.hd-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: hd-kenburns 26s ease-in-out infinite alternate;
}

@keyframes hd-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

/* 无图：纯色 + 蜀锦纹理 + 大号首字 */
.hd-hero__plain {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  display: grid;
  place-items: center;
  background:
    radial-gradient(120% 120% at 20% 0%, rgba(212, 179, 116, 0.28) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(135deg, $color-ink, #16324f 60%, #1d2436);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: $pattern-brocade;
    opacity: 0.12;
    pointer-events: none;
  }
}

.hd-hero__plain-char {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(96px, 16vw, 240px);
  color: rgba(212, 179, 116, 0.5);
  text-shadow: $shadow-text;
}

.hd-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(80px, 14vh, 170px) clamp(44px, 8vh, 100px);
}

.hd-hero__crumb {
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

.hd-hero__sep {
  color: $color-gold;
}

.hd-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero-xl;
  line-height: 1.05;
  letter-spacing: 0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.hd-hero__meta {
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

.hd-hero__level {
  padding: 3px 14px;
  border: 1px solid rgba(212, 179, 116, 0.7);
  border-radius: $radius-pill;
  color: $color-gold;
  letter-spacing: 0.14em;
}

.hd-hero__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: $color-gold;
}

/* ============================================================
 * 2. 信息卡区域
 * ========================================================== */
.hd-info {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: clamp(12px, 1.6vw, 22px);
  margin-top: clamp(-40px, -3vw, -24px);
  position: relative;
  z-index: $z-content;
  padding: clamp(18px, 2.2vw, 28px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: $color-paper-glass;
  box-shadow: $shadow-lg;
}

.hd-info__item {
  @include sy-card-window;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: clamp(14px, 1.4vw, 20px);
}

.hd-info__label {
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-gold;
}

.hd-info__value {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 20px);
  letter-spacing: 0.04em;
  color: $color-text-dark;
}

.hd-info__item.is-person {
  border-color: rgba(212, 179, 116, 0.7);
  background: rgba(212, 179, 116, 0.1);
}

.hd-info__person {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;

  &:hover {
    .hd-info__value {
      color: var(--sy-accent);
    }

    .hd-info__person-hint {
      color: $color-gold;
    }
  }
}

.hd-info__person-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.55);
  transition: color $duration-normal $ease-out-expo;
}

.hd-info__link {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 20px);
  color: var(--sy-accent);
  text-decoration: underline;
  text-underline-offset: 4px;

  &:hover {
    color: $color-gold;
  }
}

/* ============================================================
 * 3. 正文
 * ========================================================== */
.hd-people-sec {
  padding-block: clamp(24px, 3vw, 48px) clamp(8px, 1vw, 16px);
}

.hd-body {
  padding-block: clamp(40px, 6vw, 88px) clamp(24px, 3vw, 40px);
}

.hd-block__title {
  position: relative;
  margin-bottom: 16px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(20px, 1.8vw, 28px);
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

.hd-text {
  font-family: $font-body;
  font-size: $fs-body-lg;
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.84);
  text-align: justify;
}

/* ============================================================
 * 4. 图片集
 * ========================================================== */
.hd-gallery-sec {
  padding-block: clamp(40px, 6vw, 88px);
}

.hd-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: clamp(10px, 1.2vw, 18px);
  margin-top: clamp(24px, 3.4vw, 44px);
}

.hd-gallery__item {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  box-shadow: $shadow-md;
}

/* ============================================================
 * 5. 视频
 * ========================================================== */
.hd-video-sec {
  padding-block: clamp(24px, 3vw, 48px) clamp(40px, 6vw, 88px);
}

.hd-video {
  width: 100%;
  max-width: 1323px;
  aspect-ratio: 16 / 9;
  margin-top: clamp(20px, 2.6vw, 36px);
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

.hd-video__link {
  display: inline-block;
  margin-top: 12px;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
  text-decoration: underline;
  text-underline-offset: 4px;

  &:hover {
    color: $color-gold;
  }
}

/* ============================================================
 * 6. 同类别推荐
 * ========================================================== */
.hd-reco {
  padding-block: clamp(40px, 6vw, 100px);
}

.hd-reco__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: clamp(18px, 2.4vw, 34px);
  margin-top: clamp(24px, 3.4vw, 44px);
}

.hd-reco__card {
  @include hover-lift;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;
}

.hd-reco__cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background:
    radial-gradient(120% 120% at 20% 0%, rgba(212, 179, 116, 0.28) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(135deg, $color-ink, #16324f 60%, #1d2436);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: $pattern-brocade;
    opacity: 0.12;
    pointer-events: none;
  }
}

/* 封面真图：铺满卡片上部 */
.hd-reco__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform $duration-normal $ease-out-expo;
}

.hd-reco__card:hover .hd-reco__img {
  transform: scale(1.06);
}

.hd-reco__char {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(38px, 4vw, 62px);
  color: $color-gold;
  text-shadow: $shadow-text;
  transition: transform $duration-normal $ease-out-expo;
}

.hd-reco__level {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  padding: 3px 12px;
  border-radius: $radius-pill;
  background: rgba(212, 179, 116, 0.9);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: $color-ink;
}

.hd-reco__body {
  padding: clamp(14px, 1.6vw, 20px);
}

.hd-reco__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.3vw, 20px);
  letter-spacing: 0.06em;
  color: $color-text-dark;
}

.hd-reco__meta {
  margin-top: 6px;
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.55);
}

/* ============================================================
 * 找不到
 * ========================================================== */
.hd-missing {
  @include flex-center(column);
  gap: 18px;
  padding-block: clamp(80px, 14vw, 180px);
  text-align: center;
}

.hd-missing__text {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  color: rgba(var(--sy-text-rgb), 0.6);
}

/* ============================================================
 * 响应式
 * ========================================================== */
@include responsive(md) {
  .hd-info {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: clamp(16px, 4vw, 28px);
  }
}
</style>
