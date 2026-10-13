<script setup lang="ts">
/**
 * 后台 · 用户行为统计
 *
 * 功能：四大指标融合实时柱状图（浏览 / 收藏 / 笔记 / AI 对话）、近 30 天行为趋势折线图、功能使用占比环形图、
 * 热门浏览景点 TOP10 横向柱状图、活跃用户 TOP10、最近行为动态（每 5 秒自动刷新）。
 *
 * 接口：
 *   GET /api/admin/behavior/stats
 *   GET /api/admin/behavior/trend?days=30
 *   GET /api/admin/behavior/distribution
 *   GET /api/admin/behavior/hot-scenic
 *   GET /api/admin/behavior/active-user
 *   GET /api/admin/behavior/recent
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'

import adminRequest from '@/admin/api/adminRequest'

/** 行为统计卡片 */
interface BehaviorStats {
  totalViews: number
  totalCollections: number
  totalNotes: number
  totalAiChats: number
  viewsTrend: number
  collectionsTrend: number
  notesTrend: number
  aiChatsTrend: number
}
/** 趋势 */
interface BehaviorTrend {
  dates: string[]
  views: number[]
  collections: number[]
  notes: number[]
  aiChats: number[]
}
/** 名称-数值 */
interface NameValue {
  name: string
  value: number
}
/** 活跃用户 */
interface ActiveUser {
  userId: number
  username: string
  avatar?: string
  count: number
}
/** 行为动态 */
interface Activity {
  username: string
  avatar?: string
  action: string
  target: string
  time: string
}

/* ------------------------------ 状态 ------------------------------ */
const stats = ref<BehaviorStats>({
  totalViews: 0,
  totalCollections: 0,
  totalNotes: 0,
  totalAiChats: 0,
  viewsTrend: 0,
  collectionsTrend: 0,
  notesTrend: 0,
  aiChatsTrend: 0,
})
const trend = ref<BehaviorTrend>({ dates: [], views: [], collections: [], notes: [], aiChats: [] })
const distribution = ref<NameValue[]>([])
const hotScenics = ref<NameValue[]>([])
const activeUsers = ref<ActiveUser[]>([])
const activities = ref<Activity[]>([])
const recentLoading = ref(false)

/** 图表容器与实例 */
const overviewEl = ref<HTMLDivElement | null>(null)
const trendEl = ref<HTMLDivElement | null>(null)
const distEl = ref<HTMLDivElement | null>(null)
const hotEl = ref<HTMLDivElement | null>(null)
let overviewChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let distChart: echarts.ECharts | null = null
let hotChart: echarts.ECharts | null = null

/** 图表配色 */
const COLOR_GOLD = '#d4b374'
const COLOR_RED = '#7a1a22'
const COLOR_BAMBOO = '#3a5a40'
const COLOR_BLUE = '#4a7db5'
const PIE_COLORS = ['#2C2C2C', '#8C1F28', '#6B7280', '#D4B374']

/** 四大指标系列（与原来的四张卡片一一对应） */
const OVERVIEW_SERIES = computed(() => [
  { key: 'views' as const, name: '浏览', color: COLOR_GOLD, value: stats.value.totalViews, trend: stats.value.viewsTrend },
  { key: 'collections' as const, name: '收藏', color: COLOR_RED, value: stats.value.totalCollections, trend: stats.value.collectionsTrend },
  { key: 'notes' as const, name: '笔记', color: COLOR_BAMBOO, value: stats.value.totalNotes, trend: stats.value.notesTrend },
  { key: 'aiChats' as const, name: 'AI 对话', color: COLOR_BLUE, value: stats.value.totalAiChats, trend: stats.value.aiChatsTrend },
])

/* ------------------------------ 数据加载 ------------------------------ */
async function loadStats() {
  try {
    const res = await adminRequest.get('/admin/behavior/stats')
    stats.value = { ...stats.value, ...res.data }
  } catch {
    // 拦截器已提示
  }
}

