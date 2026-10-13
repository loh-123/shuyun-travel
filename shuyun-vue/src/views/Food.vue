<script setup lang="ts">
/**
 * 川味地图（美食列表页）
 * - 顶部 Hero + sticky 筛选栏（分类胶囊 + 搜索）+ 卡片网格 + 自定义分页
 * - 数据来自 /api/food/list、/api/food/categories
 * - 复用公共设计系统（variables / mixins / GoldButton）
 * - 交互状态：加载中（v-loading）/ 空数据 / 加载失败
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listFood, listFoodCategories, type FoodVO } from '@/api/food'
import StateBlock from '@/components/common/StateBlock.vue'
import Pagination from '@/components/common/Pagination.vue'
import { t } from '@/i18n'

const router = useRouter()

/** 换成本地大图：/images/food-hero.webp */
const HERO_BG =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1920&q=80'
const HERO_BG_2X =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=2560&q=80'

/** 每页条数 */
const PAGE_SIZE = 12

const categories = ref<string[]>([])
const list = ref<FoodVO[]>([])
const total = ref(0)
const loading = ref(false)
/** 接口是否加载失败 */
const loadFailed = ref(false)
/** 单张封面加载失败的标记（按美食 id） */
const imgFailed = ref<Record<number, boolean>>({})

const query = reactive({
  page: 1,
  size: PAGE_SIZE,
  category: '',
  keyword: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

/** 简介截断 */
function truncate(text: string | undefined, max: number) {
  const s = (text || '').replace(/\n/g, '')
  return s.length > max ? `${s.slice(0, max)}…` : s
}

/** 封面图加载失败：切纯色占位 */
function onImgError(item: FoodVO) {
  imgFailed.value = { ...imgFailed.value, [item.id]: true }
}

function goDetail(id: number) {
  router.push(`/food/${id}`)
}

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadCategories() {
  try {
    const data = await listFoodCategories()
    categories.value = Array.isArray(data) ? data : []
  } catch {
    // 分类为空不影响列表
  }
}

async function loadList() {
  loading.value = true
  loadFailed.value = false
  try {
    const data = await listFood({
      page: query.page,
      size: query.size,
      category: query.category || undefined,
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
  if (query.category === cat) {
    return
  }
  query.category = cat
  query.page = 1
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

onMounted(() => {
  loadCategories()
  loadList()
})
</script>

<template>
  <section class="food-page">
    <!-- ================= 1. Hero ================= -->
    <header class="food-hero">
      <img
        class="food-hero__bg"
        :src="HERO_BG"
        :srcset="`${HERO_BG} 1920w, ${HERO_BG_2X} 2560w`"
        sizes="100vw"
        alt="川味美食"
        fetchpriority="high"
        decoding="async"
      />
      <div class="sy-overlay">
        <div class="sy-overlay__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="sy-container food-hero__inner">
        <nav class="food-hero__crumb">
          <router-link to="/">{{ t('nav.home') }}</router-link>
          <span class="food-hero__sep">/</span>
          <span>{{ t('nav.food') }}</span>
        </nav>
        <h1 class="food-hero__title">川味<span>地图</span></h1>
        <p class="food-hero__sub">麻辣鲜香 · 一菜一格 · 百菜百味</p>
      </div>
    </header>

    <!-- ================= 2. sticky 筛选栏 ================= -->
    <div class="food-filter">
      <div class="sy-container food-filter__inner">
        <div class="pills">
          <button
            type="button"
            class="pill"
            :class="{ 'is-active': query.category === '' }"
            @click="selectCategory('')"
          >
            全部
          </button>
          <button
            v-for="cat in categories"
            :key="cat"
            type="button"
            class="pill"
            :class="{ 'is-active': query.category === cat }"
            @click="selectCategory(cat)"
          >
            {{ cat }}
          </button>
        </div>

        <label class="usearch">
          <input
            v-model="query.keyword"
            class="usearch__input"
            type="text"
            placeholder="搜索美食 / 店铺"
            @keyup.enter="onSearch"
          />
          <span class="usearch__line" />
        </label>
      </div>
    </div>

    <!-- ================= 3. 美食网格 ================= -->
    <div class="sy-container food-body">
      <div class="food-body__head">
        <p class="sy-eyebrow">Sichuan Food</p>
        <h2 class="sy-page-title">舌尖上的巴蜀</h2>
        <p class="sy-page-desc">火锅、串串、川菜、小吃…… 共 {{ total }} 道川味，挑一份合你口味的。</p>
      </div>

      <!-- 加载中 -->
      <StateBlock v-if="loading" state="loading" />

      <div v-if="!loading" class="food-grid">
        <article
          v-for="item in list"
          :key="item.id"
          class="food-card"
          @click="goDetail(item.id)"
        >
          <div class="food-card__cover">
            <img
              v-if="item.coverImage && !imgFailed[item.id]"
              class="food-card__img"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              decoding="async"
              @error="onImgError(item)"
            />
            <div v-else class="food-card__img food-card__img--fallback">
              {{ (item.name || '食').slice(0, 1) }}
            </div>
            <span v-if="item.category" class="food-card__cat">{{ item.category }}</span>
          </div>

          <div class="food-card__body">
            <h3 class="food-card__name">{{ item.name }}</h3>

            <div class="food-card__meta">
              <span class="food-card__city">{{ item.cityName || '—' }}</span>
              <span v-if="item.avgPrice != null" class="food-card__price">¥{{ item.avgPrice }}/人</span>
            </div>

            <p class="food-card__desc">{{ truncate(item.description, 100) }}</p>

            <div class="food-card__foot">
              <span v-if="item.spicyLevel" class="food-card__spicy" :title="`辣度 ${item.spicyLevel}/5`">
                <i
                  v-for="n in 5"
                  :key="n"
                  class="food-card__chili"
                  :class="{ 'is-on': n <= item.spicyLevel }"
                >🌶</i>
              </span>
              <span v-if="item.shopName" class="food-card__shop">{{ item.shopName }}</span>
            </div>
          </div>
        </article>
      </div>

      <!-- 加载失败 / 空数据 -->
      <StateBlock v-if="!loading && loadFailed" state="error" :retry="loadList" />

      <StateBlock
        v-if="!loading && !loadFailed && !list.length"
        state="empty"
        empty-text="暂无美食"
      />

      <!-- ================= 4. 分页 ================= -->
      <Pagination :page="query.page" :total="total" :size="query.size" @update:page="goPage" />
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.food-page {
  background: $color-paper;
}

/* ============================================================
 * 1. Hero
 * ========================================================== */
.food-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: $hero-list-height;
  overflow: hidden;
  isolation: isolate;
}

.food-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: food-kenburns 26s ease-in-out infinite alternate;
}

@keyframes food-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.food-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(72px, 12vh, 150px) clamp(36px, 6vh, 72px);
}

.food-hero__crumb {
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

.food-hero__sep {
  color: $color-gold;
}

.food-hero__title {
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

.food-hero__sub {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(15px, 1.4vw, 22px);
  letter-spacing: 0.4em;
  color: rgba(245, 241, 232, 0.88);
  text-shadow: $shadow-text-soft;
}

/* ============================================================
 * 2. sticky 筛选栏
 * ========================================================== */
.food-filter {
  position: sticky;
  top: $nav-height;
  z-index: $z-header - 1;
  border-bottom: 1px solid rgba(212, 179, 116, 0.28);
  @include glass(rgba(var(--sy-paper-rgb), 0.82), 18px);
}

.food-filter__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: clamp(12px, 1.6vw, 26px);
  padding-block: clamp(12px, 1.4vw, 20px);
}

.pills {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px;
  border: 1px solid rgba(212, 179, 116, 0.35);
  border-radius: 16px;
  background: rgba(var(--sy-surface-rgb), 0.88);
  box-shadow: 0 10px 30px rgba(14, 42, 71, 0.06);
}

.pill {
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

.usearch {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 360px;
  flex: 1 1 320px;
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
  height: 1px;
  background: rgba(122, 26, 34, 0.28);
  transform-origin: left;
  transition:
    background-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.usearch:focus-within .usearch__line {
  background: $color-gold;
  transform: scaleX(1.04);
}

/* ============================================================
 * 3. 网格
 * ========================================================== */
.food-body {
  padding-block: clamp(32px, 5vw, 72px) clamp(64px, 8vw, 120px);
}

.food-body__head {
  margin-bottom: clamp(24px, 3.4vw, 48px);
}

.food-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: clamp(18px, 2vw, 30px);
  min-height: 200px;
}

.food-card {
  @include hover-lift;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover {
    .food-card__img {
      transform: scale(1.08);
    }

    .food-card__name {
      color: var(--sy-accent);
    }
  }
}

.food-card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(14, 42, 71, 0.12), rgba(122, 26, 34, 0.12));
}

.food-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;

  &--fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, $color-primary, #6e141c);
    color: $color-gold;
    font-family: $font-title;
    font-size: 2rem;
  }
}

.food-card__cat {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 13px;
  border-radius: $radius-pill;
  background: rgba(14, 42, 71, 0.72);
  backdrop-filter: blur(6px);
  font-size: $fs-tiny;
  letter-spacing: 0.08em;
  color: $color-gold;
}

.food-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: clamp(16px, 1.8vw, 24px);
}

