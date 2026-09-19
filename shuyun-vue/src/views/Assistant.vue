<script setup lang="ts">
/**
 * AI 管家（项目核心页）
 * - 三栏布局：左 280 会话列表 / 中 flex 对话窗口 / 右 360 上下文面板
 * - 流式接收：EventSource 订阅 /api/ai/chat/stream（失败自动降级为 POST /api/ai/chat）
 * - Markdown 渲染：marked + DOMPurify（防 XSS）
 * - 上下文面板：规则识别意图与实体 → 实时天气 + 动态推荐景点 + 快捷工具
 * - 响应式：<1280 收起右栏（按钮呼出），<768 只留中栏（左右用抽屉）
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import gsap from 'gsap'

import request, { TOKEN_KEY } from '@/api/request'
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
/** 流式返回的真实 sessionId */
let streamSessionId = ''

/**
 * SSE 流式接收
 * 注：浏览器 EventSource 无法自定义请求头，token 通过查询参数传给后端
 */
function streamChat(text: string, sessionId: string) {
  return new Promise<void>((resolve, reject) => {
    const token = localStorage.getItem(TOKEN_KEY) || ''
    const url =
      `/api/ai/chat/stream?message=${encodeURIComponent(text)}` +
      `&sessionId=${encodeURIComponent(sessionId)}` +
      `&token=${encodeURIComponent(token)}`

    const es = new EventSource(url)
    let received = false
    let settled = false

    const finish = (err?: Error) => {
      if (settled) {
        return
      }
      settled = true
      es.close()
      if (err) {
        reject(err)
      } else {
        resolve()
      }
    }

    es.addEventListener('session', (ev: MessageEvent) => {
      streamSessionId = String(ev.data || '')
    })

    es.addEventListener('message', (ev: MessageEvent) => {
      received = true
      thinking.value = false
      streamingText.value = String(ev.data ?? '')
      scrollToBottom()
    })

    es.addEventListener('done', () => finish())

    es.onerror = () => finish(received ? undefined : new Error('SSE failed'))
    window.setTimeout(() => finish(received ? undefined : new Error('SSE timeout')), 45000)
  })
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
  streamSessionId = activeSessionId.value

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

  // 用户消息滑入动画
  nextTick(() => {
    const els = listEl.value?.querySelectorAll('.msg--user')
    const last = els?.[els.length - 1]
    if (last) {
      gsap.from(last, { y: 24, opacity: 0, duration: 0.5, ease: 'power3.out' })
    }
  })

  try {
    await streamChat(text, activeSessionId.value)
  } catch {
    // 流式不可用：退化为普通请求
    try {
      const res = await request.post('/ai/chat', {
        message: text,
        sessionId: activeSessionId.value || undefined,
      })
      streamingText.value = res.data?.reply ?? ''
      streamSessionId = res.data?.sessionId ?? streamSessionId
    } catch {
      streamingText.value = '抱歉，管家暂时无法应答，请稍后再试。'
    }
  }

  // 落地 AI 消息
  messages.value.push({
    id: `a-${Date.now()}`,
    role: 'assistant',
    content: streamingText.value,
    time: nowText(),
  })
  streamingText.value = ''
  thinking.value = false
  sending.value = false

  if (!activeSessionId.value && streamSessionId) {
    activeSessionId.value = streamSessionId
  }
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

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null

onMounted(async () => {
  onResize()
  window.addEventListener('resize', onResize)

  await Promise.all([loadSessions(), loadScenics()])
  if (sessions.value.length) {
    await openSession(sessions.value[0].sessionId)
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
  <div ref="rootEl" class="ai">
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

      <ul class="sessions">
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

      <!-- 消息区 -->
      <div ref="listEl" class="chat-list">
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
            <div class="msg__bubble">
              <p v-if="m.imageName" class="msg__image">🖼 {{ m.imageName }}</p>
              <div v-if="m.role === 'assistant'" class="md" v-html="renderMd(m.content)" />
              <template v-else>{{ m.content }}</template>
            </div>

            <div class="msg__foot">
              <span class="msg__time">{{ m.time }}</span>
              <button type="button" class="msg__act" @click="copyMessage(m.content)">复制</button>
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

        <!-- 流式输出中 -->
        <div v-if="streamingText" class="msg msg--assistant">
          <span class="msg__avatar">蜀</span>
          <div class="msg__body">
            <div class="msg__bubble">
              <div class="md" v-html="renderMd(streamingText)" />
              <span class="msg__caret" />
            </div>
          </div>
        </div>

        <!-- 思考中 -->
        <div v-if="thinking && !streamingText" class="msg msg--assistant">
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
    <aside class="ai-context" :class="{ 'is-open': rightDrawer }">
      <!-- 当前会话信息 -->
      <div class="panel">
        <h3 class="panel__title">当前会话</h3>
        <div class="ctx-row">
          <span class="ctx-row__label">意图</span>
          <span class="ctx-row__value ctx-row__value--tag">{{ intent }}</span>
        </div>
        <div class="ctx-row">
          <span class="ctx-row__label">目的地</span>
          <span class="ctx-row__value">{{ entities.city || '待识别' }}</span>
        </div>
        <div class="ctx-row">
          <span class="ctx-row__label">天数</span>
          <span class="ctx-row__value">{{ entities.days ? entities.days + ' 天' : '待识别' }}</span>
        </div>
        <div class="ctx-row">
          <span class="ctx-row__label">偏好</span>
          <span class="ctx-row__value">{{ entities.prefs.length ? entities.prefs.join(' · ') : '待识别' }}</span>
        </div>

        <p v-if="thinking" class="ctx-thinking">
          <span class="dot" /><span class="dot" /><span class="dot" /> 正在为你查询…
        </p>
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
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 360px;
  gap: clamp(12px, 1.2vw, 20px);
  width: 100%;
  height: calc(100vh - 68px);
  min-height: 560px;
  padding: clamp(12px, 1.2vw, 20px) $gutter;
}

/* ---------------- 左栏：会话列表 ---------------- */
.ai-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: clamp(14px, 1.4vw, 18px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
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
    background: rgba(140, 31, 40, 0.07);
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
  border: 1px solid rgba(201, 168, 106, 0.7);
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
  font-size: $fs-small;
  letter-spacing: 0.04em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}

.sessions__time {
  font-size: 11px;
  color: rgba(26, 26, 26, 0.42);
}

.sessions__del {
  flex-shrink: 0;
  color: rgba(26, 26, 26, 0.45);
  opacity: 0;
  transition:
    opacity $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
  }
}

.sessions__empty {
  flex: 1;
  display: grid;
  place-items: center;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.4);
  text-align: center;
}

.ai-side__user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px dashed rgba(140, 31, 40, 0.15);
}

