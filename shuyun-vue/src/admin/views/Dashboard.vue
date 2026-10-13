<script setup lang="ts">
/**
 * 后台数据看板（Dashboard）· 运营指挥中心
 *
 * 全部指标均来自后端真实聚合（/admin/dashboard/* 走 AdminStatMapper 查库；
 * 内容体量与市州热度复用公开的 /screen/overview、/screen/map-data）。
 *
 * - 顶部：4 张实时指标卡（数字滚动 + 环比 + 迷你走势）
 * - 中部：访问趋势（可切 今日/7/30/60 天） + 用户兴趣分布（环形）
 * - 下部：四川省 · 市州热度作战图（热力点 + 成都飞线，可点击市州） + 内容体量构成
 * - 底部：热门景点 TOP10（可点击跳详情） + 最近用户活动（时间轴）
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import gsap from 'gsap'
import AdminIcon from '@/admin/components/AdminIcon.vue'
import adminRequest from '@/admin/api/adminRequest'
import { useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { getMapData, getScreenOverview, type MapCityPoint, type ScreenOverview } from '@/api/map3d'

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
interface CardData {
  value: number
  trend: number
}
interface StatsData {
  users: CardData
  scenics: CardData
  visits: CardData
  chats: CardData
}
interface TrendPoint {
  date: string
  fullDate: string
  count: number
}
interface NameValue {
  id?: number
  name: string
  value: number
}
interface Activity {
  username: string
  action: string
  target: string
  time: string
}

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const loading = ref(false)
const router = useRouter()

const stats = ref<StatsData>({
  users: { value: 0, trend: 0 },
  scenics: { value: 0, trend: 0 },
  visits: { value: 0, trend: 0 },
  chats: { value: 0, trend: 0 },
})

const trend = ref<TrendPoint[]>([])
const interests = ref<NameValue[]>([])
const hotScenics = ref<NameValue[]>([])
const activities = ref<Activity[]>([])
const overview = ref<ScreenOverview | null>(null)
const cities = ref<MapCityPoint[]>([])
/** 作战图选中的市州 */
const selectedCity = ref<MapCityPoint | null>(null)

/** 访问趋势时间范围（天） */
const range = ref(30)
const RANGES = [
  { label: '今日', value: 1 },
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
  { label: '60 天', value: 60 },
]

/** 图表 DOM 与实例 */
const trendEl = ref<HTMLDivElement | null>(null)
const interestEl = ref<HTMLDivElement | null>(null)
const hotEl = ref<HTMLDivElement | null>(null)
const mapEl = ref<HTMLDivElement | null>(null)
const composeEl = ref<HTMLDivElement | null>(null)
const metricEl = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let interestChart: echarts.ECharts | null = null
let hotChart: echarts.ECharts | null = null
let mapChart: echarts.ECharts | null = null
let composeChart: echarts.ECharts | null = null
let metricChart: echarts.ECharts | null = null

/** 环形图配色 */
const PIE_COLORS = ['#1F1F1F', '#8C1F28', '#4B5563', '#6B7280', '#9CA3AF', '#D4B374']
/** 地图热度色带 */
const HEAT_COLORS = ['#12324f', '#1c4d78', '#2f7fb0', '#d4b374', '#c0793a', '#7a1a22']

const { themeMode } = useTheme()
const chartColors = computed(() =>
  themeMode.value === 'night'
    ? {
        tooltipBg: 'rgba(18,35,61,0.96)',
        tooltipBorder: 'rgba(212,179,116,0.35)',
        text: '#f0ebe0',
        muted: '#7a8aa0',
        axisLine: 'rgba(212,179,116,0.25)',
        splitLine: 'rgba(212,179,116,0.12)',
        labelLine: 'rgba(212,179,116,0.4)',
        pieBorder: '#12233d',
        mapArea: 'rgba(20,40,70,0.65)',
        mapBorder: 'rgba(212,179,116,0.45)',
      }
    : {
        tooltipBg: 'rgba(255,253,247,0.98)',
        tooltipBorder: 'rgba(176,122,16,0.4)',
        text: '#2c2415',
        muted: '#8a795a',
        axisLine: 'rgba(176,122,16,0.3)',
        splitLine: 'rgba(176,122,16,0.16)',
        labelLine: 'rgba(176,122,16,0.45)',
        pieBorder: '#fffdf7',
        mapArea: 'rgba(226,215,190,0.55)',
        mapBorder: 'rgba(176,122,16,0.5)',
      },
)

/* ------------------------------------------------------------
 * 数据加载
 * ---------------------------------------------------------- */
