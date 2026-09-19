<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request, { TOKEN_KEY } from '@/api/request'

const router = useRouter()

interface Message {
  role: 'user' | 'assistant'
  content: string
}

interface Session {
  id: string
  title: string
  time: string
}

/** 左侧会话列表（先做静态） */
const sessions = ref<Session[]>([
  { id: 'demo-1', title: '川西小环线规划', time: '刚刚' },
  { id: 'demo-2', title: '成都美食推荐', time: '昨天' },
  { id: 'demo-3', title: '三星堆一日游', time: '3 天前' },
])

const activeId = ref('demo-1')

/** 每个会话各自维护消息（本地），避免互相串台 */
const messageMap = reactive<Record<string, Message[]>>({
  'demo-1': [],
  'demo-2': [],
  'demo-3': [],
})

const currentMessages = computed<Message[]>(() => messageMap[activeId.value] ?? [])

const input = ref('')
const sending = ref(false)
const listEl = ref<HTMLElement | null>(null)

/** 右侧上下文面板 */
const trips = [
  { title: '成都 3 日游', date: '2026-05-01' },
  { title: '川西环线 5 日', date: '2026-06-12' },
]
const weather = { city: '成都', text: '多云 22℃', tip: '微风，适合出行' }
const recommend = ref<{ id: number; name: string; cityName?: string }[]>([])

async function loadRecommend() {
  try {
    const res = await request.get('/scenic/hot', { silent: true })
    if (Array.isArray(res.data)) {
      recommend.value = res.data.slice(0, 3)
    }
  } catch {
    // 忽略：推荐为空不影响对话
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = listEl.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/**
 * 打字机效果：逐字把 full 写入 messageMap[sessionId][index].content
 * 注意通过响应式数组下标取值，保证赋值能触发视图更新
 */
function typewriter(full: string, sessionId: string, index: number, speed = 22) {
  return new Promise<void>((resolve) => {
    let i = 0
    const timer = window.setInterval(() => {
      const msg = messageMap[sessionId][index]
      if (!msg) {
        window.clearInterval(timer)
        resolve()
        return
      }
      i += 2
      msg.content = full.slice(0, i)
      scrollToBottom()
      if (i >= full.length) {
        window.clearInterval(timer)
        msg.content = full
        resolve()
      }
    }, speed)
  })
}

async function onSend() {
  const text = input.value.trim()
  if (!text || sending.value) {
    return
  }

  const sessionId = activeId.value
  const bucket = messageMap[sessionId]
  if (!bucket) {
    return
  }

  bucket.push({ role: 'user', content: text })
  input.value = ''
  scrollToBottom()

  sending.value = true
  bucket.push({ role: 'assistant', content: '' })
  const index = bucket.length - 1

  try {
    // 优先走 SSE 流式输出
    await streamChat(text, sessionId, index)
  } catch {
    // 流式不可用时，退化为普通请求 + 前端打字机
    try {
      const res = await request.post('/ai/chat', { message: text, sessionId })
      await typewriter(res.data?.reply ?? '（管家没有返回内容）', sessionId, index)
    } catch {
      const msg = messageMap[sessionId][index]
      if (msg) {
        msg.content = '抱歉，管家暂时无法应答，请稍后再试。'
      }
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/**
 * SSE 流式对话
 * 注：浏览器 EventSource 无法自定义请求头，token 通过查询参数传给后端
 */
function streamChat(text: string, sessionId: string, index: number) {
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

    es.addEventListener('message', (ev: MessageEvent) => {
      received = true
      const msg = messageMap[sessionId][index]
      if (msg) {
        msg.content = ev.data as string
      }
      scrollToBottom()
    })

    es.addEventListener('done', () => finish())

    es.onerror = () => {
      // 已收到内容则视为正常结束，否则交给上层兜底
      finish(received ? undefined : new Error('SSE failed'))
    }

    window.setTimeout(() => finish(received ? undefined : new Error('SSE timeout')), 30000)
  })
}

function onNewSession() {
  const id = `local-${Date.now()}`
  sessions.value.unshift({ id, title: '新的对话', time: '刚刚' })
  messageMap[id] = []
  activeId.value = id
}

function goScenic(id: number) {
  router.push(`/scenic/${id}`)
}

onMounted(loadRecommend)
</script>

<template>
  <div class="ai">
    <!-- ================= 左：会话列表 ================= -->
    <aside class="ai__side">
      <el-button type="primary" round class="ai__new" @click="onNewSession">+ 新建对话</el-button>

      <ul class="sessions">
        <li
          v-for="s in sessions"
          :key="s.id"
          class="sessions__item"
          :class="{ 'is-active': s.id === activeId }"
          @click="activeId = s.id"
        >
          <span class="sessions__title">{{ s.title }}</span>
          <span class="sessions__time">{{ s.time }}</span>
        </li>
      </ul>
    </aside>

    <!-- ================= 中：对话窗口 ================= -->
    <section class="chat">
      <header class="chat__head">AI 管家 · 蜀韵智游</header>

      <div ref="listEl" class="chat__list">
        <p v-if="!currentMessages.length" class="chat__empty">
          你好，我是蜀韵·智游管家，可以帮你规划行程、推荐景点与美食。
        </p>

        <div
          v-for="(m, i) in currentMessages"
          :key="i"
          class="bubble"
          :class="`bubble--${m.role}`"
        >
          <div class="bubble__text">{{ m.content }}</div>
        </div>
      </div>

      <div class="chat__input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="说说你想去哪儿…（回车发送）"
          @keyup.enter="onSend"
        />
        <el-button type="primary" :loading="sending" @click="onSend">发送</el-button>
      </div>
    </section>

    <!-- ================= 右：上下文面板 ================= -->
    <aside class="ai__context">
      <div class="panel">
        <h4 class="panel__title">当前行程</h4>
        <ul class="panel__list">
          <li v-for="t in trips" :key="t.title">
            {{ t.title }}<span>{{ t.date }}</span>
          </li>
        </ul>
      </div>

      <div class="panel">
        <h4 class="panel__title">天气（占位）</h4>
        <p class="panel__weather">{{ weather.city }} · {{ weather.text }}</p>
        <p class="panel__tip">{{ weather.tip }}</p>
      </div>

      <div class="panel">
        <h4 class="panel__title">推荐景点</h4>
        <ul class="panel__list">
          <li
            v-for="r in recommend"
            :key="r.id"
            class="panel__reco"
            @click="goScenic(r.id)"
          >
            {{ r.name }}<span>{{ r.cityName }}</span>
          </li>
        </ul>
      </div>
    </aside>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* 三栏应用式布局：左右留白随屏幕变化 */
.ai {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 280px;
  gap: clamp(12px, 1.4vw, 22px);
  width: 100%;
  height: calc(100vh - 68px);
  min-height: 560px;
  padding: clamp(14px, 1.6vw, 26px) $gutter;
}

/* ================= 左侧 ================= */
.ai__side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: clamp(14px, 1.4vw, 20px);
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  overflow-y: auto;
}

.ai__new {
  width: 100%;
}

.sessions {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 10px;
    cursor: pointer;
    transition:
      background-color $duration-fast $ease-out-expo,
      transform $duration-fast $ease-out-expo;

    &:hover {
      background: rgba(140, 31, 40, 0.07);
      transform: translateX(3px);
    }

    &.is-active {
      background: linear-gradient(120deg, $color-primary, #6e141c);

      .sessions__title {
        color: $color-text-light;
      }

      .sessions__time {
        color: rgba(245, 241, 232, 0.7);
      }
    }
  }

  &__title {
    font-family: $font-body;
    font-size: $fs-small;
    color: $color-text-dark;
  }

  &__time {
    font-size: 11px;
    color: rgba(26, 26, 26, 0.42);
  }
}

/* ================= 中间对话 ================= */
.chat {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: $shadow-md;
}

.chat__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: clamp(13px, 1.4vw, 18px) clamp(16px, 1.8vw, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 17px);
  letter-spacing: 0.16em;
  color: $color-gold;
  background: linear-gradient(100deg, $color-ink, #16233a 60%, #1d2436);

  // 左侧金色光点
  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: $color-gold;
    box-shadow: 0 0 12px rgba(201, 168, 106, 0.9);
  }
}

.chat__list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: clamp(18px, 2vw, 28px) clamp(16px, 1.8vw, 26px);
  overflow-y: auto;
}