.ai-side__avatar {
  @include flex-center;
  width: 34px;
  height: 34px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  background: rgba(201, 168, 106, 0.14);
  font-family: $font-title;
  font-weight: 900;
  color: $color-primary;
}

.ai-side__name {
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.7);
  @include text-ellipsis(1);
}

/* ---------------- 中栏：对话窗口 ---------------- */
.ai-chat {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: $shadow-md;
  will-change: transform;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: clamp(12px, 1.3vw, 18px) clamp(14px, 1.6vw, 24px);
  background: linear-gradient(100deg, $color-ink, #16233a 60%, #1d2436);
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
  font-size: clamp(14px, 1.2vw, 17px);
  letter-spacing: 0.16em;
  color: $color-gold;
}

.chat-head__badge {
  padding: 2px 8px;
  border: 1px solid rgba(87, 201, 138, 0.5);
  border-radius: $radius-pill;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: #8fe0b4;
}

.chat-head__icon {
  @include flex-center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(201, 168, 106, 0.35);
  border-radius: $radius-md;
  color: $color-gold;
  transition:
    background-color $duration-fast $ease-out-expo,
    border-color $duration-fast $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    background: rgba(201, 168, 106, 0.16);
  }
}

.chat-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: clamp(18px, 2vw, 30px) clamp(14px, 1.8vw, 30px);
  overflow-y: auto;
}

/* 快捷问题 */
.quick {
  @include flex-center(column);
  gap: 14px;
  padding-block: clamp(16px, 4vh, 54px);
  text-align: center;
}

.quick__mark {
  @include flex-center;
  width: 64px;
  height: 64px;
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: $radius-lg;
  background-color: rgba(201, 168, 106, 0.08);
  background-image: $pattern-brocade;
  font-family: $font-title;
  font-weight: 900;
  font-size: 26px;
  color: $color-gold;

  span {
    padding: 3px 6px;
    background: $color-paper;
  }
}

.quick__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 1.5vw, 22px);
  letter-spacing: 0.1em;
  color: $color-ink;
}

.quick__sub {
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.55);
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
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.6);
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.78);
  transition:
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-primary;
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
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1px solid $color-gold;
  border-radius: $radius-sm;
  background: $color-primary;
  font-family: $font-title;
  font-weight: 900;
  font-size: 15px;
  color: $color-gold;
}

.msg__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 70%;
  min-width: 0;
}

.msg--user .msg__body {
  align-items: flex-end;
}

.msg__bubble {
  padding: 20px;
  border-radius: $radius-lg;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.9;
  word-break: break-word;
}

.msg--assistant .msg__bubble {
  border: 1px solid rgba(201, 168, 106, 0.4);
  background: rgba(255, 255, 255, 0.9);
  color: rgba(26, 26, 26, 0.84);
}

.msg--user .msg__bubble {
  background: linear-gradient(120deg, $color-primary, #6e141c);
  color: $color-text-light;
  box-shadow: 0 6px 18px rgba(140, 31, 40, 0.22);
}

.msg__image {
  margin-bottom: 8px;
  font-size: $fs-small;
  opacity: 0.9;
}

.msg__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-inline: 4px;
  font-size: 12px;
  color: rgba(26, 26, 26, 0.42);
}