async function loadTrend() {
  try {
    const res = await adminRequest.get('/admin/behavior/trend', { params: { days: 30 } })
    trend.value = { dates: [], views: [], collections: [], notes: [], aiChats: [], ...res.data }
  } catch {
    trend.value = { dates: [], views: [], collections: [], notes: [], aiChats: [] }
  }
}

async function loadDistribution() {
  try {
    const res = await adminRequest.get('/admin/behavior/distribution')
    distribution.value = res.data ?? []
  } catch {
    distribution.value = []
  }
}

async function loadHotScenics() {
  try {
    const res = await adminRequest.get('/admin/behavior/hot-scenic', { params: { limit: 10 } })
    hotScenics.value = res.data ?? []
  } catch {
    hotScenics.value = []
  }
}

async function loadActiveUsers() {
  try {
    const res = await adminRequest.get('/admin/behavior/active-user', { params: { limit: 10 } })
    activeUsers.value = res.data ?? []
  } catch {
    activeUsers.value = []
  }
}

async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await adminRequest.get('/admin/behavior/recent', { params: { limit: 12 } })
    activities.value = res.data ?? []
  } catch {
    activities.value = []
  } finally {
    recentLoading.value = false
  }
}

/** 加载全部（图表） */
async function loadAll() {
  await Promise.all([loadStats(), loadTrend(), loadDistribution(), loadHotScenics(), loadActiveUsers()])
  await nextTick()
  renderCharts()
}

/* ------------------------------ 图表 ------------------------------ */
function initCharts() {
  if (overviewEl.value && !overviewChart) overviewChart = echarts.init(overviewEl.value)
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value)
  if (distEl.value && !distChart) distChart = echarts.init(distEl.value)
  if (hotEl.value && !hotChart) hotChart = echarts.init(hotEl.value)
}

function renderCharts() {
  initCharts()
  bindDistEvents()
  renderOverview()
  renderTrend()
  renderDistribution()
  renderHot()
}

const axisStyle = {
  axisLine: { lineStyle: { color: 'rgba(212,179,116,0.25)' } },
  axisTick: { show: false },
  axisLabel: { color: '#7a8aa0', fontSize: 11 },
}

const tooltipStyle = {
  backgroundColor: 'rgba(18,35,61,0.96)',
  borderColor: 'rgba(212,179,116,0.35)',
  textStyle: { color: '#f0ebe0' },
}

/** hex 颜色 → rgba（用于柱体渐变） */
function toRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** 四大指标融合柱状图：浏览 / 收藏 / 笔记 / AI 对话（实时刷新，柱体生长动画） */
function renderOverview() {
  if (!overviewChart) return
  const data = trend.value
  overviewChart.setOption({
    grid: { left: 6, right: 18, top: 18, bottom: 6, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow',
        shadowStyle: { color: 'rgba(212,179,116,0.08)' },
        label: { show: true, color: '#f0ebe0', backgroundColor: 'rgba(18,35,61,0.92)' },
      },
      ...tooltipStyle,
    },
    xAxis: {
      type: 'category',
      data: data.dates,
      ...axisStyle,
      // 倾斜 45° 显示全部日期标签，保证「指向哪天 = 显示哪天」
      axisLabel: { color: '#7a8aa0', fontSize: 10, interval: 0, rotate: 45 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(212,179,116,0.12)' } },
      axisLabel: { color: '#7a8aa0', fontSize: 11 },
    },
    series: OVERVIEW_SERIES.value.map((s, i) => ({
      name: s.name,
      type: 'bar',
      data: data[s.key],
      barMaxWidth: 9,
      barGap: '12%',
      barCategoryGap: '38%',
      itemStyle: {
        borderRadius: [2, 2, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: s.color },
          { offset: 1, color: toRgba(s.color, 0.5) },
        ]),
      },
      emphasis: { focus: 'series' },
      animationDuration: 700,
      animationEasing: 'cubicOut',
      animationDelay: (idx: number) => idx * 5 + i * 40,
    })),
  })
}