.chat__empty {
  max-width: 52ch;
  font-family: $font-body;
  font-size: $fs-body;
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.5);
}

.bubble {
  max-width: 78%;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.85;

  &__text {
    padding: 11px 16px;
    border-radius: 14px;
    white-space: pre-wrap;
    word-break: break-word;
  }

  /* 用户：右对齐 */
  &--user {
    align-self: flex-end;

    .bubble__text {
      background: linear-gradient(120deg, $color-primary, #6e141c);
      color: $color-text-light;
      border-bottom-right-radius: 4px;
      box-shadow: 0 6px 18px rgba(140, 31, 40, 0.22);
    }
  }

  /* AI：左对齐 */
  &--assistant {
    align-self: flex-start;

    .bubble__text {
      border: 1px solid rgba(201, 168, 106, 0.38);
      background: rgba(201, 168, 106, 0.16);
      color: rgba(26, 26, 26, 0.84);
      border-bottom-left-radius: 4px;
    }
  }
}

.chat__input {
  display: flex;
  gap: 12px;
  padding: clamp(12px, 1.4vw, 18px) clamp(14px, 1.6vw, 22px);
  border-top: 1px solid rgba(201, 168, 106, 0.32);

  :deep(.el-textarea) {
    flex: 1;
  }
}

/* ================= 右侧上下文 ================= */
.ai__context {
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.2vw, 18px);
  overflow-y: auto;
}

.panel {
  padding: clamp(14px, 1.4vw, 20px);
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;

  &__title {
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

  &__list {
    display: flex;
    flex-direction: column;
    gap: 9px;
    font-family: $font-body;
    font-size: $fs-small;

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      color: rgba(26, 26, 26, 0.78);
    }

    span {
      font-size: 11px;
      color: rgba(26, 26, 26, 0.42);
    }
  }

  &__reco {
    cursor: pointer;
    transition: color $duration-fast $ease-out-expo;

    &:hover {
      color: $color-primary;
    }
  }

  &__weather {
    font-family: $font-title;
    font-size: clamp(14px, 1.2vw, 18px);
    color: $color-ink;
  }

  &__tip {
    margin-top: 4px;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.48);
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1100px) {
  .ai {
    grid-template-columns: 220px minmax(0, 1fr);
    height: auto;
    min-height: calc(100vh - 68px);
  }

  .ai__context {
    display: none;
  }
}

@media (max-width: 768px) {
  .ai {
    grid-template-columns: minmax(0, 1fr);
    padding-inline: 16px;
  }

  .ai__side {
    max-height: 180px;
  }

  .bubble {
    max-width: 100%;
  }
}
</style>
