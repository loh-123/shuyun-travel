<script setup lang="ts">
/**
 * 非遗名录（非物质文化遗产列表页）
 * - 顶部 Hero + sticky 筛选栏（类别下拉 + 级别下拉 + 搜索）+ 卡片网格 + 分页
 * - 数据来自 /api/heritage/list
 * - 表里没有封面图，卡片用「纯色 + 大号首字 + 蜀锦纹理」呈现
 * - 复用公共设计系统（variables / mixins / GoldButton）
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listHeritage, type HeritageVO } from '@/api/heritage'
import StateBlock from '@/components/common/StateBlock.vue'
import Pagination from '@/components/common/Pagination.vue'
import { t } from '@/i18n'

const router = useRouter()

/** Hero 背景：川剧变脸（本地川蜀非遗图） */
const HERO_BG = '/images/culture/chuanjubianlian/chuanjubianlian_1.jpg'

const PAGE_SIZE = 12

/** 类别 / 级别选项（与库中实际取值一致） */
const CATEGORIES = ['传统技艺', '传统美术', '传统戏剧', '民俗']
const LEVELS = ['国家级', '省级']

const list = ref<HeritageVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadFailed = ref(false)

const catOpen = ref(false)
const lvlOpen = ref(false)

const query = reactive({
  page: 1,
  size: PAGE_SIZE,
  category: '',
  level: '',
  keyword: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

function truncate(text: string | undefined, max: number) {
  const s = (text || '').replace(/\n/g, '')
  return s.length > max ? `${s.slice(0, max)}…` : s
}

/** 级别徽标样式：国家级金色，省级红色 */
function levelClass(level?: string) {
  return level === '国家级' ? 'is-national' : 'is-provincial'
}

function goDetail(id: number) {
  router.push(`/heritage/${id}`)
}

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadList() {
  loading.value = true
  loadFailed.value = false
  try {
    const data = await listHeritage({
      page: query.page,
      size: query.size,
      category: query.category || undefined,
      level: query.level || undefined,
      keyword: query.keyword || undefined,
    })
    list.value = data?.records ?? []
    total.value = data?.total ?? 0
  } catch {
    loadFailed.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function selectCategory(cat: string) {
  query.category = cat
  query.page = 1
  catOpen.value = false
  loadList()
}

function selectLevel(lv: string) {
  query.level = lv
  query.page = 1
  lvlOpen.value = false
  loadList()
}

function onSearch() {
  query.page = 1
  loadList()
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === query.page) {
    return
  }
  query.page = page
  loadList()
}

/** 点击下拉外部关闭 */
function onDocPointerDown(e: MouseEvent) {
  const target = e.target as Node
  const el = document.querySelector('.her-drops')
  if (el && !el.contains(target)) {
    catOpen.value = false
    lvlOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown)
  loadList()
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointerDown)
})
</script>

<template>
  <section class="her-page">
    <!-- ================= 1. Hero ================= -->
    <header class="her-hero">
      <img class="her-hero__bg" :src="HERO_BG" alt="非物质文化遗产" fetchpriority="high" decoding="async" />
      <div class="sy-overlay">
        <div class="sy-overlay__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="sy-container her-hero__inner">
        <nav class="her-hero__crumb">
          <router-link to="/">{{ t('nav.home') }}</router-link>
          <span class="her-hero__sep">/</span>
          <span>{{ t('nav.heritage') }}</span>
        </nav>
        <h1 class="her-hero__title">非遗<span>名录</span></h1>
        <p class="her-hero__sub">匠心传承 · 一脉相承的巴蜀技艺</p>
      </div>
    </header>

    <!-- ================= 2. sticky 筛选栏 ================= -->
    <div class="her-filter">
      <div class="sy-container her-filter__inner">
        <div class="her-drops">
          <!-- 类别下拉 -->
          <div class="dropdown" :class="{ 'is-open': catOpen }">
            <button type="button" class="dropdown__trigger" :aria-expanded="catOpen" @click="catOpen = !catOpen; lvlOpen = false">
              <span class="dropdown__value">{{ query.category || '全部类别' }}</span>
              <svg class="dropdown__caret" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6" /></svg>
            </button>
            <ul v-show="catOpen" class="dropdown__panel">
              <li class="dropdown__option" :class="{ 'is-active': query.category === '' }" @click="selectCategory('')">全部类别</li>
              <li v-for="c in CATEGORIES" :key="c" class="dropdown__option" :class="{ 'is-active': query.category === c }" @click="selectCategory(c)">{{ c }}</li>
            </ul>
          </div>

          <!-- 级别下拉 -->
          <div class="dropdown" :class="{ 'is-open': lvlOpen }">
            <button type="button" class="dropdown__trigger" :aria-expanded="lvlOpen" @click="lvlOpen = !lvlOpen; catOpen = false">
              <span class="dropdown__value">{{ query.level || '全部级别' }}</span>
              <svg class="dropdown__caret" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6" /></svg>
            </button>
            <ul v-show="lvlOpen" class="dropdown__panel">
              <li class="dropdown__option" :class="{ 'is-active': query.level === '' }" @click="selectLevel('')">全部级别</li>
              <li v-for="lv in LEVELS" :key="lv" class="dropdown__option" :class="{ 'is-active': query.level === lv }" @click="selectLevel(lv)">{{ lv }}</li>
            </ul>
          </div>
        </div>

        <button type="button" class="her-person-btn" @click="router.push('/person')">
          全部传承人
        </button>

        <label class="usearch">
          <input v-model="query.keyword" class="usearch__input" type="text" placeholder="搜索非遗项目 / 传承人" @keyup.enter="onSearch" />
          <span class="usearch__line" />
        </label>
      </div>
    </div>

    <!-- ================= 3. 网格 ================= -->
    <div class="sy-container her-body">
      <div class="her-body__head">
        <p class="sy-eyebrow">Intangible Heritage</p>
        <h2 class="sy-page-title">巴蜀非遗</h2>
        <p class="sy-page-desc">蜀锦、漆艺、川剧、扎染…… 共 {{ total }} 项非物质文化遗产，读懂匠人手上的一寸光阴。</p>
      </div>

      <!-- 加载中 -->
      <StateBlock v-if="loading" state="loading" />

      <div v-if="!loading" class="her-grid">
        <article v-for="item in list" :key="item.id" class="her-card" @click="goDetail(item.id)">
          <div class="her-card__cover">
            <img
              v-if="item.coverImage"
              class="her-card__img"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              decoding="async"
            />
            <span v-else class="her-card__char">{{ (item.name || '遗').slice(0, 1) }}</span>
            <span v-if="item.level" class="her-card__level" :class="levelClass(item.level)">{{ item.level }}</span>
          </div>

          <div class="her-card__body">
            <h3 class="her-card__name">{{ item.name }}</h3>
            <p class="her-card__meta">
              <span v-if="item.category">{{ item.category }}</span>
              <template v-if="item.inheritor"><i class="her-card__dot" />传承人 {{ item.inheritor }}</template>
            </p>
            <p class="her-card__desc">{{ truncate(item.summary, 120) }}</p>
          </div>
        </article>
      </div>

      <!-- 加载失败 / 空数据 -->
      <StateBlock v-if="!loading && loadFailed" state="error" :retry="loadList" />

      <StateBlock
        v-if="!loading && !loadFailed && !list.length"
        state="empty"
        empty-text="暂无非遗项目"
      />

      <!-- ================= 4. 分页 ================= -->
      <Pagination :page="query.page" :total="total" :size="query.size" @update:page="goPage" />
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.her-page {
  background: $color-paper;
}

/* ============================================================
 * 1. Hero
 * ========================================================== */
.her-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: $hero-detail-height;
  overflow: hidden;
  isolation: isolate;
}

