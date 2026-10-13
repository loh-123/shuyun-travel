/**
 * 非物质文化遗产相关接口
 */
import request from './request'
import type { PageResult } from './request'

/** 非遗列表项 */
export interface HeritageVO {
  id: number
  name: string
  category?: string
  level?: string
  inheritor?: string
  summary?: string
  /** 封面图（列表卡片，取图片集第一张） */
  coverImage?: string
}

/** 非遗详情（列表项字段 + 详情字段） */
export interface HeritageDetailVO extends HeritageVO {
  website?: string
  videoUrl?: string
  /** 图片集（后端已是 /images/heritage/... 完整路径） */
  images?: string[]
}

/** 非遗列表查询参数 */
export interface HeritageListParams {
  page?: number
  size?: number
  category?: string
  level?: string
  keyword?: string
}

/**
 * 非遗分页列表
 */
export function listHeritage(params: HeritageListParams = {}) {
  return request
    .get<PageResult<HeritageVO>>('/heritage/list', { params, silent: true })
    .then((res) => res.data)
}

/**
 * 非遗详情
 */
export function getHeritageDetail(id: number | string) {
  return request
    .get<HeritageDetailVO>(`/heritage/${id}`, { silent: true })
    .then((res) => res.data)
}