/** 四条线：浏览 / 收藏 / 笔记 / AI 对话 */
function renderTrend() {
  if (!trendChart) return
  const data = trend.value
  trendChart.setOption({
    grid: { left: 8, right: 18, top: 40, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', ...tooltipStyle },
    legend: {
      data: ['浏览', '收藏', '笔记', 'AI 对话'],
      textStyle: { color: '#7a8aa0', fontSize: 12 },
      top: 4,
    },
    xAxis: { type: 'category', boundaryGap: false, data: data.dates, ...axisStyle },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(212,179,116,0.12)' } },
      axisLabel: { color: '#7a8aa0', fontSize: 11 },
    },
    series: [
      { name: '浏览', type: 'line', smooth: true, symbol: 'none', data: data.views, lineStyle: { color: COLOR_GOLD, width: 2 }, itemStyle: { color: COLOR_GOLD }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(212,179,116,0.32)' }, { offset: 1, color: 'rgba(212,179,116,0)' }]) } },
      { name: '收藏', type: 'line', smooth: true, symbol: 'none', data: data.collections, lineStyle: { color: COLOR_RED, width: 2 }, itemStyle: { color: COLOR_RED }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(122,26,34,0.28)' }, { offset: 1, color: 'rgba(122,26,34,0)' }]) } },
      { name: '笔记', type: 'line', smooth: true, symbol: 'none', data: data.notes, lineStyle: { color: COLOR_BAMBOO, width: 2 }, itemStyle: { color: COLOR_BAMBOO }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(58,90,64,0.28)' }, { offset: 1, color: 'rgba(58,90,64,0)' }]) } },
      { name: 'AI 对话', type: 'line', smooth: true, symbol: 'none', data: data.aiChats, lineStyle: { color: COLOR_BLUE, width: 2 }, itemStyle: { color: COLOR_BLUE }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(74,125,181,0.28)' }, { offset: 1, color: 'rgba(74,125,181,0)' }]) } },
    ],
  })
}

/* 环形图：高级灰阶配色 + 中心读数 + 悬停切换 */
/** 悬停中的扇区下标（-1 表示无） */
const distHover = ref(-1)
/** 扇区（带配色） */
const distItems = computed(() =>
  distribution.value.map((d, i) => ({ ...d, color: PIE_COLORS[i % PIE_COLORS.length] })),
)
/** 总数 */
const distTotal = computed(() => distribution.value.reduce((s, d) => s + (d.value || 0), 0))
/** 中心读数：悬停时切到该扇区，否则显示总数 */
const distCenter = computed(() => {
  const item = distHover.value >= 0 ? distribution.value[distHover.value] : null
  return item ? { value: item.value, label: item.name } : { value: distTotal.value, label: '总行为' }
})
/** 首次渲染标记（用于 0° 展开动画） */
let distFirst = true

/** 功能使用占比环形图（极简单轴 · 浅色） */
function renderDistribution() {
  if (!distChart) return
  const data = distItems.value.map((d) => ({
    name: d.name,
    value: d.value,
    itemStyle: { color: d.color },
  }))
  const seriesBase = {
    type: 'pie' as const,
    radius: ['65%', '85%'],
    center: ['50%', '50%'],
    startAngle: 90,
    clockwise: true,
    label: { show: false },
    labelLine: { show: false },
    itemStyle: { borderColor: '#FFFFFF', borderWidth: 2 },
    // 悬停：本扇区放大 + 其余扇区变淡（ECharts 原生，不重绘，避免偶发失效）
    emphasis: { scale: true, scaleSize: 4, focus: 'self' as const },
    blur: { itemStyle: { opacity: 0.45 } },
    animationDuration: 1000,
    animationEasing: 'cubicOut' as const,
  }
  if (distFirst) {
    distFirst = false
    distChart.setOption(
      { series: [{ ...seriesBase, data: data.map((d) => ({ ...d, value: 0 })) }] },
      { notMerge: true },
    )
    requestAnimationFrame(() => distChart?.setOption({ series: [{ data }] }))
    return
  }
  distChart.setOption({ series: [{ data }], animationDurationUpdate: 0 })
}

/** 环形图交互：仅切换中心读数（变淡交给 ECharts 原生 emphasis/blur，不重绘） */
function bindDistEvents() {
  if (!distChart) return
  distChart.off('mouseover')
  distChart.off('globalout')
  distChart.on('mouseover', (p: any) => {
    if (p.componentSubType !== 'pie' || distHover.value === p.dataIndex) return
    distHover.value = p.dataIndex
  })
  distChart.on('globalout', () => {
    if (distHover.value < 0) return
    distHover.value = -1
  })
}

/** 热门浏览景点横向柱状图 */
function renderHot() {
  if (!hotChart) return
  const items = [...hotScenics.value].reverse()
  hotChart.setOption({
    grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(212,179,116,0.12)' } }, axisLabel: { color: '#7a8aa0', fontSize: 11 } },
    yAxis: { type: 'category', data: items.map((i) => i.name), ...axisStyle },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.value),
        barWidth: '55%',
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(212,179,116,0.35)' },
            { offset: 1, color: COLOR_GOLD },
          ]),
        },
      },
    ],
  })
}

