<script setup lang="ts">
/**
 * 巴蜀文旅数据大屏（DataScreen）
 *
 * 三栏式全屏可视化。数据统一由 Pinia store 管理：
 * - 首屏 Promise.all 并发拉取全部接口
 * - 实时流走 SSE（useScreenStream，断线指数退避重连；可切 WebSocket）
 * - 其余模块定时轮询刷新，ECharts 使用 setOption 合并更新（保留动画、不重建实例）
 *
 * 访问路径：/screen
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import { useScreenStore, type CityPoint, type NameValue, type StreamItem } from '@/screen/store/screen'
import { useScreenStream } from '@/screen/composables/useScreenStream'
import ScreenPanel from '@/screen/components/ScreenPanel.vue'
import MetricItem from '@/screen/components/MetricItem.vue'
import ParticleField from '@/screen/components/ParticleField.vue'
import SichuanMap from '@/screen/components/SichuanMap.vue'
import WordCloudPanel from '@/screen/components/WordCloudPanel.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import AuroraBackground from '@/components/common/AuroraBackground.vue'

const store = useScreenStore()
const router = useRouter()
const { overview, cultureRank, userProfile, mapData, realtime, visitTrend, hotScenic, wordCloud, notice } =
  storeToRefs(store)

/** 当前选中的市州（地图下钻） */
const selectedCity = ref<CityPoint | null>(null)
/** 当前选中的指标下标 */
const activeMetric = ref(-1)
/** 当前选中的实时流条目 id */
const activeStream = ref('')

/* ------------------------------ 实时数据流（车站车次屏滚动） ------------------------------ */
/** 列表常驻条数 */
const FEED_MAX = 6
/** 上屏间隔（毫秒）：1 秒一条 */
const FEED_INTERVAL = 1000

/** 车站屏展示条目 */
interface FeedItem {
  id: string
  username: string
  initial: string
  avatar?: string
  action: string
  target: string
  time: string
}

/** 模拟补位池（真实推送无新增时使用，保证屏幕持续滚动） */
const FEED_USERS = ['XM', '张三', '李四', '王五', '赵六', '钱七', '孙八', '周九', '吴十', '冯二']
const FEED_ACTIONS = ['AI 对话', '浏览', '收藏', '点赞', '咨询', '规划']
const FEED_TARGETS = [
  '三星堆的神秘故事', '川剧变脸', '蜀绣', '川菜', '九寨沟',
  '都江堰', '乐山大佛', '峨眉山', '大熊猫基地', '武侯祠',
  '成都三日游', '稻城亚丁', '西岭雪山', '青城山', '杜甫草堂',
]

/** 当前上屏条目（最多 FEED_MAX 条） */
const feedItems = ref<FeedItem[]>([])
/** 悬停暂停标记（用于面板副标题提示） */
const feedPaused = ref(false)

/** 待上屏的真实推送队列（旧 → 新，依次上屏后最新一条落在顶部） */
let feedQueue: StreamItem[] = []
/** 已处理的推送指纹，避免重复上屏 */
const feedSeen = new Set<string>()
/** 是否已用首屏真实数据铺底 */
let feedSeeded = false
/** 上屏定时器 */
let feedTimer: number | null = null

/** 随机取一个元素 */
function pickRandom<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)]
}

/** 推送指纹 */
function feedSignature(it: StreamItem): string {
  return `${it.username ?? ''}|${it.action ?? ''}|${it.target ?? ''}|${it.time ?? ''}`
}

/** ISO 时间 / 纯时间 → HH:mm:ss */
function toTimeLabel(raw?: string | null): string {
  if (!raw) {
    const d = new Date()
    return [d.getHours(), d.getMinutes(), d.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':')
  }
  if (raw.includes('T')) return raw.slice(11, 19)
  return raw.length >= 8 ? raw.slice(-8) : raw
}

/** 真实推送条目 → 展示条目 */
function toFeedItem(it: StreamItem): FeedItem {
  const username = it.username || '游客'
  return {
    id: `r-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    username,
    initial: username.charAt(0),
    avatar: it.avatar,
    action: it.action || '浏览',
    target: it.target || '—',
    time: toTimeLabel(it.time),
  }
}

/** 模拟条目（无真实推送时补位） */
function randomFeedItem(): FeedItem {
  const username = pickRandom(FEED_USERS)
  return {
    id: `s-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    username,
    initial: username.charAt(0),
    action: pickRandom(FEED_ACTIONS),
    target: pickRandom(FEED_TARGETS),
    time: toTimeLabel(null),
  }
}

/** 从顶部插入一条；超出常驻条数则移除最底部 */
function pushFeed(item: FeedItem) {
  feedItems.value.unshift(item)
  if (feedItems.value.length > FEED_MAX) {
    feedItems.value.length = FEED_MAX
  }
}

/** 推进一格：优先消费真实推送，无新增则用模拟数据补位 */
function advanceFeed() {
  const next = feedQueue.length ? toFeedItem(feedQueue.shift() as StreamItem) : randomFeedItem()
  pushFeed(next)
}

/** 启动上屏定时器 */
function startFeedTimer() {
  stopFeedTimer()
  feedTimer = window.setInterval(advanceFeed, FEED_INTERVAL)
}

/** 停止上屏定时器 */
function stopFeedTimer() {
  if (feedTimer !== null) {
    window.clearInterval(feedTimer)
    feedTimer = null
  }
}

/** 悬停：暂停滚动，方便查看 */
function onFeedEnter() {
  feedPaused.value = true
  stopFeedTimer()
}

/** 移开：恢复滚动 */
function onFeedLeave() {
  feedPaused.value = false
  startFeedTimer()
}

/**
 * 实时推送变化：
 * - 首屏用真实数据一次性铺满 6 条并标记全部已处理
 * - 之后仅把「新增」的真实记录入队（真实数据优先），无新增则由模拟数据补位
 */
watch(
  realtime,
  (items) => {
    if (!items?.length) return

    if (!feedSeeded) {
      feedSeeded = true
      feedQueue = []
      for (const it of items) feedSeen.add(feedSignature(it))
      const seeded = items.slice(0, FEED_MAX).map(toFeedItem)
      while (seeded.length < FEED_MAX) seeded.push(randomFeedItem())
      feedItems.value = seeded
      return
    }

    // 增量：仅取未处理的真实记录，按「旧 → 新」入队
    for (let i = items.length - 1; i >= 0; i--) {
      const sig = feedSignature(items[i])
      if (!feedSeen.has(sig)) {
        feedSeen.add(sig)
        feedQueue.push(items[i])
      }
    }
    // 防止队列无限增长
    if (feedQueue.length > FEED_MAX * 3) {
      feedQueue = feedQueue.slice(-FEED_MAX * 3)
    }
  },
  { immediate: true },
)

/** 首屏蜀锦 loading（2 秒） */
const loading = ref(true)

/** 顶部时钟 */
const clock = ref('')
const dateText = ref('')
const weekText = ref('')
const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/** 小屏提示 */
const isSmall = ref(false)

const COLOR_GOLD = '#d4b374'
const COLOR_CYAN = '#4ad8f0'

/**
 * 文化热度排行：分类名称 → 外部详情链接
 *
 * <p>点击柱子时按分类名映射，在新标签页打开对应外部资源。
 * 注意：数据库文化分类实际取值为「戏曲」，此处复用「非遗」链接（戏曲属非遗门类）。</p>
 */
