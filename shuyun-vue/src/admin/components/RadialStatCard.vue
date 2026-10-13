<script setup lang="ts">
/**
 * 统计卡片（RadialStatCard）
 *
 * 高级简约风：纯白卡 + 右侧径向进度环（纯手写 SVG，不依赖 ECharts / Chart.js）。
 * - 左栏 65%：技术小标签 + 大数字（等宽字体，滚动动画）+ 中文标签 + 涨跌 / 备注
 * - 右栏 35%：径向进度环，载入时绘制（0 → 目标），持续轻微呼吸脉动，悬停加粗提亮
 *
 * @example
 * <RadialStatCard title="总对话数" eyebrow="CHAT · TOTAL" :value="408" :trend="80" :percent="81.6" color="#D4B374" />
 * <RadialStatCard title="平均轮次" :value="0.8" :decimals="1" suffix="轮" :show-trend="false" hint="样本 408 段会话" :percent="40" color="#8C1F28" />
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import gsap from 'gsap'

const props = withDefaults(
  defineProps<{
    /** 名称 */
    title: string
    /** 数值 */
    value: number
    /** 技术小标签（英文，默认取 title） */
    eyebrow?: string
    /** 环比昨日（%），正为涨、负为跌 */
    trend?: number
    /** 是否展示环比 */
    showTrend?: boolean
    /** 无环比时的备注 */
    hint?: string
    /** 小数位（0 表示整数并千分位） */
    decimals?: number
    /** 数值后缀（如 轮） */
    suffix?: string
    /** 进度环百分比（0-100） */
    percent?: number
    /** 进度环主题色 */
    color?: string
  }>(),
  {
    eyebrow: '',
    trend: 0,
    showTrend: true,
    hint: '',
    decimals: 0,
    suffix: '',
    percent: 0,
    color: '#D4B374',
  },
)

/* ---------------- 数值滚动动画 ---------------- */
const display = ref(0)
let tween: gsap.core.Tween | null = null

function animateTo(target: number) {
  tween?.kill()
  const state = { v: display.value }
  tween = gsap.to(state, {
    v: target,
    duration: 1.2,
    ease: 'power2.out',
    onUpdate: () => {
      display.value = state.v
    },
  })
}

onMounted(() => animateTo(props.value))
onUnmounted(() => tween?.kill())

/** 主数值文本 */
const displayNum = computed(() =>
  props.decimals > 0 ? display.value.toFixed(props.decimals) : Math.round(display.value).toLocaleString('en-US'),
)

/** 环比：正为涨 */
const isUp = computed(() => props.trend >= 0)
const trendText = computed(() => `${Math.abs(props.trend).toFixed(1)}%`)

/* ---------------- 径向进度环 ---------------- */
/** 半径 / 周长 */
const R = 32
const CIRC = 2 * Math.PI * R
/** 载入后开始绘制（0 → 目标，配合 CSS transition 形成绘制动画） */
const drawn = ref(false)
onMounted(() => {
  requestAnimationFrame(() => {
    drawn.value = true
  })
})

const pct = computed(() => Math.max(0, Math.min(100, props.percent)))
const dashOffset = computed(() => CIRC * (1 - (drawn.value ? pct.value : 0) / 100))
</script>

<template>
  <div class="rstat">
    <div class="rstat__main">
      <span class="rstat__eyebrow">{{ eyebrow || title }}</span>
      <p class="rstat__value">
        {{ displayNum }}<em v-if="suffix" class="rstat__unit">{{ suffix }}</em>
      </p>
      <p class="rstat__label">{{ title }}</p>

      <div class="rstat__foot">
        <span v-if="showTrend" class="rstat__delta" :class="isUp ? 'is-up' : 'is-down'">
          {{ isUp ? '↑' : '↓' }} {{ trendText }}<em>较昨日</em>
        </span>
        <span v-else-if="hint" class="rstat__hint">{{ hint }}</span>
      </div>
    </div>

    <div class="rstat__ring">
      <svg width="80" height="80" viewBox="0 0 80 80" aria-hidden="true">
        <!-- 背景环 -->
        <circle class="rstat__bg" cx="40" cy="40" :r="R" stroke="rgba(0,0,0,0.06)" stroke-width="6" fill="none" />
        <!-- 进度环 -->
        <circle
          class="rstat__prog"
          cx="40"
          cy="40"
          :r="R"
          :stroke="color"
          stroke-width="6"
          fill="none"
          stroke-linecap="round"
          :stroke-dasharray="CIRC"
          :stroke-dashoffset="dashOffset"
          transform="rotate(-90 40 40)"
        />
        <!-- 中心百分比 -->
        <text
          class="rstat__pct"
          x="40"
          y="40"
          text-anchor="middle"
          dominant-baseline="central"
          font-size="14"
          font-weight="600"
          fill="#1A1A1A"
        >
          {{ Math.round(pct) }}%
        </text>
      </svg>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rstat {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 24px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition:
    box-shadow 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.3s cubic-bezier(0.22, 1, 0.36, 1);

  /* 悬停：环加粗提亮、中心百分比略放大 */
  &:hover {
    border-color: rgba(0, 0, 0, 0.1);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);

    .rstat__prog {
      stroke-width: 7px;
      filter: brightness(1.12);
      animation-play-state: paused;
    }

    .rstat__pct {
      font-size: 15px;
    }
  }
}

.rstat__main {
  display: flex;
  flex: 1 1 65%;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.rstat__eyebrow {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9ca3af;
}

.rstat__value {
  margin-top: 8px;
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  color: #1a1a1a;
}

.rstat__unit {
  margin-left: 3px;
  font-size: 0.5em;
  font-style: normal;
  font-weight: 600;
  color: #9ca3af;
}

.rstat__label {
  font-size: 13px;
  color: #6b7280;
}

.rstat__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  min-height: 18px;
  font-size: 13px;
}

.rstat__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;

  em {
    font-style: normal;
    font-weight: 400;
    color: #9ca3af;
  }

  &.is-up {
    color: #16a34a;
  }

  &.is-down {
    color: #dc2626;
  }
}

.rstat__hint {
  color: #9ca3af;
}

.rstat__ring {
  display: flex;
  flex: 0 0 35%;
  align-items: center;
  justify-content: flex-end;
}

/* 背景环 */
.rstat__bg {
  stroke-width: 6px;
}

/* 进度环：绘制过渡 + 轻微呼吸脉动 */
.rstat__prog {
  stroke-width: 6px;
  transition: stroke-dashoffset 1.2s cubic-bezier(0.22, 1, 0.36, 1);
  animation: rstat-breathe 3s ease-in-out infinite;
}

@keyframes rstat-breathe {
  0%,
  100% {
    stroke-width: 6px;
  }

  50% {
    stroke-width: 6.6px;
  }
}

.rstat__pct {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  transition: font-size 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .rstat__prog {
    animation: none;
  }
}
</style>
