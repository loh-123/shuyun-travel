import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

// 扩展 axios 配置：silent 为 true 时不弹错误提示（用于首屏可选/公开数据请求）
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

/** localStorage 中 token 的 key（与 stores/user.ts 保持一致） */
export const TOKEN_KEY = 'token'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// ---------- 请求拦截器：附带 token ----------
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ---------- 响应拦截器：统一处理 code !== 200 ----------
request.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    if (res.code !== 200) {
      if (!response.config.silent) {
        ElMessage.error(res.msg || '请求失败')
      }
      // 未登录 / token 失效：清除本地 token
      if (res.code === 401) {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem('user')
      }
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    // 直接返回业务数据，调用方通过 res.data 取载荷
    return res as unknown as AxiosResponse
  },
  (error) => {
    if (!error?.config?.silent) {
      ElMessage.error(error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default request
