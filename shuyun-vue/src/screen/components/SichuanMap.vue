<script setup lang="ts">
/**
 * 四川省数据地图（SichuanMap）
 *
 * 说明：项目未安装 echarts-gl，这里用「双层 geo 叠加」模拟 3D 厚度，
 * 并叠加飞线（lines）+ 脉冲点（effectScatter），hover 展示城市名/景点数/访问量。
 * 若加载四川省 GeoJSON 失败，降级为经纬度抽象布局（散点 + 飞线）。
 * 使用 canvas 渲染器（在 4K 屏按 devicePixelRatio 提升清晰度）。
 *
 * 交互：
 * - 点击市州（区域或散点）向上抛出 select（含点击点坐标）；再次点击同一市州取消选中
 * - 无选中时每 3.2 秒自动轮播高亮一个市州，营造动态感
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/** 城市点位（与后端 /api/screen/map-data 字段一致） */
interface CityPoint {
  name: string
  value: number
  lng: number
  lat: number
  scenicCount: number
  visitCount: number
}

const props = defineProps<{
  /** 21 市州数据 */
  cities: CityPoint[]
  /** 当前选中的市州名（父级受控） */
  selected?: string | null
}>()

const emit = defineEmits<{
  (e: 'select', city: CityPoint | null, pos?: { x: number; y: number }): void
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let geoReady = false
const loading = ref(true)

/** 自动轮播高亮的市州名（无手动选中时生效） */
const spotlight = ref('')
let spotTimer: number | null = null

const COLOR_GOLD = '#d4b374'
const COLOR_CYAN = '#4ad8f0'

/** 当前高亮市州：优先手动选中，否则自动轮播 */
function activeName(): string {
  return props.selected || spotlight.value
}

/** 四川省 GeoJSON 候选地址 */
const GEO_URLS = [
  'https://geo.datav.aliyun.com/areas_v3/bound/510000_full.json',
  'https://geojson.cn/api/data/510000.json',
]

/** 加载并注册四川省地图 */
async function loadGeo() {
  for (const url of GEO_URLS) {
    try {
      const res = await fetch(url)
      if (!res.ok) continue
      const geo = await res.json()
      if (geo && Array.isArray(geo.features) && geo.features.length) {
        echarts.registerMap('sichuan', geo as any)
        geoReady = true
        return
      }
    } catch {
      // 单个地址失败则尝试下一个
    }
  }
  geoReady = false
}

/** tooltip 基础样式 */
const tooltipBase = {
  backgroundColor: 'rgba(6,14,26,0.92)',
  borderColor: 'rgba(212,179,116,0.5)',
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: '#dce6f2', fontSize: 27 },
}

/** 自定义 tooltip：城市名 + 景点数 + 访问量 */
function tooltipFormatter(params: any) {
  const d = params?.data
  if (!d || !d.name) {
    return `<b style="color:${COLOR_GOLD}">${params?.name ?? ''}</b>`
  }
  return [
    `<b style="color:${COLOR_GOLD};font-size: 29px">${d.name}</b>`,
    `<span style="color:#7a9bbf">景点数：</span>${d.scenicCount ?? '—'} 个`,
    `<span style="color:#7a9bbf">访问量：</span>${d.visitCount ?? '—'}`,
    `<span style="color:#4d6a8c;font-size: 25px">（点击查看详情）</span>`,
  ].join('<br/>')
}

/** 飞线（从起点城市出发） */
function buildFlyLines(origin: CityPoint) {
  return props.cities
    .filter((c) => c.name !== origin.name)
    .map((c) => ({
      coords: [
        [origin.lng, origin.lat],
        [c.lng, c.lat],
      ],
      value: c.value,
    }))
}

