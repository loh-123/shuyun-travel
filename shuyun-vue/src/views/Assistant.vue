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
.ai {
  display: grid;
  grid-template-columns: 240px 1fr 260px;
  gap: 18px;
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
  height: calc(100vh - 68px);
  min-height: 560px;
}

/* ================= 左侧 ================= */
.ai__side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(201, 168, 106, 0.35);
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
    transition: background 0.25s ease;

    &:hover {
      background: rgba(140, 31, 40, 0.06);
    }

    &.is-active {
      background: var(--color-primary);

      .sessions__title,
      .sessions__time {
        color: #fff;
      }
    }
  }

  &__title {
    font-size: 14px;
    color: #3a2b23;
  }

  &__time {
    font-size: 11px;
    color: #9a8b82;
  }
}

/* ================= 中间对话 ================= */
.chat {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(201, 168, 106, 0.35);
}

.chat__head {
  padding: 14px 20px;
  font-size: 15px;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(90deg, #8c1f28, #6e141c);
}

.chat__list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 20px;
  overflow-y: auto;
}

.chat__empty {
  color: #9a8b82;
  font-size: 14px;
}

.bubble {
  max-width: 76%;
  font-size: 14px;
  line-height: 1.8;

  &__text {
    padding: 10px 15px;
    border-radius: 14px;
    white-space: pre-wrap;
    word-break: break-word;
  }

  /* 用户：右对齐 */
  &--user {
    align-self: flex-end;

    .bubble__text {
      background: var(--color-primary);
      color: #fff;
      border-bottom-right-radius: 4px;
    }
  }

  /* AI：左对齐 */
  &--assistant {
    align-self: flex-start;

    .bubble__text {
      background: rgba(201, 168, 106, 0.22);
      color: #4a3a32;
      border-bottom-left-radius: 4px;
    }
  }
}

.chat__input {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-top: 1px solid rgba(201, 168, 106, 0.3);

  :deep(.el-textarea) {
    flex: 1;
  }
}

/* ================= 右侧上下文 ================= */
.ai__context {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.panel {
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(201, 168, 106, 0.35);

  &__title {
    margin-bottom: 10px;
    font-size: 14px;
    letter-spacing: 2px;
    color: var(--color-primary);
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    font-size: 13px;

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: #4a3a32;
    }

    span {
      font-size: 11px;
      color: #9a8b82;
    }
  }

  &__reco {
    cursor: pointer;

    &:hover {
      color: var(--color-primary);
    }
  }

  &__weather {
    font-size: 15px;
    color: #4a3a32;
  }

  &__tip {
    font-size: 12px;
    color: #9a8b82;
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1100px) {
  .ai {
    grid-template-columns: 200px 1fr;
    height: auto;
    min-height: calc(100vh - 68px);
  }

  .ai__context {
    display: none;
  }
}
</style>
