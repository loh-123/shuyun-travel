<script setup lang="ts">
/**
 * 城市 3D 全景（City3D）· 高德地图 JS API 2.0
 *
 * 入口：省级 3D 地图（/map3d）点击市州名称标签 / 右侧面板按钮 → /city/:cityName
 *
 * 能力：
 * - 高德 3D 地图（倾斜视角 + 3D 建筑 + 深色底图 darkblue），自动定位到该市州；
 * - 叠加 DataV 区县 GeoJSON：青蓝边界线 + 淡填充，每个区县中心加金色圆点；
 * - 叠加本站 POI：景点（金）/ 美食（红），点击弹出信息窗体；
 * - 点击区县（面或中心点）→ 右侧滑出面板，展示该区景点 / 美食 / 非遗。
 */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDistrictStats, type DistrictStats } from '@/api/city'
import { listAllCities, type CityOption } from '@/api/scenic'
import { getMapData } from '@/api/map3d'

/* ------------------------------ 高德全局声明 ------------------------------ */
interface AMapLoaderType {
  load: (options: Record<string, unknown>) => Promise<any>
}
declare global {
  interface Window {
    AMapLoader?: AMapLoaderType
    _AMapSecurityConfig?: { securityJsCode: string }
    AMap?: any
    /** POI 信息窗体「查看详情」回调（由本组件注入） */
    __cityPoiGo?: (id: number) => void
  }
}

/** 高德 Web端(JS API) Key：优先读环境变量 VITE_AMAP_KEY，否则用内置值 */
const AMAP_KEY =
  ((import.meta as unknown as { env?: Record<string, string> }).env?.VITE_AMAP_KEY as string) ||
  'c06e5381b5e6e720153a6d5dec4d9f5f'

const route = useRoute()
const router = useRouter()

/** 当前市州名（路由参数，如「成都」） */
const cityName = computed(() => String(route.params.cityName ?? ''))

/* ------------------------------ 页面状态 ------------------------------ */
const loading = ref(true)
const errorMsg = ref('')
const showDistrict = ref(true)
const showPoi = ref(true)

/** 顶部数据总览 */
const overview = ref({ scenic: 0, food: 0, heritage: 0 })
const cityIntro = ref('')

/* ------------------------------ 面板状态 ------------------------------ */
const panelOpen = ref(false)
const panelDistrict = ref('')
const panelLoading = ref(false)
const stats = ref<DistrictStats | null>(null)
const activeTab = ref<'scenic' | 'food' | 'heritage'>('scenic')

/* ------------------------------ 高德对象（非响应式，避免深层代理） ------------------------------ */
let AMap: any = null
const map = shallowRef<any>(null)
let districtLayer: any = null
let poiGroup: any = null
let infoWindow: any = null
let districtCenters: any[] = []
let poiMarkers: any[] = []
/** POI「查看详情」回调表 */
const poiHandlers = new Map<number, () => void>()
let poiSeq = 0

/* ------------------------------ 常量 ------------------------------ */
const COLOR_LINE = '#4AD8F0'
const COLOR_GOLD = '#D4B374'
const COLOR_FOOD = '#FF5A5F'
const ZOOM = 13
const PITCH = 60
const ZOOM_RANGE: [number, number] = [10, 18]

/** 市州中心坐标兜底（接口异常时使用） */
const CITY_COORDS: Record<string, [number, number]> = {
  成都: [104.06, 30.67], 绵阳: [104.68, 31.47], 德阳: [104.4, 31.13], 自贡: [104.78, 29.34],
  攀枝花: [101.72, 26.58], 泸州: [105.44, 28.87], 广元: [105.84, 32.44], 遂宁: [105.57, 30.53],
  内江: [105.06, 29.58], 乐山: [103.76, 29.55], 南充: [106.08, 30.8], 眉山: [103.85, 30.05],
  宜宾: [104.63, 28.77], 广安: [106.63, 30.46], 达州: [107.5, 31.21], 雅安: [103.0, 29.98],
  巴中: [106.75, 31.87], 资阳: [104.63, 30.12], 阿坝: [102.22, 31.9], 甘孜: [101.96, 30.05],
  凉山: [102.27, 27.88],
}