const RANK_LINK_MAP: Record<string, string> = {
  工艺: 'https://ichsc.tsichuan.com/#/search',
  非遗: 'https://www.geodoi.ac.cn/DOIPaper/HTML/2024139402/202402139411.htm',
  戏曲: 'https://www.geodoi.ac.cn/DOIPaper/HTML/2024139402/202402139411.htm',
  饮食: 'https://cbgc.scol.com.cn/news/3884728',
  历史: 'https://baike.baidu.com/item/%E5%9B%9B%E5%B7%9D%E7%9C%81/15626925',
  民俗: 'https://www.sohu.com/a/523188413_121119017',
  自然: 'https://dnr.sc.gov.cn/guestweb4/s?uc=1&siteCode=5100000009&column=%E5%85%A8%E9%83%A8',
}

/** 按分类名解析外部链接（无匹配时回退到站内文化搜索页） */
function resolveRankLink(name: string): string {
  return RANK_LINK_MAP[name] || router.resolve({ path: '/culture', query: { keyword: name } }).href
}

/* ------------------------------ 景点 TOP 10（视频轮播） ------------------------------ */
/**
 * 景点名 → B 站视频链接
 * 注意：景点名必须与数据库景区名称严格一致（如「窦圌山」非「窦团山」）
 */
const SCENIC_VIDEO_MAP: Record<string, string> = {
  '九寨沟风景名胜区': 'https://www.bilibili.com/video/BV1MCUVB3EVF/',
  '峨眉山': 'https://www.bilibili.com/video/BV1oqLX6QEk8/',
  '成都大熊猫繁育研究基地': 'https://www.bilibili.com/video/BV1dh41177yM/',
  '三星堆博物馆': 'https://www.bilibili.com/video/BV1394y127xc/',
  '稻城亚丁': 'https://www.bilibili.com/video/BV1w841127TG/',
  '窦圌山': 'https://www.bilibili.com/video/BV1nobhz2EoP/',
  '李庄古镇': 'https://www.bilibili.com/video/BV19hEyzyEMf/',
  '海螺沟冰川森林公园': 'https://www.bilibili.com/video/BV19TKb6QESD/',
  '平武报恩寺': 'https://www.bilibili.com/video/BV1oM4y1v7GV/',
  '自贡恐龙博物馆': 'https://www.bilibili.com/video/BV1NybL68EV1/',
}

/** 景点 TOP10 列表项 */
interface ScenicTop {
  name: string
  value: number
  pct: number
}

/** 轮播节奏 / 行步进（步进需与 CSS 行高 + 间距一致） */
const RANK_INTERVAL = 1800
const RANK_STEP = 48

/** 榜单（按浏览量降序；以映射表的 10 个景点为准） */
const scenicTop = ref<ScenicTop[]>([])
/** 轮播位移（行数） */
const rankOffset = ref(0)
/** 回位时关闭过渡（实现无缝循环） */
const rankSnap = ref(false)
/** 悬停暂停标记 */
const rankPaused = ref(false)

/** 后端缺失时的一次性兜底值（缓存，避免每秒刷新导致数值跳动） */
const scenicFallback = new Map<string, number>()
function fallbackValue(name: string): number {
  if (!scenicFallback.has(name)) {
    scenicFallback.set(name, 5000 + Math.round(Math.random() * 1000))
  }
  return scenicFallback.get(name) as number
}

/** 以映射表为准构建 TOP10：后端有则用真实浏览量，缺失用随机数兜底，再降序排序 */
function buildScenicTop() {
  const backend = new Map((hotScenic.value || []).map((i) => [i.name, i.value]))
  const rows = Object.keys(SCENIC_VIDEO_MAP).map((name) => ({
    name,
    value: backend.get(name) ?? fallbackValue(name),
  }))
  rows.sort((a, b) => b.value - a.value)
  const max = Math.max(1, ...rows.map((r) => r.value))
  scenicTop.value = rows.map((r) => ({ ...r, pct: Math.round((r.value / max) * 100) }))
}

/** 渲染两遍，配合 translateY 实现无缝循环 */
const rankRows = computed(() => [...scenicTop.value, ...scenicTop.value])

/** 点击景点 → 新标签打开 B 站视频 */
function playScenicVideo(name: string) {
  const url = SCENIC_VIDEO_MAP[name]
  if (url) {
    window.open(url, '_blank', 'noopener')
  } else {
    console.warn('没有找到视频链接：', name)
  }
}

/** 轮播定时器 */
let rankTimer: number | null = null

/** 向上滚动一位；到末尾后无缝回位 */
function tickRank() {
  const len = scenicTop.value.length
  if (len <= 1) return
  rankOffset.value += 1
  if (rankOffset.value >= len) {
    // 过渡结束后瞬间回位（前后内容一致，视觉无缝）
    window.setTimeout(() => {
      rankSnap.value = true
      rankOffset.value = 0
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          rankSnap.value = false
        })
      })
    }, 620)
  }
}

/** 启动 / 停止轮播 */
function startRankRotate() {
  stopRankRotate()
  rankTimer = window.setInterval(tickRank, RANK_INTERVAL)
}

function stopRankRotate() {
  if (rankTimer !== null) {
    window.clearInterval(rankTimer)
    rankTimer = null
  }
}

/** 悬停暂停 / 移开恢复 */
function onRankEnter() {
  rankPaused.value = true
  stopRankRotate()
}

function onRankLeave() {
  rankPaused.value = false
  startRankRotate()
}

/** 后端榜单变化时重建 TOP10 */
watch(hotScenic, buildScenicTop, { immediate: true })

/* ------------------------------ 图表实例 ------------------------------ */
const rankEl = ref<HTMLDivElement | null>(null)
const profileEl = ref<HTMLDivElement | null>(null)
const trendEl = ref<HTMLDivElement | null>(null)
let rankChart: echarts.ECharts | null = null
let profileChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const tooltipBase = {
  backgroundColor: 'rgba(6,14,26,0.92)',
  borderColor: 'rgba(212,179,116,0.5)',
  textStyle: { color: '#dce6f2', fontSize: 18 },
}

/** 后台兜底页：目标路由不存在时跳转 */
const METRIC_FALLBACK = '/admin/dashboard'

/** 指标列表（供 MetricItem 使用；to 为对应的后台详情页） */
const metrics = computed(() => [
  { label: '注册用户', value: overview.value.userCount, spark: overview.value.userSpark, color: COLOR_GOLD, to: '/admin/users' },
  { label: '总访问量', value: overview.value.visitCount, spark: overview.value.visitSpark, color: COLOR_CYAN, to: '/admin/behavior' },
  { label: '景点总数', value: overview.value.scenicCount, spark: overview.value.scenicSpark, color: '#e0c07c', to: '/admin/scenic' },
  { label: 'AI 对话数', value: overview.value.aiChatCount, spark: overview.value.chatSpark, color: '#7fdcf0', to: '/admin/ai-chat' },
])

/** 打开新标签页（避免离开大屏）；目标路由不存在时回退到后台仪表盘 */
function openLink(to: string) {
  if (!to) return
  const target = router.resolve(to).matched.length ? to : METRIC_FALLBACK
  window.open(router.resolve(target).href, '_blank', 'noopener')
}

/** 点击指标：高亮 + 打开对应后台详情页 */
function onMetricClick(i: number, to: string) {
  activeMetric.value = activeMetric.value === i ? -1 : i
  openLink(to)
}

/** 点击词云：跳转到景点搜索 */
function onWordPick(word: string) {
  window.open(router.resolve({ path: '/scenic', query: { keyword: word } }).href, '_blank', 'noopener')
}

/** 地图容器（用于把下钻卡定位到点击的市州附近） */
const mapWrapEl = ref<HTMLElement | null>(null)
/** 点击市州在容器内的像素坐标 */
const cityAnchor = ref<{ x: number; y: number } | null>(null)
/** 下钻卡尺寸估算（贴边翻转 / 限位用） */
const CITY_CARD_W = 224
const CITY_CARD_H = 224

