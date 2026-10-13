/**
 * 抓取四川省卫星影像 + 高程数据，拼成两张贴图
 *
 * 输出到 shuyun-vue/src/assets/map/：
 *   - sichuan-satellite.jpg  (2048x2048)  真实卫星影像
 *   - sichuan-heightmap.png  (1024x1024)  灰度高程（越白越高）
 *
 * 数据源（均免费、无需 key）：
 *   卫星：ArcGIS World_Imagery
 *   高程：AWS Terrarium（height = R*256 + G + B/256 - 32768）
 *
 * 用法：node scripts/fetch-map-textures.mjs
 */
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const OUT_DIR = path.join(ROOT, 'src/assets/map')
const CACHE_DIR = path.join(__dirname, '.cache')

/** 四川经纬度范围 */
const BBOX = { minLon: 97.3, maxLon: 108.5, minLat: 26.0, maxLat: 34.3 }
/** 卫星图最大瓦片数（10 => zoom8，2304px 源，下采样到 2048 更锐） */
const SAT_MAX_TILES = 10
/** 高程图最大瓦片数（6 => zoom7，1280px 源） */
const DEM_MAX_TILES = 6
/** 瓦片像素 */
const PX = 256
/** 并发数 */
const CONCURRENCY = 6

const SAT_URL = (z, x, y) =>
  `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${z}/${y}/${x}`
const DEM_URL = (z, x, y) => `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/${z}/${x}/${y}.png`

/* ------------------------------ Web Mercator ------------------------------ */
const lon2x = (lon, z) => ((lon + 180) / 360) * 2 ** z
const lat2y = (lat, z) => {
  const r = (lat * Math.PI) / 180
  return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** z
}

/** 选一个最大但不超 maxTiles 的缩放级别 */
function pickZoom(maxTiles) {
  for (let z = 13; z >= 0; z--) {
    const w = Math.floor(lon2x(BBOX.maxLon, z)) - Math.floor(lon2x(BBOX.minLon, z)) + 1
    const h = Math.floor(lat2y(BBOX.minLat, z)) - Math.floor(lat2y(BBOX.maxLat, z)) + 1
    if (w <= maxTiles && h <= maxTiles) return z
  }
  return 0
}

function tileRange(z) {
  return {
    tx0: Math.floor(lon2x(BBOX.minLon, z)),
    tx1: Math.floor(lon2x(BBOX.maxLon, z)),
    ty0: Math.floor(lat2y(BBOX.maxLat, z)),
    ty1: Math.floor(lat2y(BBOX.minLat, z)),
  }
}

/** 拼接大图里对应 bbox 的裁剪矩形 */
function cropRect(z, r) {
  const left = Math.round((lon2x(BBOX.minLon, z) - r.tx0) * PX)
  const right = Math.round((lon2x(BBOX.maxLon, z) - r.tx0) * PX)
  const top = Math.round((lat2y(BBOX.maxLat, z) - r.ty0) * PX)
  const bottom = Math.round((lat2y(BBOX.minLat, z) - r.ty0) * PX)
  return { left, top, width: right - left, height: bottom - top }
}

/* ------------------------------ 下载（带缓存 + 重试） ------------------------------ */
async function fetchTile(urlFn, kind, z, x, y) {
  await mkdir(CACHE_DIR, { recursive: true })
  const ext = kind === 'dem' ? 'png' : 'jpg'
  const file = path.join(CACHE_DIR, `${kind}_${z}_${x}_${y}.${ext}`)
  if (existsSync(file)) return readFile(file)

  const url = kind === 'dem' ? DEM_URL(z, x, y) : SAT_URL(z, x, y)
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(url)
      if (res.ok) {
        const buf = Buffer.from(await res.arrayBuffer())
        await writeFile(file, buf)
        return buf
      }
    } catch {
      /* 重试 */
    }
    await new Promise((r) => setTimeout(r, 400 * attempt))
  }
  return null
}

/** 有限并发地跑任务 */
async function mapLimit(items, limit, worker) {
  const results = new Array(items.length)
  let i = 0
  async function run() {
    while (i < items.length) {
      const idx = i++
      results[idx] = await worker(items[idx], idx)
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, run))
  return results
}

