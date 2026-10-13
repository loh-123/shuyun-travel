<script setup lang="ts">
/**
 * 第四屏（原为第五屏）：八大文化入口
 * - 宣纸底色（由 Home.vue 背景舞台提供）
 * - 调 listCulture() 取前 8 条，渲染为文化卡片（封面图 + 名称 + 分类 + 摘要）
 * - 图片懒加载 + 加载失败兜底；含骨架屏 / 空数据 / 加载失败三种状态
 * - 点击卡片进入 /culture/:id
 * - 复用公共能力：SectionTitle、useScrollReveal、GSAP
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'

import SectionTitle from '@/components/common/SectionTitle.vue'
import { useScrollReveal } from '@/composables/useScrollReveal'
import { listCulture, type CultureVO } from '@/api/culture'
import StateBlock from '@/components/common/StateBlock.vue'

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)

const list = ref<CultureVO[]>([])
const loading = ref(true)
/** 接口是否加载失败 */
const failed = ref(false)
/** 单张图片加载失败的标记（按 id） */
const imgFailed = ref<Record<number, boolean>>({})

/** 区块背景：取首条文化封面作为真实背景图 */
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

// 页头文案入场
useScrollReveal(rootEl, '.culture__animate', { y: 32, duration: 0.9, stagger: 0.1, start: 'top 72%' })

/** 图片加载失败：切到纯色占位 */
function onImgError(item: CultureVO) {
  imgFailed.value = { ...imgFailed.value, [item.id]: true }
}

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

async function loadList() {
  loading.value = true
  failed.value = false
  try {
    const data = await listCulture()
    const arr = Array.isArray(data) ? data : []
    // 取前 8 条（后端一次返回全部，不分页）
    list.value = arr.slice(0, 8)
    // 区块背景：用真实文化封面图
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

  // 卡片异步渲染，数据就位后再补入场动画
  await nextTick()
  cardCtx = gsap.context(() => {
    if (gridEl.value) {
      gsap.from('.culture__card', {
        y: 48,
        opacity: 0,
        duration: 0.9,
        stagger: 0.07,
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
  <div ref="rootEl" class="culture" :style="bgStyle">
    <div class="sy-container culture__inner">
      <SectionTitle
        class="culture__head"
        eyebrow="Eight Cultures"
        variant="on-light"
        animate-class="culture__animate"
        title="八大文化 · 一脉蜀风"
        desc="川剧、蜀绣、川菜、茶马古道、三国、三星堆、熊猫与美酒 —— 八条线索，串起巴蜀大地上最有温度的部分。"
      />

      <!-- 加载中 / 失败 / 空数据 -->
      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="failed" state="error" :retry="loadList" />
      <StateBlock v-else-if="!list.length" state="empty" empty-text="暂无文化内容" />

      <!-- 正常列表 -->
      <div v-else ref="gridEl" class="culture__grid">
        <article
          v-for="item in list"
          :key="item.id"
          class="culture__card"
          @click="goDetail(item.id)"
        >
          <div class="culture__media">
            <img
              v-if="item.coverImage && !imgFailed[item.id]"
              class="culture__img"
              :src="item.coverImage"
              :alt="item.name"
              loading="lazy"
              @error="onImgError(item)"
            />
            <div v-else class="culture__img-fallback">{{ (item.name || '蜀').slice(0, 1) }}</div>

            <span v-if="item.category" class="culture__cat">{{ item.category }}</span>
          </div>

          <div class="culture__body">
            <h3 class="culture__name">{{ item.name }}</h3>
            <p class="culture__text">{{ item.summary || '' }}</p>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

/* 宣纸底：由 Home.vue 的背景舞台（宣纸层）提供，这里保持透明以便与前后屏交叉淡入 */
.culture {
  position: relative;
  width: 100%;
  height: 100%;
  overflow-y: auto;
  background-color: transparent;
  /* 真实文化背景图：铺满居中（叠加深浅渐变保证可读） */
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  color: $color-text-dark;
}

.culture__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  // 纵向节奏跟随视口高度（整屏分节），保持原有观感
  padding-block: clamp(48px, 9vh, 120px);
}

.culture__head {
  margin-bottom: clamp(28px, 5vh, 64px);
}

/* ---------------- 卡片网格 ---------------- */
.culture__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: $space-md;
}

.culture__card {
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

  &:hover .culture__img {
    transform: scale(1.06);
  }
}

.culture__media {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: linear-gradient(135deg, #d9cdb8, #bfae90);
}

.culture__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s $ease-out-expo;
}

.culture__img-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, $color-gold, $color-primary);
  color: #fff;
  font-family: $font-title;
  font-size: 2rem;
}

.culture__cat {
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

.culture__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: clamp(14px, 1.4vw, 20px);
}

.culture__name {
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.08em;
  color: $color-text-dark;
}

.culture__text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: $fs-small;
  line-height: 1.8;
  color: rgba(var(--sy-text-rgb), 0.68);
}

/* ---------------- 骨架屏 ---------------- */
.culture__card--skeleton {
  pointer-events: none;
}

.culture__media--skeleton,
.culture__skeleton-line {
  background: linear-gradient(
    90deg,
    rgba(212, 179, 116, 0.14) 25%,
    rgba(212, 179, 116, 0.3) 37%,
    rgba(212, 179, 116, 0.14) 63%
  );
  background-size: 400% 100%;
  animation: cultureShimmer 1.4s ease infinite;
}

.culture__media--skeleton {
  width: 100%;
  aspect-ratio: 16 / 10;
}

.culture__skeleton-line {
  height: 14px;
  margin: 12px 16px 0;
  border-radius: 6px;

  &--short {
    width: 40%;
    margin-bottom: 16px;
  }
}

@keyframes cultureShimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}

/* ---------------- 空 / 失败状态 ---------------- */
.culture__state {
  padding: clamp(40px, 8vh, 80px);
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.1em;
  text-align: center;
  color: rgba(var(--sy-text-rgb), 0.6);
}

/* ---------------- 响应式 ---------------- */
@include responsive(md) {
  .culture__grid {
    grid-template-columns: 1fr;
  }
}
</style>