/** 选中市州（地图下钻）：记录点击点，供详情卡就近弹出 */
function onSelectCity(city: CityPoint | null, pos?: { x: number; y: number }) {
  selectedCity.value = city
  cityAnchor.value = city && pos ? pos : null
}

/** 下钻卡定位：默认落在点击点右下方，空间不足则翻转并限位在容器内 */
const cityCardStyle = computed<Record<string, string>>(() => {
  const a = cityAnchor.value
  const style: Record<string, string> = {}
  // 未选中市州：固定在容器左下角
  if (!a) {
    style.left = '16px'
    style.bottom = '16px'
    return style
  }
  const w = mapWrapEl.value?.clientWidth ?? 960
  const h = mapWrapEl.value?.clientHeight ?? 800
  let left = a.x + 18
  let top = a.y + 14
  if (left + CITY_CARD_W > w - 8) left = a.x - CITY_CARD_W - 18
  if (top + CITY_CARD_H > h - 8) top = a.y - CITY_CARD_H - 14
  left = Math.max(8, Math.min(left, Math.max(8, w - CITY_CARD_W - 8)))
  top = Math.max(8, Math.min(top, Math.max(8, h - CITY_CARD_H - 8)))
  style.left = `${left}px`
  style.top = `${top}px`
  return style
})

/** 选中市州在全省的排名 / 最大访问量 */
const cityStats = computed(() => {
  const c = selectedCity.value
  if (!c) return null
  const sorted = [...mapData.value].sort((a, b) => b.visitCount - a.visitCount)
  return {
    rank: sorted.findIndex((x) => x.name === c.name) + 1,
    total: sorted.length,
    maxVisit: Math.max(1, ...mapData.value.map((x) => x.visitCount)),
  }
})

/** 选中市州访问量进度条宽度 */
const cityBarWidth = computed(() => {
  const c = selectedCity.value
  if (!c || !cityStats.value) return '0%'
  return `${Math.round((c.visitCount / cityStats.value.maxVisit) * 100)}%`
})

/** 打开选中市州的景点列表 */
function openCityScenic() {
  if (!selectedCity.value) return
  window.open(
    router.resolve({ path: '/scenic', query: { cityName: selectedCity.value.name } }).href,
    '_blank',
    'noopener',
  )
}

/* ------------------------------ 3D 地图融合入口 ------------------------------ */
/** 进入 3D 地图（与数据大屏融合入口） */
function go3d() {
  router.push('/map3d')
}

/* ------------------------------ 图表渲染 ------------------------------ */
function initCharts() {
  // canvas 渲染器（高 DPI，4K 屏更清晰）
  const opt = { renderer: 'canvas' as const, devicePixelRatio: window.devicePixelRatio || 1 }
  if (rankEl.value && !rankChart) rankChart = echarts.init(rankEl.value, undefined, opt)
  if (profileEl.value && !profileChart) profileChart = echarts.init(profileEl.value, undefined, opt)
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value, undefined, opt)

  // 榜单点击 → 新标签打开对应外部详情链接（不离开大屏）
  rankChart?.on('click', (p: any) => {
    // 仅响应柱状图 series 的点击，避免坐标轴 / 组件误触
    if (p?.componentType !== 'series' || p?.seriesType !== 'bar') return
    const name = String(p?.name ?? '')
    if (!name) return
    window.open(resolveRankLink(name), '_blank', 'noopener')
  })

  // hover 单根柱子：暂停该柱波动（其余柱继续）；移开恢复
  rankChart?.on('mouseover', (p: any) => {
    if (p?.componentType === 'series') rankPausedIndex = p.dataIndex ?? -1
  })
  rankChart?.on('mouseout', () => {
    rankPausedIndex = -1
  })
}

/* ------------------------------ 文化热度排行（实时动态波形） ------------------------------ */
/** 各柱的相位偏移：让每根柱子节奏完全不同，各自「呼吸」 */
const RANK_PHASES = [0, 1.2, 2.4, 3.6, 4.8, 6.0]

/** 热度分类（含基准值与权重） */
interface RankCategory {
  name: string
  baseValue: number
  weight: number
}

/** 后端缺失时的兜底分类 */
const RANK_FALLBACK: RankCategory[] = [
  { name: '工艺', baseValue: 99, weight: 1.0 },
  { name: '戏曲', baseValue: 64, weight: 0.7 },
  { name: '饮食', baseValue: 68, weight: 0.7 },
  { name: '历史', baseValue: 69, weight: 0.7 },
  { name: '民俗', baseValue: 60, weight: 0.7 },
  { name: '自然', baseValue: 44, weight: 0.5 },
]

/** 当前分类（按热度降序，索引即柱序） */
let rankCategories: RankCategory[] = []
/** 各类别基准值中的最大值（用于幅度归一） */
let rankMaxBase = 99
/** 每根柱子当前显示值（hover 暂停时冻结该值） */
let rankCurrent: number[] = []
/** 当前悬停暂停的柱子下标（-1 表示无） */
let rankPausedIndex = -1
/** rAF 循环句柄 */
let rankRaf = 0
/** 波动时间变量（按帧推进） */
let rankTimeT = 0
/** 上一帧时间戳（毫秒），用于按真实帧间隔推进时间 */
let rankPrevTs = 0

/** 由基准值推导权重（大数值权重高） */
function rankWeight(base: number): number {
  if (base >= 90) return 1.0
  if (base >= 60) return 0.7
  return 0.5
}

/** 同步分类数据：后端有数据则用真实值（取前 6），否则回退兜底 */
function syncRankCategories(list?: NameValue[]) {
  rankCategories = list && list.length
    ? list.slice(0, 6).map((c) => {
        const base = Math.max(1, Math.round(c.value))
        return { name: c.name, baseValue: base, weight: rankWeight(base) }
      })
    : RANK_FALLBACK.map((c) => ({ ...c }))
  rankMaxBase = Math.max(1, ...rankCategories.map((c) => c.baseValue))
  if (rankCurrent.length !== rankCategories.length) {
    rankCurrent = rankCategories.map((c) => c.baseValue)
  }
}

/**
 * 某根柱子此刻的值：两个正弦波叠加，产生有层次的持续波动
 * - 主波振幅 3.5、副波振幅 1.5（叠加约 ±5）
 * - 幅度按基准值轻度归一：工艺（最大）最明显，自然（最小）稍小
 */
function rankValueAt(i: number, t: number): number {
  const cat = rankCategories[i]
  if (!cat) return 0
  const base = cat.baseValue
  const phase = RANK_PHASES[i] ?? 0
  const k = 0.6 + 0.4 * (base / rankMaxBase)
  const wave1 = Math.sin(t * 1.2 + phase) * 3.5 * k
  const wave2 = Math.sin(t * 2.5 + phase * 1.7) * 1.5 * k
  const val = base + wave1 + wave2
  return Math.max(base - 15, Math.min(100, val))
}

/** 仅更新 series 数据（柱长 + 柱端数据点），不改结构 */
function updateRankSeries(values: number[]) {
  if (!rankChart) return
  rankChart.setOption(
    {
      series: [
        { data: values },
        {
          data: values.map((v, i) => [v, rankCategories[i]?.name ?? '']),
          // 柱端金色小圆点：随波动轻微脉动（半径约 2 ~ 4）
          symbolSize: (_v: any, p: any) =>
            6 + 2 * Math.sin(rankTimeT * 3 + (p?.dataIndex ?? 0) * 1.3),
        },
      ],
    },
    { lazyUpdate: true },
  )
}

/**
 * 逐帧驱动：用 requestAnimationFrame 每帧重算所有柱子的值，
 * 持续流动、永不停止（时间按真实帧间隔推进，与帧率无关）
 */