.food-card__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 1.5vw, 22px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
  transition: color $duration-normal $ease-out-expo;
}

.food-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.food-card__price {
  font-family: $font-en;
  color: var(--sy-accent);
}

.food-card__desc {
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.85;
  color: rgba(var(--sy-text-rgb), 0.66);
  @include text-ellipsis(2);
}

.food-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
}

.food-card__spicy {
  display: inline-flex;
  gap: 1px;
  font-size: 13px;
  line-height: 1;
}

.food-card__chili {
  filter: grayscale(1);
  opacity: 0.35;

  &.is-on {
    filter: none;
    opacity: 1;
  }
}

.food-card__shop {
  min-width: 0;
  font-size: $fs-caption;
  letter-spacing: 0.04em;
  color: $color-bamboo;
  @include text-ellipsis(1);
}

/* ============================================================
 * 4. 空 / 失败状态
 * ========================================================== */
.food-state {
  @include flex-center(column);
  gap: 18px;
  padding-block: clamp(48px, 8vw, 110px);
  text-align: center;
}

.food-state__text {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  color: rgba(var(--sy-text-rgb), 0.6);
}

/* ============================================================
 * 5. 分页
 * ========================================================== */
.food-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 1.4vw, 22px);
  margin-top: clamp(36px, 5vw, 72px);
}

.food-pager__num {
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

.food-pager__gap {
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(var(--sy-text-rgb), 0.35);
}

.food-pager__arrow {
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
  .food-hero__title span {
    -webkit-text-stroke-width: 1px;
  }

  .food-grid {
    grid-template-columns: 1fr;
  }

  .usearch {
    margin-left: 0;
    min-width: 100%;
    max-width: none;
  }
}
</style>