.msg__act {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(26, 26, 26, 0.5);
  transition: color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
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
  :deep(h3) {
    margin: 16px 0 8px;
    font-family: $font-title;
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
    color: $color-primary;
  }

  :deep(code) {
    padding: 1px 6px;
    border-radius: $radius-sm;
    background: rgba(201, 168, 106, 0.18);
    font-size: 0.92em;
  }

  :deep(blockquote) {
    margin: 10px 0;
    padding: 8px 14px;
    border-left: 2px solid $color-gold;
    background: rgba(201, 168, 106, 0.1);
    color: rgba(26, 26, 26, 0.7);
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
    border: 1px solid rgba(201, 168, 106, 0.4);
    text-align: left;
  }

  :deep(th) {
    background: rgba(201, 168, 106, 0.16);
    font-family: $font-title;
  }

  :deep(a) {
    color: $color-primary;
    text-decoration: underline;
  }

  :deep(hr) {
    margin: 14px 0;
    border: none;
    border-top: 1px dashed rgba(140, 31, 40, 0.2);
  }
}

/* ---------------- 输入区 ---------------- */
.chat-input {
  padding: clamp(12px, 1.4vw, 18px) clamp(14px, 1.6vw, 24px) clamp(14px, 1.6vw, 20px);
  border-top: 1px solid rgba(201, 168, 106, 0.32);
  @include glass(rgba(232, 227, 216, 0.72), 16px);
}

.chat-input__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 4px 12px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-pill;
  font-size: 12px;
  color: $color-bamboo;

  button {
    color: rgba(26, 26, 26, 0.5);

    &:hover {
      color: $color-primary;
    }
  }
}

.chat-input__box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(201, 168, 106, 0.45);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.85);
  transition:
    border-color $duration-fast $ease-out-expo,
    box-shadow $duration-fast $ease-out-expo;

  &:focus-within {
    border-color: $color-gold;
    box-shadow: 0 0 0 3px rgba(201, 168, 106, 0.16);
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
  font-size: $fs-small;
  line-height: 1.7;
  color: $color-text-dark;
  resize: none;
  caret-color: $color-gold;

  &::placeholder {
    color: rgba(26, 26, 26, 0.35);
  }
}

.chat-input__tool {
  @include flex-center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: $radius-md;
  color: rgba(26, 26, 26, 0.5);
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
    background: rgba(140, 31, 40, 0.07);
  }

  &.is-on {
    color: $color-primary;
    background: rgba(201, 168, 106, 0.2);
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
    box-shadow: 0 8px 20px rgba(201, 168, 106, 0.4);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.chat-input__hint {
  margin-top: 8px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(26, 26, 26, 0.4);
  text-align: center;
}

/* ---------------- 右栏：上下文 ---------------- */
.ai-context {
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.2vw, 18px);
  overflow-y: auto;
  will-change: transform;
}

.panel {
  padding: clamp(14px, 1.5vw, 20px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  /* 发送中：面板高亮 */
  &.is-live {
    border-color: $color-gold;
    box-shadow:
      0 0 0 3px rgba(201, 168, 106, 0.16),
      $shadow-md;
  }
}

.panel__title {
  position: relative;
  margin-bottom: 12px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(13px, 1.1vw, 16px);
  letter-spacing: 0.12em;
  color: $color-primary;

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
  font-size: 12px;
  letter-spacing: 0.12em;
  color: $color-gold;
}

.ctx-row__value {
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.78);

  &--tag {
    padding: 2px 10px;
    border-radius: $radius-pill;
    background: rgba(140, 31, 40, 0.1);
    color: $color-primary;
  }
}

.ctx-thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 12px;
  color: $color-primary;
}

.weather {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.weather__temp {
  font-family: $font-en;
  font-size: clamp(28px, 2.6vw, 40px);
  line-height: 1;
  color: $color-gold;

  em {
    font-size: 0.38em;
    font-style: normal;
    color: rgba(26, 26, 26, 0.5);
  }
}

.weather__text {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 18px);
  color: $color-ink;
}

.weather__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 10px;
  font-size: 12px;
  color: rgba(26, 26, 26, 0.6);
}

.forecast {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(140, 31, 40, 0.15);

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 12px;
    color: rgba(26, 26, 26, 0.7);

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
      color: $color-primary;
    }
  }
}

.reco__img {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(201, 168, 106, 0.4);
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
    font-size: $fs-small;
    letter-spacing: 0.06em;
    color: $color-text-dark;
    transition: color $duration-normal $ease-out-expo;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 12px;
    color: rgba(26, 26, 26, 0.5);
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
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.6);
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.78);
  transition:
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-primary;
    transform: translateY(-2px);
    box-shadow: $shadow-sm;
  }
}

.tools__icon {
  font-size: 15px;
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
    grid-template-columns: 250px minmax(0, 1fr);
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
    padding-inline: 12px;
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

  .msg__body {
    max-width: 86%;
  }
}
</style>
