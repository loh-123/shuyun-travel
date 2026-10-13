/**
 * 文化相关接口
 */
import request from './request'

/** 文化列表项 */
export interface CultureVO {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  tags?: string
}

/** 文化详情（列表项字段 + 详情字段） */
export interface CultureDetailVO extends CultureVO {
  website?: string
  content?: string
  videoUrl?: string
  /** 相关景点（逗号分隔的名称文本） */
  relatedScenic?: string
  /** 相关人物（逗号分隔） */
  relatedPerson?: string
  /** 图片集（后端已是 /images/culture/... 完整路径） */
  images?: string[]
}

/**
 * 全部文化条目（不分页）
 */
export function listCulture() {
  return request
    .get<CultureVO[]>('/culture/list', { silent: true })
    .then((res) => res.data)
}

/**
 * 文化详情
 */
export function getCultureDetail(id: number | string) {
  return request
    .get<CultureDetailVO>(`/culture/${id}`, { silent: true })
    .then((res) => res.data)
}

/**
 * 按分类查询文化条目
 */
export function listCultureByCategory(category: string) {
  return request
    .get<CultureVO[]>(`/culture/category/${encodeURIComponent(category)}`, { silent: true })
    .then((res) => res.data)
}