/** 市州行政区划代码兜底（取区县 GeoJSON 用） */
const CITY_ADCODE: Record<string, string> = {
  成都: '510100', 绵阳: '510700', 德阳: '510600', 自贡: '510300', 攀枝花: '510400',
  泸州: '510500', 广元: '510800', 遂宁: '510900', 内江: '511000', 乐山: '511100',
  南充: '511300', 眉山: '511400', 宜宾: '511500', 广安: '511600', 达州: '511700',
  雅安: '511800', 巴中: '511900', 资阳: '512000', 阿坝: '513200', 甘孜: '513300',
  凉山: '513400',
}

/* ------------------------------ 工具函数 ------------------------------ */
/** 转数字（兼容字符串 / null） */
function toNum(v: unknown): number | null {
  const n = typeof v === 'number' ? v : parseFloat(String(v ?? ''))
  return Number.isFinite(n) ? n : null
}

/** 加载高德 SDK（loader.js 由 index.html 引入） */
async function ensureAMap(): Promise<any> {
  if (window.AMap) {
    return window.AMap
  }
  await new Promise<void>((resolve, reject) => {
    let tries = 0
    const timer = window.setInterval(() => {
      if (window.AMapLoader) {
        window.clearInterval(timer)
        resolve()
      } else if (++tries > 120) {
        window.clearInterval(timer)
        reject(new Error('高德地图 loader.js 未加载，请检查网络或 index.html'))
      }
    }, 50)
  })
  return window.AMapLoader!.load({
    key: AMAP_KEY,
    version: '2.0',
    plugins: ['AMap.GeoJSON', 'AMap.Marker', 'AMap.InfoWindow'],
  })
}

/** 解析市州中心坐标 */
async function resolveCenter(): Promise<[number, number]> {
  try {
    const data = await getMapData()
    const hit = data.find((c) => c.name === cityName.value)
    if (hit) {
      const lng = toNum(hit.lng)
      const lat = toNum(hit.lat)
      if (lng != null && lat != null) {
        return [lng, lat]
      }
    }
  } catch {
    // 接口异常时走兜底
  }
  return CITY_COORDS[cityName.value] ?? CITY_COORDS['成都']
}

/** 解析市州行政区划代码 */
async function resolveAdcode(): Promise<string> {
  try {
    const cities: CityOption[] = await listAllCities()
    const hit = cities.find((c) => c.name === cityName.value)
    if (hit?.code) {
      return hit.code
    }
  } catch {
    // 忽略
  }
  return CITY_ADCODE[cityName.value] ?? CITY_ADCODE['成都']
}

/* ------------------------------ 地图初始化 ------------------------------ */
function initMap(center: [number, number]) {
  map.value = new AMap.Map('city3d-map', {
    viewMode: '3D',
    zoom: ZOOM,
    zooms: ZOOM_RANGE,
    pitch: PITCH,
    rotation: 0,
    center,
    mapStyle: 'amap://styles/darkblue',
    showBuildingBlock: true,
    features: ['bg', 'road', 'building', 'point'],
    resizeEnable: true,
    rotateEnable: true,
    pitchEnable: true,
  })
  infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -14) })
}

