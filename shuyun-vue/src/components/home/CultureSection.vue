<script setup lang="ts">
/**
 * 第四屏：八大文化入口
 * - 宣纸底色（与前后深色屏形成节奏对比）
 * - 8 个手绘线性图标（1.5px 描边，金色），hover 抬升 + 阴影加深
 * - 点击进入 /culture/:id
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)

/** 图标节点：用统一结构描述笔画，避免每个图标写一段模板 */
interface IconNode {
  t: 'path' | 'circle' | 'ellipse'
  d?: string
  cx?: number
  cy?: number
  r?: number
  rx?: number
  ry?: number
}

interface CultureItem {
  id: number
  cn: string
  en: string
  desc: string
  icon: IconNode[]
}

const CULTURES: CultureItem[] = [
  {
    id: 1,
    cn: '川剧变脸',
    en: 'Sichuan Opera',
    desc: '挥手转身之间变换脸谱，东方舞台的独门绝技。',
    icon: [
      { t: 'path', d: 'M24 7c7 0 11 5 11 12v5c0 9-5 16-11 18-6-2-11-9-11-18v-5c0-7 4-12 11-12z' },
      { t: 'path', d: 'M17 21h5M26 21h5' },
      { t: 'path', d: 'M18 31c3 3 9 3 12 0' },
      { t: 'path', d: 'M24 7V2M17 8l-3-4M31 8l3-4' },
    ],
  },
  {
    id: 2,
    cn: '蜀绣',
    en: 'Shu Embroidery',
    desc: '中国四大名绣之一，针脚细密，色彩明快。',
    icon: [
      { t: 'path', d: 'M32 6L15 23' },
      { t: 'circle', cx: 33, cy: 5, r: 2 },
      { t: 'path', d: 'M15 23c-4 5-2 11 3 13s11 1 13-4' },
      { t: 'path', d: 'M31 32l6 6M34 35l-2 2' },
    ],
  },
  {
    id: 3,
    cn: '川菜',
    en: 'Sichuan Cuisine',
    desc: '一菜一格，百菜百味，麻辣之外的万千层次。',
    icon: [
      { t: 'path', d: 'M8 26h32c0 8-7 15-16 15S8 34 8 26z' },
      { t: 'path', d: 'M5 26h38' },
      { t: 'path', d: 'M28 4l-3 16M35 4l-3 16' },
    ],
  },
  {
    id: 4,
    cn: '茶马古道',
    en: 'Tea Horse Road',
    desc: '以茶易马，穿越川滇藏高山峡谷的千年商路。',
    icon: [
      { t: 'path', d: 'M14 23h20v8a7 7 0 01-7 7h-6a7 7 0 01-7-7v-8z' },
      { t: 'path', d: 'M14 20c0-3 2-4 5-4h10c3 0 5 1 5 4' },
      { t: 'path', d: 'M34 26c5 0 7 1.5 7 4.5S39 35 34 35' },
      { t: 'path', d: 'M14 26c-5 0-7 1.5-7 4.5S9 35 14 35' },
      { t: 'path', d: 'M22 16v-4h4v4' },
    ],
  },
  {
    id: 5,
    cn: '三国文化',
    en: 'Three Kingdoms',
    desc: '蜀汉故地，武侯祠与剑门关里的英雄叙事。',
    icon: [
      { t: 'path', d: 'M24 4v20' },
      { t: 'path', d: 'M16 24h16' },
      { t: 'path', d: 'M24 24v12' },
      { t: 'path', d: 'M20 40h8' },
      { t: 'path', d: 'M21 8h6' },
    ],
  },
  {
    id: 6,
    cn: '三星堆',
    en: 'Sanxingdui',
    desc: '纵目面具与青铜神树，古蜀文明的神秘现场。',
    icon: [
      { t: 'path', d: 'M8 19c0-6 7-10 16-10s16 4 16 10v5c0 9-7 16-16 18C15 40 8 33 8 24v-5z' },
      { t: 'circle', cx: 16, cy: 23, r: 4 },
      { t: 'circle', cx: 32, cy: 23, r: 4 },
      { t: 'path', d: 'M18 33h12M24 9V4' },
    ],
  },
  {
    id: 7,
    cn: '大熊猫',
    en: 'Giant Panda',
    desc: '国宝栖息地，成都与卧龙的竹海与憨态。',
    icon: [
      { t: 'circle', cx: 24, cy: 27, r: 13 },
      { t: 'circle', cx: 13, cy: 14, r: 5 },
      { t: 'circle', cx: 35, cy: 14, r: 5 },
      { t: 'ellipse', cx: 18, cy: 24, rx: 3.4, ry: 4.6 },
      { t: 'ellipse', cx: 30, cy: 24, rx: 3.4, ry: 4.6 },
      { t: 'path', d: 'M21 34c2 1.6 4 1.6 6 0' },
    ],
  },
  {
    id: 8,
    cn: '酒文化',
    en: 'Liquor Culture',
    desc: '五粮液与泸州老窖，川酒里的时间与风土。',
    icon: [
      { t: 'path', d: 'M18 9h12v4c4 1.6 6 5 6 10v13a6 6 0 01-6 6H18a6 6 0 01-6-6V23c0-5 2-8.4 6-10V9z' },
      { t: 'path', d: 'M13 7h22' },
      { t: 'path', d: 'M18 25h12' },
      { t: 'path', d: 'M20 31h8' },
    ],
  },
]

