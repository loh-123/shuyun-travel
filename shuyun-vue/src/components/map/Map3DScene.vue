<script setup lang="ts">
/**
 * 3D 四川地形场景（Map3DScene）· 真实卫星影像 + 地形起伏 + 悬浮切片
 *
 * 技术要点：
 * - PlaneGeometry(800,600,256,256) 顶点按高程图位移（y=2 ~ 80），双线性采样保证平滑
 * - 卫星影像贴图 + 四川省边界 alphaMask 裁切（只保留四川轮廓）
 * - 边界外框拉出侧壁到 BASE_Z，形成"悬浮切片"（厚度 + 深色侧面）
 * - 市州边界线叠加（白 0.3，成都 0xC62828 加粗），悬停变红 + tooltip，点击派发 city-select
 * - 背景透明，由页面提供浅灰蓝渐变
 *
 * 经纬度 ↔ 平面 使用与纹理抓取脚本一致的 Web Mercator bbox 映射，保证影像/高程/边界三者对齐。
 */
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as THREE from 'three'
import gsap from 'gsap'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { Line2 } from 'three/examples/jsm/lines/Line2.js'
import { LineGeometry } from 'three/examples/jsm/lines/LineGeometry.js'
import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js'
import { loadSichuanGeo, type SichuanGeo } from './geo'
import { mapBus, type MapLocatePayload } from './mapBus'
import type { MapCityPoint } from '@/api/map3d'
import satelliteUrl from '@/assets/map/sichuan-satellite.jpg'
import heightmapUrl from '@/assets/map/sichuan-heightmap.png'

const props = defineProps<{
  /** 21 市州数据 */
  cities: MapCityPoint[]
}>()

const emit = defineEmits<{
  /** 点击市州色块（右侧面板快捷预览） */
  (e: 'city-select', city: MapCityPoint): void
  /** 点击市州名称标签（跳转城市 3D 页面） */
  (e: 'city-open', city: MapCityPoint): void
  /** 场景就绪 */
  (e: 'ready'): void
  /** 相机方位角变化（供右下角指南针） */
  (e: 'rotate', azimuth: number): void
}>()

/* ------------------------------ 常量 ------------------------------ */
const PLANE_W = 800
const PLANE_H = 600
const SEG = 256
/** 高程图灰度区间对应的真实海拔（来自抓取脚本输出） */
const ELEV_MIN = 56
const ELEV_MAX = 7217
/** 高度映射：成都平原(~500m) → y=3，贡嘎山(7556m) → y=240 */
const Y_FLOOR = 3
const Y_PEAK = 240
/** 切片底面（向下拉出厚度） */
const BASE_Z = -6

/** 峰值标注（经纬度取自公开信息） */
const PEAKS = [
  { name: '贡嘎山', alt: 7556, lng: 101.88, lat: 29.59 },
  { name: '峨眉山', alt: 3099, lng: 103.33, lat: 29.52 },
]
/** 侧面颜色 */
const COLOR_SIDE = 0x23252a
/** 市州边界线 */
const COLOR_LINE = 0x4ad8f0
const COLOR_LINE_HOT = 0xc62828
/** 中心市州（边界线加粗 + 常亮红） */
const CENTER_CITY = '成都'
/** 成都固定飞线目标（8 条主线） */
const FLY_TARGETS = ['绵阳', '德阳', '乐山', '宜宾', '南充', '达州', '雅安', '阿坝']
/** 市州名称标签：相对地形表面向上抬升的世界高度 */
const LABEL_LIFT = 30

/** 经纬度范围（与 scripts/fetch-map-textures.mjs 保持一致） */
const BBOX = { minLon: 97.3, maxLon: 108.5, minLat: 26.0, maxLat: 34.3 }

/* ------------------------------ Web Mercator 映射 ------------------------------ */
const D2R = Math.PI / 180
const mercX = (lon: number) => lon * D2R
const mercY = (lat: number) => Math.log(Math.tan(Math.PI / 4 + (lat * D2R) / 2))
const MX0 = mercX(BBOX.minLon)
const MX1 = mercX(BBOX.maxLon)
const MY0 = mercY(BBOX.minLat)
const MY1 = mercY(BBOX.maxLat)

/** 经纬度 → 归一化 u(0 西 → 1 东) / v(0 北 → 1 南) */
function toUV(lng: number, lat: number): [number, number] {
  return [(mercX(lng) - MX0) / (MX1 - MX0), (MY1 - mercY(lat)) / (MY1 - MY0)]
}
/** 归一化 uv → 平面局部坐标 */
function uvToXY(u: number, v: number): [number, number] {
  return [(u - 0.5) * PLANE_W, (0.5 - v) * PLANE_H]
}

/* ------------------------------ 状态 ------------------------------ */
const wrapRef = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const error = ref('')
const tip = reactive({ visible: false, x: 0, y: 0, name: '', scenic: 0 })
const hintText = '拖拽旋转 · 滚轮缩放 · 点击市州查看详情'

/** 高程数据 */
let heightData: { data: Float32Array; width: number; height: number } | null = null

interface CityShape {
  city: MapCityPoint
  /** 平面坐标下的所有环（外环 + 洞） */
  rings: THREE.Vector2[][]
  minX: number
  minY: number
  maxX: number
  maxY: number
}

interface CityLine {
  city: MapCityPoint
  name: string
  mat: LineMaterial
  lines: Line2[]
}

/** 市州名称 HTML 标签（含屏幕投影坐标） */
interface CityLabel {
  name: string
  /** 是否省会（成都，标签前加星） */
  isCapital: boolean
  /** 本地三维锚点（mapGroup 局部坐标，+Z 朝上） */
  ax: number
  ay: number
  az: number
  /** 投影到屏幕后的坐标（相对场景容器，单位 px） */
  sx: number
  sy: number
  visible: boolean
}