/* ------------------------------ 区县边界 + 中心点 ------------------------------ */
async function renderDistricts(adcode: string) {
  const url = 'https://geo.datav.aliyun.com/areas_v3/bound/' + adcode + '_full.json'
  let geojson: any = null
  try {
    const res = await fetch(url)
    if (res.ok) {
      geojson = await res.json()
    }
  } catch {
    geojson = null
  }
  if (!geojson || !Array.isArray(geojson.features)) {
    return
  }

  districtLayer = new AMap.GeoJSON({
    geoJSON: geojson,
    getPolygon: (_feature: any, lnglats: any) =>
      new AMap.Polygon({
        path: lnglats,
        strokeColor: COLOR_LINE,
        strokeWeight: 1.5,
        strokeOpacity: 0.85,
        fillColor: COLOR_LINE,
        fillOpacity: 0.05,
      }),
  })
  map.value.add(districtLayer)

  // 面点击 → 打开面板
  districtLayer.eachOverlay((overlay: any) => {
    const feature = overlay.getExtData()
    const name = feature?.properties?.name
    if (name) {
      overlay.on('click', () => openDistrict(name))
    }
  })

  // 区县中心金色圆点（直径 12px）
  districtCenters = []
  for (const feature of geojson.features) {
    const name = feature?.properties?.name
    const c = feature?.properties?.center || feature?.properties?.centroid
    if (!name || !Array.isArray(c)) {
      continue
    }
    const marker = new AMap.CircleMarker({
      center: c,
      radius: 6,
      strokeColor: '#ffffff',
      strokeWeight: 1,
      strokeOpacity: 0.9,
      fillColor: COLOR_GOLD,
      fillOpacity: 1,
      zIndex: 120,
      cursor: 'pointer',
    })
    marker.on('click', () => openDistrict(name))
    districtCenters.push(marker)
  }
  map.value.add(districtCenters)
}

/* ------------------------------ POI 标记 ------------------------------ */
/** 渲染景点 / 美食 POI（数据来自公开的 district-stats 全量，含坐标；非遗无坐标不落点） */
async function renderPois() {
  poiGroup = new AMap.OverlayGroup()
  let all: DistrictStats | null = null
  try {
    all = await getDistrictStats(cityName.value)
  } catch {
    all = null
  }
  const scenics = all?.scenics ?? []
  const foods = all?.foods ?? []

  scenics.forEach((s) =>
    addPoiMarker(
      s.name,
      s.type || '景点',
      toNum(s.longitude),
      toNum(s.latitude),
      COLOR_GOLD,
      s.coverImage,
      () => router.push('/scenic/' + s.id),
    ),
  )
  foods.forEach((f) =>
    addPoiMarker(
      f.name,
      f.shopName || f.category || '美食',
      toNum(f.longitude),
      toNum(f.latitude),
      COLOR_FOOD,
      f.coverImage,
      () => router.push('/food/' + f.id),
    ),
  )
  map.value.add(poiGroup)
}

/** 添加单个 POI 圆点 + 信息窗体 */
function addPoiMarker(
  name: string,
  kind: string,
  lng: number | null,
  lat: number | null,
  color: string,
  cover: string | undefined,
  onDetail: () => void,
) {
  if (lng == null || lat == null) {
    return
  }
  const id = ++poiSeq
  poiHandlers.set(id, onDetail)
  const thumb = cover
    ? '<img src="' + cover + '" alt="" class="win__img" onerror="this.style.display=\'none\'" />'
    : ''
  const html =
    '<div class="win">' +
    thumb +
    '<div class="win__body">' +
    '<b class="win__name">' + name + '</b>' +
    '<span class="win__kind">' + kind + '</span>' +
    '<a href="javascript:;" class="win__link" onclick="window.__cityPoiGo && window.__cityPoiGo(' + id + ')">查看详情 →</a>' +
    '</div></div>'

  const marker = new AMap.CircleMarker({
    center: [lng, lat],
    radius: 5,
    strokeColor: '#ffffff',
    strokeWeight: 1,
    strokeOpacity: 0.9,
    fillColor: color,
    fillOpacity: 1,
    zIndex: 110,
    cursor: 'pointer',
  })
  marker.on('click', () => {
    infoWindow.setContent(html)
    infoWindow.open(map.value, [lng, lat])
  })
  poiMarkers.push(marker)
  poiGroup.addOverlay(marker)
}

