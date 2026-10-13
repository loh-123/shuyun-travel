<script setup lang="ts">
/**
 * AI 管家（项目核心页）
 * - 三栏布局：左 280 会话列表 / 中 flex 对话窗口 / 右 360 上下文面板
 * - 流式接收：fetch + ReadableStream 读取 POST /api/ai/chat 的 SSE（女娲智能体）
 * - Markdown 渲染：marked + DOMPurify（防 XSS）
 * - 上下文面板：规则识别意图与实体 → 实时天气 + 动态推荐景点 + 快捷工具
 * - 行程消息支持一键「加入我的行程」（复用 /user/trip 接口，不改其它页面）
 * - 响应式：<1280 收起右栏（按钮呼出），<768 只留中栏（左右用抽屉）
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import gsap from 'gsap'

import request, { TOKEN_KEY } from '@/api/request'
import { streamChat, getSharedSessionId, setSharedSessionId } from '@/composables/useAiChatStream'
import GoldButton from '@/components/common/GoldButton.vue'

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
interface SessionItem {
  sessionId: string
  title: string
  lastTime?: string
  messageCount?: number
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
  /** 用户消息可能带图片占位 */
  imageName?: string
  /** AI 思考过程（type=THINK） */
  reasoning?: string
  /** 思考用时（秒） */
  reasoningSeconds?: number
  /** 思考过程是否展开（默认展开） */
  reasoningOpen?: boolean
}

interface ScenicItem {
  id: number
  name: string
  cityName?: string
  type?: string
  coverImage?: string
  tags?: string
}

interface WeatherDay {
  date: string
  text: string
  icon: string
  max: number
  min: number
}

interface WeatherInfo {
  city: string
  temp: string
  text: string
  tip: string
  icon: string
  wind?: string
  uv?: string
  daily?: WeatherDay[]
}

/** 工具调用记录（右侧「工具调用」面板） */
interface ToolCall {
  /** 唯一ID（TransitionGroup key） */
  id: string
  /** 工具名，如 search_scenic */
  name: string
  /** 入参展示串，如 "成都" */
  args: string
  /** 耗时（毫秒） */
  duration: number
}

/* ------------------------------------------------------------
 * 常量
 * ---------------------------------------------------------- */
/** 快捷问题（首次进入显示） */
const QUICK_QUESTIONS = [
  '帮我规划成都三日游',
  '四川有哪些必吃的川菜？',
  '九寨沟什么时候去最美？',
  '讲一讲三星堆的神秘故事',
]

/** 21 个市州名（用于识别目的地） */
const CITY_NAMES = [
  '成都', '绵阳', '德阳', '自贡', '攀枝花', '泸州', '广元', '遂宁', '内江', '乐山', '南充',
  '眉山', '宜宾', '广安', '达州', '雅安', '巴中', '资阳', '阿坝', '甘孜', '凉山',
]

/** 快捷工具 */
const TOOLS = [
  { label: '规划行程', icon: '🗺', preset: '帮我规划一份三天的四川行程' },
  { label: '查天气', icon: '⛅', preset: '成都今天的天气怎么样？' },
  { label: '找美食', icon: '🍜', preset: '四川有哪些必吃的川菜？' },
  { label: '文化科普', icon: '📖', preset: '讲一讲三星堆的神秘故事' },
]

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const router = useRouter()
const route = useRoute()

const rootEl = ref<HTMLElement | null>(null)
const listEl = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)

/** 左栏会话 */
const sessions = ref<SessionItem[]>([])
const activeSessionId = ref('')

/** 当前会话消息 */
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
/** AI 思考中（还没开始出字） */
const thinking = ref(false)
/** 正在流式输出的正文（未落库） */
const streamingText = ref('')
/** 正在流式输出的思考过程（未落库） */
const streamingReasoning = ref('')
/** 流式思考过程是否展开（默认展开） */
const streamReasoningOpen = ref(true)
/** 本轮思考用时（秒） */
const reasoningSeconds = ref(0)
/** 是否显示快捷问题 */
const showQuick = ref(true)

/** 附件（图片）占位 */
const attachmentName = ref('')
const fileRef = ref<HTMLInputElement | null>(null)

/** 语音输入 */
const listening = ref(false)
let recognition: { start: () => void; stop: () => void } | null = null

/** 右栏数据 */
const scenicList = ref<ScenicItem[]>([])
const weather = ref<WeatherInfo | null>(null)

/** 小屏抽屉 */
const leftDrawer = ref(false)
const rightDrawer = ref(false)

/** 宽屏左右栏折叠 */
const leftHidden = ref(false)
const rightHidden = ref(false)

/* ---------------- 智能体可视化面板（意图 / 工具 / 记忆） ---------------- */
/** 工具调用流水（最新在最上方，最多 6 条） */
const toolCalls = ref<ToolCall[]>([])
/** 工具调用最多保留条数 */
const MAX_TOOLS = 6
/** 本轮是否已收到后端 tool 事件（收到则停用本地模拟） */
let backendToolSeen = false
/** 本地模拟工具调用的定时器（切换回合 / 卸载时清理） */
let simTimers: number[] = []
/** 后端返回的意图 / 实体（流式首帧，缺省时回退关键词匹配） */
const aiIntent = ref('')
const aiEntities = ref<{ city?: string; days?: string; prefs?: string[] } | null>(null)
/** 用户画像（记忆体种子）：来自 GET /ai/profile，登录后可用 */
const profile = ref<{ interests?: string; companion?: string; budget?: string; region?: string } | null>(null)
/** 移动端：意图面板是否折叠 */
const intentCollapsed = ref(false)

function toggleLeft() {
  leftHidden.value = !leftHidden.value
}

function toggleRight() {
  rightHidden.value = !rightHidden.value
}

/** 断点 */
const isNarrow = ref(false)
const isSmall = ref(false)

/* ------------------------------------------------------------
 * 计算属性：右栏上下文
 * ---------------------------------------------------------- */
/** 最后一条用户消息 */
const lastUserText = computed(
  () => [...messages.value].reverse().find((m) => m.role === 'user')?.content || '',
)

/** 意图识别（规则版，接入女娲后由模型返回） */
const intent = computed(() => {
  const t = lastUserText.value
  if (/行程|规划|路线|几日|几天|安排/.test(t)) {
    return '行程规划'
  }
  if (/天气|下雨|气温|穿什么|紫外线|风力/.test(t)) {
    return '天气关怀'
  }
  if (/川菜|美食|吃|小吃|火锅/.test(t)) {
    return '美食推荐'
  }
  if (/三星堆|川剧|蜀绣|三国|茶马|文化|非遗|熊猫|酒/.test(t)) {
    return '文化科普'
  }
  return '综合咨询'
})

/** 实体抽取（目的地 / 天数 / 偏好） */
const entities = computed(() => {
  const t = lastUserText.value
  const spot = scenicList.value.find((s) => t.includes(s.name))
  const city = CITY_NAMES.find((c) => t.includes(c)) || spot?.cityName || ''
  const days = (t.match(/(\d+)\s*(天|日)/) || [])[1] || ''
  const prefs = ['亲子', '美食', '自然', '文化', '摄影', '滑雪', '古镇', '博物馆', '徒步'].filter((p) =>
    t.includes(p),
  )
  return { city, days, prefs }
})

/** 动态推荐景点：命中对话内容 / 目的地 / 偏好的优先 */
const recommends = computed(() => {
  const t = `${lastUserText.value} ${entities.value.city} ${entities.value.prefs.join(' ')}`
  const matched = scenicList.value.filter(
    (s) =>
      t.includes(s.name) ||
      (s.tags || '')
        .split(',')
        .some((tag) => tag && t.includes(tag)) ||
      (!!entities.value.city && s.cityName === entities.value.city),
  )
  return (matched.length ? matched : scenicList.value).slice(0, 3)
})

/* ------------------------------------------------------------
 * 面板 1 · 意图识别
 * ---------------------------------------------------------- */
/** 意图面板展示：优先后端返回，其次关键词匹配 */
const displayIntent = computed(() => aiIntent.value || intent.value)