/* ------------------------------ Three 对象 ------------------------------ */
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let mapGroup: THREE.Group | null = null
let terrain: THREE.Mesh | null = null
let raycaster: THREE.Raycaster | null = null
let resizeObserver: ResizeObserver | null = null

const pointer = new THREE.Vector2()
const lineMaterials: LineMaterial[] = []
const ownedTextures: THREE.Texture[] = []
const cityShapes: CityShape[] = []
const cityLines: CityLine[] = []
/** 市州名称标签（模板渲染 + 每帧更新投影坐标） */
const labels = ref<CityLabel[]>([])

let hoveredName: string | null = null
let pressed = false
let dragging = false
let downX = 0
let downY = 0
let rafId = 0
/** 指南针方位角上报节流 */
let lastAzimuthEmit = 0

/* ------------------------------ 高程采样（双线性） ------------------------------ */
/** u: 0 西→1 东；v: 0 北→1 南；返回 0-255 灰度 */
function sampleHeight(u: number, v: number): number {
  if (!heightData) {
    return 0
  }
  const { data, width, height } = heightData
  const x = Math.min(width - 1, Math.max(0, u * (width - 1)))
  const y = Math.min(height - 1, Math.max(0, v * (height - 1)))
  const x0 = Math.floor(x)
  const y0 = Math.floor(y)
  const x1 = Math.min(width - 1, x0 + 1)
  const y1 = Math.min(height - 1, y0 + 1)
  const fx = x - x0
  const fy = y - y0
  const p00 = data[y0 * width + x0]
  const p10 = data[y0 * width + x1]
  const p01 = data[y1 * width + x0]
  const p11 = data[y1 * width + x1]
  const top = p00 * (1 - fx) + p10 * fx
  const bot = p01 * (1 - fx) + p11 * fx
  return top * (1 - fy) + bot * fy
}
/** 高程图灰度（0-255）→ 实际海拔（m） */
function grayToElevation(h: number): number {
  return ELEV_MIN + (h / 255) * (ELEV_MAX - ELEV_MIN)
}
/** 海拔（m）→ 世界 y（高程加大 3 倍） */
function elevationToY(e: number): number {
  return Math.max(2, Y_FLOOR + ((e - 500) / (7556 - 500)) * (Y_PEAK - Y_FLOOR))
}
/** 灰度 → 世界 y */
function heightToY(h: number): number {
  return elevationToY(grayToElevation(h))
}

/** 分层设色（hypsometric tint，降低对比度）：低绿 → 黄绿 → 褐 → 米白 */
const TINT_BANDS: Array<{ max: number; rgb: THREE.Color; mix: number }> = [
  { max: 500, rgb: new THREE.Color(0x3a6b4f), mix: 0.65 }, // 低海拔：绿
  { max: 2500, rgb: new THREE.Color(0x8a9a5b), mix: 0.62 }, // 中海拔：黄绿
  { max: 4500, rgb: new THREE.Color(0x9b7b54), mix: 0.62 }, // 高海拔：褐
  { max: Infinity, rgb: new THREE.Color(0xe8e2d5), mix: 0.68 }, // 最高处：米白
]
function elevationTint(e: number) {
  for (const band of TINT_BANDS) {
    if (e < band.max) {
      return band
    }
  }
  return TINT_BANDS[TINT_BANDS.length - 1]
}

/* ------------------------------ 资源加载 ------------------------------ */
function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`图片加载失败：${url}`))
    img.src = url
  })
}

/** 加载高程图并取出灰度通道 */
async function loadHeightmap() {
  const img = await loadImage(heightmapUrl)
  const canvas = document.createElement('canvas')
  canvas.width = img.naturalWidth
  canvas.height = img.naturalHeight
  const ctx = canvas.getContext('2d')!
  ctx.drawImage(img, 0, 0)
  const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const gray = new Float32Array(canvas.width * canvas.height)
  for (let i = 0; i < gray.length; i++) {
    gray[i] = data[i * 4] // 灰度图 R=G=B
  }
  heightData = { data: gray, width: canvas.width, height: canvas.height }
}

/** 生成四川省 alphaMask（白=省内，黑=省外） */
function makeMaskTexture(geo: SichuanGeo, size: number): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, size, size)
  ctx.fillStyle = '#fff'
  for (const feature of geo.features) {
    const polys =
      feature.geometry.type === 'Polygon'
        ? [feature.geometry.coordinates as number[][][]]
        : (feature.geometry.coordinates as number[][][][])
    for (const poly of polys) {
      ctx.beginPath()
      for (const ring of poly) {
        ring.forEach((coord, i) => {
          const [u, v] = toUV(coord[0], coord[1])
          const px = u * size
          const py = v * size
          if (i === 0) {
            ctx.moveTo(px, py)
          } else {
            ctx.lineTo(px, py)
          }
        })
        ctx.closePath()
      }
      ctx.fill('evenodd')
    }
  }
  const tex = new THREE.CanvasTexture(canvas)
  tex.needsUpdate = true
  return tex
}