/* ------------------------------ 面板 ------------------------------ */
/** 打开某区县面板并拉取统计 */
async function openDistrict(name: string) {
  panelDistrict.value = name
  panelOpen.value = true
  panelLoading.value = true
  activeTab.value = 'scenic'
  stats.value = null
  try {
    stats.value = await getDistrictStats(cityName.value, name)
  } catch {
    stats.value = null
  } finally {
    panelLoading.value = false
  }
}

/** 图层切换：区县边界 */
function toggleDistrict() {
  showDistrict.value = !showDistrict.value
  if (!districtLayer) {
    return
  }
  if (showDistrict.value) {
    map.value.add(districtLayer)
    map.value.add(districtCenters)
  } else {
    map.value.remove(districtLayer)
    map.value.remove(districtCenters)
  }
}

/** 图层切换：POI */
function togglePoi() {
  showPoi.value = !showPoi.value
  if (!poiGroup) {
    return
  }
  if (showPoi.value) {
    map.value.add(poiGroup)
  } else {
    map.value.remove(poiGroup)
  }
}

/* ------------------------------ 生命周期 ------------------------------ */
async function boot() {
  loading.value = true
  errorMsg.value = ''
  try {
    AMap = await ensureAMap()
    const [center, adcode] = await Promise.all([resolveCenter(), resolveAdcode()])
    initMap(center)

    // 城市简介 + 总览
    try {
      const cities = await listAllCities()
      const hit = cities.find((c) => c.name === cityName.value)
      cityIntro.value = hit?.description ?? ''
    } catch {
      cityIntro.value = ''
    }
    try {
      const data = await getMapData()
      const hit = data.find((c) => c.name === cityName.value)
      if (hit) {
        overview.value = {
          scenic: hit.scenicCount ?? 0,
          food: hit.foodCount ?? 0,
          heritage: hit.heritageCount ?? 0,
        }
      }
    } catch {
      // 忽略
    }

    await renderDistricts(adcode)
    await renderPois()
    loading.value = false
  } catch (err) {
    loading.value = false
    errorMsg.value = err instanceof Error ? err.message : '城市 3D 地图初始化失败'
  }
}

/** 清理地图与回调 */
function teardown() {
  try {
    infoWindow?.close?.()
  } catch {
    // 忽略
  }
  if (map.value) {
    map.value.destroy()
    map.value = null
  }
  districtLayer = null
  poiGroup = null
  infoWindow = null
  districtCenters = []
  poiMarkers = []
  poiHandlers.clear()
  delete window.__cityPoiGo
}

onMounted(() => {
  window.__cityPoiGo = (id: number) => poiHandlers.get(id)?.()
  boot()
})

watch(cityName, () => {
  teardown()
  panelOpen.value = false
  boot()
})

onBeforeUnmount(teardown)

/** 返回省级 3D 地图 */
function goBack() {
  router.push('/map3d')
}

/** 面板列表项点击：跳详情 */
function goScenic(id: number) {
  router.push('/scenic/' + id)
}
function goFood(id: number) {
  router.push('/food/' + id)
}
function goHeritage(id: number) {
  router.push('/heritage/' + id)
}
</script>

