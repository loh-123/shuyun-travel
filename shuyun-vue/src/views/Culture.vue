<script setup lang="ts">
/**
 * 文化寻踪（列表页）
 * - 数据来自 GET /api/culture/list（游客可访问）
 * - 支持按分类筛选（分类从数据里动态去重，不写死）
 * - 卡片点击进入 /culture/:id
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

interface CultureItem {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  tags?: string
}

const router = useRouter()

const loading = ref(true)
const list = ref<CultureItem[]>([])
const activeCategory = ref('全部')

/** 分类：从数据动态去重（保持出现顺序） */
const categories = computed(() => {
  const set: string[] = []
  list.value.forEach((item) => {
    if (item.category && !set.includes(item.category)) {
      set.push(item.category)
    }
  })
  return ['全部', ...set]
})

/** 当前筛选后的列表 */
const filtered = computed(() =>
  activeCategory.value === '全部'
    ? list.value
    : list.value.filter((item) => item.category === activeCategory.value),
)

/** 标签拆分为数组 */
function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/culture/list', { silent: true })
    list.value = Array.isArray(res.data) ? res.data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

onMounted(loadList)
</script>

<template>
  <section class="culture-page">
    <!-- ============ 页头 ============ -->
    <header class="culture-page__hero">
      <div class="culture-page__hero-inner">
        <p class="culture-page__eyebrow">Culture of Shu</p>
        <h1 class="culture-page__title">文化寻踪</h1>
        <p class="culture-page__desc">
          川剧的一记变脸、蜀绣的一根丝线、三星堆的一尊面具 ——
          巴蜀文化不在博物馆的玻璃后面，它在街巷、在餐桌上、在手艺人的指尖。
        </p>

        <div class="culture-page__stats">
          <span><b>{{ list.length }}</b> 大文化</span>
          <i class="culture-page__dot" />
          <span><b>{{ categories.length - 1 }}</b> 个分类</span>
          <i class="culture-page__dot" />
          <span><b>128</b> 项非遗</span>
        </div>
      </div>
    </header>

    <div class="culture-page__body">
      <!-- ============ 分类筛选 ============ -->
      <div class="culture-page__filters">
        <button
          v-for="c in categories"
          :key="c"
          class="culture-page__chip"
          :class="{ 'is-active': c === activeCategory }"
          @click="activeCategory = c"
        >
          {{ c }}
        </button>
      </div>

      <!-- ============ 卡片网格 ============ -->
      <div v-loading="loading" class="culture-page__grid">
        <article
          v-for="item in filtered"
          :key="item.id"
          class="ccard"
          @click="goDetail(item.id)"
        >
          <div class="ccard__cover">
            <img
              v-if="item.coverImage"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              decoding="async"
            />
            <span v-if="item.category" class="ccard__badge">{{ item.category }}</span>
          </div>

          <div class="ccard__body">
            <h3 class="ccard__name">{{ item.name }}</h3>
            <p class="ccard__summary">{{ item.summary }}</p>

            <div class="ccard__tags">
              <span v-for="t in splitTags(item.tags)" :key="t" class="ccard__tag">{{ t }}</span>
            </div>

            <span class="ccard__more">了解详情 →</span>
          </div>
        </article>
      </div>

      <el-empty
        v-if="!loading && !filtered.length"
        description="该分类下暂无文化条目"
        class="culture-page__empty"
      />
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.culture-page {
  min-height: 60vh;
  background: $color-paper;
}

/* ---------------- 页头 ---------------- */
.culture-page__hero {
  position: relative;
  padding: clamp(48px, 7vw, 96px) 0 clamp(36px, 5vw, 64px);
  background:
    radial-gradient(110% 140% at 82% 0%, rgba(201, 168, 106, 0.22) 0%, rgba(14, 42, 71, 0) 58%),
    linear-gradient(160deg, $color-ink 0%, #16324f 55%, #1d2436 100%);
  overflow: hidden;

  // 蜀锦纹样 + 噪点
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image:
      repeating-linear-gradient(45deg, #{$color-gold} 0 1px, transparent 1px 16px),
      repeating-linear-gradient(-45deg, #{$color-gold} 0 1px, transparent 1px 16px);
    opacity: 0.06;
    mix-blend-mode: overlay;
    pointer-events: none;
  }
}

.culture-page__hero-inner {
  position: relative;
  z-index: 1;
  width: 100%;
  padding-inline: $gutter;
}

.culture-page__eyebrow {
  font-family: $font-en-body;
  font-size: $fs-eyebrow;
  font-weight: 500;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: $color-gold;
}

.culture-page__title {
  margin-top: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(36px, 5vw, 68px);
  letter-spacing: 0.12em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.culture-page__desc {
  max-width: 62ch;
  margin-top: clamp(14px, 2vw, 24px);
  font-family: $font-body;
  font-size: $fs-body;
  line-height: 1.9;
  color: rgba(245, 241, 232, 0.86);
}

.culture-page__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: clamp(20px, 3vw, 36px);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.72);

  b {
    font-family: $font-en;
    font-size: 1.5em;
    color: $color-gold;
  }
}

.culture-page__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(201, 168, 106, 0.6);
}

/* ---------------- 主体 ---------------- */
.culture-page__body {
  padding: clamp(28px, 4vw, 56px) $gutter clamp(64px, 8vw, 120px);
}

/* 分类筛选 */
.culture-page__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: clamp(24px, 3vw, 40px);
}

.culture-page__chip {
  padding: 9px 22px;
  border: 1px solid rgba(140, 31, 40, 0.24);
  border-radius: $radius-pill;
  background: transparent;
  font-size: $fs-small;
  letter-spacing: 0.12em;
  color: $color-text-dark;
  transition:
    background-color $dur-base $ease-out-expo,
    color $dur-base $ease-out-expo,
    border-color $dur-base $ease-out-expo,
    transform $dur-base $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-primary;
    transform: translateY(-2px);
  }

  &.is-active {
    background: $color-primary;
    border-color: $color-primary;
    color: $color-text-light;
  }
}

/* ---------------- 卡片网格 ---------------- */
.culture-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: clamp(20px, 2.4vw, 36px);
  min-height: 120px;
}

.ccard {
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-card;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-card;
  overflow: hidden;
  cursor: pointer;
  transition:
    transform $dur-base $ease-out-expo,
    box-shadow $dur-base $ease-out-expo,
    border-color $dur-base $ease-out-expo;
  will-change: transform;

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: $shadow-card-hover;
    border-color: $color-gold;

    .ccard__cover img {
      transform: scale(1.06);
    }

    .ccard__name {
      color: $color-primary;
    }
  }
}

.ccard__cover {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(14, 42, 71, 0.12), rgba(140, 31, 40, 0.12));

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 1s $ease-out-expo;
  }
}

.ccard__badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 5px 14px;
  border-radius: $radius-pill;
  background: rgba(14, 42, 71, 0.72);
  backdrop-filter: blur(6px);
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.ccard__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: clamp(18px, 2vw, 26px);
}

.ccard__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(20px, 1.8vw, 26px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
  transition: color $dur-base $ease-out-expo;
}

.ccard__summary {
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.66);
}

.ccard__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
  padding-top: 6px;
}

.ccard__tag {
  padding: 3px 10px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-sm;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-bamboo;
}

.ccard__more {
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-primary;
}

.culture-page__empty {
  margin-top: 32px;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 768px) {
  .culture-page__grid {
    grid-template-columns: 1fr;
  }
}
</style>