/* ------------------------------ 地形 ------------------------------ */
function buildTerrain(satTex: THREE.Texture, maskTex: THREE.Texture) {
  const geometry = new THREE.PlaneGeometry(PLANE_W, PLANE_H, SEG, SEG)
  const pos = geometry.attributes.position as THREE.BufferAttribute
  const uv = geometry.attributes.uv as THREE.BufferAttribute
  const tint = new Float32Array(pos.count * 3)
  const mix = new Float32Array(pos.count)

  for (let i = 0; i < pos.count; i++) {
    const u = uv.getX(i)
    const v = 1 - uv.getY(i) // 贴图行 0 = 北
    const h = sampleHeight(u, v)
    const elev = grayToElevation(h)
    pos.setZ(i, elevationToY(elev))
    const band = elevationTint(elev)
    tint[i * 3] = band.rgb.r
    tint[i * 3 + 1] = band.rgb.g
    tint[i * 3 + 2] = band.rgb.b
    mix[i] = band.mix
  }
  pos.needsUpdate = true
  geometry.setAttribute('aTint', new THREE.BufferAttribute(tint, 3))
  geometry.setAttribute('aMix', new THREE.BufferAttribute(mix, 1))
  geometry.computeVertexNormals()

  const material = new THREE.MeshStandardMaterial({
    map: satTex,
    alphaMap: maskTex,
    alphaTest: 0.5,
    color: 0xffffff,
    roughness: 0.8,
    metalness: 0.05,
  })
  // 分层设色：最终颜色 = 卫星图 × (1-mix) + 海拔 tint × mix
  material.onBeforeCompile = (shader) => {
    shader.vertexShader = shader.vertexShader
      .replace(
        '#include <common>',
        '#include <common>\nattribute vec3 aTint;\nattribute float aMix;\nvarying vec3 vTint;\nvarying float vMix;',
      )
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvTint = aTint;\nvMix = aMix;')
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vTint;\nvarying float vMix;')
      .replace(
        '#include <map_fragment>',
        '#include <map_fragment>\ndiffuseColor.rgb = mix(diffuseColor.rgb, vTint, vMix);',
      )
  }
  terrain = new THREE.Mesh(geometry, material)
  mapGroup!.add(terrain)
}

/** 悬浮切片侧壁：取"只出现一次"的边界段（市州间公共边被抵消）拉出竖直面 */
function buildWalls(geo: SichuanGeo) {
  const segs = new Map<string, { ax: number; ay: number; bx: number; by: number; count: number }>()
  const key = (x: number, y: number) => `${Math.round(x * 100)},${Math.round(y * 100)}`

  for (const feature of geo.features) {
    const polys =
      feature.geometry.type === 'Polygon'
        ? [feature.geometry.coordinates as number[][][]]
        : (feature.geometry.coordinates as number[][][][])
    for (const poly of polys) {
      for (const ring of poly) {
        for (let i = 0; i < ring.length; i++) {
          const a = ring[i]
          const b = ring[(i + 1) % ring.length]
          const [au, av] = toUV(a[0], a[1])
          const [bu, bv] = toUV(b[0], b[1])
          const [ax, ay] = uvToXY(au, av)
          const [bx, by] = uvToXY(bu, bv)
          const ka = key(ax, ay)
          const kb = key(bx, by)
          const id = ka < kb ? `${ka}|${kb}` : `${kb}|${ka}`
          const hit = segs.get(id)
          if (hit) {
            hit.count++
          } else {
            segs.set(id, { ax, ay, bx, by, count: 1 })
          }
        }
      }
    }
  }

  const positions: number[] = []
  for (const s of segs.values()) {
    if (s.count !== 1) {
      continue
    }
    const za = heightToY(sampleHeight(s.ax / PLANE_W + 0.5, 0.5 - s.ay / PLANE_H)) + 2
    const zb = heightToY(sampleHeight(s.bx / PLANE_W + 0.5, 0.5 - s.by / PLANE_H)) + 2
    // 两个三角形组成一段竖直面
    positions.push(s.ax, s.ay, za, s.bx, s.by, zb, s.bx, s.by, BASE_Z)
    positions.push(s.ax, s.ay, za, s.bx, s.by, BASE_Z, s.ax, s.ay, BASE_Z)
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.computeVertexNormals()
  const mesh = new THREE.Mesh(
    geometry,
    new THREE.MeshBasicMaterial({ color: COLOR_SIDE, side: THREE.DoubleSide }),
  )
  mapGroup!.add(mesh)

  // 底面封口
  const bottom = new THREE.Mesh(
    new THREE.PlaneGeometry(PLANE_W, PLANE_H),
    new THREE.MeshBasicMaterial({ color: COLOR_SIDE, alphaMap: maskTextureRef, alphaTest: 0.5, side: THREE.DoubleSide }),
  )
  bottom.position.z = BASE_Z
  mapGroup!.add(bottom)
}

let maskTextureRef: THREE.Texture | null = null

/* ------------------------------ 市州边界线 ------------------------------ */
function buildCityLines(geo: SichuanGeo) {
  for (const feature of geo.features) {
    const fname = String(feature.properties?.name ?? '')
    const city =
      props.cities.find((c) => c.name === fname.replace(/[市州]$/, '')) ??
      props.cities.find((c) => fname.startsWith(c.name)) ??
      props.cities.find((c) => fname.includes(c.name)) ??
      null
    const name = city?.name ?? fname
    const isCenter = name === CENTER_CITY

    const mat = new LineMaterial({
      color: isCenter ? COLOR_LINE_HOT : COLOR_LINE,
      linewidth: isCenter ? 2 : 0.8,
      transparent: true,
      opacity: isCenter ? 1 : 0.4,
    })
    lineMaterials.push(mat)

    const polys =
      feature.geometry.type === 'Polygon'
        ? [feature.geometry.coordinates as number[][][]]
        : (feature.geometry.coordinates as number[][][][])
    const lines: Line2[] = []
    const shape: CityShape = { city: city as MapCityPoint, rings: [], minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }

    for (const poly of polys) {
      for (const ring of poly) {
        const pts: number[] = []
        const v2: THREE.Vector2[] = []
        for (const coord of ring) {
          const [u, v] = toUV(coord[0], coord[1])
          const [x, y] = uvToXY(u, v)
          const z = heightToY(sampleHeight(u, v)) + 0.6
          pts.push(x, y, z)
          const p = new THREE.Vector2(x, y)
          v2.push(p)
          if (x < shape.minX) shape.minX = x
          if (x > shape.maxX) shape.maxX = x
          if (y < shape.minY) shape.minY = y
          if (y > shape.maxY) shape.maxY = y
        }
        if (pts.length < 6) {
          continue
        }
        pts.push(v2[0].x, v2[0].y, pts[2]) // 闭合
        const geom = new LineGeometry()
        geom.setPositions(pts)
        const line = new Line2(geom, mat)
        mapGroup!.add(line)
        lines.push(line)
        shape.rings.push(v2)
      }
    }
    if (city) {
      cityShapes.push(shape)
    }
    cityLines.push({ city: city as MapCityPoint, name, mat, lines })
  }
}

/* ------------------------------ 拾取（点在多边形内） ------------------------------ */
/** 峰值标签纹理（名称 + 海拔） */
function makePeakLabel(name: string, alt: number): THREE.CanvasTexture {
  const w = 256
  const h = 96
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const c = canvas.getContext('2d')!
  const r = 12
  c.beginPath()
  c.moveTo(r, 1)
  c.arcTo(w - 1, 1, w - 1, h - 1, r)
  c.arcTo(w - 1, h - 1, 1, h - 1, r)
  c.arcTo(1, h - 1, 1, 1, r)
  c.arcTo(1, 1, w - 1, 1, r)
  c.closePath()
  c.fillStyle = 'rgba(15,17,21,0.72)'
  c.fill()
  c.strokeStyle = 'rgba(255,255,255,0.35)'
  c.lineWidth = 2
  c.stroke()
  c.textAlign = 'center'
  c.fillStyle = '#ffffff'
  c.font = '600 34px "Microsoft YaHei", "PingFang SC", sans-serif'
  c.fillText(name, w / 2, 44)
  c.fillStyle = '#9fb6c8'
  c.font = '500 26px "JetBrains Mono", Consolas, monospace'
  c.fillText(`${alt}m`, w / 2, 78)
  const tex = new THREE.CanvasTexture(canvas)
  tex.needsUpdate = true
  return tex
}

/** 峰值标注：白色细线 + 标签 */
function buildPeakLabels(geo: SichuanGeo) {
  void geo
  for (const peak of PEAKS) {
    const [u, v] = toUV(peak.lng, peak.lat)
    const [x, y] = uvToXY(u, v)
    const z = heightToY(sampleHeight(u, v))

    const mat = new LineMaterial({ color: 0xffffff, linewidth: 1, transparent: true, opacity: 0.6 })
    lineMaterials.push(mat)
    const geom = new LineGeometry()
    geom.setPositions([x, y, z + 2, x, y, z + 50])
    mapGroup!.add(new Line2(geom, mat))

    const tex = makePeakLabel(peak.name, peak.alt)
    ownedTextures.push(tex)
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }))
    sprite.scale.set(110, 41, 1)
    sprite.position.set(x, y, z + 68)
    mapGroup!.add(sprite)
  }
}