/** 识别实体 chips：优先后端，其次前端抽取（目的地 / 天数 / 偏好） */
const entityChips = computed<string[]>(() => {
  const be = aiEntities.value
  const city = be?.city || entities.value.city
  const days = be?.days || entities.value.days
  const prefs = be?.prefs?.length ? be.prefs : entities.value.prefs
  const chips: string[] = []
  if (city) {
    chips.push(city)
  }
  if (days) {
    chips.push(`${days} 天`)
  }
  prefs.forEach((p) => chips.push(p))
  return chips
})

/* ------------------------------------------------------------
 * 面板 3 · 记忆体
 * ---------------------------------------------------------- */
/**
 * 记忆体：用户画像（接口） + 对话中实时抽取，去重，最多 6 条。
 * 对话内容变化时自动重算，实现「对话中如有更新则实时刷新」。
 */
const memoryList = computed<string[]>(() => {
  const out: string[] = []
  const push = (s: string) => {
    if (s && !out.includes(s)) {
      out.push(s)
    }
  }

  // 1) 用户画像接口
  const p = profile.value
  if (p) {
    if (p.interests) {
      p.interests
        .split(/[,，、/]/)
        .map((s) => s.trim())
        .filter(Boolean)
        .forEach((i) => push(`用户喜欢${i}类景点`))
    }
    if (p.companion) {
      push(`用户带${p.companion}出行`)
    }
    if (p.budget) {
      push(`用户预算 ${p.budget} 元`)
    }
    if (p.region) {
      push(`用户常驻${p.region}`)
    }
  }

  // 2) 对话中实时抽取
  const text = messages.value
    .filter((m) => m.role === 'user')
    .map((m) => m.content)
    .join(' ')
  if (text) {
    const interestKeys = ['文化', '自然', '摄影', '美食', '古镇', '博物馆', '亲子', '滑雪', '徒步', '温泉']
    interestKeys.forEach((k) => {
      if (text.includes(k)) {
        push(`用户喜欢${k}类景点`)
      }
    })
    if (/父母|爸妈|老人|长辈/.test(text)) {
      push('用户带父母出行')
    } else if (/孩子|小孩|亲子|宝宝/.test(text)) {
      push('用户带孩子出行')
    } else if (/情侣|爱人|老婆|老公/.test(text)) {
      push('用户与情侣出行')
    } else if (/朋友|同学|结伴/.test(text)) {
      push('用户与朋友出行')
    }
    const budget = text.match(/(\d{3,6})\s*(元|块钱|预算|左右)/)
    if (budget) {
      push(`用户预算 ${budget[1]} 元`)
    }
  }
  return out.slice(0, 6)
})

/* ------------------------------------------------------------
 * 工具函数
 * ---------------------------------------------------------- */
/** Markdown 渲染（marked + DOMPurify 消毒） */
function renderMd(text: string) {
  const html = marked.parse(text || '', { async: false }) as string
  return DOMPurify.sanitize(html)
}

function nowText() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 会话列表里的时间展示 */
function timeLabel(time?: string) {
  if (!time) {
    return ''
  }
  const stamp = new Date(time.replace('T', ' ').replace(/-/g, '/').split('.')[0]).getTime()
  if (Number.isNaN(stamp)) {
    return ''
  }
  const minutes = Math.floor((Date.now() - stamp) / 60000)
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  const days = Math.floor(hours / 24)
  return days === 1 ? '昨天' : `${days} 天前`
}

