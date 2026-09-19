<script setup lang="ts">
/**
 * 第二屏：数据概览
 * - 全屏实景图（峨眉云海）+ 多层遮罩
 * - 「21 市州 · 1892 处景点 · 128 项非遗」进入视口时数字滚动
 */
import { onMounted, onUnmounted, ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const rootEl = ref<HTMLElement | null>(null)
const statsEl = ref<HTMLElement | null>(null)

/** 数字会被 GSAP 逐帧改写 */
const stats = ref([
  { num: 0, target: 21, unit: '', label: '市州', en: 'Cities' },
  { num: 0, target: 1892, unit: '', label: '处景点', en: 'Scenic Spots' },
  { num: 0, target: 128, unit: '', label: '项非遗', en: 'Heritage' },
])

let ctx: gsap.Context | null = null

onMounted(() => {
  const root = rootEl.value
  if (!root) {
    return
  }

  ctx = gsap.context(() => {
    // 数字滚动
    stats.value.forEach((item) => {
      gsap.fromTo(
        item,
        { num: 0 },
        {
          num: item.target,
          duration: 2,
          ease: 'power2.out',
          snap: { num: 1 },
          scrollTrigger: { trigger: statsEl.value, start: 'top 78%', once: true },
        },
      )
    })

    // 文案依次淡入
    gsap.from('.data__animate', {
      y: 40,
      opacity: 0,
      duration: 1,
      ease: 'power3.out',
      stagger: 0.12,
      scrollTrigger: { trigger: root, start: 'top 60%', once: true },
    })
  }, root)

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  ctx?.revert()
})
</script>

<template>
  <div ref="rootEl" class="data">
    <div class="sy-container data__inner">
      <p class="sy-eyebrow data__animate">Data Overview</p>
      <h2 class="sy-title sy-on-dark data__title data__animate">蜀地家底 · 一眼千年</h2>
      <p class="sy-text sy-on-dark data__desc data__animate">
        从成都平原到川西高原，蜀韵·智游把散落在巴蜀大地上的城市、景点与非物质文化遗产，
        整理成一条可以「读懂」的旅行脉络。
      </p>

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
  padding-block: clamp(48px, 10vh, 140px);
}

.data__title {
  margin-top: clamp(10px, 1.6vh, 22px);
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.data__desc {
  margin-top: clamp(14px, 2vh, 26px);
  color: $color-text-light;
  opacity: 0.86;
  text-shadow: $shadow-text-soft;
}

/* ---------------- 数据 ---------------- */
.data__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: $gap-md;
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
    background: linear-gradient(180deg, $color-gold 0%, rgba(201, 168, 106, 0) 100%);
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
@media (max-width: 768px) {
  .data {
    align-items: center;
  }

  .data__stats {
    grid-template-columns: 1fr;
    gap: $gap-sm;
  }
}
</style>