/** 成都 → 8 市州 的静态细飞线（蜀锦红 0.3） */
function buildFlyLines() {
  const at = (c: MapCityPoint) => {
    const [u, v] = toUV(c.lng, c.lat)
    const [x, y] = uvToXY(u, v)
    return new THREE.Vector3(x, y, heightToY(sampleHeight(u, v)))
  }
  const origin = props.cities.find((c) => c.name === CENTER_CITY)
  if (!origin) {
    return
  }
  const o = at(origin)
  for (const c of props.cities) {
    if (c.name === CENTER_CITY || !FLY_TARGETS.includes(c.name)) {
      continue
    }
    const e = at(c)
    const start = new THREE.Vector3(o.x, o.y, o.z + 12)
    const end = new THREE.Vector3(e.x, e.y, e.z + 12)
    const mid = new THREE.Vector3().addVectors(start, end).multiplyScalar(0.5)
    mid.z += Math.max(60, start.distanceTo(end) * 0.4)
    const curve = new THREE.QuadraticBezierCurve3(start, mid, end)
    const tube = new THREE.TubeGeometry(curve, 48, 0.3, 6, false)
    mapGroup!.add(
      new THREE.Mesh(
        tube,
        new THREE.MeshBasicMaterial({ color: 0x8c1f28, transparent: true, opacity: 0.3, depthWrite: false }),
      ),
    )
  }
}

/* ------------------------------ 市州名称标签 ------------------------------ */
/** 构建标签：锚点取市州中心经纬度，抬升到地形之上 */
function buildLabels() {
  labels.value = props.cities.map((c) => {
    const [u, v] = toUV(c.lng, c.lat)
    const [x, y] = uvToXY(u, v)
    const z = heightToY(sampleHeight(u, v))
    return {
      name: c.name,
      isCapital: c.name === CENTER_CITY,
      ax: x,
      ay: y,
      az: z + LABEL_LIFT,
      sx: 0,
      sy: 0,
      visible: false,
    }
  })
}

/** 每帧把标签锚点投影到屏幕坐标（跟随相机旋转 / 缩放实时更新） */
const projVec = new THREE.Vector3()
function updateLabels() {
  const wrap = wrapRef.value
  if (!wrap || !camera || !mapGroup || !labels.value.length) {
    return
  }
  // 保证 world matrix 为最新，避免首帧投影错位
  mapGroup.updateWorldMatrix(true, false)
  const w = wrap.clientWidth
  const h = wrap.clientHeight
  for (const lb of labels.value) {
    projVec.set(lb.ax, lb.ay, lb.az)
    mapGroup.localToWorld(projVec)
    projVec.project(camera)
    // z >= 1 表示在相机背后；超出可视范围时隐藏
    lb.visible = projVec.z < 1 && projVec.x > -1.1 && projVec.x < 1.1 && projVec.y > -1.1 && projVec.y < 1.1
    lb.sx = (projVec.x * 0.5 + 0.5) * w
    lb.sy = (-projVec.y * 0.5 + 0.5) * h
  }
}

/** 点击标签：派发 city-open（父组件负责路由跳转） */
function onLabelClick(name: string) {
  const city = props.cities.find((c) => c.name === name)
  if (city) {
    emit('city-open', city)
  }
}

