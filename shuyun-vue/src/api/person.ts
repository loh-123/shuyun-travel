/**
 * 传承人相关接口
 */
import request from './request'

/** 传承人列表项 */
export interface CulturePersonVO {
  id: number
  name: string
  avatar?: string
  /** 代表文化名称 */
  cultureName?: string
  /** 简介（后端已截断到 60 字） */
  brief?: string
}

/** 传承人详情 */
export interface CulturePersonDetailVO {
  id: number
  name: string
  avatar?: string
  /** 简介全文 */
  brief?: string
  /** 代表文化名称 */
  cultureName?: string
  /** 关联文化ID */
  cultureId?: number
  videoUrl?: string
}

/**
 * 按文化 id 查询传承人列表
 */
export function listPersonByCulture(cultureId: number | string) {
  return request
    .get<CulturePersonVO[]>('/person/list', { params: { cultureId }, silent: true })
    .then((res) => res.data)
}

/**
 * 传承人详情
 */
export function getPersonDetail(id: number | string) {
  return request
    .get<CulturePersonDetailVO>(`/person/${id}`, { silent: true })
    .then((res) => res.data)
}

/**
 * 全部传承人（限量）
 */
export function listAllPerson(limit = 20) {
  return request
    .get<CulturePersonVO[]>('/person/all', { params: { limit }, silent: true })
    .then((res) => res.data)
}