.her-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: her-kenburns 26s ease-in-out infinite alternate;
}

@keyframes her-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.her-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(72px, 12vh, 150px) clamp(36px, 6vh, 72px);
}

.her-hero__crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-body;
  font-size: 14px;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.78);

  a:hover {
    color: $color-gold;
  }
}

.her-hero__sep {
  color: $color-gold;
}

.her-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(96px, 10vw, 160px);
  line-height: 1;
  letter-spacing: -0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;

  span {
    display: inline-block;
    margin-left: 0.06em;
    color: transparent;
    -webkit-text-stroke: 2.5px $color-gold;
    text-shadow: none;
  }
}

.her-hero__sub {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(18px, 1.6vw, 24px);
  letter-spacing: 0.3em;
  color: rgba(245, 241, 232, 0.88);
  text-shadow: $shadow-text-soft;
}

/* ============================================================
 * 2. sticky 筛选栏
 * ========================================================== */
.her-filter {
  position: sticky;
  top: $nav-height;
  z-index: $z-header - 1;
  margin-top: 40px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.28);
  @include glass(rgba(var(--sy-paper-rgb), 0.82), 18px);
}

.her-filter__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: clamp(12px, 1.6vw, 26px);
  min-height: 80px;
  padding-block: clamp(16px, 1.8vw, 24px);
}