async function loadStats() {
  try {
    const d = (await adminRequest.get('/admin/dashboard/stats')).data as Partial<StatsData> | null
    // 归一化：无论后端返回如何，都保证四个指标字段存在，避免渲染报错
    stats.value = {
      users: d?.users ?? { value: 0, trend: 0 },
      scenics: d?.scenics ?? { value: 0, trend: 0 },
      visits: d?.visits ?? { value: 0, trend: 0 },
      chats: d?.chats ?? { value: 0, trend: 0 },
    }
  } catch {
    /* 拦截器已提示 */
  }
}
async function loadTrend() {
  try {
    trend.value = (await adminRequest.get('/admin/dashboard/visit-trend', { params: { days: range.value } })).data as TrendPoint[]
  } catch {
    trend.value = []
  }
}
async function loadInterests() {
  try {
    interests.value = (await adminRequest.get('/admin/dashboard/interest-distribution')).data as NameValue[]
  } catch {
    interests.value = []
  }
}
async function loadHotScenics() {
  try {
    hotScenics.value = (await adminRequest.get('/admin/dashboard/hot-scenic', { params: { limit: 10 } })).data as NameValue[]
  } catch {
    hotScenics.value = []
  }
}
async function loadActivities() {
  try {
    activities.value = (await adminRequest.get('/admin/dashboard/recent-activities', { params: { limit: 8 } })).data as Activity[]
  } catch {
    activities.value = []
  }
}
/** 内容体量（公开接口，真实统计） */
async function loadOverview() {
  try {
    overview.value = await getScreenOverview()
  } catch {
    overview.value = null
  }
}
/** 市州热度（公开接口，真实统计） */
async function loadCities() {
  try {
    cities.value = await getMapData()
  } catch {
    cities.value = []
  }
}

async function loadAll() {
  loading.value = true
  await Promise.all([loadStats(), loadTrend(), loadInterests(), loadHotScenics(), loadActivities(), loadOverview(), loadCities()])
  loading.value = false
  await nextTick()
  renderAll()
  playEntrance()
}

/** 切换时间范围仅刷新趋势图 */
async function onRangeChange(value: number) {
  if (range.value === value) return
  range.value = value
  await loadTrend()
  await nextTick()
  renderTrend()
}

/* ------------------------------------------------------------
 * 图表渲染
 * ---------------------------------------------------------- */
function initCharts() {
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value)
  if (interestEl.value && !interestChart) interestChart = echarts.init(interestEl.value)
  if (hotEl.value && !hotChart) hotChart = echarts.init(hotEl.value)
  if (mapEl.value && !mapChart) mapChart = echarts.init(mapEl.value)
  if (composeEl.value && !composeChart) composeChart = echarts.init(composeEl.value)
  if (metricEl.value && !metricChart) metricChart = echarts.init(metricEl.value)
}

function renderAll() {
  initCharts()
  refreshMetrics()
  renderTrend()
  renderInterest()
  bindInterestEvents()
  renderHot()
  renderCompose()
  renderMap()
}

/** 访问趋势：面积渐变 + 均值线 + 峰值标注 */
function renderTrend() {
  if (!trendChart) return
  const c = chartColors.value
  const data = trend.value
  const values = data.map((d) => d.count)
  const avg = values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : 0
  const option: echarts.EChartsOption = {
    grid: { left: 8, right: 20, top: 30, bottom: 26, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.text } },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((d) => d.date),
      axisLine: { lineStyle: { color: c.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: c.muted, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: c.splitLine } },
      axisLabel: { color: c.muted, fontSize: 11 },
    },
    series: [
      {
        name: '访问量',
        type: 'line',
        smooth: true,
        showSymbol: data.length <= 20,
        symbol: 'circle',
        symbolSize: 6,
        data: values,
        lineStyle: { width: 2.5, color: '#d4b374' },
        itemStyle: { color: '#d4b374' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(212,179,116,0.42)' },
            { offset: 1, color: 'rgba(212,179,116,0.02)' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: 'rgba(74,125,181,0.7)', type: 'dashed' },
          label: { color: c.muted, formatter: `均值 ${avg}` },
          data: [{ type: 'average', name: '均值' }],
        },
        animationDuration: 1400,
        animationEasing: 'cubicOut',
      },
    ],
  }
  trendChart.setOption(option, true)
}

/* 用户兴趣分布：高级灰阶环形 + 中心读数 + 悬停切换 */
/** 悬停中的扇区下标（-1 表示无） */
const interestHover = ref(-1)
/** 扇区（带配色） */
const interestItems = computed(() =>
  interests.value.map((d, i) => ({ ...d, color: PIE_COLORS[i % PIE_COLORS.length] })),
)
/** 总数 */
const interestTotal = computed(() => interests.value.reduce((s, d) => s + (d.value || 0), 0))
/** 中心读数：悬停时切到该扇区，否则显示总数 */
const interestCenter = computed(() => {
  const item = interestHover.value >= 0 ? interests.value[interestHover.value] : null
  return item ? { value: item.value, label: item.name } : { value: interestTotal.value, label: '次浏览' }
})
/** 首次渲染标记（0° 展开动画） */
let interestFirst = true