function rankFrame(ts: number) {
  const dt = rankPrevTs ? Math.min(ts - rankPrevTs, 50) : 16.7
  rankPrevTs = ts
  // 约 0.02/帧 @60fps 的推进速度
  rankTimeT += dt * 0.0012

  const values = rankCategories.map((_, i) => {
    // hover 暂停的柱子：保持当前值不动
    if (i === rankPausedIndex) return rankCurrent[i] ?? rankCategories[i]?.baseValue ?? 0
    const v = rankValueAt(i, rankTimeT)
    rankCurrent[i] = v
    return v
  })

  updateRankSeries(values)
  rankRaf = requestAnimationFrame(rankFrame)
}

/** 启动逐帧波动循环 */
function startRankWave() {
  stopRankWave()
  rankPrevTs = 0
  rankRaf = requestAnimationFrame(rankFrame)
}

/** 停止逐帧波动循环 */
function stopRankWave() {
  if (rankRaf) {
    cancelAnimationFrame(rankRaf)
    rankRaf = 0
  }
}

/** 文化热度：横向金色渐变条（实时波动；柱子可点击，新标签打开外部详情链接） */
function renderRank() {
  if (!rankChart) return
  const names = rankCategories.map((c) => c.name)
  const values = rankCategories.map((c, i) => rankCurrent[i] ?? c.baseValue)
  rankChart.setOption({
    // 完全关闭 ECharts 动画：柱长的持续流动由 requestAnimationFrame 每帧给出
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      ...tooltipBase,
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        if (!p) return ''
        const hint = RANK_LINK_MAP[p.name]
          ? '<br/><span style="color:#d4b374;font-size: 16px">▸ 点击查看详情</span>'
          : ''
        return `${p.marker ?? ''}${p.name}<br/>热度 ${Math.round(p.value)}${hint}`
      },
    },
    grid: { left: 8, right: 54, top: 6, bottom: 6, containLabel: true },
    xAxis: { type: 'value', show: false },
    // inverse：第一项（工艺）显示在最上方
    yAxis: {
      type: 'category',
      inverse: true,
      data: names,
      axisLine: { lineStyle: { color: 'rgba(212,179,116,0.25)' } },
      axisTick: { show: false },
      axisLabel: { color: '#9fb6cf', fontSize: 16 },
    },
    series: [
      {
        type: 'bar',
        // 可点击视觉引导：手型指针
        cursor: 'pointer',
        data: values,
        barWidth: '52%',
        // 常驻可点击提示：数值后常显外链箭头 ↗，让「可点击」一直看得见
        label: {
          show: true,
          position: 'right',
          // 数值取整显示（实际柱长保留小数，持续流动）
          formatter: (p: any) => `{v|${Math.round(p.value)}}{a| ↗}`,
          rich: {
            v: { color: COLOR_GOLD, fontSize: 16 },
            a: { color: COLOR_CYAN, fontSize: 18, fontWeight: 'bold' },
          },
        },
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          // 深金 → 亮金尖端：柱端形成「伸展的尖端感」
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(212,179,116,0.18)' },
            { offset: 0.65, color: '#a67c2e' },
            { offset: 1, color: '#ffe6a0' },
          ]),
        },
        // 悬停高亮：更亮的金色渐变 + 外发光，反馈「可点击」
        emphasis: {
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: 'rgba(212,179,116,0.55)' },
              { offset: 1, color: '#f4e0a6' },
            ]),
            shadowBlur: 12,
            shadowColor: 'rgba(212,179,116,0.75)',
          },
        },
        // 完全关闭补间：位移由 rAF 每帧驱动
        animationDuration: 0,
        animationDurationUpdate: 0,
      },
      {
        // 柱端金色数据点：随柱长同步移动（脉动由逐帧 symbolSize 驱动）
        type: 'scatter',
        coordinateSystem: 'cartesian2d',
        silent: true,
        z: 3,
        animation: false,
        symbolSize: 7,
        itemStyle: { color: '#d4b374', shadowColor: '#d4b374', shadowBlur: 6 },
        data: values.map((v, i) => [v, names[i]]),
      },
    ],
  })
}

/** 文化热度基准刷新：重建分类与波形 */
watch(
  cultureRank,
  (list) => {
    syncRankCategories(list)
    renderRank()
  },
  { immediate: true },
)

/** 用户画像：年龄段环形图，中心总人数 */
function renderProfile() {
  if (!profileChart) return
  profileChart.setOption({
    color: [COLOR_GOLD, COLOR_CYAN, '#7a1a22', '#3a5a40', '#d9c07f'],
    tooltip: { trigger: 'item', ...tooltipBase },
    legend: { bottom: 0, textStyle: { color: '#7a9bbf', fontSize: 15 }, itemWidth: 9, itemHeight: 9 },
    title: {
      text: String(userProfile.value.total),
      subtext: '总人数',
      left: 'center',
      top: '28%',
      textStyle: { color: '#e0c07c', fontSize: 30, fontFamily: "Georgia, 'Songti SC', serif" },
      subtextStyle: { color: '#7a9bbf', fontSize: 15 },
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '64%'],
        center: ['50%', '40%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: 'rgba(6,14,26,0.9)', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, color: '#dce6f2', fontSize: 16, formatter: '{b}\n{d}%' } },
        data: userProfile.value.age,
      },
    ],
  })
}

/* ------------------------------ 访问趋势（实时动态波形 · rAF 连续流动） ------------------------------ */
/** 基准数据（来自后端 /screen/visit-trend） */
const trendBase = ref<number[]>([])
/** 悬停暂停标记（面板副标题提示） */
const trendPaused = ref(false)
/** 波形相位（随帧推进） */
let trendT = 0
/** rAF 句柄 */
let trendRaf = 0
/** 上一帧绘制时间（节流到约 60fps） */
let trendLastDraw = 0

/**
 * 逐帧刷新波形
 *
 * 用「两个不同频率的正弦波叠加」描述每个点的值随时间连续变化，
 * 再加极小随机扰动避免「太完美显得假」；得到的是「流动」而不是「抖动」。
 * 同时关闭 ECharts 自身补间，位移完全由本循环推进，因此不会出现
 * 「动画→停→动画→停」的顿挫感。
 */
function trendFrame(now: number) {
  trendRaf = requestAnimationFrame(trendFrame)
  // 悬停暂停：rAF 循环继续（开销极低），但不推进相位、不重绘
  if (trendPaused.value) return
  // 帧节流：最高约 60fps（高刷屏不做无谓重绘）
  if (now - trendLastDraw < 16) return
  trendLastDraw = now
  trendT += 0.015

  const base = trendBase.value
  if (!base.length || !trendChart) return
  const times = visitTrend.value.map((t) => t.time)
  const last = base.length - 1
  const data = base.map((b, i) => {
    // 两个不同频率正弦叠加：幅度加大到 ±6 左右，保证每个位置都能明显看出在变化
    const wave1 = Math.sin(trendT * 1.5 + i * 0.4) * 3.6
    const wave2 = Math.sin(trendT * 0.7 + i * 1.2) * 2.2
    const noise = (Math.random() - 0.5) * 0.2
    return Math.max(0, Math.min(12, Number((b + wave1 + wave2 + noise).toFixed(2))))
  })

  trendChart.setOption(
    {
      series: [
        { data },
        {
          data: [[times[last], data[last]]],
          // 末端小圆点脉动（半径 3.5 → 5.5）
          symbolSize: 4.5 + Math.sin(trendT * 5),
        },
      ],
    },
    { lazyUpdate: true },
  )
}

/** 启动 / 停止逐帧循环 */
function startTrendWave() {
  if (!trendRaf) trendRaf = requestAnimationFrame(trendFrame)
}