function scrollToBottom() {
  nextTick(() => {
    const el = listEl.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/* ------------------------------------------------------------
 * 数据加载
 * ---------------------------------------------------------- */
async function loadSessions() {
  try {
    const res = await request.get('/ai/sessions', { silent: true })
    sessions.value = Array.isArray(res.data) ? res.data : []
  } catch {
    sessions.value = []
  }
}

/** 打开会话：拉取历史 */
async function openSession(sessionId: string) {
  activeSessionId.value = sessionId
  // 共享 sessionId：与右下角浮层对话窗保持同一会话
  setSharedSessionId(sessionId)
  leftDrawer.value = false
  streamingText.value = ''
  showQuick.value = false
  try {
    const res = await request.get('/ai/history', { silent: true, params: { sessionId } })
    messages.value = (res.data ?? []).map((m: { id: number; role: string; content: string; createTime?: string }) => ({
      id: `m-${m.id}`,
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content,
      time: m.createTime ? m.createTime.slice(11, 16) : '',
    }))
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

/** 新建会话（不落库，发第一条消息时才生成 sessionId） */
function newSession() {
  activeSessionId.value = ''
  messages.value = []
  streamingText.value = ''
  showQuick.value = true
  leftDrawer.value = false
  inputRef.value?.focus()
}

/** 清空当前会话 */
async function clearCurrent() {
  if (!messages.value.length) {
    ElMessage.info('当前对话还是空的')
    return
  }
  try {
    await ElMessageBox.confirm('确定清空当前对话吗？', '提示', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (activeSessionId.value) {
    try {
      await request.delete(`/ai/session/${activeSessionId.value}`)
    } catch {
      // 忽略
    }
  }
  messages.value = []
  streamingText.value = ''
  showQuick.value = true
  await loadSessions()
  ElMessage.success('已清空')
}

/** 删除某个会话 */
async function removeSession(item: SessionItem) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${item.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await request.delete(`/ai/session/${item.sessionId}`)
    if (activeSessionId.value === item.sessionId) {
      newSession()
    }
    await loadSessions()
    ElMessage.success('已删除')
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

/** 导出当前对话为 markdown 文件 */
function exportChat() {
  if (!messages.value.length) {
    ElMessage.info('当前对话还是空的')
    return
  }
  const title = sessions.value.find((s) => s.sessionId === activeSessionId.value)?.title || '蜀韵智游对话'
  const body = messages.value
    .map((m) => `**${m.role === 'user' ? '我' : '蜀韵·智游管家'}**（${m.time}）\n\n${m.content}`)
    .join('\n\n---\n\n')
  const blob = new Blob([`# ${title}\n\n${body}\n`], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${title}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出为 Markdown')
}

/** 加载推荐景点池 */
async function loadScenics() {
  try {
    const res = await request.get('/scenic/list', { silent: true, params: { page: 1, size: 40 } })
    scenicList.value = res.data?.records ?? []
  } catch {
    scenicList.value = []
  }
}

/**
 * 读取用户画像（记忆体种子）。
 * 未登录 / 接口不可用时静默降级为 null（面板仍会从对话中实时抽取）。
 */
async function loadProfile() {
  if (!localStorage.getItem(TOKEN_KEY)) {
    profile.value = null
    return
  }
  try {
    const res = await request.get('/ai/profile', { silent: true })
    const data = res.data as { interests?: string; companion?: string; budget?: string; region?: string } | null
    profile.value = data && Object.keys(data).length ? data : null
  } catch {
    profile.value = null
  }
}

/** 查天气（提到城市才查） */
async function loadWeather(city: string) {
  if (!city) {
    weather.value = null
    return
  }
  try {
    const res = await request.get('/weather', { silent: true, params: { city } })
    weather.value = (res.data as WeatherInfo) ?? null
  } catch {
    weather.value = null
  }
}

/* ------------------------------------------------------------
 * 流式对话
 * ---------------------------------------------------------- */
/** 本轮思考开始的时刻（用于计算思考用时） */
let reasoningStartAt = 0

/* ------------------------------------------------------------
 * 面板 2 · 工具调用
 * ---------------------------------------------------------- */
/** 清理本地模拟工具的定时器 */
function clearSimTimers() {
  simTimers.forEach((id) => window.clearTimeout(id))
  simTimers = []
}

/** 追加一条工具调用（最新在最上方，最多 6 条） */
function pushTool(name: string, args: string, duration: number) {
  const item: ToolCall = {
    id: `t-${Date.now()}-${Math.random().toString(16).slice(2, 7)}`,
    name,
    args,
    duration,
  }
  toolCalls.value = [item, ...toolCalls.value].slice(0, MAX_TOOLS)
}

/**
 * 本地模拟工具调用（后端暂不推送 tool 事件时的可视化占位）。
 * 依据本轮意图与实体推断工具序列，并错峰推入面板，呈现「智能体在调度工具」。
 */
function scheduleSimulatedTools(text: string) {
  const plan = deriveToolPlan(text)
  plan.forEach((p, i) => {
    const id = window.setTimeout(() => {
      if (!backendToolSeen) {
        pushTool(p.name, p.args, p.duration)
      }
    }, 320 + i * 420)
    simTimers.push(id)
  })
}

/** 依据文本意图 / 实体推断工具序列（名称与后端 Function Calling 对齐） */
function deriveToolPlan(text: string): { name: string; args: string; duration: number }[] {
  const city = entities.value.city || '成都'
  const days = entities.value.days || '3'
  const prefs = entities.value.prefs.join('、')
  const rnd = (base: number) => base + Math.floor(Math.random() * 90)
  const plan: { name: string; args: string; duration: number }[] = []

  if (/行程|规划|路线|几日|几天|安排|几日游/.test(text)) {
    plan.push({ name: 'search_scenic', args: `"${city}"`, duration: rnd(120) })
    plan.push({ name: 'plan_route', args: `${days}天${prefs ? ', ' + prefs : ''}`, duration: rnd(300) })
  }
  if (/天气|下雨|气温|穿什么|紫外线|风力/.test(text)) {
    plan.push({ name: 'get_weather', args: `"${city}"`, duration: rnd(90) })
  }
  if (/美食|川菜|吃|小吃|火锅|甜皮鸭/.test(text)) {
    plan.push({ name: 'recommend_food', args: `"${city}"`, duration: rnd(150) })
  }
  if (/三星堆|川剧|蜀绣|文化|非遗|故事|历史|三国|熊猫/.test(text)) {
    plan.push({ name: 'search_culture', args: `"${text.slice(0, 8)}"`, duration: rnd(110) })
  }
  if (!plan.length) {
    plan.push({ name: 'search_knowledge', args: `"${city}"`, duration: rnd(95) })
  }
  return plan
}

/** 发送 */
async function onSend(preset?: string) {
  const text = (preset ?? input.value).trim()
  if (!text || sending.value) {
    return
  }

  showQuick.value = false
  sending.value = true
  thinking.value = true
  streamingText.value = ''
  streamingReasoning.value = ''
  streamReasoningOpen.value = true
  reasoningSeconds.value = 0
  reasoningStartAt = Date.now()

  // 新一轮：复位意图/工具面板，并启动工具调用可视化
  aiIntent.value = ''
  aiEntities.value = null
  backendToolSeen = false
  clearSimTimers()

  messages.value.push({
    id: `u-${Date.now()}`,
    role: 'user',
    content: text,
    time: nowText(),
    imageName: attachmentName.value || undefined,
  })
  attachmentName.value = ''
  input.value = ''
  scrollToBottom()

  // 工具面板：后端若未推送 tool 事件，则由本地按意图/实体模拟
  scheduleSimulatedTools(text)

  // 用户消息滑入动画
  nextTick(() => {
    const els = listEl.value?.querySelectorAll('.msg--user')
    const last = els?.[els.length - 1]
    if (last) {
      gsap.from(last, { y: 24, opacity: 0, duration: 0.5, ease: 'power3.out' })
    }
  })

  let answer = ''
  let answerReasoning = ''
  try {
    const res = await streamChat(
      text,
      activeSessionId.value,
      (full, reasoning) => {
        thinking.value = false
        streamingText.value = full
        if (reasoning) {
          streamingReasoning.value = reasoning
        }
        // 正文开始输出即认定思考结束，记录用时
        if (full && !reasoningSeconds.value) {
          reasoningSeconds.value = Math.max(1, Math.round((Date.now() - reasoningStartAt) / 1000))
        }
        scrollToBottom()
      },
      (ev) => {
        // 后端结构化事件：意图首帧 / 工具事件
        if (ev.type === 'intent') {
          if (ev.intent) {
            aiIntent.value = ev.intent
          }
          if (ev.entities) {
            aiEntities.value = ev.entities
          }
        } else if (ev.type === 'tool' && ev.name) {
          // 一旦收到后端工具事件，停用本地模拟，改用真实数据
          if (!backendToolSeen) {
            backendToolSeen = true
            clearSimTimers()
            toolCalls.value = []
          }
          pushTool(ev.name, ev.args || '', ev.duration || 0)
        }
      },
    )
    answer = res.content
    answerReasoning = res.reasoning
    // 共享 sessionId：与右下角浮层对话窗保持同一会话
    if (res.sessionId) {
      setSharedSessionId(res.sessionId)
      if (!activeSessionId.value) {
        activeSessionId.value = res.sessionId
      }
    }
  } catch {
    // 网络 / 服务异常：保留已收到的部分内容，否则给出友好提示
    answer = streamingText.value
    answerReasoning = streamingReasoning.value
  }
  if (!answer) {
    answer = '抱歉，管家暂时无法应答，请稍后再试。'
  }

  // 落地 AI 消息（含思考过程，默认展开）
  messages.value.push({
    id: `a-${Date.now()}`,
    role: 'assistant',
    content: answer,
    time: nowText(),
    reasoning: answerReasoning || undefined,
    reasoningSeconds: answerReasoning ? reasoningSeconds.value || undefined : undefined,
    reasoningOpen: true,
  })
  streamingText.value = ''
  streamingReasoning.value = ''
  reasoningSeconds.value = 0
  thinking.value = false
  sending.value = false

  await loadSessions()
  scrollToBottom()
}

/** 重新生成：删掉最后一条 AI 回复后重发上一条用户消息 */
async function regenerate(index: number) {
  const userMsg = [...messages.value.slice(0, index)].reverse().find((m) => m.role === 'user')
  if (!userMsg || sending.value) {
    return
  }
  messages.value = messages.value.slice(0, index)
  await onSend(userMsg.content)
}

/** 复制消息 */
async function copyMessage(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.info('复制失败，请手动选择')
  }
}

/** 判断 AI 回复是否包含行程序列（Day 1 / Day1） */
function isTripReply(content: string): boolean {
  return /(^|\n)\s*[-*•]?\s*\**\s*day\s*\d+/i.test(content || '')
}

/** 行程解析结果 */
interface ParsedTrip {
  title: string
  days: number
  dayList: { dayIndex: number; scenicName: string; tips: string }[]
}

/**
 * 从 AI 回复中解析出行程结构（标题 + 每日明细）
 * 规则：标题优先取加粗短语，其次取首行；以「Day N」为分界切分每日内容
 */
function parseTrip(content: string): ParsedTrip {
  const text = (content || '').trim()
  const bold = text.match(/\*\*([^*\n]{2,24})\*\*/)
  const firstLine = text.split('\n')[0].replace(/[#>*`]/g, '').trim()
  const title = (bold ? bold[1] : firstLine.slice(0, 20)).trim() || '我的四川行程'

  // 在「Day N」之前切分，每段即一天
  const parts = text.split(/(?=(?:^|\n)\s*[-*•]?\s*\**\s*day\s*\d+)/i)
  const dayList: ParsedTrip['dayList'] = []
  parts.forEach((raw) => {
    if (!/^\s*[-*•]?\s*\**\s*day\s*\d+/i.test(raw)) {
      return
    }
    // 去掉「Day N」标记，只取当天第一段（截掉其后的补充说明）
    let body = raw.replace(/^\s*[-*•]?\s*\**\s*day\s*\d+\s*\**/i, '')
    body = body.split(/\n{2,}/)[0]
    body = body.replace(/\*\*/g, '').replace(/^[:：]\s*/, '').replace(/\s+/g, ' ').trim()
    const head = body.split(/[:：]/)[0].trim()
    const scenicName = head && head.length <= 18 ? head : body.slice(0, 24)
    dayList.push({ dayIndex: dayList.length + 1, scenicName, tips: body })
  })

  if (!dayList.length) {
    dayList.push({ dayIndex: 1, scenicName: title, tips: text.replace(/\*\*/g, '') })
  }
  return { title, days: dayList.length, dayList }
}

/** 正在加入行程的消息 id（用于按钮禁用态） */
const tripSavingId = ref('')

/** 把一条 AI 行程回复保存到「我的行程」（复用现成 /user/trip 接口） */
async function addToTrip(msg: ChatMessage) {
  if (!localStorage.getItem(TOKEN_KEY)) {
    ElMessage.warning('请先登录后再加入行程')
    router.push('/login')
    return
  }
  const parsed = parseTrip(msg.content)
  tripSavingId.value = msg.id
  try {
    await request.post('/user/trip', {
      title: parsed.title,
      days: parsed.days,
      companion: '',
      interests: '',
      budget: null,
      status: 1,
      dayList: parsed.dayList.map((d) => ({
        dayIndex: d.dayIndex,
        date: null,
        scenicName: d.scenicName,
        stayDuration: null,
        transport: null,
        food: null,
        tips: d.tips,
      })),
    })
    ElMessage.success('已加入「我的行程」')
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    tripSavingId.value = ''
  }
}

/** 历史消息的重发（保留给「重新生成历史消息」扩展用） */

/* ------------------------------------------------------------
 * 输入区交互
 * ---------------------------------------------------------- */
/** 回车发送，Shift+Enter 换行 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSend()
  }
}

/** 输入框自适应高度 */
function autoGrow() {
  const el = inputRef.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

watch(input, () => nextTick(autoGrow))

/** 选择图片（本地预览 + 以文字占位随消息发出） */
function pickImage() {
  fileRef.value?.click()
}

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) {
    return
  }
  attachmentName.value = file.name
  ElMessage.success(`已选择图片：${file.name}（图片理解待接入模型）`)
  ;(e.target as HTMLInputElement).value = ''
}

/** 语音输入（浏览器 SpeechRecognition） */
function toggleVoice() {
  const SpeechRecognition =
    (window as unknown as { SpeechRecognition?: new () => never; webkitSpeechRecognition?: new () => never })
      .SpeechRecognition ||
    (window as unknown as { webkitSpeechRecognition?: new () => never }).webkitSpeechRecognition

  if (!SpeechRecognition) {
    ElMessage.info('当前浏览器不支持语音输入，建议使用 Chrome / Edge')
    return
  }
  if (listening.value) {
    recognition?.stop()
    listening.value = false
    return
  }

  type RecognitionLike = {
    lang: string
    interimResults: boolean
    onresult: (ev: { results: { 0: { 0: { transcript: string } } } }) => void
    onend: () => void
    onerror: () => void
    start: () => void
    stop: () => void
  }
  recognition = new (SpeechRecognition as unknown as new () => RecognitionLike)()
  const rec = recognition as unknown as RecognitionLike
  rec.lang = 'zh-CN'
  rec.interimResults = false
  rec.onresult = (ev) => {
    const text = ev.results?.[0]?.[0]?.transcript ?? ''
    if (text) {
      input.value = (input.value + text).trim()
    }
  }
  rec.onend = () => {
    listening.value = false
  }
  rec.onerror = () => {
    listening.value = false
    ElMessage.info('语音识别失败，请重试')
  }
  rec.start()
  listening.value = true
}

/* ------------------------------------------------------------
 * 断点监听
 * ---------------------------------------------------------- */
function onResize() {
  const w = window.innerWidth
  isNarrow.value = w < 1280
  isSmall.value = w < 768
}

// 小屏：意图面板默认折叠，节省竖向空间（用户可手动展开）
watch(isSmall, (v) => {
  if (v) {
    intentCollapsed.value = true
  }
})

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null

onMounted(async () => {
  onResize()
  window.addEventListener('resize', onResize)

  await Promise.all([loadSessions(), loadScenics(), loadProfile()])

  // 从首页「智能体入口」跳转时携带问题：
  // - q            填入输入框（用户自己按回车发送）
  // - q & auto=1   直接自动提交
  const initialQ = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  const autoAsk = route.query.auto === '1'

  if (initialQ) {
    newSession()
    if (autoAsk) {
      await onSend(initialQ)
    } else {
      input.value = initialQ
      nextTick(() => inputRef.value?.focus())
    }
  } else if (sessions.value.length) {
    // 优先打开共享会话（与右下角浮层对话窗一致），否则打开最近一条
    const shared = getSharedSessionId()
    const target = sessions.value.find((s) => s.sessionId === shared) || sessions.value[0]
    await openSession(target.sessionId)
  } else {
    newSession()
  }

  // 三栏依次从下方淡入
  ctx = gsap.context(() => {
    gsap.from(['.ai-side', '.ai-chat', '.ai-context'], {
      y: 28,
      opacity: 0,
      duration: 0.75,
      stagger: 0.12,
      ease: 'power3.out',
    })
  }, rootEl.value ?? undefined)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  recognition?.stop()
  clearSimTimers()
  ctx?.revert()
  ctx = null
})

// 对话变化 → 刷新右栏天气
watch(
  () => entities.value.city,
  (city) => loadWeather(city || '成都'),
  { immediate: true },
)
</script>

<template>
  <div
    ref="rootEl"
    class="ai"
    :class="{ 'is-left-hidden': leftHidden, 'is-right-hidden': rightHidden }"
  >
    <!-- ================= 左栏：会话列表 ================= -->
    <aside class="ai-side" :class="{ 'is-open': leftDrawer }">
      <GoldButton block class="ai-side__new" @click="newSession">
        <template #icon-left>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </template>
        新建对话
      </GoldButton>

      <ul class="sessions" data-lenis-prevent>
        <li
          v-for="s in sessions"
          :key="s.sessionId"
          class="sessions__item"
          :class="{ 'is-active': s.sessionId === activeSessionId }"
          @click="openSession(s.sessionId)"
        >
          <span class="sessions__dot" />
          <span class="sessions__main">
            <b class="sessions__title">{{ s.title }}</b>
            <span class="sessions__time">{{ timeLabel(s.lastTime) }}</span>
          </span>
          <button type="button" class="sessions__del" title="删除会话" @click.stop="removeSession(s)">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <path d="M6 7h12M9 7V5h6v2M8 7l1 12h6l1-12" />
            </svg>
          </button>
        </li>
      </ul>

      <div v-if="!sessions.length" class="sessions__empty">还没有会话，点上方新建一个吧</div>

      <!-- 底部：用户信息 -->
      <div class="ai-side__user">
        <span class="ai-side__avatar">蜀</span>
        <span class="ai-side__name">{{ '我的蜀韵智游' }}</span>
      </div>
    </aside>

    <!-- ================= 中栏：对话窗口 ================= -->
    <section class="ai-chat">
      <!-- 左右栏折叠把手（位于分割线中央） -->
      <button
        type="button"
        class="chat-toggle chat-toggle--left"
        :title="leftHidden ? '展开会话列表' : '收起会话列表'"
        @click="toggleLeft"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="leftHidden ? 'M9 6l6 6-6 6' : 'M15 6l-6 6 6 6'" />
        </svg>
      </button>
      <button
        type="button"
        class="chat-toggle chat-toggle--right"
        :title="rightHidden ? '展开上下文面板' : '收起上下文面板'"
        @click="toggleRight"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="rightHidden ? 'M15 6l-6 6 6 6' : 'M9 6l6 6-6 6'" />
        </svg>
      </button>

      <!-- 标题栏 -->
      <header class="chat-head">
        <div class="chat-head__left">
          <button
            v-if="isSmall"
            type="button"
            class="chat-head__icon"
            title="会话列表"
            @click="leftDrawer = true"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <span class="chat-head__status" />
          <h1 class="chat-head__title">蜀韵·智游管家</h1>
          <span class="chat-head__badge">在线</span>
        </div>

        <div class="chat-head__right">
          <button type="button" class="chat-head__icon" title="导出对话" @click="exportChat">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3v12M8 11l4 4 4-4M5 21h14" />
            </svg>
          </button>
          <button type="button" class="chat-head__icon" title="清空对话" @click="clearCurrent">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <path d="M6 7h12M9 7V5h6v2M8 7l1 12h6l1-12" />
            </svg>
          </button>
          <button
            v-if="isNarrow && !isSmall"
            type="button"
            class="chat-head__icon"
            title="上下文面板"
            @click="rightDrawer = true"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <path d="M12 3v18M5 8h4M5 12h4M5 16h4" />
            </svg>
          </button>
        </div>
      </header>

      <!-- 面板1：意图识别（对话上方） -->
      <div class="intent" :class="{ 'is-collapsed': intentCollapsed }">
        <button type="button" class="intent__head" @click="intentCollapsed = !intentCollapsed">
          <span class="intent__dot" aria-hidden="true" />
          <span class="intent__title">意图识别</span>
          <span class="intent__value">{{ displayIntent }}</span>
          <svg class="intent__caret" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
        <div v-show="!intentCollapsed" class="intent__body">
          <span class="intent__label">识别实体</span>
          <span v-if="entityChips.length" class="intent__chips">
            <i v-for="c in entityChips" :key="c" class="intent__chip">{{ c }}</i>
          </span>
          <span v-else class="intent__empty">等待你的问题…</span>
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="listEl" class="chat-list" data-lenis-prevent>
        <!-- 快捷问题 -->
        <div v-if="showQuick && !messages.length" class="quick">
          <div class="quick__mark"><span>蜀</span></div>
          <h2 class="quick__title">你好，我是蜀韵·智游管家</h2>
          <p class="quick__sub">可以帮你规划行程、推荐景点与美食，也可以聊巴蜀文化。</p>
          <div class="quick__list">
            <button
              v-for="q in QUICK_QUESTIONS"
              :key="q"
              type="button"
              class="quick__item"
              @click="onSend(q)"
            >
              {{ q }}
            </button>
          </div>
        </div>

        <!-- 历史消息 -->
        <div
          v-for="(m, i) in messages"
          :key="m.id"
          class="msg"
          :class="`msg--${m.role}`"
        >
          <span v-if="m.role === 'assistant'" class="msg__avatar">蜀</span>

          <div class="msg__body">
            <!-- 思考过程：灰色小字、可折叠、默认展开 -->
            <div
              v-if="m.role === 'assistant' && m.reasoning"
              class="think"
              :class="{ 'is-open': m.reasoningOpen !== false }"
            >
              <button
                type="button"
                class="think__head"
                @click="m.reasoningOpen = m.reasoningOpen === false"
              >
                <svg class="think__icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3.2" />
                  <path d="M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4M5.3 5.3l1.7 1.7M17 17l1.7 1.7M18.7 5.3 17 7M7 17l-1.7 1.7" />
                </svg>
                <span class="think__label">已思考{{ m.reasoningSeconds ? `（用时 ${m.reasoningSeconds} 秒）` : '' }}</span>
                <svg class="think__arrow" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>
              <div v-show="m.reasoningOpen !== false" class="think__body">{{ m.reasoning }}</div>
            </div>

            <div class="msg__bubble">
              <p v-if="m.imageName" class="msg__image">🖼 {{ m.imageName }}</p>
              <div v-if="m.role === 'assistant'" class="md" v-html="renderMd(m.content)" />
              <template v-else>{{ m.content }}</template>
            </div>

            <div class="msg__foot">
              <span class="msg__time">{{ m.time }}</span>
              <button type="button" class="msg__act" @click="copyMessage(m.content)">复制</button>
              <button
                v-if="m.role === 'assistant' && isTripReply(m.content)"
                type="button"
                class="msg__act msg__act--trip"
                :disabled="tripSavingId === m.id"
                @click="addToTrip(m)"
              >
                {{ tripSavingId === m.id ? '加入中…' : '加入我的行程' }}
              </button>
              <button
                v-if="m.role === 'assistant'"
                type="button"
                class="msg__act"
                @click="regenerate(i)"
              >
                重新生成
              </button>
            </div>
          </div>
        </div>

        <!-- 流式输出中（含思考过程） -->
        <div v-if="streamingText || streamingReasoning" class="msg msg--assistant">
          <span class="msg__avatar">蜀</span>
          <div class="msg__body">
            <!-- 思考过程：有正文后显示「已思考」，否则「正在深度思考」 -->
            <div v-if="streamingReasoning" class="think" :class="{ 'is-open': streamReasoningOpen }">
              <button type="button" class="think__head" @click="streamReasoningOpen = !streamReasoningOpen">
                <svg class="think__icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3.2" />
                  <path d="M12 2.5v2.4M12 19.1v2.4M2.5 12h2.4M19.1 12h2.4M5.3 5.3l1.7 1.7M17 17l1.7 1.7M18.7 5.3 17 7M7 17l-1.7 1.7" />
                </svg>
                <span class="think__label">
                  {{ streamingText ? `已思考${reasoningSeconds ? `（用时 ${reasoningSeconds} 秒）` : ''}` : '正在深度思考…' }}
                </span>
                <svg class="think__arrow" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>
              <div v-show="streamReasoningOpen" class="think__body">{{ streamingReasoning }}</div>
            </div>

            <div v-if="streamingText" class="msg__bubble">
              <div class="md" v-html="renderMd(streamingText)" />
              <span class="msg__caret" />
            </div>
          </div>
        </div>

        <!-- 思考中（尚未产生任何增量） -->
        <div v-if="thinking && !streamingText && !streamingReasoning" class="msg msg--assistant">
          <span class="msg__avatar">蜀</span>
          <div class="msg__body">
            <div class="msg__bubble msg__bubble--thinking">
              <span class="dot" /><span class="dot" /><span class="dot" />
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <div v-if="attachmentName" class="chat-input__chip">
          <span>🖼 {{ attachmentName }}</span>
          <button type="button" @click="attachmentName = ''">✕</button>
        </div>

        <div class="chat-input__box">
          <button type="button" class="chat-input__tool" title="上传图片" @click="pickImage">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15l-5-5L5 21M3 5h6M6 2v6" />
            </svg>
          </button>
          <input ref="fileRef" type="file" accept="image/*" class="chat-input__file" @change="onFileChange" />

          <textarea
            ref="inputRef"
            v-model="input"
            class="chat-input__field"
            rows="1"
            placeholder="问我关于四川的任何问题…"
            @keydown="onKeydown"
          />

          <button
            type="button"
            class="chat-input__tool"
            :class="{ 'is-on': listening }"
            title="语音输入"
            @click="toggleVoice"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z" />
              <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
            </svg>
          </button>

          <button
            type="button"
            class="chat-input__send"
            :disabled="!input.trim() || sending"
            @click="onSend()"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 12h15M13 6l6 6-6 6" />
            </svg>
          </button>
        </div>

        <p class="chat-input__hint">Enter 发送 · Shift+Enter 换行</p>
      </div>
    </section>

    <!-- ================= 右栏：上下文面板 ================= -->
    <aside class="ai-context" :class="{ 'is-open': rightDrawer }" data-lenis-prevent>
      <!-- 面板2：工具调用（新记录从顶部滑入，最多 6 条） -->
      <div class="panel">
        <h3 class="panel__title">工具调用</h3>
        <div class="tools-feed">
          <TransitionGroup name="tslide">
            <div v-for="tc in toolCalls" :key="tc.id" class="tool-call">
              <span class="tool-call__name">{{ tc.name }}</span>
              <span v-if="tc.args" class="tool-call__args">({{ tc.args }})</span>
              <span class="tool-call__meta">
                <span v-if="tc.duration" class="tool-call__dur">{{ tc.duration }}ms</span>
                <span class="tool-call__ok" aria-hidden="true">✓</span>
              </span>
            </div>
          </TransitionGroup>
          <p v-if="!toolCalls.length" class="tools-feed__empty">智能体尚未调用工具</p>
          <p v-if="sending" class="tools-feed__live">
            <span class="dot" /><span class="dot" /><span class="dot" /> 正在调度工具…
          </p>
        </div>
      </div>

      <!-- 面板3：记忆体 -->
      <div class="panel">
        <h3 class="panel__title">记忆体</h3>
        <p class="mem__hint">已记住：</p>
        <ul v-if="memoryList.length" class="mem">
          <li v-for="m in memoryList" :key="m" class="mem__item">
            <span class="mem__dot" aria-hidden="true" />{{ m }}
          </li>
        </ul>
        <p v-else class="mem__empty">还没有积累到你的偏好，聊几句吧</p>
      </div>

      <!-- 天气 -->
      <div v-if="weather" class="panel" :class="{ 'is-live': sending }">
        <h3 class="panel__title">{{ weather.city }} · 实时天气</h3>
        <div class="weather">
          <span class="weather__temp">{{ weather.temp }}<em>℃</em></span>
          <span class="weather__text">{{ weather.text }}</span>
        </div>
        <div class="weather__meta">
          <span>风力 {{ weather.wind || '—' }}</span>
          <span>紫外线 {{ weather.uv || '—' }}</span>
        </div>
        <ul v-if="weather.daily?.length" class="forecast">
          <li v-for="d in weather.daily" :key="d.date">
            <b>{{ d.date.slice(5) }}</b>
            <span>{{ d.text }}</span>
            <em>{{ d.min }}° / {{ d.max }}°</em>
          </li>
        </ul>
      </div>

      <!-- 推荐景点 -->
      <div v-if="recommends.length" class="panel">
        <h3 class="panel__title">推荐景点</h3>
        <ul class="reco">
          <li v-for="s in recommends" :key="s.id" class="reco__item" @click="router.push(`/scenic/${s.id}`)">
            <img class="reco__img" :src="s.coverImage" alt="" loading="lazy" />
            <span class="reco__info">
              <b>{{ s.name }}</b>
              <em>{{ s.cityName }} · {{ s.type }}</em>
            </span>
          </li>
        </ul>
      </div>

      <!-- 快捷工具 -->
      <div class="panel">
        <h3 class="panel__title">快捷工具</h3>
        <div class="tools">
          <button
            v-for="t in TOOLS"
            :key="t.label"
            type="button"
            class="tools__item"
            @click="onSend(t.preset)"
          >
            <span class="tools__icon">{{ t.icon }}</span>
            {{ t.label }}
          </button>
        </div>
      </div>
    </aside>

    <!-- 小屏遮罩 -->
    <div
      v-if="leftDrawer || rightDrawer"
      class="ai-mask"
      @click="leftDrawer = false; rightDrawer = false"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;
/* ============================================================
 * 三栏布局
 * ========================================================== */
.ai {
  --ai-left: 416px;
  --ai-right: 468px;
  /* 固定视口：顶部让开导航栏、底部贴边；整页不滚动，仅内部各区域各自滚动 */
  position: fixed;
  top: $nav-height;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: $z-content;
  display: grid;
  grid-template-columns: var(--ai-left) minmax(0, 1fr) var(--ai-right);
  /* 单行铺满且允许收缩，避免内容把行高撑开 */
  grid-auto-rows: minmax(0, 1fr);
  width: 100%;
  overflow: hidden;
  background: $color-paper;
}

/* ---------------- 左栏：会话列表 ---------------- */
.ai-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 14px;
  border-right: 1px solid $color-border-gold-light;
  background: rgba(var(--sy-surface-rgb), 0.42);
  overflow: hidden;
  will-change: transform;
}

.ai-side__new {
  width: 100%;
}

.sessions {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0 -4px;
  padding: 0 4px;
  overflow-y: auto;
}

.sessions__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: $radius-md;
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.07);
    transform: translateX(3px);

    .sessions__del {
      opacity: 1;
    }
  }

  &.is-active {
    background: linear-gradient(120deg, $color-primary, #6e141c);

    .sessions__title {
      color: $color-text-light;
    }

    .sessions__time {
      color: rgba(245, 241, 232, 0.7);
    }

    .sessions__dot {
      background: $color-gold;
      border-color: $color-gold;
    }
  }
}

.sessions__dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border: 1px solid rgba(212, 179, 116, 0.7);
  border-radius: 50%;
}

.sessions__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.sessions__title {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.04em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}

.sessions__time {
  font-size: 16.5px;
  color: rgba(var(--sy-text-rgb), 0.42);
}

.sessions__del {
  flex-shrink: 0;
  color: rgba(var(--sy-text-rgb), 0.45);
  opacity: 0;
  transition:
    opacity $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    color: var(--sy-accent);
  }
}

.sessions__empty {
  flex: 1;
  display: grid;
  place-items: center;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.4);
  text-align: center;
}

.ai-side__user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px dashed rgba(122, 26, 34, 0.15);
}

.ai-side__avatar {
  @include flex-center;
  width: 34px;
  height: 34px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  background: rgba(212, 179, 116, 0.14);
  font-family: $font-title;
  font-weight: 900;
  font-size: calc(#{$fs-body} * 1.5);
  color: var(--sy-accent);
}

.ai-side__name {
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.7);
  @include text-ellipsis(1);
}

/* ---------------- 中栏：对话窗口 ---------------- */
.ai-chat {
  position: relative;
  z-index: 3;
  display: flex;
  flex-direction: column;
  min-width: 0;
  /* 关键：允许纵向收缩，否则 flex 子项默认 min-height:auto 会撑破容器 */
  min-height: 0;
  overflow: visible;
  background: transparent;
  will-change: transform;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
  padding: 14px max(24px, calc((100% - 1120px) / 2));
  border-bottom: 1px solid $color-border-gold-light;
  background: rgba(var(--sy-paper-rgb), 0.66);
}

.chat-head__left,
.chat-head__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 在线状态点 */
.chat-head__status {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #57c98a;
  box-shadow: 0 0 0 4px rgba(87, 201, 138, 0.18);
}

.chat-head__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(22.5px, 1.8vw, 27px);
  letter-spacing: 0.14em;
  color: var(--sy-accent);
}

.chat-head__badge {
  padding: 2px 10px;
  border: 1px solid rgba(58, 90, 64, 0.28);
  border-radius: $radius-pill;
  background: rgba(58, 90, 64, 0.08);
  font-size: 16.5px;
  letter-spacing: 0.1em;
  color: $color-bamboo;
}

.chat-head__icon {
  @include flex-center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(212, 179, 116, 0.42);
  border-radius: $radius-md;
  color: rgba(var(--sy-text-rgb), 0.66);
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo,
    border-color $duration-fast $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    background: rgba(212, 179, 116, 0.16);
    color: var(--sy-accent);
  }
}