function pointInShape(shape: CityShape, x: number, y: number): boolean {
  let inside = false
  for (const ring of shape.rings) {
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const xi = ring[i].x
      const yi = ring[i].y
      const xj = ring[j].x
      const yj = ring[j].y
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) {
        inside = !inside
      }
    }
  }
  return inside
}

function pickCity(): MapCityPoint | null {
  if (!raycaster || !camera || !terrain) {
    return null
  }
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObject(terrain, false)
  if (!hits.length) {
    return null
  }
  const local = mapGroup!.worldToLocal(hits[0].point.clone())
  for (const shape of cityShapes) {
    if (local.x < shape.minX || local.x > shape.maxX || local.y < shape.minY || local.y > shape.maxY) {
      continue
    }
    if (pointInShape(shape, local.x, local.y)) {
      return shape.city
    }
  }
  return null
}

/* ------------------------------ 场景初始化 ------------------------------ */
function initRenderer(wrap: HTMLDivElement) {
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(wrap.clientWidth, wrap.clientHeight)
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.touchAction = 'none'
  wrap.appendChild(renderer.domElement)
}

function initCamera(wrap: HTMLDivElement) {
  const aspect = wrap.clientWidth / wrap.clientHeight || 1
  camera = new THREE.PerspectiveCamera(45, aspect, 1, 8000)
  camera.position.set(0, 533, 800)

  controls = new OrbitControls(camera, renderer!.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.enablePan = false
  controls.minDistance = 400
  controls.maxDistance = 1400
  controls.minPolarAngle = 0.15
  controls.maxPolarAngle = Math.PI / 2.05
  controls.rotateSpeed = 0.5
  controls.zoomSpeed = 0.8
  controls.autoRotate = false
  controls.target.set(0, 0, 0)
  controls.update()
}

function initLights() {
  scene!.add(new THREE.AmbientLight(0xffffff, 0.85))
  // 中性白光：突出山体明暗，不用彩色光
  const dir = new THREE.DirectionalLight(0xffffff, 0.6)
  dir.position.set(200, 400, 200)
  scene!.add(dir)
}

function syncLineResolution() {
  const wrap = wrapRef.value
  if (!wrap) {
    return
  }
  const w = wrap.clientWidth
  const h = wrap.clientHeight || 1
  lineMaterials.forEach((m) => m.resolution.set(w, h))
}

/* ------------------------------ 交互 ------------------------------ */
function updatePointer(e: PointerEvent) {
  const wrap = wrapRef.value
  if (!wrap) {
    return
  }
  const rect = wrap.getBoundingClientRect()
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
}

/** 悬停高亮：边界线变红加粗 */
function setHovered(name: string | null) {
  if (hoveredName === name) {
    return
  }
  const prev = cityLines.find((c) => c.name === hoveredName)
  if (prev) {
    const isCenter = prev.name === CENTER_CITY
    prev.mat.color.setHex(isCenter ? COLOR_LINE_HOT : COLOR_LINE)
    prev.mat.linewidth = isCenter ? 2 : 0.8
    prev.mat.opacity = isCenter ? 1 : 0.4
  }
  hoveredName = name
  const next = cityLines.find((c) => c.name === name)
  if (next) {
    next.mat.color.setHex(COLOR_LINE_HOT)
    next.mat.linewidth = 2
    next.mat.opacity = 1
  }
}

function onPointerDown(e: PointerEvent) {
  pressed = true
  dragging = false
  downX = e.clientX
  downY = e.clientY
}

function onPointerMove(e: PointerEvent) {
  if (pressed && (Math.abs(e.clientX - downX) > 5 || Math.abs(e.clientY - downY) > 5)) {
    dragging = true
  }
  if (dragging) {
    if (tip.visible) {
      tip.visible = false
    }
    setHovered(null)
    return
  }
  updatePointer(e)
  const city = pickCity()
  setHovered(city?.name ?? null)
  const wrap = wrapRef.value
  if (city && wrap) {
    const rect = wrap.getBoundingClientRect()
    tip.name = city.name
    tip.scenic = city.scenicCount ?? 0
    tip.x = e.clientX - rect.left + 14
    tip.y = e.clientY - rect.top + 12
    tip.visible = true
  } else {
    tip.visible = false
  }
}

function onPointerUp(e: PointerEvent) {
  const moved = Math.hypot(e.clientX - downX, e.clientY - downY)
  pressed = false
  const wasClick = !dragging && moved < 5
  dragging = false
  if (wasClick) {
    updatePointer(e)
    // 优先判定是否点中「定位标记」：点中则切换弹窗，不再触发市州选中
    if (hitLocateMarker()) {
      const open = !locatePopup.value
      locatePopup.value = open
      // 打开弹窗时取消标记自动淡出，避免弹窗刚出现标记就消失
      if (open) {
        locateFadeTween?.kill()
        locateFadeTween = null
        if (locateMarker) (locateMarker.material as THREE.SpriteMaterial).opacity = 1
      }
      return
    }
    const city = pickCity()
    if (city) {
      emit('city-select', city)
    }
  }
}

function onPointerLeave() {
  pressed = false
  dragging = false
  tip.visible = false
  setHovered(null)
}

/* ------------------------------ 外部定位联动（mapBus） ------------------------------ */
/** 定位标记（金色水滴） */
let locateMarker: THREE.Sprite | null = null
/** 本次定位产生的补间（切换定位时统一清理） */
let locateTweens: gsap.core.Tween[] = []
/** 高亮金（与页面主题一致） */
const LOCATE_GOLD = new THREE.Color(0xd4b374)
/** 当前定位条目的弹窗内容（视频 / 简介链接） */
const locateInfo = reactive({ title: '', introUrl: '', videoUrl: '' })
/** 定位弹窗是否可见（点击定位标记后出现） */
const locatePopup = ref(false)
/** 定位标记实时屏幕坐标（每帧更新，供弹窗定位） */
const locateScreen = reactive({ x: 0, y: 0, ok: false })
/** 投影复用向量 */
const locateVec = new THREE.Vector3()
/** 标记自动淡出补间（点击标记时取消，避免弹窗刚出现标记就消失） */
let locateFadeTween: gsap.core.Tween | null = null

/** 恢复所有市州边界线到默认状态 */
function restoreCityLines() {
  for (const entry of cityLines) {
    const isCenter = entry.name === CENTER_CITY
    entry.mat.color.setHex(isCenter ? COLOR_LINE_HOT : COLOR_LINE)
    entry.mat.linewidth = isCenter ? 2 : 0.8
    entry.mat.opacity = isCenter ? 1 : 0.4
  }
}

/** 清理上一次定位的标记 / 动画，并复位边界线 */
function clearLocate() {
  locateTweens.forEach((t) => t.kill())
  locateTweens = []
  if (locateMarker && mapGroup) {
    mapGroup.remove(locateMarker)
    const m = locateMarker.material as THREE.SpriteMaterial
    m.map?.dispose()
    m.dispose()
  }
  locateMarker = null
  locateFadeTween = null
  locatePopup.value = false
  locateScreen.ok = false
  restoreCityLines()
}

/** 经纬度 → mapGroup 局部坐标（含地形高度，可抬升） */
function localPos(lng: number, lat: number, lift = 0): THREE.Vector3 {
  const [u, v] = toUV(lng, lat)
  const [x, y] = uvToXY(u, v)
  return new THREE.Vector3(x, y, heightToY(sampleHeight(u, v)) + lift)
}

/** 相机平滑飞向目标地点（GSAP 1.2s，同步移动 OrbitControls.target） */
function flyToLocation(lng: number, lat: number) {
  if (!camera || !controls || !mapGroup) return
  mapGroup.updateWorldMatrix(true, false)
  const target = localPos(lng, lat).applyMatrix4(mapGroup.matrixWorld)
  // 保持一定距离以看到周边地形
  const camPos = target.clone().add(new THREE.Vector3(0, 380, 560))
  controls.enabled = false
  gsap.killTweensOf(camera.position)
  gsap.killTweensOf(controls.target)
  gsap.to(camera.position, { x: camPos.x, y: camPos.y, z: camPos.z, duration: 1.2, ease: 'power2.inOut' })
  gsap.to(controls.target, {
    x: target.x,
    y: target.y,
    z: target.z,
    duration: 1.2,
    ease: 'power2.inOut',
    onComplete: () => {
      if (controls) controls.enabled = true
    },
  })
}

/** 高亮市州：边界线变金 + 加粗脉冲（3 次），3 秒后恢复 */
function highlightCity(name: string) {
  const entry = cityLines.find((c) => c.name === name)
  if (!entry) return
  const isCenter = name === CENTER_CITY
  const baseColor = new THREE.Color(isCenter ? COLOR_LINE_HOT : COLOR_LINE)
  const baseWidth = isCenter ? 2 : 0.8
  const baseOpacity = isCenter ? 1 : 0.4
  const mat = entry.mat
  gsap.killTweensOf(mat.color)
  gsap.to(mat.color, { r: LOCATE_GOLD.r, g: LOCATE_GOLD.g, b: LOCATE_GOLD.b, duration: 0.35, ease: 'power2.out' })
  mat.opacity = 1
  // 线宽脉冲：baseWidth → 3.2 → baseWidth，循环 3 次
  const state = { w: baseWidth }
  locateTweens.push(
    gsap.to(state, {
      w: 3.2,
      duration: 0.35,
      yoyo: true,
      repeat: 5,
      ease: 'sine.inOut',
      onUpdate: () => {
        mat.linewidth = state.w
      },
    }),
  )
  // 3 秒后恢复原色 / 线宽
  locateTweens.push(
    gsap.delayedCall(3, () => {
      gsap.to(mat.color, { r: baseColor.r, g: baseColor.g, b: baseColor.b, duration: 0.5 })
      mat.linewidth = baseWidth
      mat.opacity = baseOpacity
    }),
  )
}

/** 生成金色水滴指针纹理 */
function makePinTexture(): THREE.CanvasTexture {
  const w = 64
  const h = 88
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const c = canvas.getContext('2d')!
  const cx = w / 2
  const cy = 30
  c.fillStyle = '#d4b374'
  c.strokeStyle = 'rgba(255,247,230,0.95)'
  c.lineWidth = 3
  c.beginPath()
  c.moveTo(cx - 15, cy + 10)
  c.lineTo(cx, h - 6)
  c.lineTo(cx + 15, cy + 10)
  c.closePath()
  c.fill()
  c.stroke()
  c.beginPath()
  c.arc(cx, cy, 22, 0, Math.PI * 2)
  c.fill()
  c.stroke()
  c.beginPath()
  c.arc(cx, cy, 8, 0, Math.PI * 2)
  c.fillStyle = '#0f2540'
  c.fill()
  const tex = new THREE.CanvasTexture(canvas)
  tex.needsUpdate = true
  return tex
}

/** 落下定位标记（0.5s 从上方落下，保持 3s 后淡出） */
function showMarker(lng: number, lat: number) {
  if (!mapGroup) return
  const tex = makePinTexture()
  const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false, depthWrite: false })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(46, 63, 1)
  const base = localPos(lng, lat, 18)
  sprite.position.set(base.x, base.y, base.z + 20)
  mapGroup.add(sprite)
  locateMarker = sprite
  locateTweens.push(gsap.to(sprite.position, { z: base.z, duration: 0.5, ease: 'back.out(1.7)' }))
  locateFadeTween = gsap.to(mat, {
    opacity: 0,
    duration: 0.6,
    delay: 3,
    onComplete: () => {
      if (locateMarker === sprite && mapGroup) {
        mapGroup.remove(sprite)
        tex.dispose()
        mat.dispose()
        locateMarker = null
      }
    },
  })
  locateTweens.push(locateFadeTween)
}