/** 拼接瓦片成一张大图（返回 { buffer, failed } ） */
async function stitch(kind, z, r) {
  const coords = []
  for (let x = r.tx0; x <= r.tx1; x++) {
    for (let y = r.ty0; y <= r.ty1; y++) coords.push({ x, y })
  }
  let failed = 0
  let done = 0
  const tiles = await mapLimit(coords, CONCURRENCY, async (c) => {
    const buf = await fetchTile(null, kind, z, c.x, c.y)
    done++
    if (!buf) failed++
    if (done % 10 === 0 || done === coords.length) {
      process.stdout.write(`\r  ${kind} 瓦片 ${done}/${coords.length} (失败 ${failed})   `)
    }
    return { ...c, buf }
  })
  process.stdout.write('\n')

  const totalW = (r.tx1 - r.tx0 + 1) * PX
  const totalH = (r.ty1 - r.ty0 + 1) * PX
  const channels = kind === 'dem' ? 4 : 3
  const background =
    kind === 'dem' ? { r: 0, g: 0, b: 0, alpha: 0 } : { r: 0, g: 0, b: 0 }

  const valid = tiles.filter((t) => t.buf)
  const canvas = sharp({ create: { width: totalW, height: totalH, channels, background } })
  const composites = valid.map((t) => ({
    input: t.buf,
    left: (t.x - r.tx0) * PX,
    top: (t.y - r.ty0) * PX,
  }))
  const buffer = await canvas.composite(composites).raw().toBuffer()
  return { buffer, width: totalW, height: totalH, failed }
}

/* ------------------------------ 主流程 ------------------------------ */
async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const satOut = path.join(OUT_DIR, 'sichuan-satellite.jpg')
  const demOut = path.join(OUT_DIR, 'sichuan-heightmap.png')

  /* ---- 1. 卫星影像 ---- */
  const satZ = pickZoom(SAT_MAX_TILES)
  const satR = tileRange(satZ)
  const satCrop = cropRect(satZ, satR)
  console.log(`[卫星] zoom=${satZ} 瓦片网格=${satR.tx1 - satR.tx0 + 1}x${satR.ty1 - satR.ty0 + 1}`)
  const sat = await stitch('sat', satZ, satR)
  if (sat.failed > 0) console.log(`[卫星] 有 ${sat.failed} 张瓦片下载失败（已留黑）`)
  const satStitched = sharp(sat.buffer, { raw: { width: sat.width, height: sat.height, channels: 3 } })
    .extract(satCrop)
    .resize(2048, 2048, { fit: 'fill' })
    .jpeg({ quality: 86, mozjpeg: true })
  await satStitched.toFile(satOut)
  console.log(`[卫星] 已输出 ${satOut}`)

  /* ---- 2. 高程 ---- */
  const demZ = pickZoom(DEM_MAX_TILES)
  const demR = tileRange(demZ)
  const demCrop = cropRect(demZ, demR)
  console.log(`[高程] zoom=${demZ} 瓦片网格=${demR.tx1 - demR.tx0 + 1}x${demR.ty1 - demR.ty0 + 1}`)
  const dem = await stitch('dem', demZ, demR)
  if (dem.failed > 0) console.log(`[高程] 有 ${dem.failed} 张瓦片下载失败（已留空）`)

  const size = 1024
  const { data, info } = await sharp(dem.buffer, {
    raw: { width: dem.width, height: dem.height, channels: 4 },
  })
    .extract(demCrop)
    .resize(size, size, { fit: 'fill' })
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true })

  const n = info.width * info.height
  const heights = new Float32Array(n)
  let minH = Infinity
  let maxH = -Infinity
  for (let i = 0; i < n; i++) {
    const r = data[i * 4]
    const g = data[i * 4 + 1]
    const b = data[i * 4 + 2]
    const a = data[i * 4 + 3]
    let h = r * 256 + g + b / 256 - 32768
    if (a === 0 || h < -1000) h = -1000 // 无数据
    heights[i] = h
    if (h > -1000) {
      if (h < minH) minH = h
      if (h > maxH) maxH = h
    }
  }
  // 归一化到 0-255 灰度（越白越高）
  const gray = Buffer.alloc(n)
  const span = Math.max(1, maxH - minH)
  for (let i = 0; i < n; i++) {
    const v = heights[i] <= -1000 ? 0 : Math.round(((heights[i] - minH) / span) * 255)
    gray[i] = Math.max(0, Math.min(255, v))
  }
  await sharp(gray, { raw: { width: size, height: size, channels: 1 } }).png().toFile(demOut)
  console.log(`[高程] 海拔范围 ${Math.round(minH)}m ~ ${Math.round(maxH)}m，已输出 ${demOut}`)

  console.log('\n完成：')
  console.log('  ' + satOut)
  console.log('  ' + demOut)
}

main().catch((err) => {
  console.error('\n抓取失败：', err?.message || err)
  console.error('（若为网络问题，可手动准备两张图放到 src/assets/map/，命名见脚本顶部注释）')
  process.exitCode = 1
})