/** 散点（高亮当前市州，其余降亮） */
function buildScatter(active: string) {
  return props.cities.map((c) => {
    const on = c.name === active
    return {
      name: c.name,
      value: [c.lng, c.lat, c.value],
      scenicCount: c.scenicCount,
      visitCount: c.visitCount,
      symbolSize: on ? 20 + c.value / 12 : 7 + c.value / 16,
      itemStyle: {
        color: on ? COLOR_GOLD : COLOR_CYAN,
        shadowBlur: on ? 22 : 10,
        shadowColor: on ? COLOR_GOLD : COLOR_CYAN,
        opacity: on ? 1 : 0.9,
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{b}',
        color: on ? '#ffe6a0' : '#9fd8ea',
        fontSize: on ? 27 : 22,
        fontWeight: on ? 700 : 400,
      },
    }
  })
}

/** 渲染（使用 setOption 合并更新，保留动画、避免闪烁） */
function render() {
  if (!chart) return
  const cities = props.cities
  if (!cities.length) {
    return
  }
  const active = activeName()
  const origin =
    cities.find((c) => c.name === (props.selected || '')) ||
    cities.find((c) => c.name === '成都') ||
    cities[0]
  const flyLines = buildFlyLines(origin)
  const scatter = buildScatter(active)

  if (geoReady) {
    chart.setOption({
      tooltip: { trigger: 'item', ...tooltipBase, formatter: tooltipFormatter },
      geo: [
        {
          map: 'sichuan',
          roam: false,
          zoom: 1.06,
          layoutCenter: ['50%', '50%'],
          layoutSize: '96%',
          itemStyle: {
            areaColor: 'rgba(16,52,84,0.9)',
            borderColor: 'rgba(74,216,240,0.55)',
            borderWidth: 1,
            shadowColor: 'rgba(74,216,240,0.45)',
            shadowBlur: 20,
          },
          emphasis: {
            itemStyle: { areaColor: 'rgba(212,179,116,0.4)' },
            label: { show: true, color: '#fff', fontSize: 27 },
          },
        },
        {
          map: 'sichuan',
          roam: false,
          zoom: 1.06,
          layoutCenter: ['50%', '51.6%'],
          layoutSize: '96%',
          silent: true,
          itemStyle: {
            areaColor: 'rgba(6,14,26,0.85)',
            borderColor: 'rgba(212,179,116,0.28)',
            borderWidth: 1,
          },
        },
      ],
      series: [
        {
          type: 'lines',
          coordinateSystem: 'geo',
          geoIndex: 0,
          zlevel: 3,
          data: flyLines,
          lineStyle: { color: COLOR_GOLD, width: 1, opacity: 0.5, curveness: 0.25 },
          effect: { show: true, period: 4, trailLength: 0.35, symbol: 'circle', symbolSize: 3, color: COLOR_GOLD },
        },
        {
          type: 'effectScatter',
          coordinateSystem: 'geo',
          geoIndex: 0,
          zlevel: 4,
          data: scatter,
          rippleEffect: { brushType: 'stroke', scale: 3, period: 3 },
        },
      ],
    })
  } else {
    const lngs = cities.map((c) => c.lng)
    const lats = cities.map((c) => c.lat)
    const padLng = (Math.max(...lngs) - Math.min(...lngs)) * 0.12
    const padLat = (Math.max(...lats) - Math.min(...lats)) * 0.12
    chart.setOption({
      tooltip: { trigger: 'item', ...tooltipBase, formatter: tooltipFormatter },
      grid: { left: 20, right: 20, top: 20, bottom: 20 },
      xAxis: { type: 'value', min: Math.min(...lngs) - padLng, max: Math.max(...lngs) + padLng, show: false },
      yAxis: { type: 'value', min: Math.min(...lats) - padLat, max: Math.max(...lats) + padLat, show: false },
      series: [
        {
          type: 'lines',
          coordinateSystem: 'cartesian2d',
          data: flyLines,
          lineStyle: { color: COLOR_GOLD, width: 1, opacity: 0.5, curveness: 0.25 },
          effect: { show: true, period: 4, trailLength: 0.35, symbol: 'circle', symbolSize: 3, color: COLOR_GOLD },
        },
        {
          type: 'effectScatter',
          coordinateSystem: 'cartesian2d',
          data: scatter,
          rippleEffect: { brushType: 'stroke', scale: 3, period: 3 },
        },
      ],
    })
  }
}

