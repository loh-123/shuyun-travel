/**
 * 四川省 GeoJSON 加载与缓存
 *
 * 加载优先级（逐级降级，任一成功即返回）：
 * 1. 本地静态文件（可选，放到 src/assets/geo/sichuan.json，构建时自动打包）
 * 2. 本地 /geo/sichuan.json（放到 public/geo/sichuan.json，可完全离线）
 * 3. DataV.GeoAtlas 在线接口
 * 4. geojson.cn 在线接口
 *
 * 首次成功拉取后写入模块内存与 sessionStorage 缓存，刷新页面不再重复请求。
 */

/** 单个 GeoJSON Feature（仅声明用到的字段） */
export interface GeoFeature {
  type: string
  properties: { name?: string; [key: string]: unknown }
  geometry: {
    type: string
    /** Polygon: number[][][] ； MultiPolygon: number[][][][] */
    coordinates: number[][][] | number[][][][]
  }
}

/** 四川省 FeatureCollection */
export interface SichuanGeo {
  type: string
  features: GeoFeature[]
}

const CACHE_KEY = 'shuyun_sichuan_geo_v1'

/** 在线数据源（按顺序尝试） */
const ONLINE_URLS = [
  'https://geo.datav.aliyun.com/areas_v3/bound/510000_full.json',
  'https://geojson.cn/api/data/510000.json',
]

/** 本地静态文件（可选；文件不存在时 glob 返回空对象，不影响构建） */
const LOCAL_ASSETS = import.meta.glob<{ default: SichuanGeo }>('../../assets/geo/sichuan.json')

let memory: SichuanGeo | null = null

/** 校验是否是一份可用的四川省 GeoJSON */
function isValid(geo: unknown): geo is SichuanGeo {
  const g = geo as SichuanGeo
  return !!g && Array.isArray(g.features) && g.features.length > 0
}

/**
 * 拉取并缓存四川省 GeoJSON
 *
 * @returns 四川省 FeatureCollection
 * @throws 所有数据源都失败时抛出异常
 */
export async function loadSichuanGeo(): Promise<SichuanGeo> {
  if (memory) {
    return memory
  }

  // 1) 会话缓存
  try {
    const cached = sessionStorage.getItem(CACHE_KEY)
    if (cached) {
      const parsed = JSON.parse(cached) as unknown
      if (isValid(parsed)) {
        memory = parsed
        return parsed
      }
    }
  } catch {
    // 忽略缓存读取异常
  }

  // 2) 本地 src/assets（若人工放置）
  const localKeys = Object.keys(LOCAL_ASSETS)
  if (localKeys.length) {
    try {
      const mod = await LOCAL_ASSETS[localKeys[0]]()
      if (isValid(mod?.default)) {
        memory = mod.default
        cacheToSession(mod.default)
        return mod.default
      }
    } catch {
      // 继续尝试其它来源
    }
  }

  // 3) 本地 public/geo 静态文件
  const candidates = ['/geo/sichuan.json', ...ONLINE_URLS]
  for (const url of candidates) {
    try {
      const res = await fetch(url)
      if (!res.ok) {
        continue
      }
      const geo = (await res.json()) as unknown
      if (isValid(geo)) {
        memory = geo
        cacheToSession(geo)
        return geo
      }
    } catch {
      // 单个数据源失败则尝试下一个
    }
  }

  throw new Error('四川省 GeoJSON 加载失败')
}

/** 写入 sessionStorage（可能超出配额，忽略异常） */
function cacheToSession(geo: SichuanGeo) {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(geo))
  } catch {
    // 超出配额或隐私模式，忽略
  }
}