function stopTrendWave() {
  if (trendRaf) {
    cancelAnimationFrame(trendRaf)
    trendRaf = 0
  }
}

/** 悬停暂停 / 移开恢复 */
function onTrendEnter() {
  trendPaused.value = true
}

function onTrendLeave() {
  trendPaused.value = false
}

/** 后端基准刷新：仅更新基准（波形由逐帧计算，不重置，天然平滑过渡） */
watch(
  visitTrend,
  (list) => {
    trendBase.value = (list || []).map((t) => t.value)
    renderTrend()
  },
  { immediate: true },
)

/** 访问趋势：金色发光折线 + 渐变填充 + 末端脉冲点（静态骨架，数据由 rAF 逐帧写入） */
function renderTrend() {
  if (!trendChart) return
  const times = visitTrend.value.map((t) => t.time)
  const base = trendBase.value
  const last = base.length - 1
  trendChart.setOption({
    // 关闭 ECharts 自身动画：位移完全由 rAF 逐帧驱动
    animation: false,
    // 悬停时数据已被冻结，tooltip 不做过渡，避免闪烁
    tooltip: { trigger: 'axis', transitionDuration: 0, ...tooltipBase },
    grid: { left: 6, right: 16, top: 16, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: { show: false },
      axisTick: { show: false },
      // 每 4 小时一个刻度：00:00 / 04:00 / ... / 20:00
      axisLabel: { color: '#6b7280', fontSize: 16, interval: 3 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 12,
      interval: 2,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#6b7280',
        fontSize: 16,
        // 面板高度有限：网格线仍每 2 一条，标签每 4 一个（0/4/8/12）避免相邻标签重叠
        formatter: (v: number) => (v % 4 === 0 ? String(v) : ''),
      },
      // 仅保留横向虚线网格线
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)', type: 'dashed' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: base,
        animation: false,
        animationDuration: 0,
        animationDurationUpdate: 0,
        lineStyle: {
          color: COLOR_GOLD,
          width: 2,
          shadowColor: 'rgba(212,179,116,0.5)',
          shadowBlur: 8,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(212,179,116,0.3)' },
            { offset: 1, color: 'rgba(212,179,116,0)' },
          ]),
        },
      },
      {
        // 末端实时点：脉动由逐帧 symbolSize 驱动
        type: 'scatter',
        coordinateSystem: 'cartesian2d',
        silent: true,
        animation: false,
        z: 3,
        symbolSize: 4.5,
        itemStyle: { color: COLOR_GOLD, shadowColor: COLOR_GOLD, shadowBlur: 8 },
        data: last >= 0 ? [[times[last], base[last]]] : [],
      },
    ],
  })
}

function renderAllCharts() {
  renderRank()
  renderProfile()
  renderTrend()
}

function resizeCharts() {
  rankChart?.resize()
  profileChart?.resize()
  trendChart?.resize()
}

