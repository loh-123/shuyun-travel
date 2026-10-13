/**
 * 美食相关接口
 */
import request from './request'
import type { PageResult } from './request'

/** 美食列表项 */
export interface FoodVO {
  id: number
  name: string
  category?: string
  cityName?: string
  coverImage?: string
  description?: string
  spicyLevel?: number
  avgPrice?: number
  shopName?: string
  /** 店铺地址（供城市 3D 页面按区县归集） */
  shopAddress?: string
  /** 店铺经度（供城市 3D 页面 POI 打点） */
  shopLongitude?: number | string | null
  /** 店铺纬度（供城市 3D 页面 POI 打点） */
  shopLatitude?: number | string | null
}

/** 美食详情（列表项字段 + 详情字段） */
export interface FoodDetailVO extends FoodVO {
  videoUrl?: string
  history?: string
  recommendIndex?: number
  shopAddress?: string
  shopLongitude?: number | string | null
  shopLatitude?: number | string | null
  shopImage?: string
}

/** 美食列表查询参数 */
export interface FoodListParams {
  page?: number
  size?: number
  category?: string
  cityName?: string
  keyword?: string
}

/**
 * 美食分页列表
 */
export function listFood(params: FoodListParams = {}) {
  return request
    .get<PageResult<FoodVO>>('/food/list', { params, silent: true })
    .then((res) => res.data)
}

/**
 * 美食详情
 */
export function getFoodDetail(id: number | string) {
  return request
    .get<FoodDetailVO>(`/food/${id}`, { silent: true })
    .then((res) => res.data)
}

/**
 * 美食分类列表（动态去重）
 */
export function listFoodCategories() {
  return request
    .get<string[]>('/food/categories', { silent: true })
    .then((res) => res.data)
}
