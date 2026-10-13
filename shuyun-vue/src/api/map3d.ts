/**
 * 3D 四川数字地图相关接口
 *
 * 复用大屏接口 /api/screen/map-data：
 * 返回 21 市州的热度与数量统计，供 3D 地图的高度、悬停提示与信息面板使用。
 */
import request from './request'

/** 市州地图点位（与后端 /api/screen/map-data 字段一致） */
export interface MapCityPoint {
  /** 市州ID（用于面板拉取该市州景点列表） */
  id: number
  /** 市州短名，如「成都」「阿坝」 */
  name: string
  /** 热力值 */
  value: number
  /** 经度 */
  lng: number
  /** 纬度 */
  lat: number
  /** 景点数 */
  scenicCount: number
  /** 访问量 */
  visitCount: number
  /** 美食数 */
  foodCount: number
  /** 非遗数（近似统计） */
  heritageCount: number
}

/**
 * 21 市州地图数据
 */
export function getMapData() {
  return request
    .get<MapCityPoint[]>('/screen/map-data', { silent: true })
    .then((res) => res.data)
}

/** 大屏关键指标（左上角总览卡片使用） */
export interface ScreenOverview {
  userCount: number
  visitCount: number
  scenicCount: number
  aiChatCount: number
  /** 美食总数 */
  foodCount: number
  /** 非遗总数 */
  heritageCount: number
  /** 文化总数 */
  cultureCount: number
  /** 景点累计浏览量（游客数） */
  viewSum: number
}

/**
 * 数据大屏关键指标
 */
export function getScreenOverview() {
  return request
    .get<ScreenOverview>('/screen/overview', { silent: true })
    .then((res) => res.data)
}

/** 实时行为流条目 */
export interface StreamItem {
  username?: string
  avatar?: string
  action?: string
  target?: string
  time?: string
}

/**
 * 实时用户行为流
 */
export function getRealTime(limit = 10) {
  return request
    .get<StreamItem[]>('/screen/real-time', { params: { limit }, silent: true })
    .then((res) => res.data)
}
