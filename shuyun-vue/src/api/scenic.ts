/**
 * 景点相关接口
 */
import request from './request'
import type { PageResult } from './request'

/** 景点列表项 */
export interface ScenicSpotVO {
  id: number
  name: string
  cityName?: string
  type?: string
  level?: string
  coverImage?: string
  description?: string
  score?: number
  viewCount?: number
  tags?: string
  recommendReason?: string
  /** 地址（供城市 3D 页面按区县归集） */
  address?: string
  /** 经度（供城市 3D 页面 POI 打点） */
  longitude?: number | string | null
  /** 纬度（供城市 3D 页面 POI 打点） */
  latitude?: number | string | null
}

/** 景点详情（列表项字段 + 详情字段） */
export interface ScenicDetailVO extends ScenicSpotVO {
  address?: string
  website?: string
  longitude?: number | string | null
  latitude?: number | string | null
  openTime?: string
  ticketInfo?: string
  /** 景点视频链接（B站 / 央视网等，可能为空） */
  videoUrl?: string
  /** 详情图（后端已是 /images/scenic/... 完整路径） */
  images?: string[]
  /** 同城推荐 */
  recommends?: ScenicSpotVO[]
}

/** 景点列表查询参数 */
export interface ScenicListParams {
  page?: number
  size?: number
  cityId?: number | string
  /** 市州名称（后端会换算为 cityId，可与 cityId 二选一） */
  cityName?: string
  type?: string
  keyword?: string
  /** 排序：default / score / hot（后端 pageScenic 支持） */
  sort?: string
}

/**
 * 景点分页列表
 */
export function listScenic(params: ScenicListParams = {}) {
  return request
    .get<PageResult<ScenicSpotVO>>('/scenic/list', { params, silent: true })
    .then((res) => res.data)
}

/**
 * 景点详情
 */
export function getScenicDetail(id: number | string) {
  return request
    .get<ScenicDetailVO>(`/scenic/${id}`, { silent: true })
    .then((res) => res.data)
}

/**
 * 热门景点（按评分倒序）
 */
export function getHotScenic(limit = 6) {
  return request
    .get<ScenicSpotVO[]>('/scenic/hot', { params: { limit }, silent: true })
    .then((res) => res.data)
}

/**
 * 城市列表（景点表中出现过的市州名称）
 */
export function listCities() {
  return request
    .get<string[]>('/scenic/cities', { silent: true })
    .then((res) => res.data)
}

/** 市州 id / 名称（来自 /city/list） */
export interface CityOption {
  id: number
  name: string
  /** 行政区划代码（如 510100，用于取区县 GeoJSON） */
  code?: string
  /** 简介 */
  description?: string
  longitude?: number | string | null
  latitude?: number | string | null
}

/**
 * 全部市州（city 表全量，含 id 与名称）
 *
 * <p>用于把「城市名」换算成后端 /scenic/list 过滤所需的 cityId。</p>
 */
export function listAllCities() {
  return request
    .get<CityOption[]>('/city/list', { silent: true })
    .then((res) => res.data)
}