/** 用户兴趣分布环形图（极简浅色） */
function renderInterest() {
  if (!interestChart) return
  const data = interestItems.value.map((d) => ({ name: d.name, value: d.value, itemStyle: { color: d.color } }))
  const seriesBase = {
    type: 'pie' as const,
    radius: ['65%', '85%'],
    center: ['50%', '50%'],
    startAngle: 90,
    clockwise: true,
    label: { show: false },
    labelLine: { show: false },
    itemStyle: { borderColor: '#FFFFFF', borderWidth: 2 },
    emphasis: { scale: true, scaleSize: 4, focus: 'self' as const },
    blur: { itemStyle: { opacity: 0.45 } },
    animationDuration: 1000,
    animationEasing: 'cubicOut' as const,
  }
  if (interestFirst) {
    interestFirst = false
    interestChart.setOption(
      { series: [{ ...seriesBase, data: data.map((d) => ({ ...d, value: 0 })) }] },
      { notMerge: true },
    )
    requestAnimationFrame(() => interestChart?.setOption({ series: [{ data }] }))
    return
  }
  interestChart.setOption({ series: [{ data }], animationDurationUpdate: 0 })
}

/** 环形图交互：仅切换中心读数（变淡交给 ECharts 原生 emphasis/blur） */
function bindInterestEvents() {
  if (!interestChart) return
  interestChart.off('mouseover')
  interestChart.off('globalout')
  interestChart.on('mouseover', (p: any) => {
    if (p.componentSubType !== 'pie' || interestHover.value === p.dataIndex) return
    interestHover.value = p.dataIndex
  })
  interestChart.on('globalout', () => {
    if (interestHover.value < 0) return
    interestHover.value = -1
  })
}

/** 热门景点 TOP10：横向柱状，可点击跳详情 */
function renderHot() {
  if (!hotChart) return
  const c = chartColors.value
  const list = [...hotScenics.value].reverse()
  const option: echarts.EChartsOption = {
    grid: { left: 8, right: 30, top: 12, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.text } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: c.splitLine } }, axisLabel: { color: c.muted, fontSize: 11 } },
    yAxis: {
      type: 'category',
      data: list.map((d) => d.name),
      axisLine: { lineStyle: { color: c.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: c.text, fontSize: 12 },
    },
    series: [
      {
        name: '人气',
        type: 'bar',
        barWidth: 12,
        cursor: 'pointer',
        data: list.map((d) => d.value),
        itemStyle: {
          borderRadius: 0,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(212,179,116,0.35)' },
            { offset: 1, color: '#d4b374' },
          ]),
        },
        emphasis: { itemStyle: { color: '#c0793a' } },
        label: { show: true, position: 'right', color: c.muted, fontSize: 11 },
        animationDuration: 1200,
        animationEasing: 'cubicOut',
      },
    ],
  }
  hotChart.setOption(option, true)
  // 点击柱子 → 跳前台景点详情（用名称反查 id）
  hotChart.off('click')
  hotChart.on('click', (p: any) => {
    if (p.componentSubType !== 'bar') return
    const hit = hotScenics.value.find((d) => d.name === p.name)
    if (hit?.id) router.push(`/scenic/${hit.id}`)
  })
}

/** 内容体量构成 */
function renderCompose() {
  if (!composeChart) return
  const c = chartColors.value
  const o = overview.value
  const items = [
    { name: '景点', value: o?.scenicCount ?? 0, color: '#d4b374' },
    { name: '美食', value: o?.foodCount ?? 0, color: '#c0793a' },
    { name: '非遗', value: o?.heritageCount ?? 0, color: '#7a1a22' },
    { name: '文化', value: o?.cultureCount ?? 0, color: '#3a5a40' },
    { name: '用户', value: o?.userCount ?? 0, color: '#4a7db5' },
    { name: 'AI 对话', value: o?.aiChatCount ?? 0, color: '#6b8f71' },
  ]
  const option: echarts.EChartsOption = {
    grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: c.tooltipBg, borderColor: c.tooltipBorder, textStyle: { color: c.text } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: c.splitLine } }, axisLabel: { color: c.muted, fontSize: 11 } },
    yAxis: { type: 'category', data: items.map((d) => d.name), axisLine: { lineStyle: { color: c.axisLine } }, axisTick: { show: false }, axisLabel: { color: c.text, fontSize: 12 } },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: items.map((d) => ({ value: d.value, itemStyle: { color: d.color } })),
        label: { show: true, position: 'right', color: c.muted, fontSize: 11 },
        animationDuration: 1200,
        animationEasing: 'cubicOut',
      },
    ],
  }
  composeChart.setOption(option, true)
}