/** 收到外部定位事件：飞行 + 高亮 + 落标记 */
function onLocate(payload: MapLocatePayload) {
  clearLocate()
  locateInfo.title = payload.title
  locateInfo.introUrl = payload.introUrl
  locateInfo.videoUrl = payload.videoUrl
  flyToLocation(payload.lng, payload.lat)
  highlightCity(payload.cityName)
  showMarker(payload.lng, payload.lat)
}

/** 是否点中定位标记（金色水滴） */
function hitLocateMarker(): boolean {
  if (!locateMarker || !camera || !raycaster) return false
  raycaster.setFromCamera(pointer, camera)
  return raycaster.intersectObject(locateMarker, false).length > 0
}

/** 每帧把定位标记投影到屏幕坐标（供弹窗跟随） */
function updateLocateScreen() {
  const wrap = wrapRef.value
  if (!wrap || !camera || !locateMarker) {
    locateScreen.ok = false
    return
  }
  locateMarker.getWorldPosition(locateVec)
  locateVec.project(camera)
  locateScreen.ok =
    locateVec.z < 1 && locateVec.x > -1.2 && locateVec.x < 1.2 && locateVec.y > -1.2 && locateVec.y < 1.2
  locateScreen.x = (locateVec.x * 0.5 + 0.5) * wrap.clientWidth
  locateScreen.y = (-locateVec.y * 0.5 + 0.5) * wrap.clientHeight
}