/* ------------------------------ 时钟 / 全屏 ------------------------------ */
function tickClock() {
  const now = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clock.value = `${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`
  dateText.value = `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`
  weekText.value = WEEK[now.getDay()]
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

function checkSize() {
  isSmall.value = window.innerWidth < 1280
}

/* ------------------------------ 实时通道 / 轮询 ------------------------------ */
const { start: startStream } = useScreenStream()

let timers: number[] = []

onMounted(async () => {
  checkSize()
  tickClock()

  // 首屏并发加载
  await store.loadAll()
  startStream()
  // 实时数据流：车站车次屏滚动（每 1 秒推进一格）
  startFeedTimer()
  // 景点 TOP10：每 1.8 秒向上轮播一位
  startRankRotate()

  // 蜀锦 loading 至少展示 2 秒，再播放 stagger 入场
  window.setTimeout(async () => {
    loading.value = false
    await nextTick()
    initCharts()
    renderAllCharts()
    // 访问趋势：rAF 逐帧驱动，连续流动（悬停暂停）
    startTrendWave()
    // 文化热度：每根柱子独立波动（悬停单柱暂停）
    startRankWave()
  }, 2000)

  // 轮询：时钟 1s；指标/地图/趋势/榜单/词云 5s；文化/画像 10s；通知 8s；实时流兜底 30s
  timers = [
    window.setInterval(tickClock, 1000),
    window.setInterval(async () => {
      await store.refreshOverview()
    }, 5000),
    window.setInterval(async () => {
      await store.refreshMapData()
    }, 5000),
    window.setInterval(async () => {
      // 基准数据刷新后由 watch(visitTrend) 重建波形
      await store.refreshVisitTrend()
    }, 5000),
    window.setInterval(async () => {
      await store.refreshHotScenic()
    }, 5000),
    window.setInterval(async () => {
      await store.refreshWordCloud()
    }, 5000),
    window.setInterval(async () => {
      // 基准数据刷新后由 watch(cultureRank) 重建波形
      await store.refreshCultureRank()
    }, 10000),
    window.setInterval(async () => {
      await store.refreshUserProfile()
      renderProfile()
    }, 10000),
    window.setInterval(async () => {
      await store.refreshNotice()
    }, 8000),
    // SSE 断线兜底：定时走一次 REST
    window.setInterval(() => store.refreshRealtime(), 30000),
    window.setInterval(resizeCharts, 1000),
  ]
  window.addEventListener('resize', checkSize)
})

onUnmounted(() => {
  timers.forEach((t) => window.clearInterval(t))
  timers = []
  stopFeedTimer()
  stopRankRotate()
  stopTrendWave()
  stopRankWave()
  window.removeEventListener('resize', checkSize)
  rankChart?.dispose()
  profileChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <div class="ds">
    <!-- 极光氛围背景（缓慢流动的柔和光晕） -->
    <AuroraBackground />

    <!-- 背景：网格 + 光晕 + 粒子 -->
    <div class="ds__grid" />
    <div class="ds__glow" />
    <ParticleField />

    <!-- 蜀锦 loading 动画 -->
    <transition name="ds-loading">
      <div v-if="loading" class="ds__loading">
        <div class="ds__brocade" />
        <p class="ds__loading-text">数据加载中…</p>
      </div>
    </transition>

    <!-- 小屏提示 -->
    <div v-if="isSmall" class="ds__guard">
      <p class="ds__guard-title">请在 1920 × 1080 分辨率下查看</p>
      <p class="ds__guard-sub">当前窗口过小，数据大屏最佳展示尺寸为 1920×1080</p>
    </div>

    <div v-else class="ds__inner" :class="{ 'is-ready': !loading }">
      <!-- 顶部标题栏 -->
      <header class="ds__header">
        <div class="ds__brand">
          <BrandLogo :size="64" />
          <div class="ds__brand-text">
            <b>蜀韵·智游 · 数据中台</b>
            <span class="ds__dots"><i v-for="n in 5" :key="n" /></span>
          </div>
        </div>

        <h1 class="ds__title">巴蜀文旅数据总览</h1>

        <div class="ds__meta">
          <div class="ds__clock">
            <b>{{ clock }}</b>
            <span>{{ dateText }} {{ weekText }}</span>
          </div>
          <div class="ds__status">
            <i class="ds__status-dot" />
            系统运行正常
          </div>
          <button type="button" class="ds__link" title="进入 3D 地图" @click="go3d">
            <span class="ds__link-icon" aria-hidden="true">◈</span> 3D 地图
          </button>
          <button type="button" class="ds__fullscreen" title="全屏" @click="toggleFullscreen">⛶</button>
        </div>
      </header>

      <!-- 主体三栏 -->
      <main class="ds__body">
        <!-- 左栏 -->
        <section class="ds__col ds__col--left">
          <ScreenPanel title="关键指标" class="ds__panel" style="--i: 0">
            <div class="ds__metrics">
              <template v-if="store.loaded">
                <MetricItem
                  v-for="(m, i) in metrics"
                  :key="m.label"
                  :label="m.label"
                  :value="m.value"
                  :spark="m.spark"
                  :color="m.color"
                  :active="activeMetric === i"
                  :clickable="!!m.to"
                  @click="onMetricClick(i, m.to)"
                />
              </template>
            </div>
          </ScreenPanel>

          <ScreenPanel title="文化热度排行以及相关扩展" sub="TOP 8" class="ds__panel" style="--i: 1">
            <template #extra>
              <span class="ds__rank-hint">↗ 点击柱状查看详情</span>
            </template>
            <div ref="rankEl" class="ds__chart" />
          </ScreenPanel>

          <ScreenPanel title="用户画像" sub="年龄段分布" class="ds__panel" style="--i: 2">
            <div ref="profileEl" class="ds__chart ds__chart--profile" />
            <div class="ds__profile-extra">
              <div class="ds__mini-group">
                <span class="ds__mini-title">性别</span>
                <span v-for="g in userProfile.gender" :key="g.name" class="ds__mini-item">
                  {{ g.name }} {{ g.value }}
                </span>
              </div>
              <div class="ds__mini-group">
                <span class="ds__mini-title">地域</span>
                <span v-for="r in userProfile.region" :key="r.name" class="ds__mini-item">
                  {{ r.name }} {{ r.value }}
                </span>
              </div>
            </div>
          </ScreenPanel>
        </section>

        <!-- 中央地图 -->
        <section class="ds__col ds__col--center">
          <div ref="mapWrapEl" class="ds__map-wrap" style="--i: 3">
            <SichuanMap :cities="mapData" :selected="selectedCity?.name ?? null" @select="onSelectCity" />

            <!-- 市州下钻详情卡（就近弹出到点击的市州旁） -->
            <transition name="ds-city">
              <div v-if="selectedCity" class="ds__city" :style="cityCardStyle">
                <button type="button" class="ds__city-close" title="关闭" @click="selectedCity = null">×</button>
                <h4 class="ds__city-name">{{ selectedCity.name }}</h4>
                <div class="ds__city-row">
                  <span>景点数</span><b>{{ selectedCity.scenicCount }} 个</b>
                </div>
                <div class="ds__city-row">
                  <span>访问量</span><b>{{ selectedCity.visitCount.toLocaleString('en-US') }}</b>
                </div>
                <div class="ds__city-row">
                  <span>全省排名</span>
                  <b>第 {{ cityStats?.rank ?? '—' }} / {{ cityStats?.total ?? '—' }}</b>
                </div>
                <div class="ds__city-bar"><i :style="{ width: cityBarWidth }" /></div>
                <button type="button" class="ds__city-link" @click="openCityScenic">查看该市景点 →</button>
              </div>
            </transition>
          </div>
        </section>

        <!-- 右栏 -->
        <section class="ds__col ds__col--right">
          <ScreenPanel
            title="实时数据流"
            :sub="feedPaused ? '已暂停' : '每 1 秒滚动 · 悬停暂停'"
            class="ds__panel"
            style="--i: 0"
          >
            <TransitionGroup
              name="feed"
              tag="div"
              class="ds__stream"
              @mouseenter="onFeedEnter"
              @mouseleave="onFeedLeave"
            >
              <div
                v-for="s in feedItems"
                :key="s.id"
                class="ds__stream-item"
                :class="{ 'is-active': activeStream === s.id }"
                @click="activeStream = s.id"
              >
                <img v-if="s.avatar" class="ds__stream-avatar" :src="s.avatar" :alt="s.username" />
                <span v-else class="ds__stream-avatar ds__stream-avatar--letter">{{ s.initial }}</span>
                <p class="ds__stream-text">
                  <b>{{ s.username }}</b>
                  <em>{{ s.action }}</em>
                  <span>{{ s.target }}</span>
                </p>
                <span class="ds__stream-time">{{ s.time }}</span>
              </div>
            </TransitionGroup>
          </ScreenPanel>

          <ScreenPanel
            title="访问趋势"
            :sub="trendPaused ? '已暂停' : '近 24 小时 · 悬停暂停'"
            class="ds__panel"
            style="--i: 1"
          >
            <template #extra>
              <span class="ds__live"><i class="ds__live-dot" />实时</span>
            </template>
            <div
              ref="trendEl"
              class="ds__chart"
              @mouseenter="onTrendEnter"
              @mouseleave="onTrendLeave"
            />
          </ScreenPanel>

          <ScreenPanel
            title="景点 TOP 10"
            :sub="rankPaused ? '已暂停' : '视频轮播 · 悬停暂停'"
            class="ds__panel"
            style="--i: 2"
          >
            <div class="rank-wrap">
              <p class="rank-tip">点击任意景点查看视频介绍 ▶</p>
              <div class="rank-list" @mouseenter="onRankEnter" @mouseleave="onRankLeave">
                <div
                  class="rank-track"
                  :class="{ 'is-snap': rankSnap }"
                  :style="{ transform: `translateY(${-rankOffset * RANK_STEP}px)` }"
                >
                  <div
                    v-for="(s, i) in rankRows"
                    :key="`${s.name}-${i}`"
                    class="rank-item"
                    @click="playScenicVideo(s.name)"
                  >
                    <span class="rank-no">{{ (i % (scenicTop.length || 1)) + 1 }}</span>
                    <span class="rank-name" :title="s.name">{{ s.name }}</span>
                    <span class="rank-bar"><i :style="{ width: s.pct + '%' }" /></span>
                    <span class="rank-val">{{ s.value.toLocaleString('en-US') }}</span>
                    <button type="button" class="rank-play" @click.stop="playScenicVideo(s.name)">
                      <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
                        <path d="M3 1.5L10 6L3 10.5V1.5Z" />
                      </svg>
                      <span>播放</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </ScreenPanel>

          <ScreenPanel title="AI 对话词云" sub="每 5 秒重排" class="ds__panel" style="--i: 3">
            <WordCloudPanel :words="wordCloud" @pick="onWordPick" />
          </ScreenPanel>
        </section>
      </main>

      <!-- 底部滚动通知 -->
      <footer class="ds__notice">
        <span class="ds__notice-tag">实时快讯</span>
        <div class="ds__notice-track">
          <span class="ds__notice-text">{{ notice.join('　★　') }}</span>
          <span class="ds__notice-text">{{ notice.join('　★　') }}</span>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/screen/styles/screen-tokens' as *;

.ds {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: $screen-bg;
  color: $screen-text;
}

/* 网格背景 */
.ds__grid {
  position: fixed;
  inset: 0;
  z-index: 0;
  opacity: 0.05;
  background-image:
    linear-gradient(rgba(212, 179, 116, 0.6) 1px, transparent 1px),
    linear-gradient(90deg, rgba(212, 179, 116, 0.6) 1px, transparent 1px),
    repeating-linear-gradient(45deg, rgba(212, 179, 116, 0.5) 0 1px, transparent 1px 26px);
  background-size: 60px 60px, 60px 60px, auto;
}

/* 中央光晕（呼吸） */
.ds__glow {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 0;
  width: 120vmin;
  height: 120vmin;
  margin: -60vmin 0 0 -60vmin;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(212, 179, 116, 0.08), transparent 62%);
  animation: glow-breathe 8s ease-in-out infinite;
}

@keyframes glow-breathe {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.06); }
}

/* 蜀锦 loading */
.ds__loading {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 22px;
  align-items: center;
  justify-content: center;
  background: $screen-bg;
}