/* ---------------- 左右栏折叠把手（分割线中央） ---------------- */
.chat-toggle {
  position: absolute;
  top: 50%;
  z-index: 6;
  display: grid;
  place-items: center;
  width: 22px;
  height: 46px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-pill;
  background: rgba(var(--sy-paper-rgb), 0.94);
  color: rgba(var(--sy-text-rgb), 0.6);
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition:
    color $duration-fast $ease-out-expo,
    border-color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    background: rgba(212, 179, 116, 0.16);
    color: var(--sy-accent);
  }
}

.chat-toggle--left {
  left: 0;
  transform: translate(-50%, -50%);
}

.chat-toggle--right {
  right: 0;
  transform: translate(50%, -50%);
}

/* 收起后把手贴到屏幕边缘内侧，便于再次展开 */
@media (min-width: 769px) {
  .ai.is-left-hidden {
    --ai-left: 0px;
  }

  .ai.is-left-hidden .ai-side {
    display: none;
  }

  .ai.is-left-hidden .chat-toggle--left {
    left: 10px;
    transform: translate(0, -50%);
  }
}

@media (min-width: 1281px) {
  .ai.is-right-hidden {
    --ai-right: 0px;
  }

  .ai.is-right-hidden .ai-context {
    display: none;
  }

  .ai.is-right-hidden .chat-toggle--right {
    right: 10px;
    transform: translate(0, -50%);
  }
}