/* -------- 运营指标趋势（4 项合一 · 实时流动） -------- */
/** 折线点数（滚动窗口） */
const METRIC_POINTS = 40
/** 相邻两点代表的秒数（横轴时间刻度） */
const METRIC_STEP_SEC = 60
/** 4 条曲线各占一条泳道，竖轴均分互不重叠 */
const LANE_H = 100 / 4

interface MetricMeta {
  key: keyof StatsData
  name: string
  color: string
  /** 波动幅度（真实值量级） */
  amp: number
  /** 波动频率 */
  freq: number
  /** 逐点相位步长 */
  phase: number
}

/** 四条曲线：用户 / 景点 / 访问 / 对话 */
const METRICS: MetricMeta[] = [
  { key: 'users', name: '用户总数', color: '#D4B374', amp: 10, freq: 1.2, phase: 0.4 },
  { key: 'scenics', name: '景点总数', color: '#8C1F28', amp: 3, freq: 1.5, phase: 0.5 },
  { key: 'visits', name: '今日访问', color: '#4AD8F0', amp: 2, freq: 1.0, phase: 0.3 },
  { key: 'chats', name: 'AI 对话数', color: '#9B6B9E', amp: 15, freq: 1.3, phase: 0.6 },
]

/** 横轴时间刻度（滚动更新） */
const metricLabels = ref<string[]>([])
/** 真实基数（每帧在其上叠加波动） */
const baseData: Record<string, number[]> = { users: [], scenics: [], visits: [], chats: [] }
/** 当前帧真实值（供 tooltip 显示） */
let rawSeries: Record<string, number[]> = { users: [], scenics: [], visits: [], chats: [] }

/** rAF 驱动状态 */
let metricRaf = 0
let metricRunning = false
let metricPaused = false
let metricTimeT = 0
let metricLabelTick = 0

/** 读取某指标的真实基数 */
function baseValue(key: keyof StatsData): number {
  return Number(stats.value[key]?.value ?? 0)
}

/** 用真实基数铺底 */
function seedBaseData() {
  for (const m of METRICS) {
    baseData[m.key] = Array.from({ length: METRIC_POINTS }, () => baseValue(m.key))
  }
}

/** 时间刻度：末点=现在，按 METRIC_STEP_SEC 回推 */
function syncMetricLabels() {
  const now = Date.now()
  metricLabels.value = Array.from({ length: METRIC_POINTS }, (_, i) => {
    const d = new Date(now - (METRIC_POINTS - 1 - i) * METRIC_STEP_SEC * 1000)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  })
}

/** 单帧真实值：基数 + 正弦波动 */
function buildRaw() {
  const next: Record<string, number[]> = {}
  for (const m of METRICS) {
    next[m.key] = baseData[m.key].map(
      (v, i) => v + Math.sin(metricTimeT * m.freq + i * m.phase) * m.amp,
    )
  }
  rawSeries = next
}

/** 取某曲线的泳道坐标（按自身极值映射进第 lane 条泳道） */
function laneOf(key: string, lane: number): number[] {
  const raw = rawSeries[key] ?? []
  const min = Math.min(...raw)
  const max = Math.max(...raw)
  const span = max - min || 1
  const bottom = lane * LANE_H
  return raw.map((v) => bottom + LANE_H * (0.16 + 0.68 * ((v - min) / span)))
}

/** 单条曲线的 series 配置 */
function metricSeriesConf(m: MetricMeta, lane: number) {
  return {
    name: m.name,
    type: 'line' as const,
    smooth: true,
    symbol: 'none',
    showSymbol: false,
    lineStyle: { width: 2, color: m.color, cap: 'round' as const },
    itemStyle: { color: m.color },
    emphasis: { focus: 'series' as const },
    data: laneOf(m.key, lane),
  }
}

/** 图表基础配置（浅色极简） */
function metricOption(): echarts.EChartsOption {
  return {
    animation: false,
    animationDurationUpdate: 0,
    grid: { left: 8, right: 14, top: 12, bottom: 6, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        crossStyle: { color: '#9CA3AF' },
        lineStyle: { color: '#9CA3AF', type: 'dashed' },
        label: { show: false },
      },
      backgroundColor: '#FFFFFF',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      padding: [10, 12],
      extraCssText: 'border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,0.08);',
      textStyle: { color: '#1A1A1A', fontSize: 12 },
      formatter: (ps: any) => {
        const idx = ps?.[0]?.dataIndex ?? 0
        const head = `<div style="color:#9CA3AF;font-size:11px">${metricLabels.value[idx] ?? ''}</div>`
        const rows = METRICS.map((m) => {
          const v = rawSeries[m.key]?.[idx] ?? 0
          return (
            `<div style="display:flex;align-items:center;gap:6px;margin-top:5px">` +
            `<i style="width:8px;height:8px;border-radius:50%;background:${m.color}"></i>` +
            `<span style="color:#6B7280">${m.name}</span>` +
            `<b style="margin-left:auto;color:#1A1A1A">${Math.round(v).toLocaleString('en-US')}</b></div>`
          )
        }).join('')
        return head + rows
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: metricLabels.value,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9CA3AF', fontSize: 11, interval: 7 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.04)' } },
    },
    series: METRICS.map((m, i) => metricSeriesConf(m, METRICS.length - 1 - i)),
  }
}