function resizeCharts() {
  overviewChart?.resize()
  trendChart?.resize()
  distChart?.resize()
  hotChart?.resize()
}

/* ------------------------------ 自动刷新 ------------------------------ */
let timer: number | null = null
let liveTimer: number | null = null

/** 顶部四指标折线图：实时刷新（数据 + 图） */
async function refreshOverview() {
  await Promise.all([loadStats(), loadTrend()])
  renderOverview()
}

onMounted(async () => {
  await loadAll()
  await loadRecent()
  timer = window.setInterval(loadRecent, 5000)
  liveTimer = window.setInterval(refreshOverview, 5000)
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  if (timer !== null) window.clearInterval(timer)
  if (liveTimer !== null) window.clearInterval(liveTimer)
  window.removeEventListener('resize', resizeCharts)
  overviewChart?.dispose()
  trendChart?.dispose()
  distChart?.dispose()
  hotChart?.dispose()
})
</script>

<template>
  <div class="ub">
    <header class="ub__head">
      <h2 class="ub__title">用户行为统计</h2>
      <p class="ub__sub">浏览、收藏、笔记与 AI 对话的综合分析</p>
    </header>

    <!-- 顶部：四大指标融合柱状图（实时刷新） -->
    <section class="ub__overview">
      <div class="ub__card">
        <div class="ub__overview-head">
          <h3 class="ub__card-title">四大指标实时走势</h3>
          <span class="ub__overview-live"><i />实时更新</span>
        </div>

        <div class="ov__legend">
          <div v-for="s in OVERVIEW_SERIES" :key="s.key" class="ov__item">
            <span class="ov__dot" :style="{ background: s.color }" />
            <span class="ov__name">{{ s.name }}</span>
            <span class="ov__value">{{ s.value.toLocaleString() }}</span>
            <span class="ov__trend" :class="s.trend >= 0 ? 'is-up' : 'is-down'">
              {{ s.trend >= 0 ? '↑' : '↓' }}{{ Math.abs(s.trend).toFixed(1) }}%
            </span>
          </div>
        </div>

        <div ref="overviewEl" class="ub__chart ub__chart--overview" />
      </div>
    </section>

    <!-- 中部：趋势 + 占比 -->
    <section class="ub__mid">
      <div class="ub__card ub__card--wide">
        <h3 class="ub__card-title">近 30 天行为趋势</h3>
        <div ref="trendEl" class="ub__chart ub__chart--trend" />
      </div>
      <div class="ub__card ub__card--dist">
        <h3 class="ub__card-title">功能使用占比</h3>
        <div class="ub__donut">
          <div ref="distEl" class="ub__chart ub__chart--pie" />
          <div :key="distCenter.label" class="ub__donut-center">
            <b class="ub__donut-num">{{ distCenter.value.toLocaleString('en-US') }}</b>
            <span class="ub__donut-label">{{ distCenter.label }}</span>
          </div>
        </div>
        <div class="ub__donut-leg">
          <div v-for="d in distItems" :key="d.name" class="ub__donut-leg-item">
            <i :style="{ background: d.color }" />
            <span class="ub__donut-leg-name">{{ d.name }}</span>
            <b class="ub__donut-leg-val">{{ d.value.toLocaleString('en-US') }}</b>
          </div>
        </div>
      </div>
    </section>

    <!-- 下部：热门景点 + 活跃用户 -->
    <section class="ub__bottom">
      <div class="ub__card">
        <h3 class="ub__card-title">热门浏览景点 TOP 10</h3>
        <div ref="hotEl" class="ub__chart ub__chart--hot" />
      </div>
      <div class="ub__card">
        <h3 class="ub__card-title">活跃用户 TOP 10</h3>
        <ul class="ub__users">
          <li v-for="(u, i) in activeUsers" :key="u.userId" class="ub__user">
            <span class="ub__rank" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
            <img v-if="u.avatar" class="ub__avatar" :src="u.avatar" :alt="u.username" />
            <span v-else class="ub__avatar ub__avatar--letter">{{ (u.username || '?').charAt(0) }}</span>
            <span class="ub__username">{{ u.username }}</span>
            <span class="ub__count">{{ u.count }}</span>
          </li>
          <li v-if="!activeUsers.length" class="ub__empty">暂无数据</li>
        </ul>
      </div>
    </section>

    <!-- 最近行为动态 -->
    <section class="ub__card ub__recent">
      <div class="ub__recent-head">
        <h3 class="ub__card-title">最近用户行为动态</h3>
        <span class="ub__refresh" :class="{ 'is-loading': recentLoading }">每 5 秒自动刷新</span>
      </div>
      <ul class="ub__timeline">
        <li v-for="(a, i) in activities" :key="i" class="ub__tl-item">
          <span class="ub__tl-dot" />
          <div class="ub__tl-main">
            <img v-if="a.avatar" class="ub__tl-avatar" :src="a.avatar" :alt="a.username" />
            <span v-else class="ub__tl-avatar ub__tl-avatar--letter">{{ (a.username || '?').charAt(0) }}</span>
            <p class="ub__tl-text">
              <b>{{ a.username }}</b>
              <em class="ub__tl-action">{{ a.action }}</em>
              <span class="ub__tl-target">{{ a.target }}</span>
            </p>
            <span class="ub__tl-time">{{ a.time }}</span>
          </div>
        </li>
        <li v-if="!activities.length" class="ub__empty">暂无动态</li>
      </ul>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.ub__head {
  margin-bottom: 20px;
}

