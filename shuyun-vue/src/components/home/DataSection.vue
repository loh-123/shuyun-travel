<script setup lang="ts">
/**
 * 第二屏：数据概览
 * - 全屏实景图 + 多层遮罩（背景由 Home.vue 的统一舞台渲染）
 * - 「21 市州 · 1892 处景点 · 128 项非遗」进入视口时数字滚动
 * - 复用公共能力：useScrollReveal（入场动画）、useCountUp（数字滚动）、SectionTitle
 */
import { ref } from 'vue'
import { useScrollReveal } from '@/composables/useScrollReveal'
import { useCountUp } from '@/composables/useCountUp'
import SectionTitle from '@/components/common/SectionTitle.vue'

const rootEl = ref<HTMLElement | null>(null)
const statsEl = ref<HTMLElement | null>(null)

/** 数字会被 GSAP 逐帧改写 */
const stats = ref([
  { num: 0, target: 21, unit: '', label: '市州', en: 'Cities' },
  { num: 0, target: 1892, unit: '', label: '处景点', en: 'Scenic Spots' },
  { num: 0, target: 128, unit: '', label: '项非遗', en: 'Heritage' },
])

// 文案依次淡入（与原实现参数一致）
useScrollReveal(rootEl, '.data__animate', { y: 40, stagger: 0.12, start: 'top 60%' })

// 数字滚动：进入视口时把 num 从 0 滚到 target
useCountUp(stats, { trigger: statsEl, duration: 2 })
</script>

<template>
  <div ref="rootEl" class="data">
    <div class="sy-container data__inner">
      <!-- 顶部小标题：蜀韵管家的知识底座 -->
      <div class="data__kb data__animate">
        <h3 class="data__kb-title">蜀韵管家的知识底座</h3>
        <p class="data__kb-sub">374 条结构化数据 · 覆盖 21 市州</p>
      </div>

      <SectionTitle
        eyebrow="Data Overview"
        variant="on-dark"
        animate-class="data__animate"
        title="蜀地家底 · 一眼千年"
        desc="从成都平原到川西高原，蜀韵·智游把散落在巴蜀大地上的城市、景点与非物质文化遗产，整理成一条可以「读懂」的旅行脉络。"
      />

      <div ref="statsEl" class="data__stats">
        <div v-for="item in stats" :key="item.label" class="data__stat data__animate">
          <div class="data__num">
            <span>{{ item.num }}</span><em>{{ item.unit }}</em>
          </div>
          <div class="data__label">{{ item.label }}</div>
          <div class="data__en">{{ item.en }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.data {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  // 三分法：内容整体偏下，给背景留出呼吸区
  display: flex;
  align-items: flex-end;
}

.data__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  // 纵向节奏跟随视口高度（整屏分节），保持原有观感
  padding-block: clamp(48px, 10vh, 140px);
}

/* ---------------- 顶部小标题：知识底座 ---------------- */
.data__kb {
  margin-bottom: clamp(18px, 3vh, 36px);
}

.data__kb-title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 28px;
  line-height: 1.3;
  letter-spacing: 0.08em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.data__kb-sub {
  margin-top: 10px;
  font-family: $font-body;
  font-size: 16px;
  letter-spacing: 0.06em;
  color: rgba(245, 241, 232, 0.62);
  text-shadow: $shadow-text-soft;
}

/* ---------------- 数据 ---------------- */
.data__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: $space-md;
  margin-top: clamp(32px, 6vh, 80px);
}

.data__stat {
  position: relative;
  padding-left: clamp(16px, 1.6vw, 28px);

  // 左侧金色细线
  &::before {
    content: '';
    position: absolute;
    top: 6px;
    left: 0;
    width: 1px;
    height: calc(100% - 12px);
    background: linear-gradient(180deg, $color-gold 0%, rgba(212, 179, 116, 0) 100%);
  }
}

.data__num {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-family: $font-en;
  font-size: $fs-num;
  font-weight: 700;
  line-height: 1;
  color: $color-gold;
  text-shadow: $shadow-text;

  em {
    font-size: 0.4em;
    font-style: normal;
  }
}

.data__label {
  margin-top: 8px;
  font-family: $font-title;
  font-size: $fs-h3;
  letter-spacing: 0.12em;
  color: $color-text-light;
  text-shadow: $shadow-text-soft;
}

.data__en {
  margin-top: 4px;
  font-family: $font-en-body;
  font-size: $fs-small;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: rgba(245, 241, 232, 0.6);
}

/* ---------------- 响应式 ---------------- */
@include responsive(md) {
  .data {
    align-items: center;
  }

  .data__stats {
    grid-template-columns: 1fr;
    gap: $space-sm;
  }
}
</style>
