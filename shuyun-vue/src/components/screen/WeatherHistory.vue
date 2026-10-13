<script setup lang="ts">
/**
 * 四川历史天气（WeatherHistory）
 *
 * 数据源：Open-Meteo 历史存档（免 Key、无额度限制）
 * - 近 30 天 · 成都（30.67,104.06）：日最高/最低温、降水量、最大风速
 * - 上：平均高温 / 平均低温 / 月总降水 三张卡
 * - 中：温度双曲线（暖红/青蓝）+ 两线间渐变填充 + 末端脉冲点
 * - 下：降水柱状图（与温度图共享 X 轴）
 * - 入场 1500ms 绘制；随后 rAF 逐帧正弦微调（±0.3，仅视觉），hover 暂停
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import TechFrame from '@/components/map/TechFrame.vue'

/** 成都坐标 / 统计天数 / 视觉波动幅度 */
const LAT = 30.67
const LON = 104.06
const DAYS = 30
/** 视觉波动幅度（真实数据不变；加大以便一眼看出在流动） */
const WAVE = 1.5
/** 真实天气详情页（成都历史天气） */
const DETAIL_URL = 'https://lishi.tianqi.com/chengdu/'

interface DayPoint {
  date: string
  tMax: number
  tMin: number
  precip: number
  wind: number
}

const chartEl = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const failed = ref(false)
/** 是否已回退到模拟数据 */
const mocked = ref(false)
const days = ref<DayPoint[]>([])

/** 概览：平均高温 / 平均低温 / 月总降水 */
const avgMax = computed(() => (days.value.length ? avg(days.value.map((d) => d.tMax)) : 0))
const avgMin = computed(() => (days.value.length ? avg(days.value.map((d) => d.tMin)) : 0))
const sumPrecip = computed(() => days.value.reduce((s, d) => s + d.precip, 0))

let chart: echarts.ECharts | null = null
let baseMax: number[] = []
let baseMin: number[] = []
let precip: number[] = []
let raf = 0
let entranceTimer = 0
let timeT = 0
let lastDraw = 0
const paused = ref(false)