/** 载入真实基数并整体重绘 */
function refreshMetrics() {
  seedBaseData()
  syncMetricLabels()
  buildRaw()
  metricChart?.setOption(metricOption(), { lazyUpdate: true })
}

/** 单帧推进：重算真实值 + 更新曲线（rAF 每帧调用） */
function applyMetricFrame() {
  if (!metricChart) return
  buildRaw()
  metricLabelTick += 1
  if (metricLabelTick % 240 === 0) {
    syncMetricLabels()
    metricChart.setOption({ xAxis: { data: metricLabels.value } }, { lazyUpdate: true })
  }
  metricChart.setOption(
    { series: METRICS.map((m, i) => metricSeriesConf(m, METRICS.length - 1 - i)) },
    { lazyUpdate: true },
  )
}

/** rAF 主循环 */
function metricLoop() {
  if (!metricRunning) return
  if (!metricPaused) {
    metricTimeT += 0.02
    applyMetricFrame()
  }
  metricRaf = requestAnimationFrame(metricLoop)
}

/** 启动：铺底 + 首帧 + 开启 rAF */
function startMetricLoop() {
  metricRunning = true
  seedBaseData()
  syncMetricLabels()
  buildRaw()
  metricChart?.setOption(metricOption(), { lazyUpdate: true })
  applyMetricFrame()
  cancelAnimationFrame(metricRaf)
  metricRaf = requestAnimationFrame(metricLoop)
}

/** 停止 rAF */
function stopMetricLoop() {
  metricRunning = false
  cancelAnimationFrame(metricRaf)
  metricRaf = 0
}

/** 悬停暂停波动，便于查看 tooltip */
function onMetricEnter() {
  metricPaused = true
}
function onMetricLeave() {
  metricPaused = false
}

/* -------- 四川省市州热度作战图 -------- */
let sichuanReady = false
async function ensureSichuanMap(): Promise<boolean> {
  if (sichuanReady) return true
  try {
    const res = await fetch('https://geo.datav.aliyun.com/areas_v3/bound/510000_full.json')
    const geo = await res.json()
    echarts.registerMap('sichuan', geo)
    sichuanReady = true
    return true
  } catch {
    return false
  }
}

async function renderMap() {
  if (!mapChart) return
  const ok = await ensureSichuanMap()
  if (!ok) {
    mapChart.clear()
    return
  }
  const c = chartColors.value
  const list = cities.value
  if (!list.length) {
    mapChart.clear()
    return
  }
  const heat = list.map((x) => x.visitCount || 0)
  const min = Math.min(...heat)
  const max = Math.max(...heat)
  const maxScenic = Math.max(...list.map((x) => x.scenicCount || 1))

  // 成都 → 热度前 6 市州的飞线
  const chengdu = list.find((x) => x.name === '成都')
  const fly = chengdu
    ? [...list].filter((x) => x.name !== '成都').sort((a, b) => (b.visitCount || 0) - (a.visitCount || 0)).slice(0, 6)
        .map((x) => ({
          fromName: '成都',
          toName: x.name,
          coords: [[Number(chengdu.lng), Number(chengdu.lat)], [Number(x.lng), Number(x.lat)]],
        }))
    : []

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.text },
      formatter: (p: any) => {
        const d = list.find((x) => x.name === p.name)
        if (!d) return p.name
        return `<b>${d.name}</b><br/>景点 ${d.scenicCount ?? 0} · 美食 ${d.foodCount ?? 0} · 非遗 ${d.heritageCount ?? 0}<br/>累计浏览 ${d.visitCount ?? 0}`
      },
    },
    visualMap: {
      seriesIndex: 0,
      min,
      max: max === min ? min + 1 : max,
      calculable: true,
      left: 6,
      bottom: 6,
      itemHeight: 90,
      textStyle: { color: c.muted, fontSize: 11 },
      inRange: { color: HEAT_COLORS },
    },
    geo: {
      map: 'sichuan',
      roam: true,
      zoom: 1.16,
      layoutCenter: ['52%', '52%'],
      layoutSize: '96%',
      label: { show: false },
      itemStyle: { areaColor: c.mapArea, borderColor: c.mapBorder, borderWidth: 0.8 },
      emphasis: { itemStyle: { areaColor: 'rgba(212,179,116,0.28)' }, label: { show: false } },
    },
    series: [
      {
        name: '市州热度',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        zlevel: 2,
        rippleEffect: { brushType: 'stroke', scale: 2.6 },
        symbolSize: (val: any) => 8 + (val[2] / maxScenic) * 22,
        data: list.map((x) => ({ name: x.name, value: [Number(x.lng), Number(x.lat), x.scenicCount || 0] })),
      },
      {
        name: '成都飞线',
        type: 'lines',
        coordinateSystem: 'geo',
        zlevel: 1,
        effect: { show: true, period: 5, trailLength: 0.25, symbol: 'arrow', symbolSize: 6, color: '#d4b374' },
        lineStyle: { color: 'rgba(212,179,116,0.55)', width: 1.2, curveness: 0.28 },
        data: fly,
      },
    ],
  }
  mapChart.setOption(option, true)
  mapChart.off('click')
  mapChart.on('click', (p: any) => {
    if (p.componentSubType !== 'effectScatter') return
    selectedCity.value = cities.value.find((x) => x.name === p.name) ?? null
  })
}