let ctx: gsap.Context | null = null

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

onMounted(() => {
  const root = rootEl.value
  if (!root) {
    return
  }
  ctx = gsap.context(() => {
    if (gridEl.value) {
      const cards = Array.from(gridEl.value.querySelectorAll<HTMLElement>('.culture__card'))
      gsap.from(cards, {
        y: 48,
        opacity: 0,
        duration: 0.9,
        ease: 'power3.out',
        stagger: 0.07,
        scrollTrigger: { trigger: gridEl.value, start: 'top 82%', once: true },
      })
    }
    gsap.from('.culture__animate', {
      y: 32,
      opacity: 0,
      duration: 0.9,
      ease: 'power3.out',
      stagger: 0.1,
      scrollTrigger: { trigger: root, start: 'top 72%', once: true },
    })
  }, root)
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  ctx?.revert()
})
</script>

<template>
  <div ref="rootEl" class="culture">
    <div class="sy-container culture__inner">
      <header class="culture__head">
        <p class="sy-eyebrow culture__animate">Eight Cultures</p>
        <h2 class="sy-title culture__title culture__animate">八大文化 · 一脉蜀风</h2>
        <p class="sy-text culture__desc culture__animate">
          川剧、蜀绣、川菜、茶马古道、三国、三星堆、熊猫与美酒 ——
          八条线索，串起巴蜀大地上最有温度的部分。
        </p>
      </header>

      <div ref="gridEl" class="culture__grid">
        <article
          v-for="item in CULTURES"
          :key="item.id"
          class="culture__card"
          @click="goDetail(item.id)"
        >
          <div class="culture__icon">
            <svg
              viewBox="0 0 48 48"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              xmlns="http://www.w3.org/2000/svg"
            >
              <template v-for="(n, i) in item.icon" :key="i">
                <path v-if="n.t === 'path'" :d="n.d" />
                <circle v-else-if="n.t === 'circle'" :cx="n.cx" :cy="n.cy" :r="n.r" />
                <ellipse v-else :cx="n.cx" :cy="n.cy" :rx="n.rx" :ry="n.ry" />
              </template>
            </svg>
          </div>

          <h3 class="culture__name">{{ item.cn }}</h3>
          <p class="culture__en">{{ item.en }}</p>
          <p class="culture__text">{{ item.desc }}</p>

          <span class="culture__more sy-btn-text">了解详情</span>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* 宣纸底：由 Home.vue 的背景舞台（宣纸层）提供，这里保持透明以便与前后屏交叉淡入 */
.culture {
  position: relative;
  width: 100%;
  height: 100%;
  overflow-y: auto;
  background: transparent;
  color: $color-text-dark;
}

.culture__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  padding-block: clamp(48px, 9vh, 120px);
}

.culture__head {
  margin-bottom: clamp(28px, 5vh, 64px);
}

.culture__title {
  margin-top: 12px;
  color: $color-primary;
}

.culture__desc {
  margin-top: clamp(12px, 1.8vh, 22px);
  color: $color-text-dark;
  opacity: 0.78;
}

/* ---------------- 卡片网格 ---------------- */
.culture__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: $gap-md;
}

.culture__card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: clamp(20px, 2vw, 32px);
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-card;
  background: rgba(255, 255, 255, 0.55);
  box-shadow: $shadow-card;
  backdrop-filter: blur(6px);
  transition:
    transform $dur-base $ease-out-expo,
    box-shadow $dur-base $ease-out-expo,
    border-color $dur-base $ease-out-expo;
  will-change: transform;

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: $shadow-card-hover;
    border-color: $color-gold;

    .culture__icon {
      color: $color-gold;
      transform: rotate(-2deg) scale(1.06);
    }

    .culture__more::after {
      transform: scaleX(1);
    }
  }
}

.culture__icon {
  width: clamp(40px, 3.4vw, 56px);
  color: $color-primary;
  transition:
    color $dur-base $ease-out-expo,
    transform $dur-base $ease-out-expo;
}

.culture__name {
  margin-top: 6px;
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.08em;
  color: $color-text-dark;
}

.culture__en {
  font-family: $font-en-body;
  font-size: $fs-small;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: $color-gold;
}

.culture__text {
  margin-top: 4px;
  font-size: $fs-small;
  line-height: 1.8;
  color: rgba(26, 26, 26, 0.68);
}

.culture__more {
  align-self: flex-start;
  margin-top: 10px;
  color: $color-primary;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 768px) {
  .culture__grid {
    grid-template-columns: 1fr;
  }
}
</style>