.ub__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.ub__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

/* 顶部：四大指标融合折线图 */
.ub__overview {
  margin-bottom: 20px;
}

.ub__overview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ub__overview-head .ub__card-title {
  margin-bottom: 0;
}

.ub__overview-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: $admin-text-muted;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $admin-gold;
    animation: ub-pulse 1.6s $admin-ease infinite;
  }
}

@keyframes ub-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

.ov__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 22px;
  margin: 14px 0 4px;
}

.ov__item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;
}

.ov__dot {
  align-self: center;
  width: 9px;
  height: 9px;
  border-radius: 2px;
}

.ov__name {
  font-size: 13px;
  color: $admin-text-muted;
}

.ov__value {
  font-family: $admin-font-num;
  font-size: 19px;
  line-height: 1;
  color: $admin-text;
}

.ov__trend {
  font-size: 12px;

  &.is-up {
    color: $admin-green-up;
  }

  &.is-down {
    color: $admin-red-down;
  }
}

.ub__chart--overview {
  height: 300px;
}

.ub__mid,
.ub__bottom {
  display: grid;
  gap: 20px;
  margin-bottom: 20px;
}

.ub__mid {
  grid-template-columns: 3fr 2fr;
}

.ub__bottom {
  grid-template-columns: 1fr 1fr;
}

.ub__card {
  padding: 20px;
  animation: ub-fade 0.5s $admin-ease both;

  @include admin-card;
}