/* ------------------------------------------------------------
 * 入场动效
 * ---------------------------------------------------------- */
function playEntrance() {
  // 注意：.dash__trend 本身即 .panel，统一交给 .panel 动画；
  // 否则同一元素会叠加两个 from(opacity:0)，第二个 tween 会停在透明。
  gsap.from('.panel', {
    y: 20,
    opacity: 0,
    duration: 0.6,
    stagger: 0.08,
    ease: 'power3.out',
    clearProps: 'opacity,transform',
  })
}

/* ------------------------------------------------------------
 * 工具
 * ---------------------------------------------------------- */
function activityIcon(action: string) {
  if (action === '收藏') return 'check'
  if (action === 'AI 对话') return 'ai'
  if (action === '创建行程') return 'trip'
  return 'eye'
}

function onResize() {
  metricChart?.resize()
  trendChart?.resize()
  interestChart?.resize()
  hotChart?.resize()
  mapChart?.resize()
  composeChart?.resize()
}

watch(themeMode, () => nextTick(() => renderAll()))

onMounted(async () => {
  await loadAll()
  startMetricLoop()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopMetricLoop()
  window.removeEventListener('resize', onResize)
  ;[trendChart, interestChart, hotChart, mapChart, composeChart, metricChart].forEach((c) => c?.dispose())
  trendChart = interestChart = hotChart = mapChart = composeChart = metricChart = null
})
</script>

