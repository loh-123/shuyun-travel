<script setup lang="ts">
/**
 * 3D 四川数字地图页面（Map3D）· 《本草纲目》式科技数据大屏
 *
 * 布局：三栏 flex（左 380 / 中 flex / 右 380），纵向 gap 16，绝不重叠。
 * 风格：深蓝径向渐变背景 + 网格 + 四角角标 + 切角科技框架。
 *
 * 数据：基准来自 /api/screen/overview、/screen/real-time、/scenic/hot；
 *       数字由前端定时器模拟增量（上滑更新 + 青蓝高亮）。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Map3DScene from '@/components/map/Map3DScene.vue'
import CityInfoPanel from '@/components/map/CityInfoPanel.vue'
import KnowledgesPanel from '@/components/map/KnowledgesPanel.vue'
import TechFrame from '@/components/map/TechFrame.vue'
import MetricValue from '@/components/map/MetricValue.vue'
import HeritageLinksPanel from '@/components/map/HeritageLinksPanel.vue'
import WeatherHistory from '@/components/screen/WeatherHistory.vue'
import AuroraBackground from '@/components/common/AuroraBackground.vue'
import { getMapData, getRealTime, getScreenOverview, type MapCityPoint, type ScreenOverview } from '@/api/map3d'

const router = useRouter()
const sceneRef = ref<InstanceType<typeof Map3DScene> | null>(null)

const cities = ref<MapCityPoint[]>([])
const selected = ref<MapCityPoint | null>(null)
const panelOpen = ref(false)
const azimuth = ref(0)
const compassRot = computed(() => -(azimuth.value * 180) / Math.PI)

const webglOk = ref(true)
const dataReady = ref(false)
const failed = ref(false)
const overview = ref<ScreenOverview | null>(null)

/* 原「温度图例」已由左栏底部的「四川历史天气」面板（WeatherHistory）替代 */

/* ------------------------------ 总览 ------------------------------ */
const cityCount = computed(() => cities.value.length || 21)
const totalScenic = computed(
  () => overview.value?.scenicCount || cities.value.reduce((s, c) => s + (c.scenicCount || 0), 0),
)
const totalFood = computed(() => overview.value?.foodCount || cities.value.reduce((s, c) => s + (c.foodCount || 0), 0))
const totalHeritage = computed(
  () => overview.value?.heritageCount || cities.value.reduce((s, c) => s + (c.heritageCount || 0), 0),
)
const totalCulture = computed(() => overview.value?.cultureCount || 8)
const totalVisitor = computed(() => formatWan(overview.value?.viewSum || 0))

const summary = computed(() => [
  [
    { n: cityCount.value, t: '市州' },
    { n: totalScenic.value, t: '景点' },
  ],
  [
    { n: totalFood.value, t: '美食' },
    { n: totalCulture.value, t: '文化' },
  ],
  [
    { n: totalHeritage.value, t: '非遗' },
    { n: totalVisitor.value, t: '游客' },
  ],
])

/* ------------------------------ 底部滚动栏 ------------------------------ */
const tickerItems = computed(() =>
  [...cities.value]
    .sort((a, b) => (b.scenicCount || 0) - (a.scenicCount || 0))
    .map((c) => ({
      isHot: c.name === '成都',
      text: `${c.name} ${c.scenicCount ?? 0} 景点 · ${c.foodCount ?? 0} 美食 · ${c.heritageCount ?? 0} 非遗`,
    })),
)
const tickerLooped = computed(() => [...tickerItems.value, ...tickerItems.value])

function formatWan(n: number): string {
  return n >= 10000 ? `${(n / 10000).toFixed(1)} 万` : String(n)
}

/* ------------------------------ 数据窗口（模拟增量） ------------------------------ */
const live = reactive({ visits: 0, online: 0, chats: 0, heritageTotal: 100, heirs: 27, weekNew: 16 })
const logs = ref<Array<{ id: number; user: string; action: string; target: string }>>([])
const flash = reactive<Record<string, boolean>>({})
let logSeed = 0
let timers: number[] = []

