/**
 * 3D 地图联动事件总线（mapBus）
 *
 * 用途：让非兄弟关系的组件（如「蜀地知多少」面板 → 3D 场景）以松耦合方式通信，
 * 避免层层 props / emit。基于 mitt，仅暴露 on / off / emit。
 */
import mitt, { type Emitter } from 'mitt'

/** 地图定位事件载荷 */
export interface MapLocatePayload {
  /** 目标经度 */
  lng: number
  /** 目标纬度 */
  lat: number
  /** 所属市州名（用于高亮该市州边界） */
  cityName: string
  /** 地点名称（定位落点弹窗标题） */
  title: string
  /** 简介链接（弹窗「简介」按钮） */
  introUrl: string
  /** 视频链接（弹窗「视频」按钮） */
  videoUrl: string
}

/** 事件定义：事件名 → 载荷类型 */
export type MapBusEvents = {
  /** 请求 3D 地图定位到指定地点 */
  locate: MapLocatePayload
}

/** 全局地图事件总线 */
export const mapBus: Emitter<MapBusEvents> = mitt<MapBusEvents>()