<template>
  <div class="city3d">
    <!-- 地图容器 -->
    <div id="city3d-map" class="city3d__map" />

    <!-- 加载 / 错误 -->
    <div v-if="loading" class="city3d__mask">正在加载 {{ cityName }} 三维城市…</div>
    <div v-else-if="errorMsg" class="city3d__mask city3d__mask--error">{{ errorMsg }}</div>

    <!-- 左上：返回 -->
    <button type="button" class="city3d__back" @click="goBack">← 返回四川地图</button>

    <!-- 顶部：城市名 + 一句话简介 + 总览 + 图层切换 -->
    <header class="topbar">
      <div class="topbar__main">
        <h1>{{ cityName }}</h1>
        <p v-if="cityIntro" class="topbar__intro">{{ cityIntro }}</p>
      </div>
      <div class="topbar__stats">
        <div class="metric metric--scenic" :class="{ 'is-zero': !overview.scenic }">
          <i class="metric__dot" />
          <b>{{ overview.scenic }}</b><span>景点</span>
        </div>
        <div class="metric metric--food" :class="{ 'is-zero': !overview.food }">
          <i class="metric__dot" />
          <b>{{ overview.food }}</b><span>美食</span>
        </div>
        <div class="metric metric--heritage" :class="{ 'is-zero': !overview.heritage }">
          <i class="metric__dot" />
          <b>{{ overview.heritage }}</b><span>非遗</span>
        </div>
      </div>
      <div class="topbar__layers">
        <button type="button" :class="{ 'is-on': showDistrict }" @click="toggleDistrict">区县边界</button>
        <button type="button" :class="{ 'is-on': showPoi }" @click="togglePoi">POI 点位</button>
      </div>
    </header>

    <!-- 底部提示 -->
    <p class="city3d__hint">拖拽旋转 · 滚轮缩放 · 点击区县查看详情</p>

    <!-- 右侧：区县面板 -->
    <aside class="panel" :class="{ 'is-open': panelOpen }" aria-label="区县信息">
      <header class="panel__head">
        <h3>{{ panelDistrict || '—' }}</h3>
        <button type="button" class="panel__close" title="关闭" @click="panelOpen = false">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>

      <div class="panel__body">
        <p v-if="panelLoading" class="panel__tip">正在加载…</p>
        <template v-else-if="stats">
          <!-- 统计 -->
          <div class="stats">
            <button type="button" class="tab tab--scenic" :class="{ on: activeTab === 'scenic' }" @click="activeTab = 'scenic'">
              <b>{{ stats.scenicCount }}</b><span>景点</span>
            </button>
            <button type="button" class="tab tab--food" :class="{ on: activeTab === 'food' }" @click="activeTab = 'food'">
              <b>{{ stats.foodCount }}</b><span>美食</span>
            </button>
            <button type="button" class="tab tab--heritage" :class="{ on: activeTab === 'heritage' }" @click="activeTab = 'heritage'">
              <b>{{ stats.heritageCount }}</b><span>非遗</span>
            </button>
          </div>

          <!-- 列表 -->
          <ul class="list">
            <template v-if="activeTab === 'scenic'">
              <li v-for="s in stats.scenics" :key="'s' + s.id" class="card" @click="goScenic(s.id)">
                <img v-if="s.coverImage" class="card__img" :src="s.coverImage" :alt="s.name" loading="lazy" />
                <span v-else class="card__img card__img--ph">{{ s.name.charAt(0) }}</span>
                <div class="card__info">
                  <b>{{ s.name }}</b>
                  <em>{{ s.type || '景点' }}{{ s.address ? ' · ' + s.address : '' }}</em>
                </div>
              </li>
              <li v-if="!stats.scenics.length" class="empty">该区暂未收录景点</li>
            </template>

            <template v-else-if="activeTab === 'food'">
              <li v-for="f in stats.foods" :key="'f' + f.id" class="card" @click="goFood(f.id)">
                <img v-if="f.coverImage" class="card__img" :src="f.coverImage" :alt="f.name" loading="lazy" />
                <span v-else class="card__img card__img--ph">{{ f.name.charAt(0) }}</span>
                <div class="card__info">
                  <b>{{ f.name }}</b>
                  <em>{{ f.shopName || f.category || '美食' }}{{ f.shopAddress ? ' · ' + f.shopAddress : '' }}</em>
                </div>
              </li>
              <li v-if="!stats.foods.length" class="empty">该区暂未收录美食</li>
            </template>

            <template v-else>
              <li v-for="h in stats.heritages" :key="'h' + h.id" class="card" @click="goHeritage(h.id)">
                <span class="card__img card__img--ph card__img--teal">{{ h.name.charAt(0) }}</span>
                <div class="card__info">
                  <b>{{ h.name }}</b>
                  <em>{{ h.category || '非遗' }}{{ h.level ? ' · ' + h.level : '' }}</em>
                </div>
              </li>
              <li v-if="!stats.heritages.length" class="empty">暂无归属该市的非遗数据</li>
            </template>
          </ul>
          <p v-if="activeTab === 'heritage' && stats.heritages.length" class="panel__note">非遗无区县字段，按市州归属展示</p>
        </template>
        <p v-else class="panel__tip">该区暂无可展示数据</p>
      </div>
    </aside>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