const USERS = ['张三', '李四', '王五', '赵六', '钱七', '孙八', '吴十', '郑一', '周九', '冯二']
const ACTIONS = ['浏览了', '收藏了', '咨询了', '规划了', '点赞了']
const TARGETS = ['都江堰', '九寨沟', '峨眉山', '乐山大佛', '三星堆', '稻城亚丁', '西岭雪山', '青城山', '武侯祠', '杜甫草堂']
const TRIPS = ['成都三日游', '川西环线', '蜀道寻踪']

const liveRows = computed(() => [
  { label: '今日访问量', value: fmt(live.visits), flash: flash.visits },
  { label: '实时在线用户', value: live.online, flash: flash.online },
  { label: '今日 AI 对话数', value: fmt(live.chats), flash: flash.chats },
])

function rand(min: number, max: number) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}
function pick<T>(arr: T[]): T {
  return arr[rand(0, arr.length - 1)]
}
function fmt(n: number) {
  return n.toLocaleString('en-US')
}
function flashKey(key: string) {
  flash[key] = true
  window.setTimeout(() => {
    flash[key] = false
  }, 300)
}
function makeLog() {
  const action = pick(ACTIONS)
  const target = action === '规划了' ? pick(TRIPS) : pick(TARGETS)
  return { id: ++logSeed, user: pick(USERS), action, target }
}

async function loadLiveBase() {
  try {
    const ov = overview.value ?? (await getScreenOverview())
    if (ov) {
      live.visits = ov.viewSum || 0
      live.chats = ov.aiChatCount || 0
      live.heritageTotal = ov.heritageCount || 100
    }
  } catch {
    live.visits = 332000
  }
  live.online = rand(80, 150)

  try {
    const rt = await getRealTime(6)
    logs.value = (rt ?? []).map((s) => ({
      id: ++logSeed,
      user: s.username ?? '用户',
      action: s.action ?? '浏览了',
      target: s.target ?? '景点',
    }))
  } catch {
    logs.value = []
  }
  while (logs.value.length < 5) {
    logs.value.push(makeLog())
  }
}

function startTimers() {
  // 1s：访问量 +1~+5；在线用户 80~150
  timers.push(
    window.setInterval(() => {
      live.visits += rand(1, 5)
      flashKey('visits')
      live.online = rand(80, 150)
      flashKey('online')
    }, 1000),
  )
  // 2s：AI 对话 +1；新增一条行为
  timers.push(
    window.setInterval(() => {
      live.chats += 1
      flashKey('chats')
      logs.value = [makeLog(), ...logs.value].slice(0, 6)
    }, 2000),
  )
  // 30s：传承人 ±1
  timers.push(
    window.setInterval(() => {
      if (Math.random() < 0.5) {
        live.heirs = Math.max(0, live.heirs + (Math.random() < 0.5 ? 1 : -1))
        flashKey('heritage')
      }
    }, 30000),
  )
  // 4s：非遗总数偶发 ±1；本周新增缓慢 +1
  timers.push(
    window.setInterval(() => {
      if (Math.random() < 0.3) {
        live.heritageTotal = Math.max(0, live.heritageTotal + (Math.random() < 0.5 ? 1 : -1))
      }
      if (Math.random() < 0.5) {
        live.weekNew += 1
      }
      flashKey('heritage')
    }, 4000),
  )
}

function stopTimers() {
  timers.forEach((t) => window.clearInterval(t))
  timers = []
}

/* ------------------------------ 生命周期 ------------------------------ */
function detectWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    return !!gl
  } catch {
    return false
  }
}

onMounted(async () => {
  webglOk.value = detectWebGL()
  try {
    const data = await getMapData()
    cities.value = Array.isArray(data) ? data : []
  } catch {
    failed.value = true
  } finally {
    dataReady.value = true
  }
  await loadOverview()
  await loadLiveBase()
  startTimers()
})

onBeforeUnmount(stopTimers)

async function loadOverview() {
  try {
    overview.value = await getScreenOverview()
  } catch {
    overview.value = null
  }
}