.ds__brocade {
  width: 96px;
  height: 96px;
  border: 2px solid rgba(212, 179, 116, 0.5);
  border-radius: 12px;
  background-image:
    repeating-linear-gradient(45deg, $screen-gold 0 2px, transparent 2px 16px),
    repeating-linear-gradient(-45deg, $screen-gold 0 2px, transparent 2px 16px);
  animation: brocade-pulse 1.6s ease-in-out infinite;
}

@keyframes brocade-pulse {
  0%, 100% { transform: scale(0.92) rotate(0deg); opacity: 0.55; }
  50% { transform: scale(1.04) rotate(8deg); opacity: 1; }
}

.ds__loading-text {
  font-size: 22px;
  letter-spacing: 0.2em;
  @include screen-glow($screen-gold, 16px);
}

.ds-loading-leave-active {
  transition: opacity 0.6s $screen-ease;
}
.ds-loading-leave-to {
  opacity: 0;
}

.ds__guard {
  position: relative;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  height: 100vh;
  text-align: center;
}

.ds__guard-title {
  font-size: 36px;
  @include screen-glow(#e0c07c, 18px);
}

.ds__guard-sub {
  font-size: 21px;
  color: $screen-text-muted;
}

/* 主容器 */
.ds__inner {
  position: relative;
  z-index: $screen-z-content;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 0 18px 10px;
}

/* 顶部 */
.ds__header {
  display: flex;
  flex: 0 0 80px;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.22);
  background: linear-gradient(180deg, rgba(212, 179, 116, 0.08), transparent);
}

.ds__brand {
  display: flex;
  gap: 12px;
  align-items: center;
}

.ds__brand-text {
  display: flex;
  flex-direction: column;
  gap: 6px;

  b {
    font-size: 22px;
    letter-spacing: 0.08em;
    color: $screen-text;
  }
}

.ds__dots {
  display: flex;
  gap: 5px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $screen-gold;
    opacity: 0.7;
    animation: dot-blink 1.6s ease-in-out infinite;
  }

  i:nth-child(2) { animation-delay: 0.2s; }
  i:nth-child(3) { animation-delay: 0.4s; }
  i:nth-child(4) { animation-delay: 0.6s; }
  i:nth-child(5) { animation-delay: 0.8s; }
}

@keyframes dot-blink {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.ds__title {
  font-family: 'STKaiti', 'KaiTi', serif;
  font-size: 57px;
  font-weight: 900;
  letter-spacing: 0.22em;
  @include screen-glow($screen-gold, 22px);
}

.ds__meta {
  display: flex;
  gap: 18px;
  align-items: center;
}

.ds__clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;

  b {
    font-family: $screen-font-num;
    font-size: 33px;
    letter-spacing: 0.06em;
    color: $screen-cyan;
  }

  span {
    font-size: 18px;
    color: $screen-text-muted;
  }
}

.ds__status {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 5px 12px;
  border: 1px solid rgba(74, 216, 240, 0.4);
  border-radius: 999px;
  font-size: 18px;
  color: #8fe6f7;
}

.ds__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4caf7d;
  box-shadow: 0 0 8px #4caf7d;
  animation: dot-blink 1.4s infinite;
}

.ds__fullscreen {
  width: 34px;
  height: 34px;
  border: 1px solid $screen-border;
  border-radius: 8px;
  background: transparent;
  font-size: 24px;
  color: $screen-gold;
  cursor: pointer;

  &:hover {
    background: rgba(212, 179, 116, 0.14);
  }
}

/* 3D 地图入口按钮（数据大屏 → 3D 地图） */
.ds__link {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border: 1px solid rgba(74, 216, 240, 0.5);
  border-radius: 8px;
  background: rgba(74, 216, 240, 0.08);
  font-size: 20px;
  letter-spacing: 0.08em;
  white-space: nowrap;
  color: $screen-cyan;
  cursor: pointer;
  transition: background-color 0.3s $screen-ease;

  &:hover {
    background: rgba(74, 216, 240, 0.2);
  }
}

.ds__link-icon {
  font-size: 21px;
}

/* 三栏 */
.ds__body {
  display: grid;
  flex: 1;
  grid-template-columns: 25% 50% 25%;
  gap: 14px;
  min-height: 0;
  padding: 14px 0;
}

.ds__col {
  display: grid;
  gap: 14px;
  min-height: 0;
}

.ds__col--left {
  grid-template-rows: 1.15fr 1.15fr 1fr;
}

.ds__col--right {
  /* 景点 TOP 10 需要更高空间展示多行轮播，适度从访问趋势 / 词云借位 */
  grid-template-rows: 1.25fr 0.7fr 1.45fr 0.7fr;
}

.ds__col--center {
  grid-template-rows: 1fr;
}

/* 入场 stagger：仅当 loading 结束后播放 */
.ds__panel,
.ds__map-wrap {
  min-height: 0;
  opacity: 0;
}

.ds__inner.is-ready .ds__panel,
.ds__inner.is-ready .ds__map-wrap {
  animation: ds-in 0.7s $screen-ease both;
  animation-delay: calc(var(--i) * 100ms);
}

@keyframes ds-in {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}

.ds__map-wrap {
  position: relative;
  height: 100%;
}

/* 市州下钻详情卡（位置由 cityCardStyle 就近计算：left/top 或兑底 left/bottom） */
.ds__city {
  position: absolute;
  z-index: 6;
  width: 224px;
  padding: 12px 14px 14px;
  border: 1px solid rgba(212, 179, 116, 0.45);
  border-radius: 6px;
  background: rgba(8, 20, 34, 0.92);
  box-shadow: 0 0 24px rgba(74, 216, 240, 0.18);
  backdrop-filter: blur(8px);
  transition:
    left 0.3s $screen-ease,
    top 0.3s $screen-ease;
}

.ds__city-close {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  color: $screen-text-muted;
  cursor: pointer;

  &:hover {
    color: $screen-gold;
  }
}

.ds__city-name {
  margin-bottom: 8px;
  font-size: 27px;
  letter-spacing: 0.08em;
  @include screen-glow($screen-gold, 12px);
}

.ds__city-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 3px 0;
  font-size: 18px;

  span {
    color: $screen-text-muted;
  }

  b {
    font-family: $screen-font-num;
    font-size: 22px;
    color: $screen-cyan;
  }
}

.ds__city-bar {
  height: 4px;
  margin: 8px 0 10px;
  border-radius: 2px;
  background: rgba(212, 179, 116, 0.15);
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    background: linear-gradient(90deg, rgba(212, 179, 116, 0.35), $screen-gold);
    transition: width 0.6s $screen-ease;
  }
}

.ds__city-link {
  width: 100%;
  /* 字号 / 内边距放大 0.5 倍 */
  padding: calc(6px * 1.5) 0;
  border: 1px solid rgba(74, 216, 240, 0.4);
  border-radius: 4px;
  background: rgba(74, 216, 240, 0.08);
  font-size: calc(12px * 2.25);
  letter-spacing: 0.08em;
  color: #8fe6f7;
  cursor: pointer;

  &:hover {
    background: rgba(74, 216, 240, 0.18);
  }
}

.ds-city-enter-active,
.ds-city-leave-active {
  transition:
    opacity 0.4s $screen-ease,
    transform 0.4s $screen-ease;
}
.ds-city-enter-from,
.ds-city-leave-to {
  opacity: 0;
  transform: translateY(14px);
}

.ds__chart {
  width: 100%;
  height: 100%;
}

.ds__chart--profile {
  height: 66%;
}

/* ---------------- 景点 TOP 10（视频轮播） ---------------- */
.rank-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.rank-tip {
  flex: 0 0 auto;
  margin-bottom: 6px;
  /* 收紧行高，把更多高度留给轮播列表 */
  line-height: 1.2;
  font-size: 18px;
  text-align: center;
  color: rgba(122, 149, 184, 0.9);
}

