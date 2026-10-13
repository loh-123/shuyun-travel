/**
 * 数据大屏 Pinia Store
 *
 * 统一管理大屏各模块数据：页面加载时并发拉取（Promise.all），
 * 并提供各模块的单独刷新方法（供定时轮询与 SSE 调用）。
 */
import { defineStore } from 'pinia'
import adminRequest from '@/admin/api/adminRequest'

/** 名称-数值 */
export interface NameValue {
  name: string
  value: number
}

/** 地图城市点位 */
export interface CityPoint {
  name: string
  value: number
  lng: number
  lat: number
  scenicCount: number
  visitCount: number
}

/** 实时流条目 */
export interface StreamItem {
  username?: string
  avatar?: string
  action?: string
  target?: string
  time?: string
}

/** 关键指标 */
export interface Overview {
  userCount: number
  visitCount: number
  scenicCount: number
  aiChatCount: number
  userSpark: number[]
  visitSpark: number[]
  scenicSpark: number[]
  chatSpark: number[]
}

/** 用户画像 */
export interface UserProfile {
  total: number
  age: NameValue[]
  gender: NameValue[]
  region: NameValue[]
}

/** 访问趋势点 */
export interface TrendPoint {
  time: string
  value: number
}

export const useScreenStore = defineStore('screen', {
  state: () => ({
    /** 是否已完成首次加载 */
    loaded: false,
    /** 关键指标 */
    overview: {
      userCount: 0,
      visitCount: 0,
      scenicCount: 0,
      aiChatCount: 0,
      userSpark: [] as number[],
      visitSpark: [] as number[],
      scenicSpark: [] as number[],
      chatSpark: [] as number[],
    } as Overview,
    /** 文化热度 */
    cultureRank: [] as NameValue[],
    /** 用户画像 */
    userProfile: { total: 0, age: [], gender: [], region: [] } as UserProfile,
    /** 地图数据 */
    mapData: [] as CityPoint[],
    /** 实时流 */
    realtime: [] as StreamItem[],
    /** 访问趋势 */
    visitTrend: [] as TrendPoint[],
    /** 热门景点 */
    hotScenic: [] as NameValue[],
    /** 词云 */
    wordCloud: [] as NameValue[],
    /** 滚动通知 */
    notice: [] as string[],
  }),

  actions: {
    /**
     * 并发拉取全部接口（页面首屏）
     */
    async loadAll() {
      const [overview, cultureRank, userProfile, mapData, realtime, visitTrend, hotScenic, wordCloud, notice] =
        await Promise.all([
          this.get<Overview>('/screen/overview'),
          this.get<NameValue[]>('/screen/culture-rank'),
          this.get<UserProfile>('/screen/user-profile'),
          this.get<CityPoint[]>('/screen/map-data'),
          this.get<StreamItem[]>('/screen/real-time', { limit: 10 }),
          this.get<TrendPoint[]>('/screen/visit-trend'),
          this.get<NameValue[]>('/screen/hot-scenic', { limit: 10 }),
          this.get<NameValue[]>('/screen/word-cloud'),
          this.get<string[]>('/screen/notice'),
        ])
      if (overview) this.overview = overview
      if (cultureRank) this.cultureRank = cultureRank
      if (userProfile) this.userProfile = userProfile
      if (mapData) this.mapData = mapData
      if (realtime) this.realtime = realtime
      if (visitTrend) this.visitTrend = visitTrend
      if (hotScenic) this.hotScenic = hotScenic
      if (wordCloud) this.wordCloud = wordCloud
      if (notice) this.notice = notice
      this.loaded = true
    },

    /** 刷新关键指标 */
    async refreshOverview() {
      const data = await this.get<Overview>('/screen/overview')
      if (data) this.overview = data
    },

    /** 刷新文化热度 */
    async refreshCultureRank() {
      const data = await this.get<NameValue[]>('/screen/culture-rank')
      if (data) this.cultureRank = data
    },

    /** 刷新用户画像 */
    async refreshUserProfile() {
      const data = await this.get<UserProfile>('/screen/user-profile')
      if (data) this.userProfile = data
    },

    /** 刷新地图数据 */
    async refreshMapData() {
      const data = await this.get<CityPoint[]>('/screen/map-data')
      if (data) this.mapData = data
    },

    /** 刷新访问趋势 */
    async refreshVisitTrend() {
      const data = await this.get<TrendPoint[]>('/screen/visit-trend')
      if (data) this.visitTrend = data
    },

    /** 刷新热门景点 */
    async refreshHotScenic() {
      const data = await this.get<NameValue[]>('/screen/hot-scenic', { limit: 10 })
      if (data) this.hotScenic = data
    },

    /** 刷新词云 */
    async refreshWordCloud() {
      const data = await this.get<NameValue[]>('/screen/word-cloud')
      if (data) this.wordCloud = data
    },

    /** 刷新滚动通知 */
    async refreshNotice() {
      const data = await this.get<string[]>('/screen/notice')
      if (data) this.notice = data
    },

    /** 轮询实时流（SSE 不可用时的兜底） */
    async refreshRealtime() {
      const data = await this.get<StreamItem[]>('/screen/real-time', { limit: 10 })
      if (data) this.applyRealtime(data)
    },

    /**
     * 应用实时流推送：新数据从顶部插入，最多保留 10 条
     *
     * @param items 推送的实时数据
     */
    applyRealtime(items: StreamItem[]) {
      if (!items || !items.length) return
      this.realtime = items.slice(0, 10)
    },

    /**
     * 统一 GET 封装：失败返回 null，避免单个接口异常影响整体
     *
     * @param url    接口路径（相对 /api）
     * @param params 查询参数
     */
    async get<T>(url: string, params?: Record<string, unknown>): Promise<T | null> {
      try {
        const res = await adminRequest.get(url, { params })
        return (res.data ?? null) as T | null
      } catch {
        return null
      }
    },
  },
})
