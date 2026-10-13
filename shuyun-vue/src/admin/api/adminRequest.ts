import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后台统一的响应结构（与前台一致） */
export interface AdminResult<T = any> {
  code: number
  msg: string
  data: T
}

/** 后台独立存储的 token / 用户信息 key（与前台隔离） */
export const ADMIN_TOKEN_KEY = 'admin_token'
export const ADMIN_USER_KEY = 'admin_user'

/**
 * 后台专用 axios 实例
 *
 * 与前台 `@/api/request` 隔离：使用独立的 localStorage.admin_token 作为凭证，
 * 避免前台登录态与后台登录态互相覆盖。
 */
const adminRequest = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// ---------- 请求拦截：附带后台 token ----------
adminRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(ADMIN_TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ---------- 响应拦截：统一处理非 200 ----------
adminRequest.interceptors.response.use(
  (response: AxiosResponse<AdminResult>) => {
    // 二进制下载（如导出 Excel）：直接透传原始响应，交给调用方读取 blob 与响应头
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      // 未登录 / 无权限：清除后台登录态并跳回后台登录页
      if (res.code === 401 || res.code === 403) {
        localStorage.removeItem(ADMIN_TOKEN_KEY)
        localStorage.removeItem(ADMIN_USER_KEY)
        if (!window.location.pathname.startsWith('/admin/login')) {
          window.location.href = '/admin/login'
        }
      }
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res as unknown as AxiosResponse
  },
  (error) => {
    ElMessage.error(error?.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

export default adminRequest