<template>
  <div class="dash">
    <!-- 页头 -->
    <div class="dash__head">
      <div>
        <h2 class="dash__title">数据看板</h2>
        <p class="dash__sub">实时掌握蜀韵·智游的运营全景</p>
      </div>
      <div class="dash__actions">
        <button type="button" class="dash__screen" @click="router.push('/screen')">
          <AdminIcon name="behavior" :size="16" />
          查看数据大屏
        </button>
        <div class="range">
          <button
            v-for="r in RANGES"
            :key="r.value"
            type="button"
            class="range__btn"
            :class="{ 'is-active': range === r.value }"
            @click="onRangeChange(r.value)"
          >
            {{ r.label }}
          </button>
        </div>
        <button type="button" class="refresh" :class="{ 'is-loading': loading }" @click="loadAll">
          <AdminIcon name="refresh" :size="17" />
          刷新
        </button>
      </div>
    </div>

    <!-- 运营指标趋势（4 张卡的指标合并为一图，实时波动） -->
    <section class="panel dash__trend">
      <h3 class="mt-title">运营指标趋势</h3>

      <div class="mt-stats">
        <div v-for="m in METRICS" :key="m.key" class="mt-item">
          <div class="mt-item__head">
            <i class="mt-item__dot" :style="{ background: m.color }" />
            <b class="mt-item__val">{{ stats[m.key].value.toLocaleString('en-US') }}</b>
          </div>
          <span class="mt-item__label">{{ m.name }}</span>
          <span class="mt-item__trend" :class="stats[m.key].trend >= 0 ? 'is-up' : 'is-down'">
            {{ stats[m.key].trend >= 0 ? '↑' : '↓' }}{{ Math.abs(stats[m.key].trend).toFixed(1) }}%
          </span>
        </div>
      </div>

      <div class="mt-divider" />

      <div ref="metricEl" class="mt-chart" @mouseenter="onMetricEnter" @mouseleave="onMetricLeave" />
    </section>

    <!-- 趋势 + 兴趣 -->
    <div class="dash__row">
      <section class="panel panel--60">
        <header class="panel__head">
          <h3 class="panel__title">访问趋势</h3>
          <span class="panel__desc">近 {{ range }} 天 · 悬停查看每日明细</span>
        </header>
        <div ref="trendEl" class="panel__chart" />
      </section>

      <section class="panel panel--40 dash__interest">
        <header class="panel__head">
          <h3 class="panel__title">用户兴趣分布</h3>
          <span class="panel__desc">按浏览的文化类别</span>
        </header>
        <div class="dash__donut">
          <div ref="interestEl" class="panel__chart" />
          <div :key="interestCenter.label" class="dash__donut-center">
            <b class="dash__donut-num">{{ interestCenter.value.toLocaleString('en-US') }}</b>
            <span class="dash__donut-label">{{ interestCenter.label }}</span>
          </div>
        </div>
        <div class="dash__donut-leg">
          <div v-for="d in interestItems" :key="d.name" class="dash__donut-leg-item">
            <i :style="{ background: d.color }" />
            <span class="dash__donut-leg-name">{{ d.name }}</span>
            <b class="dash__donut-leg-val">{{ d.value.toLocaleString('en-US') }}</b>
          </div>
        </div>
      </section>
    </div>

    <!-- 作战图 + 内容体量 -->
    <div class="dash__row">
      <section class="panel panel--60">
        <header class="panel__head">
          <h3 class="panel__title">四川省 · 市州热度作战图</h3>
          <span class="panel__desc">点击光点查看该市州</span>
        </header>
        <div class="mapwrap">
          <div ref="mapEl" class="panel__chart panel__chart--map" />
          <transition name="fade">
            <div v-if="selectedCity" class="citycard">
              <button type="button" class="citycard__close" @click="selectedCity = null">✕</button>
              <b class="citycard__name">{{ selectedCity.name }}</b>
              <ul class="citycard__list">
                <li><span>景点</span><em>{{ selectedCity.scenicCount ?? 0 }}</em></li>
                <li><span>美食</span><em>{{ selectedCity.foodCount ?? 0 }}</em></li>
                <li><span>非遗</span><em>{{ selectedCity.heritageCount ?? 0 }}</em></li>
                <li><span>浏览</span><em>{{ selectedCity.visitCount ?? 0 }}</em></li>
              </ul>
            </div>
          </transition>
        </div>
      </section>

      <section class="panel panel--40">
        <header class="panel__head">
          <h3 class="panel__title">平台内容体量</h3>
          <span class="panel__desc">景点 / 美食 / 非遗 / 文化 / 用户 / 对话</span>
        </header>
        <div ref="composeEl" class="panel__chart" />
      </section>
    </div>

    <!-- 热门景点 + 最近活动 -->
    <div class="dash__row">
      <section class="panel panel--50">
        <header class="panel__head">
          <h3 class="panel__title">热门景点 TOP 10</h3>
          <span class="panel__desc">点击柱条查看详情</span>
        </header>
        <div ref="hotEl" class="panel__chart" />
      </section>

      <section class="panel panel--50">
        <header class="panel__head">
          <h3 class="panel__title">最近用户活动</h3>
          <span class="panel__desc">浏览 / 收藏 / 对话 / 行程</span>
        </header>
        <ul class="timeline">
          <li v-for="(a, i) in activities" :key="i" class="timeline__item">
            <span class="timeline__avatar">{{ (a.username || '蜀').charAt(0).toUpperCase() }}</span>
            <div class="timeline__body">
              <p class="timeline__text">
                <b>{{ a.username }}</b>
                <span class="timeline__action">
                  <AdminIcon :name="activityIcon(a.action)" :size="14" />
                  {{ a.action }}
                </span>
                <em>{{ a.target || '—' }}</em>
              </p>
              <p class="timeline__time">{{ a.time }}</p>
            </div>
          </li>
          <li v-if="!activities.length" class="timeline__empty">暂无用户活动</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

$dash-gap: 28px;