$blue: #4ad8f0;
$gold: #d4b374;
$muted: #7a95b8;
$line: rgba(58, 140, 220, 0.4);

.city3d {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #071426;
}

.city3d__map {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* 高德信息窗体内容（非 scoped，需全局样式；写在 scoped 下仍会作用于动态插入的 DOM 依赖 :deep，故这里用普通类 + 全局兜底见下方） */

.city3d__mask {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 20;
  padding: 12px 26px;
  border: 1px solid $line;
  border-radius: 8px;
  background: rgba(7, 20, 38, 0.92);
  font-family: $font-mono;
  font-size: 21px;
  color: #fff;
  transform: translate(-50%, -50%);

  &--error {
    border-color: #c62828;
    color: #ff8a8a;
  }
}

/* 左上返回 */
.city3d__back {
  position: absolute;
  top: 24px;
  left: 24px;
  z-index: 12;
  padding: 10px 18px;
  border: 1px solid $line;
  border-radius: 8px;
  background: rgba(7, 20, 38, 0.82);
  font-family: $font-mono;
  font-size: 21px;
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(6px);
  transition: border-color 0.25s $ease-out-expo;

  &:hover {
    border-color: $blue;
  }
}

/* 顶部条 */
.topbar {
  position: absolute;
  top: 24px;
  left: 50%;
  z-index: 12;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 26px;
  border: 1px solid $line;
  border-radius: 12px;
  background: rgba(7, 20, 38, 0.82);
  transform: translateX(-50%);
  backdrop-filter: blur(8px);
}

.topbar__main h1 {
  font-family: $font-title;
  font-size: 45px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #fff;
}

.topbar__intro {
  max-width: 460px;
  margin-top: 4px;
  overflow: hidden;
  font-size: 19.5px;
  color: $muted;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__stats {
  display: flex;
  align-items: stretch;
  font-family: $font-mono;
}

/* 顶部读数：类型色点 + 等宽数字 + 单位，替代裸数字堆叠 */
.metric {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 16px;

  & + .metric {
    border-left: 1px solid rgba(58, 140, 220, 0.28);
  }

  .metric__dot {
    align-self: center;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  b {
    font-size: 33px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: #fff;
  }

  span {
    font-size: calc(13px * 1.5);
    color: $muted;
  }

  &.is-zero b {
    color: $muted;
    opacity: 0.5;
  }
}

.metric--scenic {
  color: $gold;

  b {
    color: $gold;
  }
}

.metric--food {
  color: #ff5a5f;

  b {
    color: #ff5a5f;
  }
}

.metric--heritage {
  color: #2ed3c6;

  b {
    color: #2ed3c6;
  }
}

.topbar__layers {
  display: flex;
  gap: 10px;

  button {
    padding: 8px 14px;
    border: 1px solid $line;
    border-radius: 8px;
    background: transparent;
    font-family: $font-mono;
    font-size: 19.5px;
    color: $muted;
    cursor: pointer;
    transition: all 0.25s $ease-out-expo;

    &.is-on {
      border-color: $blue;
      color: #071426;
      background: $blue;
    }
  }
}

/* 底部提示 */
.city3d__hint {
  position: absolute;
  bottom: 20px;
  left: 50%;
  z-index: 12;
  font-family: $font-mono;
  font-size: 19.5px;
  letter-spacing: 0.08em;
  color: $muted;
  transform: translateX(-50%);
  pointer-events: none;
}

/* ---------------- 右侧面板 ---------------- */
.panel {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: 440px;
  max-width: 94vw;
  height: 100vh;
  border-left: 1px solid $line;
  background: rgba(7, 20, 38, 0.96);
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  backdrop-filter: blur(10px);

  &.is-open {
    transform: translateX(0);
  }
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 26px 18px;
  border-bottom: 1px solid rgba(58, 140, 220, 0.3);

  h3 {
    font-family: $font-title;
    font-size: 36px;
    font-weight: 700;
    letter-spacing: 0.04em;
    color: #fff;
  }
}

.panel__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid $line;
  border-radius: 6px;
  background: transparent;
  color: $muted;
  cursor: pointer;
  transition: all 0.25s $ease-out-expo;

  &:hover {
    border-color: $blue;
    color: $blue;
  }
}

.panel__body {
  flex: 1;
  padding: 20px 26px 26px;
  overflow-y: auto;
}

.panel__tip {
  padding: 30px 0;
  font-family: $font-mono;
  font-size: 21px;
  text-align: center;
  color: $muted;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 18px;
  border: 1px solid rgba(58, 140, 220, 0.18);
  border-radius: 10px;
  overflow: hidden;

  button {
    position: relative;
    padding: 18px 0;
    border: none;
    background: transparent;
    cursor: pointer;
    transition: background-color 0.25s $ease-out-expo;

    & + button {
      border-left: 1px solid rgba(58, 140, 220, 0.14);
    }

    /* 选中态：底部类型色指示条 + 数字着色 */
    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: 0;
      width: 0;
      height: 2px;
      background: currentColor;
      transform: translateX(-50%);
      transition: width 0.25s $ease-out-expo;
    }

    &.on {
      background: rgba(58, 140, 220, 0.1);

      &::after {
        width: 42%;
      }

      b {
        color: currentColor;
      }
    }

    b {
      display: block;
      font-family: $font-mono;
      font-size: 39px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: #fff;
      transition: color 0.25s $ease-out-expo;
    }

    span {
      display: block;
      margin-top: 6px;
      font-family: $font-mono;
      font-size: calc(12px * 1.5);
      color: $muted;
    }
  }

  .tab--scenic {
    color: $gold;
  }

  .tab--food {
    color: #ff5a5f;
  }

  .tab--heritage {
    color: #2ed3c6;
  }
}

.list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(58, 140, 220, 0.08);
  cursor: pointer;
  transition: background-color 0.25s $ease-out-expo;

  &:hover {
    background: rgba(58, 140, 220, 0.16);
  }
}