function onCitySelect(city: MapCityPoint) {
  selected.value = city
  panelOpen.value = true
}
/** 点击市州名称标签 / 面板按钮：进入该市州的城市 3D 页面 */
function openCity3D(city: MapCityPoint | null) {
  if (city) {
    router.push(`/city/${city.name}`)
  }
}
/** 进入数据大屏（与 3D 地图融合入口） */
function goScreen() {
  router.push('/screen')
}
function resetView() {
  sceneRef.value?.resetView()
}
</script>

<template>
  <div class="map3d">
    <!-- 极光氛围背景（缓慢流动的柔和光晕） -->
    <AuroraBackground />

    <!-- 背景装饰：网格 + 四角角标 + 顶部横条 -->
    <div class="map3d__grid" aria-hidden="true" />
    <i class="brk brk--tl" aria-hidden="true" />
    <i class="brk brk--tr" aria-hidden="true" />
    <i class="brk brk--bl" aria-hidden="true" />
    <i class="brk brk--br" aria-hidden="true" />
    <div class="map3d__topbar" aria-hidden="true" />

    <div class="stage">
      <!-- ============ 左栏 ============ -->
      <aside class="col col--left">
        <header class="brand">
          <h1>四川数字地图</h1>
          <span class="brand__en">SHUYUN · 3D MAP</span>
          <p>21 市州 · 立体热度 · 点击市州查看详情</p>
        </header>

        <TechFrame title="四川文旅实时概况">
          <ul class="kv">
            <li v-for="row in liveRows" :key="row.label">
              <span>{{ row.label }}</span>
              <MetricValue :value="row.value" :flash="row.flash" :size="42" />
            </li>
          </ul>

          <!-- 总览（已并入本卡片，与原“四川文旅总览”合并） -->
          <div class="ov">
            <div v-for="(row, ri) in summary" :key="ri" class="ov__row">
              <span v-for="(it, ci) in row" :key="ci" class="ov__item">
                <b>{{ it.n }}</b><em>{{ it.t }}</em>
              </span>
            </div>
          </div>
        </TechFrame>

        <!-- 实时用户行为（与右栏互换位置） -->
        <TechFrame title="实时用户行为">
          <transition-group name="list" tag="ul" class="logs">
            <li v-for="l in logs" :key="l.id">
              <i>{{ l.user }}</i>
              <b>{{ l.action }}</b>
              <em>{{ l.target }}</em>
            </li>
          </transition-group>
        </TechFrame>
      </aside>

      <!-- ============ 中栏：3D 地图 ============ -->
      <main class="col col--center">
        <Map3DScene
          v-if="webglOk && dataReady"
          ref="sceneRef"
          :cities="cities"
          @city-select="onCitySelect"
          @city-open="openCity3D"
          @rotate="azimuth = $event"
        />
        <div v-else-if="!webglOk" class="map-fallback">
          <b>当前浏览器不支持 3D 渲染</b>
          <p>请升级到较新的 Chrome / Edge / Firefox 后再访问本页。</p>
        </div>
        <div v-else class="map-fallback"><b>正在加载地图数据…</b></div>

        <div v-if="webglOk" class="map-tools">
          <button type="button" class="map-tools__btn map-tools__btn--screen" @click="goScreen">数据大屏</button>
          <button type="button" class="map-tools__btn" @click="resetView">复位视角</button>
          <div class="compass" :style="{ transform: `rotate(${compassRot}deg)` }" title="指南针">
            <svg viewBox="0 0 76 76" width="72" height="72">
              <circle cx="38" cy="38" r="34" fill="rgba(15,37,64,0.85)" stroke="rgba(58,140,220,0.5)" stroke-width="1.5" />
              <path d="M38 10 L43 38 L38 44 L33 38 Z" fill="#4ad8f0" />
              <path d="M38 66 L43 38 L38 32 L33 38 Z" fill="#7a95b8" opacity="0.6" />
              <text x="38" y="21" fill="#7a95b8" font-size="12" font-family="monospace" text-anchor="middle">N</text>
              <text x="38" y="71" fill="#7a95b8" font-size="10" font-family="monospace" text-anchor="middle">S</text>
              <text x="11" y="42" fill="#7a95b8" font-size="10" font-family="monospace" text-anchor="middle">W</text>
              <text x="65" y="42" fill="#7a95b8" font-size="10" font-family="monospace" text-anchor="middle">E</text>
            </svg>
          </div>
        </div>

        <div v-if="failed" class="map-toast">地图数据加载失败，请确认后端 /api/screen/map-data 可用</div>
      </main>

      <!-- ============ 右栏 ============ -->
      <aside class="col col--right">
        <KnowledgesPanel />

        <!-- 四川历史天气（接入 Open-Meteo 真实数据；与左栏互换位置） -->
        <WeatherHistory />

        <!-- 非遗扩展数据链接总览（3 行轮转，点击跳转外部网页） -->
        <HeritageLinksPanel />
      </aside>
    </div>

    <!-- 底部数据滚动栏 -->
    <div v-if="webglOk && dataReady" class="ticker">
      <div class="ticker__track">
        <span v-for="(t, i) in tickerLooped" :key="i" class="ticker__item">
          <i class="ticker__dot" :class="{ 'is-hot': t.isHot }" />
          {{ t.text }}
        </span>
      </div>
    </div>

    <CityInfoPanel :city="selected" :visible="panelOpen" @close="panelOpen = false" @enter="openCity3D(selected)" />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