.chat-list {
  flex: 1;
  /* 关键：消息区独立滚动的前提，令其可被压缩到容器高度内 */
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 28px max(24px, calc((100% - 1120px) / 2)) 24px;
  overflow-y: auto;
  /* 滚到尽头不再把滚动传递给页面，根除双重滚动 */
  overscroll-behavior: contain;
  scroll-behavior: smooth;
}

/* 快捷问题 */
.quick {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding-block: clamp(16px, 4vh, 54px);
  text-align: center;
}

.quick__mark {
  @include flex-center;
  width: 64px;
  height: 64px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: $radius-lg;
  background-color: rgba(212, 179, 116, 0.08);
  background-image: $pattern-brocade;
  font-family: $font-title;
  font-weight: 900;
  font-size: 39px;
  color: $color-gold;

  span {
    padding: 3px 6px;
    background: $color-paper;
  }
}

.quick__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(25.5px, 2.25vw, 33px);
  letter-spacing: 0.1em;
  color: $color-ink;
}

.quick__sub {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.55);
}

.quick__list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 6px;
}

.quick__item {
  padding: 9px 18px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-pill;
  background: rgba(var(--sy-surface-rgb), 0.6);
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.78);
  transition:
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: var(--sy-accent);
    transform: translateY(-2px);
    box-shadow: $shadow-sm;
  }
}