/** 仅更新散点高亮（避免重设飞线导致动画被重置） */
function renderScatter() {
  if (!chart || !props.cities.length) return
  chart.setOption({ series: [{}, { data: buildScatter(activeName()) }] })
}

/** 市州在当前容器内的像素坐标（供父级把详情卡就近弹出到点击点旁） */
function cityPixel(city: CityPoint): { x: number; y: number } | undefined {
  if (!chart) return undefined
  try {
    const p = geoReady
      ? chart.convertToPixel({ geoIndex: 0 }, [city.lng, city.lat])
      : chart.convertToPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [city.lng, city.lat])
    if (Array.isArray(p) && p.length === 2) {
      return { x: Number(p[0]), y: Number(p[1]) }
    }
  } catch {
    // 坐标换算不可用时由父级兜底到默认位置
  }
  return undefined
}

/** 点击市州：再次点击同一市州即取消选中；同时抛出点击点的容器内坐标 */
function onClick(params: any) {
  const name = params?.name
  const city = props.cities.find((c) => c.name === name)
  if (city) {
    if (props.selected === city.name) {
      emit('select', null)
    } else {
      emit('select', city, cityPixel(city))
    }
  } else {
    emit('select', null)
  }
}

/** 自动轮播高亮（有手动选中时暂停） */
function startSpotlight() {
  stopSpotlight()
  spotTimer = window.setInterval(() => {
    if (props.selected) return
    const list = props.cities
    if (!list.length) return
    const idx = list.findIndex((c) => c.name === spotlight.value)
    spotlight.value = list[(idx + 1) % list.length].name
  }, 3200)
}

function stopSpotlight() {
  if (spotTimer !== null) {
    window.clearInterval(spotTimer)
    spotTimer = null
  }
}

function resize() {
  chart?.resize()
}

onMounted(async () => {
  if (chartEl.value) {
    // canvas 渲染器 + 高 DPI（4K 屏更清晰）
    chart = echarts.init(chartEl.value, undefined, {
      renderer: 'canvas',
      devicePixelRatio: window.devicePixelRatio || 1,
    })
    chart.on('click', onClick)
  }
  await loadGeo()
  loading.value = false
  render()
  startSpotlight()
  window.addEventListener('resize', resize)
})

watch(
  () => props.cities,
  () => render(),
  { deep: true },
)

// 选中变化：整体重绘（飞线起点 + 高亮）
watch(() => props.selected, () => render())

// 轮播高亮变化：只更新散点，避免飞线动画被重置
watch(spotlight, () => renderScatter())

onUnmounted(() => {
  stopSpotlight()
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="map">
    <div ref="chartEl" class="map__canvas" />
    <p class="map__source">数据来源：四川省文旅厅 · 实时更新 · 点击市州查看详情</p>
    <span v-if="loading" class="map__loading">地图加载中…</span>
  </div>
</template>

<style scoped lang="scss">
@use '@/screen/styles/screen-tokens' as *;

.map {
  position: relative;
  width: 100%;
  height: 100%;
}

.map__canvas {
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.map__source {
  position: absolute;
  bottom: 6px;
  left: 50%;
  /* 字号放大 0.5 倍：12px → 18px */
  font-size: calc(12px * 2.25);
  letter-spacing: 0.08em;
  color: $screen-text-dim;
  transform: translateX(-50%);
}

.map__loading {
  position: absolute;
  top: 50%;
  left: 50%;
  /* 字号放大 0.5 倍：13px → 19.5px */
  font-size: calc(13px * 2.25);
  color: $screen-text-muted;
  transform: translate(-50%, -50%);
}
</style>