@keyframes ub-fade {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.ub__card-title {
  margin-bottom: 12px;
  font-size: 15px;
  color: $admin-text;
}

.ub__chart {
  width: 100%;
}

.ub__chart--trend {
  height: 320px;
}

.ub__chart--pie {
  height: 320px;
}

/* ============ 功能使用占比：极简浅色卡 ============ */
.ub__card.ub__card--dist {
  padding: 32px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #fff;

  .ub__card-title {
    margin-bottom: 24px;
    font-size: 16px;
    font-weight: 600;
    color: #1a1a1a;
  }

  .ub__chart--pie {
    height: 300px;
  }
}

.ub__donut {
  position: relative;
}

/* 中心读数（切换时淡入 + 上浮） */
.ub__donut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: ub-donut-center 0.3s $admin-ease both;
}

@keyframes ub-donut-center {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 10px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.ub__donut-num {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: #1a1a1a;
}

.ub__donut-label {
  font-size: 12px;
  color: #9ca3af;
}

/* 图例：点 + 标签 + 数值 */
.ub__donut-leg {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px 20px;
  margin-top: 24px;
}

.ub__donut-leg-item {
  display: inline-flex;
  gap: 6px;
  align-items: center;

  i {
    width: 6px;
    height: 6px;
    flex: 0 0 auto;
    border-radius: 50%;
  }
}

.ub__donut-leg-name {
  font-size: 13px;
  color: #6b7280;
}

.ub__donut-leg-val {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #1a1a1a;
}

.ub__chart--hot {
  height: 360px;
}

/* 活跃用户 */
.ub__users {
  display: flex;
  flex-direction: column;
}

.ub__user {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 9px 4px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.08);
}

.ub__rank {
  width: 26px;
  font-family: $admin-font-num;
  font-size: 15px;
  text-align: center;
  color: $admin-text-muted;

  &.is-top {
    color: $admin-gold;
    font-weight: 700;
    font-size: 22px;
  }
}

.ub__avatar {
  width: 32px;
  height: 32px;
  border: 1px solid $admin-gold-line;
  border-radius: 50%;
  object-fit: cover;
}

.ub__avatar--letter {
  display: inline-grid;
  place-items: center;
  background: $admin-gold-soft;
  font-size: 14px;
  font-weight: 700;
  color: $admin-gold;
}

.ub__username {
  flex: 1;
  font-size: 14px;
  color: $admin-text;
}

.ub__count {
  font-family: $admin-font-num;
  font-size: 15px;
  color: $admin-gold;
}

/* 最近动态时间轴 */
.ub__recent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ub__refresh {
  font-size: 12px;
  color: $admin-text-muted;

  &.is-loading {
    color: $admin-gold;
  }
}

.ub__timeline {
  position: relative;
  max-height: 320px;
  padding-left: 20px;
  overflow-y: auto;

  &::before {
    content: '';
    position: absolute;
    top: 6px;
    bottom: 6px;
    left: 5px;
    width: 1px;
    background: $admin-border;
  }
}

.ub__tl-item {
  position: relative;
  display: flex;
  padding: 8px 0;
  animation: ub-fade 0.4s $admin-ease both;
}

.ub__tl-dot {
  position: absolute;
  top: 13px;
  left: -19px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $admin-gold;
}

.ub__tl-main {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.ub__tl-avatar {
  width: 28px;
  height: 28px;
  border: 1px solid $admin-gold-line;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.ub__tl-avatar--letter {
  display: inline-grid;
  place-items: center;
  background: $admin-gold-soft;
  font-size: 12px;
  font-weight: 700;
  color: $admin-gold;
}

.ub__tl-text {
  font-size: 13px;
  color: $admin-text;

  b {
    color: $admin-gold;
  }
}

.ub__tl-action {
  margin: 0 6px;
  padding: 1px 8px;
  border-radius: 999px;
  background: $admin-gold-soft;
  font-size: 12px;
  font-style: normal;
  color: $admin-gold;
}

.ub__tl-target {
  color: $admin-text-muted;
}

.ub__tl-time {
  font-size: 12px;
  color: $admin-text-muted;
  white-space: nowrap;
}

.ub__empty {
  padding: 20px 0;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

/* 响应式 */
@media (max-width: 1200px) {
  .ub__mid,
  .ub__bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .ov__value {
    font-size: 16px;
  }
}
</style>
