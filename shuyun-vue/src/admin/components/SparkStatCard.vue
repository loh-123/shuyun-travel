<script setup lang="ts">
/**
 * 简约统计卡（SparkStatCard）· 行程管理页专用
 *
 * 左栏：技术标签 + 大数字 + 中文标签 + 环比涨跌；
 * 右栏：持续波动的迷你折线图（sparkline）。
 *
 * 设计取向：纯白卡片 / 8px 圆角 / 极淡阴影 / 无任何装饰性图形（无切角线、无图标块、无色块遮罩）。
 * 动效：requestAnimationFrame 每帧驱动（关闭 ECharts 自身动画），四卡相位错开。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    /** 顶部技术标签（英文大写） */
    eyebrow: string
    /** 中文标签 */
    label: string
    /** 主数值 */
    value: number
    /** 小数位数（0 表示整数并千分位） */
    decimals?: number
    /** 数值后缀（如 天 / 个） */
    suffix?: string
    /** 环比昨日（%），正为涨、负为跌、0 为平 */
    trend?: number
    /** 折线/面积颜色 */
    color: string
    /** 折线基础序列（旧→新）；不足 2 个点时使用内置波形 */
    data?: number[]
  }>(),
  { decimals: 0, suffix: '', trend: 0, data: () => [] },
)

/** 波动幅度（±1.5，肉眼可见但不夸张） */
const AMPLITUDE = 1.5
/** 每帧时间步进 */
const TIME_STEP = 0.02

/* ---------------- 文字信息 ---------------- */
/** 主数值文本 */
const displayNum = computed(() =>
  props.decimals > 0 ? props.value.toFixed(props.decimals) : Math.round(props.value).toLocaleString('en-US'),
)

/** 涨跌方向：1 涨 / -1 跌 / 0 平 */
const trendDir = computed(() => (props.trend > 0 ? 1 : props.trend < 0 ? -1 : 0))
/** 涨跌符号：↑ ↓ - */
const trendSymbol = computed(() => (trendDir.value > 0 ? '↑' : trendDir.value < 0 ? '↓' : '-'))
/** 涨跌百分比文本 */
const trendText = computed(() => `${Math.abs(props.trend).toFixed(1)}%`)

/** 涨跌样式类 */
const trendClass = computed(() => (trendDir.value > 0 ? 'is-up' : trendDir.value < 0 ? 'is-down' : 'is-flat'))

/* ---------------- Sparkline ---------------- */
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null
let rafId = 0
let timeT = 0
/** 相位：让四张卡片的波动错开，避免同步 */
const phase = Math.random() * Math.PI * 2

/** 内置波形（真实序列缺失时占位） */
const FALLBACK = [10, 12, 8, 15, 11, 18, 14, 20]

/** 基础序列（旧→新） */
const baseData = computed(() => (props.data && props.data.length >= 2 ? props.data : FALLBACK))

/** hex → rgba（面积渐变用） */
function toRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** 构建 sparkline 配置（隐藏坐标轴、关闭 ECharts 动画） */
function buildOption(data: number[]) {
  return {
    grid: { left: 0, right: 0, top: 8, bottom: 8, containLabel: false },
    xAxis: { type: 'category', show: false, boundaryGap: false },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    tooltip: { show: false },
    animation: false,
    series: [
      {
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: props.color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: toRgba(props.color, 0.25) },
            { offset: 1, color: toRgba(props.color, 0) },
          ]),
        },
        animation: false,
        animationDurationUpdate: 0,
      },
    ],
  }
}

/** 每帧：在基础序列上叠加相位错开的正弦波动 */
function animate() {
  if (!chart) {
    return
  }
  timeT += TIME_STEP
  const next = baseData.value.map((v, i) => v + Math.sin(timeT * 1.5 + i * 0.4 + phase) * AMPLITUDE)
  chart.setOption({ series: [{ data: next }] }, { lazyUpdate: true })
  rafId = requestAnimationFrame(animate)
}

function initChart() {
  if (!chartEl.value || chart) {
    return
  }
  chart = echarts.init(chartEl.value)
  chart.setOption(buildOption(baseData.value))
  rafId = requestAnimationFrame(animate)

  // grid 布局下容器宽度变化时需显式 resize，否则 canvas 会撑破卡片
  ro = new ResizeObserver(() => chart?.resize())
  ro.observe(chartEl.value)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
  }
  rafId = 0
  window.removeEventListener('resize', resize)
  ro?.disconnect()
  ro = null
  chart?.dispose()
  chart = null
})

// 真实序列到位 / 变化时重建坐标系
watch(baseData, () => {
  chart?.setOption(buildOption(baseData.value))
})
</script>

<template>
  <div class="sc">
    <div class="sc__main">
      <p class="sc__eyebrow">{{ eyebrow }}</p>
      <p class="sc__value">
        {{ displayNum }}<em v-if="suffix" class="sc__unit">{{ suffix }}</em>
      </p>
      <p class="sc__label">{{ label }}</p>
      <p class="sc__delta" :class="trendClass">
        <span class="sc__arrow">{{ trendSymbol }}</span>
        <b>{{ trendText }}</b>
        <em>较昨日</em>
      </p>
    </div>

    <div ref="chartEl" class="sc__spark" />
  </div>
</template>

<style scoped lang="scss">
.sc {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.sc__main {
  flex: 1 1 70%;
  min-width: 0;
}

/* ---------------- 文字 ---------------- */
.sc__eyebrow {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sc__value {
  margin-top: 12px;
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 36px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  color: #1a1a1a;
}

.sc__unit {
  margin-left: 4px;
  font-size: 0.45em;
  font-style: normal;
  font-weight: 600;
  color: #9ca3af;
}

.sc__label {
  margin-top: 6px;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #6b7280;
}

.sc__delta {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  margin-top: 14px;
  font-size: 12px;

  > * {
    white-space: nowrap;
  }

  b {
    font-weight: 600;
  }

  em {
    font-style: normal;
    color: #9ca3af;
  }

  &.is-up {
    color: #16a34a;
  }

  &.is-down {
    color: #dc2626;
  }

  &.is-flat {
    color: #9ca3af;
  }
}

/* ---------------- Sparkline ---------------- */
.sc__spark {
  flex: 0 0 30%;
  height: 96px;
  min-width: 0;
  overflow: hidden;
}
</style>