.rank-list {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  /* 上下边缘渐隐（比纯色遮罩更自然，不会出现色带） */
  -webkit-mask-image: linear-gradient(180deg, transparent 0, #000 18px, #000 calc(100% - 18px), transparent 100%);
  mask-image: linear-gradient(180deg, transparent 0, #000 18px, #000 calc(100% - 18px), transparent 100%);
}

.rank-track {
  will-change: transform;
  transition: transform 0.6s $screen-ease;

  /* 回位瞬间关闭过渡，实现无缝循环 */
  &.is-snap {
    transition: none;
  }
}

.rank-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  margin-bottom: 4px;
  padding: 0 8px 0 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s $screen-ease;

  /* 左侧金色竖条：hover 出现 */
  &::before {
    content: '';
    position: absolute;
    top: 6px;
    bottom: 6px;
    left: 0;
    width: 3px;
    border-radius: 2px;
    background: $screen-gold;
    opacity: 0;
    transition: opacity 0.3s $screen-ease;
  }

  &:hover {
    background: rgba(201, 168, 106, 0.08);

    &::before {
      opacity: 1;
    }

    .rank-name {
      color: $screen-gold;
    }

    .rank-bar > i {
      filter: brightness(1.2);
    }
  }
}

.rank-no {
  flex: 0 0 22px;
  font-size: 21px;
  text-align: center;
  color: #6b7280;
}

.rank-name {
  flex: 0 1 180px;
  max-width: 180px;
  overflow: hidden;
  font-size: 21px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #ffffff;
  transition: color 0.3s $screen-ease;
}

.rank-bar {
  flex: 1 1 auto;
  min-width: 40px;
  height: 10px;
  border-radius: 5px;
  background: rgba(74, 216, 240, 0.1);
  overflow: hidden;

  > i {
    display: block;
    height: 100%;
    border-radius: 5px;
    background: linear-gradient(90deg, rgba(74, 216, 240, 0.3), $screen-cyan);
    transition:
      width 0.6s $screen-ease,
      filter 0.3s $screen-ease;
  }
}

.rank-val {
  flex: 0 0 auto;
  font-size: 20px;
  color: $screen-cyan;
}

.rank-play {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
  padding: 4px 10px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: 4px;
  background: rgba(201, 168, 106, 0.18);
  font-size: 18px;
  letter-spacing: 0.05em;
  color: $screen-gold;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.3s $screen-ease,
    border-color 0.3s $screen-ease,
    transform 0.3s $screen-ease;

  &:hover {
    border-color: $screen-gold;
    background: rgba(201, 168, 106, 0.35);
    transform: scale(1.05);
  }
}

/* 文化热度排行：常驻「可点击」提示胶囊（呼吸高亮，始终可见） */
.ds__rank-hint {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 1px 9px;
  border: 1px solid rgba(74, 216, 240, 0.5);
  border-radius: 999px;
  background: rgba(74, 216, 240, 0.12);
  /* 字号放大 0.5 倍：11px → 16.5px */
  font-size: calc(11px * 2.25);
  letter-spacing: 0.06em;
  white-space: nowrap;
  color: #8fe6f7;
  animation: ds-rank-hint 1.8s ease-in-out infinite;
}

@keyframes ds-rank-hint {
  0%, 100% { opacity: 0.7; box-shadow: 0 0 0 rgba(74, 216, 240, 0); }
  50% { opacity: 1; box-shadow: 0 0 12px rgba(74, 216, 240, 0.55); }
}

/* 访问趋势：右上角「● 实时」标记（绿点每 1 秒闪烁） */
.ds__live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 18px;
  letter-spacing: 0.06em;
  color: $screen-cyan;
}

.ds__live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3ddc84;
  box-shadow: 0 0 8px rgba(61, 220, 132, 0.85);
  animation: ds-live-blink 1s ease-in-out infinite;
}

@keyframes ds-live-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* 关键指标 */
.ds__metrics {
  display: grid;
  grid-template-rows: repeat(4, 1fr);
  /* 收紧行距，保证 4 张卡片在面板可视高度内完整展示 */
  gap: 4px;
  height: 100%;
}

/* 画像附加：性别 / 地域 */
.ds__profile-extra {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.ds__mini-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
  font-size: 16px;
}

.ds__mini-title {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(212, 179, 116, 0.14);
  color: $screen-gold;
}

.ds__mini-item {
  color: $screen-text-muted;
}

/* 实时流 */
.ds__stream {
  /* 供离开条目绝对定位（滑出）参照 */
  position: relative;
  display: flex;
  flex-direction: column;
  /* 收紧行距，保证 6 条在面板可视高度内完整展示 */
  gap: 3px;
  height: 100%;
  overflow: hidden;
}

.ds__stream-item {
  display: flex;
  gap: 10px;
  align-items: center;
  /* 统一行高，避免子项继承 1.8 把行高撑大，保证 6 条完整展示 */
  line-height: 1.4;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(74, 216, 240, 0.05);
  cursor: pointer;
  transition:
    background-color 0.3s $screen-ease,
    box-shadow 0.3s $screen-ease;

  &.is-active {
    background: rgba(212, 179, 116, 0.16);
    box-shadow: inset 0 0 14px rgba(212, 179, 116, 0.18);
  }
}

.ds__stream-avatar {
  width: 24px;
  height: 24px;
  border: 1px solid $screen-border;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.ds__stream-avatar--letter {
  display: inline-grid;
  place-items: center;
  background: rgba(212, 179, 116, 0.16);
  /* 字号随视口自适应，避免大屏上过小 */
  font-size: clamp(20px, 1.125vw, 24px);
  font-weight: 700;
  color: $screen-gold;
}

.ds__stream-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  /* 字号随视口自适应（大屏不过小），行高收紧以保证 6 条仍能完整展示 */
  font-size: clamp(21px, 1.2vw, 26px);
  line-height: 1.4;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: $screen-text;

  b {
    color: $screen-gold;
  }

  em {
    margin: 0 6px;
    font-style: normal;
    color: $screen-cyan;
  }

  span {
    color: $screen-text-muted;
  }
}

.ds__stream-time {
  font-size: clamp(20px, 1.125vw, 24px);
  color: $screen-text-dim;
}

/* 车站车次屏：新条目由上滑入 → 已有条目下移 → 最底部向下滑出 */
.feed-enter-active {
  transition:
    opacity 0.5s $screen-ease,
    transform 0.5s $screen-ease;
}

.feed-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.feed-leave-active {
  position: absolute;
  width: 100%;
  transition:
    opacity 0.5s $screen-ease,
    transform 0.5s $screen-ease;
}

.feed-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

.feed-move {
  transition: transform 0.5s $screen-ease;
}

/* 底部通知 */
.ds__notice {
  display: flex;
  flex: 0 0 52px;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  border: 1px solid rgba(212, 179, 116, 0.22);
  border-radius: 6px;
  background: rgba(14, 42, 71, 0.55);
  overflow: hidden;
}

.ds__notice-tag {
  flex: 0 0 auto;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(212, 179, 116, 0.16);
  font-size: 18px;
  color: $screen-gold;
}

.ds__notice-track {
  display: flex;
  gap: 60px;
  white-space: nowrap;
  animation: notice-scroll 40s linear infinite;
}

.ds__notice-text {
  font-size: 20px;
  letter-spacing: 0.04em;
  color: $screen-text;
}

@keyframes notice-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

/* 响应式 */
@media (max-width: 1920px) {
  .ds__title { font-size: 45px; }
}

@media (max-width: 1600px) {
  .ds__title { letter-spacing: 0.14em; }
}
</style>