function avg(arr: number[]): number {
  return arr.length ? arr.reduce((a, b) => a + b, 0) / arr.length : 0
}
function num(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** 兜底模拟：30 天随机温度 20~32℃ */
function mockDays(): DayPoint[] {
  const out: DayPoint[] = []
  const end = new Date()
  for (let i = DAYS - 1; i >= 0; i--) {
    const d = new Date(end.getTime() - i * 86400000)
    const tMin = 20 + Math.random() * 4
    const tMax = Math.min(32, tMin + 6 + Math.random() * 4)
    out.push({
      date: ymd(d),
      tMax: +tMax.toFixed(1),
      tMin: +tMin.toFixed(1),
      precip: +(Math.random() * 12).toFixed(1),
      wind: +(Math.random() * 14).toFixed(1),
    })
  }
  return out
}

/** 拉取近 30 天真实数据 */
async function load() {
  loading.value = true
  failed.value = false
  stopAnim()
  try {
    const end = new Date()
    const start = new Date(end.getTime() - (DAYS - 1) * 86400000)
    const url =
      `https://archive-api.open-meteo.com/v1/archive?latitude=${LAT}&longitude=${LON}` +
      `&start_date=${ymd(start)}&end_date=${ymd(end)}` +
      `&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max` +
      `&timezone=Asia/Shanghai`
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const json = await res.json()
    const d = json?.daily
    if (!d?.time?.length) throw new Error('empty')
    days.value = (d.time as string[]).map((t, i) => ({
      date: t,
      tMax: num(d.temperature_2m_max?.[i]),
      tMin: num(d.temperature_2m_min?.[i]),
      precip: num(d.precipitation_sum?.[i]),
      wind: num(d.wind_speed_10m_max?.[i]),
    }))
    mocked.value = false
  } catch {
    // 失败 / 为空：提示 + 回退模拟数据，保证面板可用
    failed.value = true
    days.value = mockDays()
    mocked.value = true
  } finally {
    loading.value = false
  }
  await nextTick()
  buildChart()
  // 入场动画播完再启动逐帧波动
  entranceTimer = window.setTimeout(startAnim, 1500)
}

/** 构建图表（静态骨架 + 入场动画） */
function buildChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' })
  const dates = days.value.map((d) => d.date.slice(5))
  baseMax = days.value.map((d) => d.tMax)
  baseMin = days.value.map((d) => d.tMin)
  precip = days.value.map((d) => d.precip)
  const last = baseMax.length - 1
  const bandData = baseMax.map((v, i) => v - baseMin[i])
  const dot = (v: number) => (last >= 0 ? [[dates[last], v]] : [])

  chart.setOption({
    // 入场动画保留（1500ms）；更新动画关闭 → 位移完全由 rAF 推进，杜绝顿挫
    animation: true,
    animationDuration: 1500,
    animationEasing: 'cubicOut',
    animationDurationUpdate: 0,
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    tooltip: {
      trigger: 'axis',
      transitionDuration: 0,
      backgroundColor: 'rgba(15, 37, 64, 0.95)',
      borderColor: 'rgba(201, 168, 106, 0.4)',
      borderWidth: 1,
      textStyle: { color: '#FFFFFF', fontSize: 13 },
      formatter: (ps: unknown) => {
        const p = (Array.isArray(ps) ? ps[0] : ps) as { dataIndex?: number } | undefined
        const d = days.value[p?.dataIndex ?? -1]
        if (!d) return ''
        return (
          `日期：${d.date}<br/>最高温：${d.tMax.toFixed(1)}°C<br/>最低温：${d.tMin.toFixed(1)}°C` +
          `<br/>降水量：${d.precip.toFixed(1)}mm<br/>风速：${d.wind.toFixed(1)}km/h`
        )
      },
    },
    grid: [
      { left: 46, right: 18, top: 12, height: 198 },
      { left: 46, right: 18, top: 246, height: 64 },
    ],
    xAxis: [
      {
        type: 'category',
        gridIndex: 0,
        boundaryGap: false,
        data: dates,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
      },
      {
        type: 'category',
        gridIndex: 1,
        boundaryGap: true,
        data: dates,
        axisLine: { lineStyle: { color: 'rgba(58, 140, 220, 0.35)' } },
        axisTick: { show: false },
        // 每 5 天一个刻度
        axisLabel: { color: '#7A95B8', fontSize: 14, interval: 4 },
      },
    ],
    yAxis: [
      {
        type: 'value',
        gridIndex: 0,
        scale: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#7A95B8', fontSize: 14, formatter: '{value}°' },
        // 仅横向虚线网格线
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)', type: 'dashed' } },
      },
      {
        type: 'value',
        gridIndex: 1,
        min: 0,
        // 柱图区较矮：减少刻度数量并自动隐藏重叠标签
        splitNumber: 2,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#7A95B8', fontSize: 13, hideOverlap: true },
        splitLine: { show: false },
      },
    ],
    series: [
      // ① 隐形下底（用于堆叠出「两线之间」的填充带）
      {
        name: '_bandBase',
        type: 'line',
        stack: 'band',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: baseMin,
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0 },
        silent: true,
        z: 1,
      },
      // ② 填充带（最高温 - 最低温 的差值）
      {
        name: '_bandFill',
        type: 'line',
        stack: 'band',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: bandData,
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 107, 107, 0.20)' },
            { offset: 1, color: 'rgba(74, 216, 240, 0.20)' },
          ]),
        },
        silent: true,
        z: 1,
      },
      // ③ 最高温（暖红）
      {
        name: '最高温',
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: baseMax,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#FF6B6B', width: 2, shadowColor: 'rgba(255,107,107,0.5)', shadowBlur: 8 },
        z: 3,
      },
      // ④ 最低温（青蓝）
      {
        name: '最低温',
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: baseMin,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#4AD8F0', width: 2, shadowColor: 'rgba(74,216,240,0.5)', shadowBlur: 8 },
        z: 3,
      },
      // ⑤ 降水柱（共享 X 轴）
      {
        name: '降水量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: precip,
        barWidth: '60%',
        itemStyle: { color: 'rgba(74, 216, 240, 0.5)', borderRadius: [2, 2, 0, 0] },
      },
      // ⑥ 最高温末端脉冲点
      {
        name: '_dotMax',
        type: 'scatter',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: dot(baseMax[last] ?? 0),
        symbolSize: 5,
        itemStyle: { color: '#FF6B6B', shadowColor: '#FF6B6B', shadowBlur: 8 },
        silent: true,
        z: 5,
      },
      // ⑦ 最低温末端脉冲点
      {
        name: '_dotMin',
        type: 'scatter',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: dot(baseMin[last] ?? 0),
        symbolSize: 5,
        itemStyle: { color: '#4AD8F0', shadowColor: '#4AD8F0', shadowBlur: 8 },
        silent: true,
        z: 5,
      },
    ],
  })
}

/** 逐帧微调（正弦叠加，仅视觉波动） */
function frame(now: number) {
  raf = requestAnimationFrame(frame)
  if (paused.value || !chart || !baseMax.length) return
  if (now - lastDraw < 16) return
  lastDraw = now
  timeT += 0.02
  const dMax = baseMax.map((v, i) => v + Math.sin(timeT * 1.2 + i * 0.4) * WAVE)
  const dMin = baseMin.map((v, i) => v + Math.sin(timeT * 1.2 + i * 0.4 + 1) * WAVE)
  const last = dMax.length - 1
  const x = days.value[last]?.date.slice(5) ?? ''
  chart.setOption(
    {
      series: [
        { data: dMin },
        { data: dMax.map((v, i) => v - dMin[i]) },
        { data: dMax },
        { data: dMin },
        {},
        { data: [[x, dMax[last]]], symbolSize: 5 + Math.sin(timeT * 4) },
        { data: [[x, dMin[last]]], symbolSize: 5 + Math.sin(timeT * 4 + Math.PI) },
      ],
    },
    { lazyUpdate: true },
  )
}