$blue: #4ad8f0;
$line: rgba(58, 140, 220, 0.4);
$muted: #7a95b8;
$gold: #d4b374;
$red: #c62828;

.map3d {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: calc(100vh - #{$nav-height});
  overflow: hidden;
  background: radial-gradient(120% 100% at 50% 38%, #0f2540 0%, #071426 100%);
}

/* 背景网格 */
.map3d__grid {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image:
    linear-gradient(rgba(58, 140, 220, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(58, 140, 220, 0.05) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

/* 四角角标 */
.brk {
  position: absolute;
  z-index: 1;
  width: 40px;
  height: 40px;
  border: 0 solid rgba(58, 140, 220, 0.5);
  pointer-events: none;

  &--tl {
    top: 12px;
    left: 12px;
    border-top-width: 2px;
    border-left-width: 2px;
  }

  &--tr {
    top: 12px;
    right: 12px;
    border-top-width: 2px;
    border-right-width: 2px;
  }

  &--bl {
    bottom: 84px;
    left: 12px;
    border-bottom-width: 2px;
    border-left-width: 2px;
  }

  &--br {
    right: 12px;
    bottom: 84px;
    border-right-width: 2px;
    border-bottom-width: 2px;
  }
}

/* 顶部中间横条 */
.map3d__topbar {
  position: absolute;
  top: 12px;
  left: 50%;
  z-index: 1;
  width: 200px;
  height: 4px;
  background: linear-gradient(90deg, transparent, $blue, transparent);
  transform: translateX(-50%);
  pointer-events: none;
}

/* ---------------- 三栏 ---------------- */
.stage {
  position: relative;
  z-index: 2;
  display: flex;
  flex: 1;
  gap: 10px;
  min-height: 0;
  padding: 14px 20px 12px;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.col--left,
.col--right {
  flex: none;
  width: 440px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-color: rgba(74, 216, 240, 0.4) transparent;
  scrollbar-width: thin;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    border-radius: 3px;
    background: rgba(74, 216, 240, 0.4);
  }
}

.col--center {
  position: relative;
  flex: 1;
  min-width: 0;
}

/* ---------------- 标题区 ---------------- */
.brand {
  flex: none;
}

.brand h1 {
  font-family: $font-title;
  font-size: 64px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: 0.02em;
  color: #ffffff;
  white-space: nowrap;
}

.brand__en {
  display: block;
  margin-top: 6px;
  font-family: $font-mono;
  font-size: 18px;
  letter-spacing: 0.2em;
  color: $muted;
}

.brand p {
  margin-top: 8px;
  font-size: 20px;
  color: $muted;
}

/* ---------------- 窗口 1：实时概况 ---------------- */
.kv {
  display: flex;
  flex-direction: column;
}

.kv li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 48px;

  & + li {
    border-top: 1px dashed rgba(58, 140, 220, 0.1);
  }

  span {
    font-size: 20px;
    color: $muted;
  }
}

/* ---------------- 窗口 2：总览（已并入实时概况卡片） ---------------- */
.ov {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(58, 140, 220, 0.18);
}

.ov__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;

  & + .ov__row {
    margin-top: 6px;
  }
}

.ov__item {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;

  b {
    font-family: $font-mono;
    font-size: 36px;
    font-weight: 500;
    color: #ffffff;
  }

  em {
    font-size: 20px;
    font-style: normal;
    color: $muted;
  }
}

/* ---------------- 窗口 3：四川历史天气 ---------------- */
/* 样式随 WeatherHistory 组件一同维护（@/components/screen/WeatherHistory.vue） */

/* ---------------- 窗口 5：行为日志 ---------------- */
.logs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.logs li {
  display: flex;
  gap: 8px;
  overflow: hidden;
  font-size: 20px;
  white-space: nowrap;

  i {
    flex: none;
    font-style: normal;
    color: $muted;
  }

  b {
    flex: none;
    font-weight: 400;
    color: #ffffff;
  }

  em {
    overflow: hidden;
    font-style: normal;
    color: $blue;
    text-overflow: ellipsis;
  }
}

/* 列表平滑过渡：expo-out 缓动，更高级顺滑 */
.list-enter-active,
.list-leave-active {
  transition:
    opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1),
    transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.list-enter-from {
  opacity: 0;
  transform: translateY(22px);
}

.list-leave-to {
  opacity: 0;
  transform: translateY(-28px);
}

.list-move {
  transition: transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

/* ---------------- 窗口 6：非遗扩展数据链接总览 ---------------- */
/* 样式随 HeritageLinksPanel 组件一同维护（@/components/map/HeritageLinksPanel.vue） */

/* ---------------- 中栏：地图工具 ---------------- */
.map-fallback {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 3;
  padding: 24px 32px;
  border: 1px solid $line;
  background: #0f2540;
  text-align: center;
  transform: translate(-50%, -50%);

  b {
    font-family: $font-mono;
    font-size: 24px;
    color: $red;
  }

  p {
    margin-top: 10px;
    font-size: 20px;
    color: $muted;
  }
}

.map-tools {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 12px;
  pointer-events: none;
}

.map-tools__btn {
  padding: 12px 24px;
  border: 1px solid $line;
  background: rgba(15, 37, 64, 0.85);
  font-family: $font-mono;
  font-size: 20px;
  color: #ffffff;
  cursor: pointer;
  pointer-events: auto;
  transition: border-color 0.3s $ease-out-expo;

  &:hover {
    border-color: $blue;
  }
}

/* 数据大屏入口按钮（3D 地图 → 数据大屏） */
.map-tools__btn--screen {
  border-color: $gold;
  color: #ffe0a3;

  &:hover {
    border-color: $gold;
    background: rgba(212, 179, 116, 0.18);
  }
}

.compass {
  width: 72px;
  height: 72px;
  transition: transform 0.15s linear;
  will-change: transform;
}

.map-toast {
  position: absolute;
  bottom: 16px;
  left: 50%;
  z-index: 5;
  padding: 10px 16px;
  border: 1px solid $red;
  background: rgba(15, 37, 64, 0.92);
  font-family: $font-mono;
  font-size: 20px;
  color: #ff8a8a;
  transform: translateX(-50%);
}

/* ---------------- 底部滚动栏 ---------------- */
.ticker {
  position: relative;
  z-index: 2;
  flex: none;
  height: 84px;
  overflow: hidden;
  border-top: 1px solid $line;
  background: rgba(15, 37, 64, 0.92);

  &:hover .ticker__track {
    animation-play-state: paused;
  }
}

.ticker__track {
  display: inline-flex;
  align-items: center;
  height: 100%;
  white-space: nowrap;
  animation: ticker-scroll 36s linear infinite;
  will-change: transform;
}

.ticker__item {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-right: 120px;
  font-family: $font-mono;
  font-size: 27px;
  font-weight: 500;
  color: $muted;
}

.ticker__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $blue;

  &.is-hot {
    background: $red;
  }
}

@keyframes ticker-scroll {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-50%);
  }
}
</style>