/** 打开弹窗中的外部链接 */
function openLocateUrl(url: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

/* ------------------------------ 动画 / 尺寸 / 销毁 ------------------------------ */
function animate() {
  rafId = requestAnimationFrame(animate)
  controls?.update()

  // 相机方位角变化 → 通知外部（指南针）
  const now = performance.now()
  if (controls && now - lastAzimuthEmit > 50) {
    lastAzimuthEmit = now
    emit('rotate', controls.getAzimuthalAngle())
  }
  // 先渲染，再依据最新相机矩阵更新标签投影（避免首帧错位）
  if (renderer && scene && camera) {
    renderer.render(scene, camera)
  }
  updateLabels()
  updateLocateScreen()
}

function onResize() {
  const wrap = wrapRef.value
  if (!wrap || !renderer || !camera) {
    return
  }
  const w = wrap.clientWidth
  const h = wrap.clientHeight || 1
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
  syncLineResolution()
}

function destroy() {
  clearLocate()
  cancelAnimationFrame(rafId)
  rafId = 0
  resizeObserver?.disconnect()
  resizeObserver = null
  controls?.dispose()
  controls = null

  if (scene) {
    scene.traverse((obj) => {
      const mesh = obj as THREE.Mesh
      if (mesh.geometry) {
        mesh.geometry.dispose()
      }
      const material = (mesh as THREE.Mesh).material
      if (Array.isArray(material)) {
        material.forEach((m) => m.dispose())
      } else if (material) {
        ;(material as THREE.Material).dispose()
      }
    })
    scene = null
  }
  if (renderer) {
    renderer.dispose()
    const dom = renderer.domElement
    dom.parentNode?.removeChild(dom)
    renderer = null
  }

  lineMaterials.length = 0
  ownedTextures.forEach((t) => t.dispose())
  ownedTextures.length = 0
  cityShapes.length = 0
  cityLines.length = 0
  labels.value = []
  terrain = null
  heightData = null
  maskTextureRef = null
  hoveredName = null
}

/* ------------------------------ 生命周期 ------------------------------ */
onMounted(async () => {
  const wrap = wrapRef.value
  if (!wrap) {
    return
  }
  mapBus.on('locate', onLocate)
  try {
    initRenderer(wrap)
    scene = new THREE.Scene()
    // 极淡雾效：远处有层次、不空洞
    scene.fog = new THREE.Fog(0x0f2540, 2000, 4500)
    raycaster = new THREE.Raycaster()
    initCamera(wrap)
    initLights()

    mapGroup = new THREE.Group()
    mapGroup.rotation.x = -Math.PI / 2 // 局部 +Z 变为世界向上
    scene.add(mapGroup)

    const [geo, satTex] = await Promise.all([loadSichuanGeo(), loadTexture(satelliteUrl)])
    await loadHeightmap()

    const maskTex = makeMaskTexture(geo, 1024)
    maskTextureRef = maskTex
    ownedTextures.push(maskTex, satTex)

    buildTerrain(satTex, maskTex)
    buildWalls(geo as SichuanGeo)
    buildCityLines(geo as SichuanGeo)
    buildFlyLines()
    buildPeakLabels(geo as SichuanGeo)
    buildLabels()
    syncLineResolution()

    loading.value = false

    resizeObserver = new ResizeObserver(onResize)
    resizeObserver.observe(wrap)

    animate()
    emit('ready')
  } catch (err) {
    loading.value = false
    error.value = err instanceof Error ? err.message : '3D 场景初始化失败'
  }
})

/** 加载卫星贴图 */
async function loadTexture(url: string): Promise<THREE.Texture> {
  const img = await loadImage(url)
  const tex = new THREE.Texture(img)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.wrapS = THREE.ClampToEdgeWrapping
  tex.wrapT = THREE.ClampToEdgeWrapping
  if (renderer) {
    tex.anisotropy = renderer.capabilities.getMaxAnisotropy()
  }
  tex.needsUpdate = true
  return tex
}

onBeforeUnmount(() => {
  mapBus.off('locate', onLocate)
  destroy()
})

defineExpose({ resetView })
/** 复位视角 */
function resetView() {
  if (camera && controls) {
    camera.position.set(0, 533, 800)
    controls.target.set(0, 0, 0)
    controls.update()
  }
}
</script>

<template>
  <div
    ref="wrapRef"
    class="scene"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointerleave="onPointerLeave"
  >
    <div v-if="loading" class="scene__mask">正在加载地形…</div>
    <div v-else-if="error" class="scene__mask scene__mask--error">{{ error }}</div>

    <!-- 市州名称标签：HTML 覆盖层，拒绝指针事件透传到 3D 画布 -->
    <div v-if="!loading && !error" class="map-labels">
      <button
        v-for="lb in labels"
        v-show="lb.visible"
        :key="lb.name"
        type="button"
        class="city-label"
        :style="{ left: lb.sx + 'px', top: lb.sy + 'px' }"
        @pointerdown.stop
        @pointerup.stop
        @click.stop="onLabelClick(lb.name)"
      >
        <span v-if="lb.isCapital" class="city-label__star" aria-hidden="true">★</span>
        {{ lb.name }}
      </button>
    </div>

    <div v-show="tip.visible" class="scene__tip" :style="{ left: `${tip.x}px`, top: `${tip.y}px` }">
      <b>{{ tip.name }}</b>
      <span>景点 {{ tip.scenic }} 个</span>
    </div>

    <!-- 定位标记弹窗：选择「视频 / 简介」（点中定位标后出现） -->
    <div
      v-if="locatePopup && locateScreen.ok && !loading && !error"
      class="locate-pop"
      :style="{ left: `${locateScreen.x}px`, top: `${locateScreen.y}px` }"
      @pointerdown.stop
      @pointerup.stop
    >
      <button type="button" class="locate-pop__close" title="关闭" @click="locatePopup = false">×</button>
      <p class="locate-pop__title">{{ locateInfo.title }}</p>
      <div class="locate-pop__btns">
        <button
          type="button"
          class="locate-pop__btn"
          :disabled="!locateInfo.videoUrl"
          @click="openLocateUrl(locateInfo.videoUrl)"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" stroke="none">
            <path d="M8 5v14l11-7z" />
          </svg>
          视频
        </button>
        <button
          type="button"
          class="locate-pop__btn"
          :disabled="!locateInfo.introUrl"
          @click="openLocateUrl(locateInfo.introUrl)"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 6C10 4.5 7 4 4 4v14c3 0 6 .5 8 2 2-1.5 5-2 8-2V4c-3 0-6 .5-8 2Z" />
            <path d="M12 6v14" />
          </svg>
          简介
        </button>
      </div>
    </div>

    <p v-if="!loading && !error" class="scene__hint">{{ hintText }}</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  cursor: grab;
}

