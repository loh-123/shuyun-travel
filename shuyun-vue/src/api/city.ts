/**
 * 城市 3D 页面相关接口
 *
 * 后端按「市州 + 区县」聚合景点 / 美食 / 非遗，供城市 3D 页面的右侧面板使用。
 * 区县归属由后端从地址字段解析（scenic.address / food.shop_address）。
 */
import request from './request'

/** 区县统计项：景点 */
export interface DistrictScenic {
  id: number
  name: string
  type?: string
  coverImage?: string
  description?: string
  address?: string
  longitude?: number | string | null
  latitude?: number | string | null
}

/** 区县统计项：美食 */
export interface DistrictFood {
  id: number
  name: string
  category?: string
  coverImage?: string
  description?: string
  shopName?: string
  shopAddress?: string
  longitude?: number | string | null
  latitude?: number | string | null
}

/** 区县统计项：非遗 */
export interface DistrictHeritage {
  id: number
  name: string
  category?: string
  level?: string
  summary?: string
}

/** 区县统计结果 */
export interface DistrictStats {
  cityName: string
  districtName?: string
  scenicCount: number
  foodCount: number
  heritageCount: number
  scenics: DistrictScenic[]
  foods: DistrictFood[]
  heritages: DistrictHeritage[]
}

/**
 * 区县统计与明细
 *
 * @param cityName     市州名称（如「成都」）
 * @param districtName 区县名称（如「武侯区」）；为空时返回该市全部
 */
export function getDistrictStats(cityName: string, districtName?: string) {
  return request
    .get<DistrictStats>('/city/district-stats', { params: { cityName, districtName }, silent: true })
    .then((res) => res.data)
}