/* ---------------- 消息气泡 ---------------- */
.msg {
  display: flex;
  gap: 10px;
  max-width: 100%;
}

.msg--assistant {
  align-items: flex-start;
}

.msg--user {
  justify-content: flex-end;
}

/* AI 头像：小「蜀」字印章 */
.msg__avatar {
  @include flex-center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: 1px solid $color-border-gold;
  border-radius: 50%;
  background: rgba(212, 179, 116, 0.14);
  font-family: $font-title;
  font-weight: 900;
  font-size: 21px;
  color: var(--sy-accent);
}

.msg__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}

.msg--user .msg__body {
  align-items: flex-end;
  max-width: 78%;
}

.msg__bubble {
  padding: 2px 0;
  border-radius: $radius-lg;
  font-family: $font-body;
  font-size: calc(#{$fs-body} * 1.5);
  line-height: 1.9;
  word-break: break-word;
}

/* AI 消息：去掉气泡框，贴近 Codex / DeepSeek 的正文排版 */
.msg--assistant .msg__bubble {
  padding: 0;
  background: transparent;
  color: rgba(var(--sy-text-rgb), 0.86);
}

.msg--user .msg__bubble {
  padding: 12px 18px;
  border: 1px solid rgba(212, 179, 116, 0.38);
  background: rgba(212, 179, 116, 0.14);
  color: $color-text-dark;
}

.msg__image {
  margin-bottom: 8px;
  font-size: calc(#{$fs-small} * 1.5);
  opacity: 0.9;
}

.msg__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-inline: 4px;
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.42);
}