function startAnim() {
  if (!raf) raf = requestAnimationFrame(frame)
}
function stopAnim() {
  if (raf) {
    cancelAnimationFrame(raf)
    raf = 0
  }
  if (entranceTimer) {
    window.clearTimeout(entranceTimer)
    entranceTimer = 0
  }
}
/** hover 暂停 / 移开恢复 */
function onEnter() {
  paused.value = true
}
function onLeave() {
  paused.value = false
}
/** 打开真实天气详情页（新标签） */
function openDetail() {
  window.open(DETAIL_URL, '_blank', 'noopener')
}
function onResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  stopAnim()
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <TechFrame title="四川历史天气">
    <template #extra>
      <button type="button" class="wh-refresh" title="刷新" :disabled="loading" @click="load">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 11a8 8 0 1 0-2.3 6.4" />
          <path d="M20 4v7h-7" />
        </svg>
      </button>
    </template>

    <div class="wh">
      <div v-if="failed" class="wh__err">加载失败，点击刷新重试{{ mocked ? '（当前为模拟数据）' : '' }}</div>

      <div class="wh__cards">
        <div class="wh__card">
          <b class="wh__num">{{ avgMax.toFixed(1) }}<em>°C</em></b>
          <span class="wh__label">平均高温</span>
        </div>
        <div class="wh__card">
          <b class="wh__num">{{ avgMin.toFixed(1) }}<em>°C</em></b>
          <span class="wh__label">平均低温</span>
        </div>
        <div class="wh__card">
          <b class="wh__num">{{ sumPrecip.toFixed(1) }}<em>mm</em></b>
          <span class="wh__label">月总降水</span>
        </div>
      </div>

      <div ref="chartEl" class="wh__chart" @mouseenter="onEnter" @mouseleave="onLeave" />

      <div class="wh__foot">
        <span class="wh__tip">近 30 天 · 成都 · 鼠标悬停暂停</span>
        <button type="button" class="wh__detail" title="打开成都历史天气网站" @click="openDetail">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 4h6v6" />
            <path d="M20 4l-8 8" />
            <path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" />
          </svg>
          查看真实详情
        </button>
      </div>
    </div>
  </TechFrame>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.wh {
  display: flex;
  flex-direction: column;
}

/* 失败提示 */
.wh__err {
  margin-bottom: 8px;
  padding: 4px 10px;
  border: 1px solid rgba(255, 107, 107, 0.45);
  background: rgba(198, 40, 40, 0.16);
  font-size: clamp(14px, 1.05vw, 18px);
  color: #ffb3b3;
}

/* 三张概览卡 */
.wh__cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.wh__card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid rgba(58, 140, 220, 0.15);
  background: rgba(15, 37, 64, 0.6);
}

.wh__num {
  font-family: $font-mono;
  font-size: clamp(26px, 1.8vw, 36px);
  font-weight: 500;
  line-height: 1.1;
  color: #ffffff;

  em {
    margin-left: 2px;
    font-size: 0.55em;
    font-style: normal;
    color: #7a95b8;
  }
}

.wh__label {
  font-size: clamp(14px, 1.05vw, 18px);
  color: #7a95b8;
}

.wh__chart {
  width: 100%;
  height: 330px;
}

.wh__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
}

.wh__tip {
  font-size: clamp(13px, 0.95vw, 17px);
  color: rgba(122, 149, 184, 0.6);
}

/* 「查看真实详情」按钮：金色描边，hover 填充，提示可跳转外部网站 */
.wh__detail {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border: 1px solid rgba(212, 179, 116, 0.55);
  border-radius: 999px;
  background: rgba(212, 179, 116, 0.14);
  font-size: clamp(14px, 1vw, 18px);
  letter-spacing: 0.04em;
  color: #d4b374;
  cursor: pointer;
  transition:
    background-color 0.3s $ease-out-expo,
    border-color 0.3s $ease-out-expo,
    color 0.3s $ease-out-expo,
    transform 0.3s $ease-out-expo;

  &:hover {
    border-color: #d4b374;
    background: rgba(212, 179, 116, 0.3);
    color: #ffe0a3;
    transform: translateY(-1px);
  }
}

/* 刷新按钮：hover 旋转 180° */
.wh-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: 50%;
  background: rgba(212, 179, 116, 0.12);
  color: #d4b374;
  cursor: pointer;
  transition:
    transform 0.5s $ease-out-expo,
    background-color 0.3s $ease-out-expo,
    border-color 0.3s $ease-out-expo;

  &:hover:not(:disabled) {
    border-color: #d4b374;
    background: rgba(212, 179, 116, 0.22);
    transform: rotate(180deg);
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}
</style>