.her-drops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border: 1px solid rgba(212, 179, 116, 0.35);
  border-radius: 16px;
  background: rgba(var(--sy-surface-rgb), 0.88);
  box-shadow: 0 10px 30px rgba(14, 42, 71, 0.06);
}

.dropdown {
  position: relative;
  min-width: 168px;
}

.dropdown__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  height: 54px;
  padding: 0 26px;
  border: none;
  border-radius: 11px;
  background: transparent;
  font-family: $font-body;
  font-size: 24px;
  letter-spacing: 0.04em;
  color: $color-text-dark;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.07);
  }
}

.dropdown.is-open .dropdown__caret {
  transform: rotate(180deg);
}

.dropdown__caret {
  color: $color-gold;
  transition: transform $duration-fast $ease-out-expo;
}

.dropdown__panel {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 20;
  width: max(100%, 168px);
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
  border-radius: $radius-lg;
  @include gold-border(0.42);
  background: rgba(var(--sy-surface-rgb), 0.94);
  backdrop-filter: blur(14px);
  box-shadow: $shadow-lg;
}

.dropdown__option {
  padding: 14px 20px;
  border-radius: $radius-md;
  font-family: $font-body;
  font-size: 24px;
  letter-spacing: 0.04em;
  color: rgba(var(--sy-text-rgb), 0.78);
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.07);
    color: var(--sy-accent);
  }

  &.is-active {
    background: linear-gradient(120deg, $color-primary, #6e141c);
    color: $color-text-light;
  }
}

.usearch {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 360px;
  flex: 1 1 360px;
  max-width: 640px;
  height: 60px;
  margin-left: auto;
  padding: 0 26px;
  border: 1px solid rgba(212, 179, 116, 0.45);
  border-radius: 14px;
  background: rgba(var(--sy-surface-rgb), 0.9);
  box-shadow: 0 6px 18px rgba(14, 42, 71, 0.05);
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:focus-within {
    border-color: $color-gold;
    box-shadow: 0 10px 26px rgba(212, 179, 116, 0.2);
  }

  // 方框搜索不再需要下划线
  .usearch__line {
    display: none;
  }
}

.usearch__input {
  width: 100%;
  padding: 6px 0;
  border: none;
  background: transparent;
  font-family: $font-body;
  font-size: 24px;
  letter-spacing: 0.04em;
  color: $color-text-dark;

  &::placeholder {
    color: rgba(var(--sy-text-rgb), 0.4);
  }
}

