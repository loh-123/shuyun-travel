import { defineStore } from 'pinia'
import { TOKEN_KEY } from '@/api/request'

/** 登录用户信息 */
export interface UserInfo {
  id: number
  username: string
  email: string
  avatar?: string | null
  role?: string
  status?: number
}

const USER_KEY = 'user'

function readUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

/**
 * 用户状态：管理 token 与用户信息
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
  }),

  getters: {
    /** 是否已登录 */
    isLogin: (state): boolean => !!state.token,
  },

  actions: {
    /** 保存 token */
    setToken(token: string) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
    },

    /** 保存用户信息 */
    setUser(user: UserInfo | null) {
      this.user = user
      if (user) {
        localStorage.setItem(USER_KEY, JSON.stringify(user))
      } else {
        localStorage.removeItem(USER_KEY)
      }
    },

    /** 退出登录，清空状态与本地缓存 */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