.card__img {
  flex: none;
  width: 64px;
  height: 64px;
  border-radius: 6px;
  object-fit: cover;
  background: rgba(58, 140, 220, 0.12);
}

.card__img--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: $font-mono;
  font-size: 27px;
  color: $muted;
}

.card__img--teal {
  background: rgba(46, 211, 198, 0.14);
  color: #2ed3c6;
}

.card__info {
  flex: 1;
  min-width: 0;

  b {
    display: block;
    overflow: hidden;
    font-size: 21px;
    color: #fff;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    display: block;
    margin-top: 5px;
    overflow: hidden;
    font-family: $font-mono;
    font-size: 18px;
    font-style: normal;
    color: $muted;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.empty {
  padding: 26px 0;
  font-family: $font-mono;
  font-size: 19.5px;
  text-align: center;
  color: $muted;
}

.panel__note {
  margin-top: 12px;
  font-size: 18px;
  text-align: center;
  color: rgba(122, 149, 184, 0.7);
}
</style>

<style lang="scss">
/* 高德信息窗体为动态插入的全局 DOM，需非 scoped 样式 */
.amap-info-content {
  padding: 0;
  background: #0f2540;
  border: 1px solid rgba(58, 140, 220, 0.5);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
}

.win {
  display: flex;
  gap: 10px;
  padding: 10px;
  color: #fff;
}

.win__img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  object-fit: cover;
}

.win__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.win__name {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', serif;
  font-size: 22.5px;
  color: #fff;
}

.win__kind {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 18px;
  color: #7a95b8;
}

.win__link {
  margin-top: 2px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 18px;
  color: #4ad8f0;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
