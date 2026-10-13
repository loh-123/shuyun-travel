/**
 * 大屏实时数据通道（useScreenStream）
 *
 * 默认使用 WebSocket 连接后端 {@code /ws/screen}：
 * - 服务端每 5 秒推送一次实时数据（JSON 数组）
 * - 使用 requestAnimationFrame 节流，避免高频更新造成重复渲染
 * - 断线自动重连（指数退避，最大 30 秒）
 *
 * 若 WebSocket 不可用（如反向代理未开启 ws），可将 {@link TRANSPORT} 改为 'sse'，
 * 后端同样提供 SSE 端点 {@code /api/screen/stream}，上层 store 接口无需改动。
 */
import { onUnmounted } from 'vue'
import { useScreenStore } from '@/screen/store/screen'
import type { StreamItem } from '@/screen/store/screen'

/** 传输方式：'ws'（默认，WebSocket） | 'sse'（回退） */
const TRANSPORT: 'sse' | 'ws' = 'ws'

/** 后端 API 基址：生产由 .env.production 注入（已含 /api），开发回退 /api 走 Vite 代理 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

/** SSE 端点（HTTP 协议，直接拼接 API 基址） */
const SSE_URL = `${API_BASE}/screen/stream`

/**
 * WebSocket 端点：
 * - 开发环境（未注入 VITE_API_BASE_URL）：用当前站点域名，走 Vite 的 /ws 代理
 * - 生产环境：从 API 基址推导 origin（https→wss / http→ws），路径固定 /ws/screen
 */
function resolveWsUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL
  if (!base) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws/screen`
  }
  const url = new URL(base)
  const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${url.host}/ws/screen`
}
/** WebSocket 端点（默认传输方式） */
const WS_URL = resolveWsUrl()

/** 最大重连延迟（毫秒） */
const MAX_DELAY = 30000

export function useScreenStream() {
  const store = useScreenStore()

  let es: EventSource | null = null
  let ws: WebSocket | null = null
  let retry = 0
  let reconnectTimer: number | null = null
  let rafId = 0
  let stopped = false
  /** 待应用的推送数据（rAF 节流） */
  let pending: StreamItem[] | null = null

  /** 在下一帧统一应用，避免多帧重复更新 */
  function scheduleApply(data: StreamItem[]) {
    pending = data
    if (!rafId) {
      rafId = requestAnimationFrame(() => {
        rafId = 0
        if (pending) {
          store.applyRealtime(pending)
          pending = null
        }
      })
    }
  }

  /** 解析并调度 */
  function onPayload(raw: string) {
    try {
      const items = JSON.parse(raw) as StreamItem[]
      if (Array.isArray(items)) {
        scheduleApply(items)
      }
    } catch {
      // 忽略解析失败的帧
    }
  }

  /** 关闭当前连接 */
  function close() {
    if (es) {
      es.close()
      es = null
    }
    if (ws) {
      ws.close()
      ws = null
    }
  }

  /** 指数退避重连 */
  function scheduleReconnect() {
    if (stopped) return
    const delay = Math.min(MAX_DELAY, 1000 * Math.pow(2, retry))
    retry += 1
    reconnectTimer = window.setTimeout(connect, delay)
  }

  /** 建立连接 */
  function connect() {
    if (stopped) return
    close()
    if (TRANSPORT === 'ws') {
      connectWs()
    } else {
      connectSse()
    }
  }

  /** SSE 连接 */
  function connectSse() {
    es = new EventSource(SSE_URL)
    es.addEventListener('realtime', (e: MessageEvent) => {
      retry = 0
      onPayload(e.data)
    })
    es.onopen = () => {
      retry = 0
    }
    es.onerror = () => {
      // 关闭后手动重连（避免 EventSource 默认重连策略）
      close()
      scheduleReconnect()
    }
  }

  /** WebSocket 连接（默认） */
  function connectWs() {
    ws = new WebSocket(WS_URL)
    ws.onmessage = (e) => {
      retry = 0
      onPayload(e.data)
    }
    ws.onopen = () => {
      retry = 0
    }
    ws.onerror = () => {
      close()
      scheduleReconnect()
    }
    ws.onclose = () => {
      if (!stopped) scheduleReconnect()
    }
  }

  /** 启动 */
  function start() {
    stopped = false
    retry = 0
    connect()
  }

  /** 停止 */
  function stop() {
    stopped = true
    close()
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  onUnmounted(stop)

  return { start, stop }
}
