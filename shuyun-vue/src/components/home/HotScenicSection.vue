<script setup lang="ts">
/**
 * 第四屏：热门景点
 * - 宣纸底色（由 Home.vue 的背景舞台提供）
 * - 调 getHotScenic(6) 展示 6 张景点卡片（封面图 + 名称 + 城市/类型 + 推荐理由 + 标签）
 * - 图片懒加载 + 加载失败兜底；含骨架屏 / 空数据 / 加载失败三种状态
 * - 点击卡片进入 /scenic/:id
 * - 复用公共能力：SectionTitle、useScrollReveal、GSAP
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'

import SectionTitle from '@/components/common/SectionTitle.vue'
import { useScrollReveal } from '@/composables/useScrollReveal'
import { getHotScenic, type ScenicSpotVO } from '@/api/scenic'
import StateBlock from '@/components/common/StateBlock.vue'

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)

const list = ref<ScenicSpotVO[]>([])
const loading = ref(true)
/** 接口是否加载失败 */
const failed = ref(false)
/** 单张图片加载失败的标记（按 id） */
const imgFailed = ref<Record<number, boolean>>({})

/** 区块背景：取首个热门景点封面作为真实背景图 */
const bgImage = ref('')
/** 背景样式：真实图片 + 上下深浅渐变遮罩（保证标题与卡片可读） */
const bgStyle = computed(() =>
  bgImage.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(245,241,232,0.72) 0%, rgba(245,241,232,0.4) 46%, rgba(245,241,232,0.78) 100%), url('${bgImage.value}')`,
      }
    : undefined,
)

let cardCtx: gsap.Context | null = null

// 页头文案入场（与其它分节一致的入场方式）
useScrollReveal(rootEl, '.hot__animate', { y: 32, duration: 0.9, stagger: 0.1, start: 'top 72%' })

/** 标签拆分 */
function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/** 图片加载失败：切到纯色占位 */
function onImgError(item: ScenicSpotVO) {
  imgFailed.value = { ...imgFailed.value, [item.id]: true }
}

function goDetail(id: number) {
  router.push(`/scenic/${id}`)
}

async function loadList() {
  loading.value = true
  failed.value = false
  try {
    const data = await getHotScenic(6)
    list.value = Array.isArray(data) ? data.slice(0, 6) : []
    // 区块背景：用真实景点封面图
    bgImage.value = list.value[0]?.coverImage || ''
  } catch {
    failed.value = true
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadList()

  // 卡片是异步渲染的，数据就位后再补入场动画
  await nextTick()
  cardCtx = gsap.context(() => {
    if (gridEl.value) {
      gsap.from('.hot__card', {
        y: 48,
        opacity: 0,
        duration: 0.9,
        stagger: 0.08,
        ease: 'power3.out',
        scrollTrigger: { trigger: gridEl.value, start: 'top 82%', once: true },
      })
    }
  }, rootEl.value ?? undefined)
})

onUnmounted(() => {
  cardCtx?.revert()
  cardCtx = null
})
</script>

<template>
  <div ref="rootEl" class="hot" :style="bgStyle">
    <div class="sy-container hot__inner">
      <SectionTitle
        class="hot__head"
        eyebrow="Hot Scenic"
        variant="on-light"
        animate-class="hot__animate"
        title="热门景点 · 蜀地必访"
        desc="从高评分的世界遗产到人气古镇，选 6 处最值得专程前往的蜀地风景，作为你行程的起点。"
      />

      <!-- 加载中 / 失败 / 空数据 -->
      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="failed" state="error" :retry="loadList" />
      <StateBlock v-else-if="!list.length" state="empty" empty-text="暂无热门景点" />

      <!-- 正常列表 -->
      <div v-else ref="gridEl" class="hot__grid">
        <article
          v-for="item in list"
          :key="item.id"
          class="hot__card"
          @click="goDetail(item.id)"
        >
          <div class="hot__media">
            <img
              v-if="item.coverImage && !imgFailed[item.id]"
              class="hot__img"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              @error="onImgError(item)"
            />
            <div v-else class="hot__img-fallback">{{ (item.name || '蜀').slice(0, 1) }}</div>

            <span v-if="item.level" class="hot__level">{{ item.level }}</span>
            <span v-if="item.score != null" class="hot__score">★ {{ item.score }}</span>
          </div>

          <div class="hot__body">
            <h3 class="hot__name">{{ item.name }}</h3>
            <p class="hot__meta">
              {{ item.cityName || '—' }}<template v-if="item.type"> · {{ item.type }}</template>
            </p>
            <p class="hot__text">
              {{ item.recommendReason || item.description || '' }}
            </p>
            <div v-if="splitTags(item.tags).length" class="hot__tags">
              <span v-for="t in splitTags(item.tags).slice(0, 3)" :key="t" class="hot__tag">
                {{ t }}
              </span>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

/* 宣纸底：由 Home.vue 的背景舞台提供，这里保持透明以便与前后屏交叉淡入 */
.hot {
  position: relative;
  width: 100%;
  height: 100%;
  overflow-y: auto;
  background-color: transparent;
  /* 真实景点背景图：铺满居中（叠加深浅渐变保证可读） */
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  color: $color-text-dark;
}

.hot__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  padding-block: clamp(48px, 9vh, 120px);
}

.hot__head {
  margin-bottom: clamp(24px, 4vh, 48px);
}

/* ---------------- 卡片网格 ---------------- */
.hot__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: $space-md;
}

.hot__card {
  @include hover-lift;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.42);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.72);
  box-shadow: $shadow-md;
  backdrop-filter: blur(6px);
  cursor: pointer;

  &:hover .hot__img {
    transform: scale(1.06);
  }
}

.hot__media {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  // 纯色底：图片未加载 / 加载失败时同样有底色
  background: linear-gradient(135deg, #d9cdb8, #bfae90);
}

.hot__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s $ease-out-expo;
}

.hot__img-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, $color-gold, $color-primary);
  color: #fff;
  font-family: $font-title;
  font-size: 2rem;
}

.hot__level {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(122, 26, 34, 0.9);
  color: #f5f1e8;
  font-size: $fs-small;
  letter-spacing: 0.08em;
}

.hot__score {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: $color-gold;
  font-size: $fs-small;
}

.hot__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: clamp(14px, 1.4vw, 20px);
}

.hot__name {
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.06em;
  color: $color-text-dark;
}

.hot__meta {
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.hot__text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: $fs-small;
  line-height: 1.7;
  color: rgba(var(--sy-text-rgb), 0.68);
}

.hot__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.hot__tag {
  padding: 2px 8px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: 999px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.7);
}

/* ---------------- 骨架屏 ---------------- */
.hot__card--skeleton {
  pointer-events: none;
}

.hot__media--skeleton,
.hot__skeleton-line {
  background: linear-gradient(
    90deg,
    rgba(212, 179, 116, 0.14) 25%,
    rgba(212, 179, 116, 0.3) 37%,
    rgba(212, 179, 116, 0.14) 63%
  );
  background-size: 400% 100%;
  animation: hotShimmer 1.4s ease infinite;
}

.hot__media--skeleton {
  width: 100%;
  aspect-ratio: 16 / 10;
}

.hot__skeleton-line {
  height: 14px;
  margin: 12px 16px 0;
  border-radius: 6px;

  &--short {
    width: 40%;
    margin-bottom: 16px;
  }
}

@keyframes hotShimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}

/* ---------------- 空 / 失败状态 ---------------- */
.hot__state {
  padding: clamp(40px, 8vh, 80px);
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.1em;
  text-align: center;
  color: rgba(var(--sy-text-rgb), 0.6);
}

/* ---------------- 响应式 ---------------- */
@include responsive(md) {
  .hot__grid {
    grid-template-columns: 1fr;
  }
}
</style>