.scene__mask {
  position: absolute;
  top: 50%;
  left: 50%;
  padding: 10px 22px;
  border: 1px solid rgba(58, 140, 220, 0.4);
  border-radius: 8px;
  background: rgba(15, 37, 64, 0.92);
  font-family: $font-mono;
  font-size: 13px;
  color: #ffffff;
  transform: translate(-50%, -50%);

  &--error {
    color: #ff8a8a;
  }
}

.scene__tip {
  position: absolute;
  z-index: 5;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.28);
  font-size: 12px;
  color: #1a1a1a;
  pointer-events: none;
  white-space: nowrap;

  b {
    display: block;
    margin-bottom: 2px;
    font-family: $font-mono;
    font-size: 13px;
    color: #1a1a1a;
  }

  span {
    color: #555;
  }
}

/* ---------------- 定位标记弹窗（整体与字号放大 0.5 倍） ---------------- */
.locate-pop {
  position: absolute;
  z-index: 6;
  min-width: calc(148px * 1.5);
  padding: calc(10px * 1.5) calc(12px * 1.5) calc(12px * 1.5);
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: calc(8px * 1.5);
  background: rgba(15, 37, 64, 0.96);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.45);
  text-align: center;
  transform: translate(-50%, calc(-100% - 63px));
}

.locate-pop__close {
  position: absolute;
  top: calc(4px * 1.5);
  right: calc(6px * 1.5);
  width: calc(18px * 1.5);
  height: calc(18px * 1.5);
  border: none;
  background: transparent;
  font-size: calc(15px * 1.5);
  line-height: 1;
  color: #7a95b8;
  cursor: pointer;

  &:hover {
    color: #d4b374;
  }
}

.locate-pop__title {
  margin-bottom: calc(8px * 1.5);
  font-family: $font-title;
  font-size: calc(16px * 1.5);
  color: #ffffff;
  white-space: nowrap;
}

.locate-pop__btns {
  display: flex;
  gap: calc(8px * 1.5);
}

.locate-pop__btn {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: calc(4px * 1.5);
  height: calc(30px * 1.5);
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: calc(4px * 1.5);
  background: transparent;
  font-family: $font-body;
  font-size: calc(12px * 1.5);
  color: #d4b374;
  cursor: pointer;
  transition:
    background-color 0.3s $ease-out-expo,
    border-color 0.3s $ease-out-expo;

  &:hover:not(:disabled) {
    border-color: #d4b374;
    background: rgba(201, 168, 106, 0.15);
  }

  &:disabled {
    border-color: rgba(201, 168, 106, 0.25);
    color: rgba(212, 179, 116, 0.45);
    cursor: not-allowed;
  }
}

.scene__hint {
  position: absolute;
  bottom: 16px;
  left: 50%;
  font-family: $font-mono;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: #7a95b8;
  transform: translateX(-50%);
  pointer-events: none;
}

/* ---------------- 市州名称标签 ---------------- */
.map-labels {
  position: absolute;
  inset: 0;
  z-index: 4;
  overflow: hidden;
  pointer-events: none;
}

.city-label {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.6);
  font-family: $font-title;
  font-size: 18px;
  font-weight: 500;
  line-height: 1;
  color: #ffffff;
  white-space: nowrap;
  cursor: pointer;
  transform: translate(-50%, -100%);
  pointer-events: auto;
  transition:
    background-color 0.2s $ease-out-expo,
    color 0.2s $ease-out-expo;

  &:hover {
    background: #d4b374;
    color: #1a1a1a;
  }
}

.city-label__star {
  font-size: 16px;
  color: #ffd166;
}

.city-label:hover .city-label__star {
  color: #b8860b;
}
</style>
