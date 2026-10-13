/**
 * AI 对话流式能力（供 /assistant 页与右下角「蜀韵管家」浮层对话窗共用）
 *
 * - streamChat：fetch + ReadableStream 读取 POST /api/ai/chat 的 SSE 流
 *   （EventSource 无法自定义请求头，故用 fetch 携带 Authorization）
 * - 共享 sessionId：同一浏览器内 /assistant 页与浮层共用同一会话，
 *   通过 localStorage 持久化（key = SHARED_SESSION_KEY）
 */
import { TOKEN_KEY } from '@/api/request'

/** 流式结构化事件（后端 SSE 首帧意图 / 工具事件，均为可选） */
export type StreamEvent =
  | { type: 'intent'; intent?: string; entities?: { city?: string; days?: string; prefs?: string[] } }
  | { type: 'tool'; name?: string; args?: string; duration?: number }

/** 流式响应头：后端回传的会话ID */
const SESSION_HEADER = 'X-Session-Id'

/** 后端 API 基址：生产由 .env.production 注入（已含 /api），开发回退 /api 走 Vite 代理 */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

/** 共享会话ID 的存储 key */
export const SHARED_SESSION_KEY = 'shuyun_ai_session'

/** 读取共享会话ID（/assistant 与浮层共用） */
export function getSharedSessionId(): string {
  return localStorage.getItem(SHARED_SESSION_KEY) || ''
}

/** 写入共享会话ID */
export function setSharedSessionId(sessionId: string) {
  if (sessionId) {
    localStorage.setItem(SHARED_SESSION_KEY, sessionId)
  }
}

/**
 * fetch + ReadableStream 接收 SSE 流
 *
 * 后端每段正文以 {@code data: {"content":"xxx"}} 推送，结束为 {@code data: [DONE]}；
 * 同时兼容 {@code {"type":"intent"|"tool", ...}} 结构化帧与 {@code {"reasoning":"xxx"}} 思考帧。
 *
 * @param text      用户消息
 * @param sessionId 会话ID（为空时后端生成，经响应头 X-Session-Id 回传）
 * @param onDelta   收到增量时的回调（参数为累计后的完整文本）
 * @param onEvent   收到结构化事件（意图 / 工具）时的回调
 * @returns AI 完整回复 + 本次会话ID
 */
export async function streamChat(
  text: string,
  sessionId: string,
  onDelta: (content: string, reasoning: string) => void,
  onEvent?: (ev: StreamEvent) => void,
): Promise<{ content: string; reasoning: string; sessionId: string }> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await fetch(`${API_BASE}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ message: text, sessionId }),
  })

  // 非 SSE 响应（未登录 / 参数错误等）按 JSON 解析后抛出，交由上层兜底
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !response.body || !contentType.includes('text/event-stream')) {
    let msg = `请求失败（${response.status}）`
    try {
      const json = await response.json()
      msg = json?.msg || msg
    } catch {
      // 忽略解析失败
    }
    throw new Error(msg)
  }

  // 后端回传的会话ID（前端未传时会生成新会话）
  const newSessionId = response.headers.get(SESSION_HEADER) || sessionId

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let aiContent = ''
  let aiReasoning = ''
  let finished = false

  while (!finished) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    // 按 SSE 帧（空行分隔）切分，剩余不完整片段留在 buffer
    let sep = buffer.indexOf('\n\n')
    while (sep >= 0) {
      const frame = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)

      // 同一帧内的多行 data: 以换行拼接
      const data = frame
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n')

      if (data === '[DONE]') {
        finished = true
        break
      }
      if (data) {
        // 支持三类帧：{"type":"intent",...} / {"type":"tool",...} / {"content"|"reasoning",...}
        let contentChunk: string | null = null
        let reasoningChunk: string | null = null
        try {
          const obj = JSON.parse(data) as {
            type?: string
            content?: string
            reasoning?: string
            intent?: string
            entities?: { city?: string; days?: string; prefs?: string[] }
            name?: string
            args?: string
            duration?: number
          }
          if (obj?.type === 'intent') {
            onEvent?.({ type: 'intent', intent: obj.intent, entities: obj.entities })
          } else if (obj?.type === 'tool') {
            onEvent?.({ type: 'tool', name: obj.name, args: obj.args, duration: obj.duration })
          } else if (typeof obj?.reasoning === 'string') {
            reasoningChunk = obj.reasoning
          } else if (typeof obj?.content === 'string') {
            contentChunk = obj.content
          }
        } catch {
          // 非 JSON，保持原文
          contentChunk = data
        }
        if (reasoningChunk !== null) {
          aiReasoning += reasoningChunk
          onDelta(aiContent, aiReasoning)
        } else if (contentChunk !== null) {
          aiContent += contentChunk
          onDelta(aiContent, aiReasoning)
        }
      }
      sep = buffer.indexOf('\n\n')
    }
  }

  return { content: aiContent, reasoning: aiReasoning, sessionId: newSessionId }
}