.msg__act {
  font-size: 18px;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.5);
  transition: color $duration-fast $ease-out-expo;

  &:hover {
    color: var(--sy-accent);
  }
}

/* 「加入我的行程」：胶囊按钮，用主题金色强调，与普通操作区分 */
.msg__act--trip {
  display: inline-flex;
  align-items: center;
  padding: 3px 14px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: 999px;
  color: var(--sy-accent);

  &:hover:not(:disabled) {
    border-color: var(--sy-accent);
    background: rgba(212, 179, 116, 0.12);
    color: var(--sy-accent);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* 思考中：三个金点 */
.msg__bubble--thinking {
  display: flex;
  gap: 6px;
  padding: 18px 22px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: $color-gold;
  animation: dot-bounce 1.2s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.16s;
  }

  &:nth-child(3) {
    animation-delay: 0.32s;
  }
}

@keyframes dot-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

/* ---------------- 思考过程（仿 DeepSeek，可折叠） ---------------- */
.think {
  max-width: 100%;
}

.think__head {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 4px 4px 0;
  font-family: $font-body;
  /* 字号 = 正文字号（$fs-body × 1.5）的 0.7 倍 */
  font-size: calc(#{$fs-body} * 1.05);
  color: rgba(var(--sy-text-rgb), 0.5);
  cursor: pointer;
  transition: color $duration-fast $ease-out-expo;

  &:hover {
    color: rgba(var(--sy-text-rgb), 0.72);
  }
}

.think__icon,
.think__arrow {
  flex-shrink: 0;
}

.think__arrow {
  transition: transform $duration-fast $ease-out-expo;
}

.think.is-open .think__arrow {
  transform: rotate(180deg);
}

/* 思考正文：灰色、小字、左侧细描边 */
.think__body {
  margin: 6px 0 2px;
  padding-left: 10px;
  border-left: 2px solid rgba(var(--sy-text-rgb), 0.16);
  font-family: $font-body;
  font-size: calc(#{$fs-body} * 1.05);
  line-height: 1.75;
  color: rgba(var(--sy-text-rgb), 0.5);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 流式输出光标 */
.msg__caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -0.14em;
  background: $color-gold;
  animation: caret-blink 1s steps(1) infinite;
}

@keyframes caret-blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}

/* ---------------- Markdown 排版 ---------------- */
.md {
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 16px 0 8px;
    font-family: $font-body;
    font-weight: 900;
    letter-spacing: 0.06em;
    color: $color-ink;
  }

  :deep(h2) {
    font-size: 1.16em;
  }

  :deep(h3) {
    font-size: 1.04em;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(ul),
  :deep(ol) {
    margin: 8px 0;
    padding-left: 1.25em;
  }

  :deep(li) {
    margin: 4px 0;
    list-style: disc;
  }

  :deep(ol li) {
    list-style: decimal;
  }

  :deep(strong) {
    color: var(--sy-accent);
  }

  :deep(code) {
    padding: 1px 6px;
    border-radius: $radius-sm;
    background: rgba(212, 179, 116, 0.18);
    font-size: 0.92em;
  }

  :deep(blockquote) {
    margin: 10px 0;
    padding: 8px 14px;
    border-left: 2px solid $color-gold;
    background: rgba(212, 179, 116, 0.1);
    color: rgba(var(--sy-text-rgb), 0.7);
  }

  :deep(table) {
    width: 100%;
    margin: 12px 0;
    border-collapse: collapse;
    font-size: 0.96em;
  }

  :deep(th),
  :deep(td) {
    padding: 8px 10px;
    border: 1px solid rgba(212, 179, 116, 0.4);
    text-align: left;
  }

  :deep(th) {
    background: rgba(212, 179, 116, 0.16);
    font-family: $font-body;
  }

  :deep(a) {
    color: var(--sy-accent);
    text-decoration: underline;
  }

  :deep(hr) {
    margin: 14px 0;
    border: none;
    border-top: 1px dashed rgba(122, 26, 34, 0.2);
  }
}

/* ---------------- 输入区 ---------------- */
.chat-input {
  flex-shrink: 0;
  padding: 16px max(24px, calc((100% - 1120px) / 2)) 20px;
  border-top: 1px solid $color-border-gold-light;
  @include glass(rgba(var(--sy-paper-rgb), 0.72), 16px);
}

.chat-input__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 4px 12px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-pill;
  font-size: 18px;
  color: $color-bamboo;

  button {
    color: rgba(var(--sy-text-rgb), 0.5);

    &:hover {
      color: var(--sy-accent);
    }
  }
}

.chat-input__box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(212, 179, 116, 0.45);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.85);
  transition:
    border-color $duration-fast $ease-out-expo,
    box-shadow $duration-fast $ease-out-expo;

  &:focus-within {
    border-color: $color-gold;
    box-shadow: 0 0 0 3px rgba(212, 179, 116, 0.16);
  }
}

.chat-input__file {
  display: none;
}

/* 输入框：光标金色 */
.chat-input__field {
  flex: 1;
  max-height: 160px;
  padding: 8px 4px;
  border: none;
  background: transparent;
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  line-height: 1.7;
  color: $color-text-dark;
  resize: none;
  caret-color: $color-gold;

  &::placeholder {
    color: rgba(var(--sy-text-rgb), 0.35);
  }
}

.chat-input__tool {
  @include flex-center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: $radius-md;
  color: rgba(var(--sy-text-rgb), 0.5);
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &:hover {
    color: var(--sy-accent);
    background: rgba(122, 26, 34, 0.07);
  }

  &.is-on {
    color: var(--sy-accent);
    background: rgba(212, 179, 116, 0.2);
  }
}

.chat-input__send {
  @include flex-center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: $radius-md;
  background: linear-gradient(120deg, $color-gold, #b8954f);
  color: $color-ink;
  transition:
    transform $duration-fast $ease-out-expo,
    box-shadow $duration-fast $ease-out-expo,
    opacity $duration-fast $ease-out-expo;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(212, 179, 116, 0.4);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.chat-input__hint {
  margin-top: 8px;
  font-size: 18px;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.4);
  text-align: center;
}

/* ---------------- 右栏：上下文 ---------------- */
.ai-context {
  display: flex;
  flex-direction: column;
  padding: 18px 16px;
  border-left: 1px solid $color-border-gold-light;
  background: rgba(var(--sy-surface-rgb), 0.42);
  overflow-y: auto;
  will-change: transform;
}

.panel {
  padding: 16px 0;
  border-bottom: 1px solid rgba(212, 179, 116, 0.22);

  &:first-child {
    padding-top: 0;
  }

  &:last-child {
    padding-bottom: 0;
    border-bottom: none;
  }

  /* 发送中：标题高亮 */
  &.is-live .panel__title {
    color: $color-gold;
  }
}

.panel__title {
  position: relative;
  margin-bottom: 12px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(19.5px, 1.65vw, 24px);
  letter-spacing: 0.12em;
  color: var(--sy-accent);

  &::before {
    content: '';
    position: absolute;
    top: 0.14em;
    left: 0;
    width: 2px;
    height: 0.86em;
    background: $color-gold;
  }
}

.ctx-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 6px 0;
}