.usearch__line {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 1.5px;
  background: $color-gold;
  transform-origin: left;
  transition: background-color $duration-normal $ease-out-expo, transform $duration-normal $ease-out-expo;
}

.usearch:focus-within .usearch__line {
  background: $color-gold;
  transform: scaleX(1.04);
}

/* ============================================================
 * 3. 网格
 * ========================================================== */
.her-body {
  padding-block: clamp(32px, 5vw, 72px) clamp(64px, 8vw, 120px);
}

.her-body__head {
  margin-bottom: clamp(24px, 3.4vw, 48px);
}

.her-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 24px;
  min-height: 200px;
}

.her-card {
  @include hover-lift;
  display: flex;
  flex-direction: column;
  min-height: 380px;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover .her-card__char {
    transform: scale(1.06);
  }
}

/* 无封面：纯色 + 蜀锦纹理 + 大号首字 */
.her-card__cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
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

.her-card__char {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(48px, 6vw, 84px);
  color: $color-gold;
  text-shadow: $shadow-text;
  transition: transform $duration-normal $ease-out-expo;
}

/* 封面真图：铺满卡片上部 */
.her-card__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.her-card__level {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 6px 16px;
  border-radius: $radius-pill;
  font-size: 15px;
  letter-spacing: 0.08em;

  &.is-national {
    background: $color-gold;
    color: $color-ink;
  }

  &.is-provincial {
    background: $color-primary;
    color: $color-text-light;
  }
}

.her-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 32px;
}

.her-card__name {
  font-family: $font-title;
  font-weight: 700;
  font-size: 28px;
  letter-spacing: 0.04em;
  color: $color-text-dark;
  transition: color $duration-normal $ease-out-expo;
}

.her-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.her-card__dot {
  width: 3px;
  height: 3px;
  margin-right: 6px;
  border-radius: 50%;
  background: $color-gold;
}

.her-card__desc {
  font-family: $font-body;
  font-size: 16px;
  line-height: 1.85;
  color: rgba(var(--sy-text-rgb), 0.66);
  @include text-ellipsis(2);
}

/* ============================================================
 * 4. 空 / 失败 + 分页
 * ========================================================== */
.her-state {
  @include flex-center(column);
  gap: 18px;
  padding-block: clamp(48px, 8vw, 110px);
  text-align: center;
}

.her-state__text {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  color: rgba(var(--sy-text-rgb), 0.6);
}

.her-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 1.4vw, 22px);
  margin-top: clamp(36px, 5vw, 72px);
}

.her-pager__num {
  position: relative;
  padding: 4px 2px;
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(var(--sy-text-rgb), 0.55);
  transition: color $duration-fast $ease-out-expo;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 1px;
    background: $color-gold;
    transform: scaleX(0);
    transform-origin: center;
    transition: transform $duration-normal $ease-out-expo;
  }

  &:hover {
    color: var(--sy-accent);
  }

  &.is-active {
    color: $color-gold;

    &::after {
      transform: scaleX(1);
    }
  }
}

.her-pager__gap {
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(var(--sy-text-rgb), 0.35);
}

.her-pager__arrow {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  @include gold-border(0.5);
  color: var(--sy-accent);
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover:not(:disabled) {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-2px);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}

/* ============================================================
 * 响应式
 * ========================================================== */
@include responsive(md) {
  .her-hero__title span {
    -webkit-text-stroke-width: 1px;
  }

  .her-grid {
    grid-template-columns: 1fr;
  }

  .usearch {
    margin-left: 0;
    min-width: 100%;
    max-width: none;
  }
}
/* 全部传承人入口按钮（搜索框左侧） */
.her-person-btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: 1px solid $color-gold;
  border-radius: $radius-pill;
  background: rgba(212, 179, 116, 0.1);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  white-space: nowrap;
  color: var(--sy-accent);
  cursor: pointer;
  transition:
    background-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.22);
    transform: translateY(-1px);
  }
}
</style>