/* 页头 */
.dash__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.dash__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.dash__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.dash__actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.dash__screen {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 9px 18px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;
  transition: background-color 0.3s $admin-ease;

  &:hover {
    background: linear-gradient(120deg, #b98f45, $admin-gold);
    color: #241a08;
  }
}

.range {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid $admin-border;
  background: rgba(var(--admin-mask-rgb), 0.6);
}

.range__btn {
  padding: 7px 16px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;
  transition: color 0.3s $admin-ease, background-color 0.3s $admin-ease;

  &:hover {
    color: $admin-gold;
  }

  &.is-active {
    color: #241a08;
    background: linear-gradient(120deg, #b98f45, $admin-gold);
  }
}

.refresh {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 9px 18px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    background: rgba(212, 179, 116, 0.2);
  }

  &.is-loading :deep(svg) {
    animation: spin 0.9s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 运营指标趋势（极简 · 浅色，覆盖后台卡片默认皮肤） */
.panel.dash__trend {
  margin-bottom: $dash-gap;
  padding: 20px 22px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #fff;

  /* 去掉金角标 */
  &::before {
    display: none;
  }
}

.mt-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.mt-stats {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-top: 14px;
}

.mt-item {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mt-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mt-item__dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.mt-item__val {
  font-family: $admin-font-num;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 0.01em;
  color: #1a1a1a;
}

.mt-item__label {
  font-size: 13px;
  color: #6b7280;
}

.mt-item__trend {
  font-size: 13px;
  font-weight: 600;

  &.is-up {
    color: #16a34a;
  }

  &.is-down {
    color: #dc2626;
  }
}

.mt-divider {
  height: 1px;
  margin: 16px 0 6px;
  background: rgba(0, 0, 0, 0.06);
}

.mt-chart {
  width: 100%;
  height: 300px;
}

/* ============ 用户兴趣分布：极简浅色卡 ============ */
.panel.dash__interest {
  padding: 32px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: #fff;

  &::before {
    display: none;
  }

  .panel__head {
    margin-bottom: 24px;
  }

  .panel__title {
    font-size: 16px;
    font-weight: 600;
    color: #1a1a1a;
  }

  .panel__desc {
    color: #9ca3af;
  }

  .panel__chart {
    height: 300px;
  }
}

.dash__donut {
  position: relative;
}

/* 中心读数（切换时淡入 + 上浮） */
.dash__donut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: dash-donut-center 0.3s $admin-ease both;
}

@keyframes dash-donut-center {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 10px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.dash__donut-num {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: #1a1a1a;
}

.dash__donut-label {
  font-size: 12px;
  color: #9ca3af;
}

/* 图例：点 + 标签 + 数值 */
.dash__donut-leg {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px 18px;
  margin-top: 24px;
}

.dash__donut-leg-item {
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

.dash__donut-leg-name {
  font-size: 13px;
  color: #6b7280;
}

.dash__donut-leg-val {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #1a1a1a;
}

.dash__row {
  display: flex;
  gap: $dash-gap;
  margin-bottom: $dash-gap;
}

.panel {
  display: flex;
  flex-direction: column;
  padding: 22px;
  min-width: 0;

  @include admin-card;
  @include admin-tech-frame;
}

.panel--60 {
  flex: 0 0 calc(60% - #{$dash-gap} / 2);
}

.panel--40 {
  flex: 1;
}

.panel--50 {
  flex: 1;
}

.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel__title {
  font-size: 16px;
  font-weight: 600;
  color: $admin-text;
}

.panel__desc {
  font-size: 12px;
  color: $admin-text-muted;
}

.panel__chart {
  width: 100%;
  height: 300px;
}

.panel__chart--map {
  height: 360px;
}

/* 作战图容器 + 悬浮市州卡 */
.mapwrap {
  position: relative;
}

.citycard {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 5;
  min-width: 150px;
  padding: 12px 14px;
  border: 1px solid $admin-gold-line;
  background: rgba(var(--admin-panel-rgb, 20, 35, 61), 0.92);
  box-shadow: $admin-shadow;

  @include admin-glass(rgba(var(--admin-glass-rgb), 0.9), 14px);
}

.citycard__close {
  position: absolute;
  top: 6px;
  right: 8px;
  border: none;
  background: transparent;
  color: $admin-text-muted;
  cursor: pointer;
}

.citycard__name {
  display: block;
  margin-bottom: 8px;
  font-size: 15px;
  color: $admin-gold;
}

.citycard__list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 14px;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: $admin-text-muted;
  }

  em {
    font-style: normal;
    font-weight: 700;
    color: $admin-text;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s $admin-ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 时间轴 */
.timeline {
  flex: 1;
  padding-left: 0;
  margin: 0;
  list-style: none;
  overflow-y: auto;
  max-height: 300px;
}

.timeline__item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(212, 179, 116, 0.08);

  &:last-child {
    border-bottom: none;
  }
}

.timeline__avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, $admin-red, #5c1219);
  font-size: 14px;
  font-weight: 700;
  color: $admin-gold;
}

.timeline__body {
  min-width: 0;
}

.timeline__text {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  color: $admin-text;

  b {
    font-weight: 600;
  }

  em {
    font-style: normal;
    color: $admin-text-muted;
  }
}

.timeline__action {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  background: $admin-gold-soft;
  font-size: 12px;
  color: $admin-gold;
}

.timeline__time {
  margin-top: 4px;
  font-size: 12px;
  color: $admin-text-muted;
}

.timeline__empty {
  padding: 40px 0;
  font-size: 14px;
  color: $admin-text-muted;
  text-align: center;
}

/* 响应式 */
@media (max-width: 1280px) {
  .dash__row {
    flex-wrap: wrap;
  }

  .panel--60,
  .panel--40,
  .panel--50 {
    flex: 1 1 100%;
  }
}

@media (max-width: 768px) {
  .dash__head {
    flex-direction: column;
    gap: 14px;
    align-items: flex-start;
  }

  .dash__stats {
    grid-template-columns: 1fr;
  }
}
</style>