.ctx-row__label {
  width: 56px;
  flex-shrink: 0;
  font-size: 18px;
  letter-spacing: 0.12em;
  color: $color-gold;
}

.ctx-row__value {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.78);

  &--tag {
    padding: 2px 10px;
    border-radius: $radius-pill;
    background: rgba(122, 26, 34, 0.1);
    color: var(--sy-accent);
  }
}

.ctx-thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 18px;
  color: var(--sy-accent);
}

/* ============================================================
 * 智能体可视化面板（意图 / 工具 / 记忆）
 * ========================================================== */
/* ---------------- 面板1：意图识别（中栏对话上方） ---------------- */
.intent {
  flex-shrink: 0;
  margin: 12px max(24px, calc((100% - 1120px) / 2)) 0;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.62);
  overflow: hidden;
}

.intent__head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.82);
}

.intent__dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 50%;
  background: $color-gold;
  animation: intentPulse 2s ease-out infinite;
}

@keyframes intentPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(212, 179, 116, 0.5);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(212, 179, 116, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(212, 179, 116, 0);
  }
}

.intent__title {
  letter-spacing: 0.14em;
  color: $color-gold;
}

.intent__value {
  font-family: $font-title;
  font-weight: 900;
  font-size: calc(#{$fs-small} * 1.7);
  color: var(--sy-accent);
}

.intent__caret {
  margin-left: auto;
  flex-shrink: 0;
  color: rgba(var(--sy-text-rgb), 0.5);
  transition: transform $duration-normal $ease-out-expo;
}

.intent.is-collapsed .intent__caret {
  transform: rotate(-90deg);
}

.intent__body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 0 14px 12px;
}

.intent__label {
  font-size: 18px;
  letter-spacing: 0.12em;
  color: $color-gold;
}

.intent__chips {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.intent__chip {
  padding: 3px 12px;
  border-radius: $radius-pill;
  background: rgba(122, 26, 34, 0.1);
  font-style: normal;
  font-size: calc(#{$fs-small} * 1.5);
  color: var(--sy-accent);
}

.intent__empty {
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.42);
}

/* ---------------- 面板2：工具调用（右栏） ---------------- */
.tools-feed {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-call {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(212, 179, 116, 0.28);
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.5);
  font-size: calc(#{$fs-small} * 1.4);
}

.tool-call__name {
  flex-shrink: 0;
  font-family: $font-en;
  color: var(--sy-accent);
}

.tool-call__args {
  flex: 1;
  min-width: 0;
  color: rgba(var(--sy-text-rgb), 0.58);
  @include text-ellipsis(1);
}

.tool-call__meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.tool-call__dur {
  font-family: $font-en;
  font-size: 16px;
  color: $color-gold;
}

.tool-call__ok {
  color: #3a8f5f;
}

.tools-feed__empty,
.mem__empty {
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.42);
}

.tools-feed__live {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 18px;
  color: var(--sy-accent);
}

/* 工具记录：新记录从顶部滑入 */
.tslide-enter-from {
  opacity: 0;
  transform: translateY(-16px);
}

.tslide-enter-active {
  transition:
    opacity 0.45s $ease-out-expo,
    transform 0.45s $ease-out-expo;
}

.tslide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.tslide-leave-active {
  position: absolute;
  width: 100%;
  transition:
    opacity 0.35s $ease-out-expo,
    transform 0.35s $ease-out-expo;
}

.tslide-move {
  transition: transform 0.4s $ease-out-expo;
}

/* ---------------- 面板3：记忆体（右栏） ---------------- */
.mem__hint {
  margin-bottom: 8px;
  font-size: 18px;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.mem {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.mem__item {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.8);
}

.mem__dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: $color-gold;
}

.weather {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.weather__temp {
  font-family: $font-en;
  font-size: clamp(42px, 3.9vw, 60px);
  line-height: 1;
  color: $color-gold;

  em {
    font-size: 0.38em;
    font-style: normal;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.weather__text {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(21px, 1.8vw, 27px);
  color: $color-ink;
}

.weather__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 10px;
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.6);
}

.forecast {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(122, 26, 34, 0.15);

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 18px;
    color: rgba(var(--sy-text-rgb), 0.7);

    b {
      width: 44px;
      font-family: $font-en;
      color: $color-gold;
    }

    span {
      flex: 1;
    }

    em {
      font-style: normal;
      color: $color-ink;
    }
  }
}

.reco {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reco__item {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform $duration-normal $ease-out-expo;

  &:hover {
    transform: translateX(4px);

    b {
      color: var(--sy-accent);
    }
  }
}

.reco__img {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-md;
}

.reco__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: calc(#{$fs-small} * 1.5);
    letter-spacing: 0.06em;
    color: $color-text-dark;
    transition: color $duration-normal $ease-out-expo;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 18px;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.tools {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.tools__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 12px;
  border: 1px solid rgba(212, 179, 116, 0.42);
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.6);
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.78);
  transition:
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: var(--sy-accent);
    transform: translateY(-2px);
    box-shadow: $shadow-sm;
  }
}

.tools__icon {
  font-size: 22.5px;
}

/* ---------------- 抽屉遮罩 ---------------- */
.ai-mask {
  position: fixed;
  inset: 0;
  z-index: $z-header + 1;
  background: rgba(14, 42, 71, 0.4);
  backdrop-filter: blur(4px);
}

/* ============================================================
 * 响应式
 * ========================================================== */
/* 中屏（<1280）：右栏改为抽屉 */
@include responsive(1280px) {
  .ai {
    --ai-left: 260px;
    grid-template-columns: var(--ai-left) minmax(0, 1fr);
  }

  /* 右栏收进抽屉，隐藏右侧折叠把手 */
  .chat-toggle--right {
    display: none;
  }

  .ai-context {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: $z-header + 2;
    width: min(88vw, 380px);
    padding: 82px 16px 20px;
    background: $color-paper;
    box-shadow: -20px 0 50px rgba(0, 0, 0, 0.25);
    transform: translateX(100%);
    transition: transform $duration-normal $ease-out-expo;

    &.is-open {
      transform: translateX(0);
    }
  }
}

/* 小屏（<768）：只留中栏，左右都走抽屉 */
@include responsive(md) {
  .ai {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 小屏左右栏改为抽屉，隐藏折叠把手 */
  .chat-toggle {
    display: none;
  }

  .ai-side {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: $z-header + 2;
    width: min(86vw, 300px);
    padding: 82px 16px 20px;
    background: $color-paper;
    box-shadow: 20px 0 50px rgba(0, 0, 0, 0.25);
    transform: translateX(-100%);
    transition: transform $duration-normal $ease-out-expo;

    &.is-open {
      transform: translateX(0);
    }
  }

  .msg--user .msg__body {
    max-width: 88%;
  }
}

/* ============================================================
 * 字体统一：整个对话界面使用思源宋体
 * - 全局字体栈：思源宋体（含中文字体兼容栈）
 * - 右侧边栏大标题：半粗 600
 * - 正文（聊天内容 / 景点地名 / 列表）：常规 400，行高 1.6
 * - 天气温度数字：24px 宋体（不超大）
 * - 代码保持等宽，避免宋体下难读
 * ========================================================== */
.ai,
.ai :deep(*) {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', 'SimSun', serif !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 代码等宽 */
.ai :deep(code),
.ai :deep(pre) {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace !important;
}

/* 右侧边栏 / 面板大标题：半粗 */
.ai .panel__title {
  font-weight: 600;
}

/* 正文：常规字重 + 行高 1.6 */
.ai .msg__bubble,
.ai :deep(.md),
.ai :deep(.md p),
.ai :deep(.md li),
.ai .reco__info b,
.ai .reco__info em,
.ai .ctx-row__value,
.ai .mem__item,
.ai .tool-call,
.ai .sessions__title,
.ai .ai-side__name {
  font-weight: 400;
  line-height: 1.6;
}

/* 天气温度数字：缩小到 24px */
.ai .weather__temp {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
}

.ai .weather__temp em {
  font-size: 0.62em;
}
</style>
