<script setup lang="ts">
/**
 * 第四屏：八大文化入口
 * - 宣纸底色（由 Home.vue 背景舞台提供）
 * - 8 个手绘线性图标（公共组件 CultureIcon，1.5px 金色描边）
 * - 卡片 hover 抬升 + 阴影加深；点击进入 /culture/:id
 * - 复用公共能力：SectionTitle、CultureIcon、GhostButton、useScrollReveal
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SectionTitle from '@/components/common/SectionTitle.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import GhostButton from '@/components/common/GhostButton.vue'
import { useScrollReveal } from '@/composables/useScrollReveal'

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)

interface CultureItem {
  id: number
  cn: string
  en: string
  desc: string
}

const CULTURES: CultureItem[] = [
  { id: 1, cn: '川剧变脸', en: 'Sichuan Opera', desc: '挥手转身之间变换脸谱，东方舞台的独门绝技。' },
  { id: 2, cn: '蜀绣', en: 'Shu Embroidery', desc: '中国四大名绣之一，针脚细密，色彩明快。' },
  { id: 3, cn: '川菜', en: 'Sichuan Cuisine', desc: '一菜一格，百菜百味，麻辣之外的万千层次。' },
  { id: 4, cn: '茶马古道', en: 'Tea Horse Road', desc: '以茶易马，穿越川滇藏高山峡谷的千年商路。' },
  { id: 5, cn: '三国文化', en: 'Three Kingdoms', desc: '蜀汉故地，武侯祠与剑门关里的英雄叙事。' },
  { id: 6, cn: '三星堆', en: 'Sanxingdui', desc: '纵目面具与青铜神树，古蜀文明的神秘现场。' },
  { id: 7, cn: '大熊猫', en: 'Giant Panda', desc: '国宝栖息地，成都与卧龙的竹海与憨态。' },
  { id: 8, cn: '酒文化', en: 'Liquor Culture', desc: '五粮液与泸州老窖，川酒里的时间与风土。' },
]

// 页头文案入场（与原实现参数一致）
useScrollReveal(rootEl, '.culture__animate', { y: 32, duration: 0.9, stagger: 0.1, start: 'top 72%' })

// 卡片逐个入场（以网格为触发元素）
useScrollReveal(gridEl, '.culture__card', { y: 48, duration: 0.9, stagger: 0.07, start: 'top 82%' })

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}
</script>

<template>
  <div ref="rootEl" class="culture">
    <div class="sy-container culture__inner">
      <SectionTitle
        class="culture__head"
        eyebrow="Eight Cultures"
        variant="on-light"
        animate-class="culture__animate"
        title="八大文化 · 一脉蜀风"
        desc="川剧、蜀绣、川菜、茶马古道、三国、三星堆、熊猫与美酒 —— 八条线索，串起巴蜀大地上最有温度的部分。"
      />

      <div ref="gridEl" class="culture__grid">
        <article
          v-for="item in CULTURES"
          :key="item.id"
          class="culture__card"
          @click="goDetail(item.id)"
        >
          <CultureIcon class="culture__icon" :name="item.cn" />

          <h3 class="culture__name">{{ item.cn }}</h3>
          <p class="culture__en">{{ item.en }}</p>
          <p class="culture__text">{{ item.desc }}</p>

          <GhostButton class="culture__more">了解详情</GhostButton>
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
  background: transparent;
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
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: $space-md;
}

.culture__card {
  @include hover-lift;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: clamp(20px, 2vw, 32px);
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.55);
  box-shadow: $shadow-md;
  backdrop-filter: blur(6px);

  &:hover {
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
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
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
@include responsive(md) {
  .culture__grid {
    grid-template-columns: 1fr;
  }
}
</style>
