<script setup lang="ts">
/**
 * 右下角浮层对话窗 · 蜀韵管家（AiChatPopup）
 * - 380 × 520px，右下角悬浮，紧邻熊猫球上方
 * - 复用 /assistant 的 SSE 流式逻辑（@/composables/useAiChatStream）
 * - 与 /assistant 页共享 sessionId（localStorage）
 * - ESC / 点击外部关闭；底部「查看完整对话页 →」跳转 /assistant
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

import { streamChat, getSharedSessionId, setSharedSessionId } from '@/composables/useAiChatStream'
import request from '@/api/request'

interface PopupMsg {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const router = useRouter()

const listEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLTextAreaElement | null>(null)
const input = ref('')
const sending = ref(false)
const messages = ref<PopupMsg[]>([])
const streamingText = ref('')
/** 是否已拉取过历史（仅首次打开拉一次） */
let historyLoaded = false

/** 示例问题（空态） */
const QUICK = ['帮我规划成都三日游', '讲一讲三星堆的故事', '今天九寨沟适合去吗']

function nowText() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** Markdown 渲染（marked + DOMPurify 消毒） */
function renderMd(text: string) {
  const html = marked.parse(text || '', { async: false }) as string
  return DOMPurify.sanitize(html)
}

function scrollToBottom() {
  nextTick(() => {
    const el = listEl.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 打开时：首次拉取共享会话历史 */
watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    if (historyLoaded) {
      nextTick(() => inputEl.value?.focus())
      return
    }
    historyLoaded = true
    const sid = getSharedSessionId()
    if (sid) {
      try {
        const res = await request.get('/ai/history', { silent: true, params: { sessionId: sid } })
        messages.value = (res.data ?? []).map(
          (m: { id: number; role: string; content: string; createTime?: string }) => ({
            id: `h-${m.id}`,
            role: m.role === 'user' ? 'user' : 'assistant',
            content: m.content,
            time: m.createTime ? m.createTime.slice(11, 16) : '',
          }),
        )
      } catch {
        messages.value = []
      }
    }
    scrollToBottom()
    nextTick(() => inputEl.value?.focus())
  },
)

/** 发送（复用 SSE 流式逻辑） */
async function send(preset?: string) {
  const text = (preset ?? input.value).trim()
  if (!text || sending.value) {
    return
  }
  sending.value = true
  messages.value.push({ id: `u-${Date.now()}`, role: 'user', content: text, time: nowText() })
  input.value = ''
  streamingText.value = ''
  scrollToBottom()

  let answer = ''
  try {
    const res = await streamChat(text, getSharedSessionId(), (full) => {
      streamingText.value = full
      scrollToBottom()
    })
    answer = res.content
    if (res.sessionId) {
      setSharedSessionId(res.sessionId)
    }
  } catch {
    answer = streamingText.value
  }
  if (!answer) {
    answer = '抱歉，管家暂时无法应答，请稍后再试。'
  }
  messages.value.push({ id: `a-${Date.now()}`, role: 'assistant', content: answer, time: nowText() })
  streamingText.value = ''
  sending.value = false
  scrollToBottom()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

/** 前往完整对话页 */
function goFullPage() {
  emit('close')
  router.push('/assistant')
}

/* ---------------- 关闭：ESC / 点击外部 ---------------- */
function onEsc(e: KeyboardEvent) {
  if (props.open && e.key === 'Escape') {
    emit('close')
  }
}
function onDocPointerDown(e: PointerEvent) {
  if (!props.open) {
    return
  }
  const el = e.target as HTMLElement
  if (!el.closest('.ai-chat-popup') && !el.closest('.ai-fab')) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onEsc)
  document.addEventListener('pointerdown', onDocPointerDown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onEsc)
  document.removeEventListener('pointerdown', onDocPointerDown)
})
</script>

<template>
  <Teleport to="body">
    <transition name="acp">
      <section v-if="open" class="ai-chat-popup" data-lenis-prevent aria-label="蜀韵管家对话窗">
        <!-- 顶部 -->
        <header class="acp__head">
          <img class="acp__avatar" src="/images/panda-avatar.png" alt="蜀韵管家" />
          <span class="acp__meta">
            <b class="acp__name">蜀韵管家</b>
            <em class="acp__status"><span class="acp__dot" />在线</em>
          </span>
          <button type="button" class="acp__close" title="关闭" @click="emit('close')">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </header>

        <!-- 消息区 -->
        <div ref="listEl" class="acp__list">
          <!-- 空态 -->
          <div v-if="!messages.length && !streamingText" class="acp__empty">
            <img class="acp__empty-avatar" src="/images/panda-avatar.png" alt="" />
            <p class="acp__empty-title">你好呀，我是蜀韵管家</p>
            <p class="acp__empty-sub">一个懂巴蜀文化的 AI 旅行智能体</p>
            <div class="acp__quick">
              <button v-for="q in QUICK" :key="q" type="button" @click="send(q)">{{ q }}</button>
            </div>
          </div>

          <!-- 消息 -->
          <div v-for="m in messages" :key="m.id" class="acp-msg" :class="`acp-msg--${m.role}`">
            <img v-if="m.role === 'assistant'" class="acp-msg__avatar" src="/images/panda-avatar.png" alt="" />
            <div class="acp-msg__body">
              <div v-if="m.role === 'assistant'" class="acp-msg__md" v-html="renderMd(m.content)" />
              <template v-else>{{ m.content }}</template>
              <span class="acp-msg__time">{{ m.time }}</span>
            </div>
          </div>

          <!-- 流式输出中 -->
          <div v-if="streamingText" class="acp-msg acp-msg--assistant">
            <img class="acp-msg__avatar" src="/images/panda-avatar.png" alt="" />
            <div class="acp-msg__body">
              <div class="acp-msg__md" v-html="renderMd(streamingText)" />
              <span class="acp-msg__caret" />
            </div>
          </div>

          <!-- 等待中 -->
          <div v-if="sending && !streamingText" class="acp-msg acp-msg--assistant">
            <img class="acp-msg__avatar" src="/images/panda-avatar.png" alt="" />
            <div class="acp-msg__bubble-typing"><span class="dot" /><span class="dot" /><span class="dot" /></div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="acp__input">
          <div class="acp__box">
            <textarea
              ref="inputEl"
              v-model="input"
              class="acp__field"
              rows="1"
              placeholder="问我关于四川的任何问题…"
              @keydown="onKeydown"
            />
            <button
              type="button"
              class="acp__send"
              :disabled="!input.trim() || sending"
              title="发送"
              @click="send()"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 12h15M13 6l6 6-6 6" />
              </svg>
            </button>
          </div>
          <button type="button" class="acp__more" @click="goFullPage">查看完整对话页 →</button>
        </div>
      </section>
    </transition>
  </Teleport>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.ai-chat-popup {
  position: fixed;
  right: 24px;
  bottom: 132px;
  z-index: $z-header + 40;
  display: flex;
  flex-direction: column;
  width: min(380px, calc(100vw - 32px));
  height: 520px;
  max-height: calc(100vh - 160px);
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.98);
  box-shadow:
    0 26px 60px rgba(14, 42, 71, 0.28),
    0 0 34px rgba(212, 179, 116, 0.28);
  backdrop-filter: blur(12px);
}

/* 顶部 */
.acp__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.28);
  background: linear-gradient(120deg, rgba(122, 26, 34, 0.06), rgba(212, 179, 116, 0.06));
}

.acp__avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 1.5px rgba(212, 179, 116, 0.6);
}

.acp__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.acp__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
}

.acp__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-style: normal;
  font-size: 15px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.acp__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3ec07a;
}

.acp__close {
  @include flex-center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: rgba(var(--sy-text-rgb), 0.6);
  transition: background-color $duration-fast $ease-out-expo, color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.08);
    color: var(--sy-accent);
  }
}

/* 消息区 */
.acp__list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* 空态 */
.acp__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: auto 0;
  text-align: center;
}

.acp__empty-avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(212, 179, 116, 0.5), 0 10px 26px rgba(14, 42, 71, 0.18);
}

.acp__empty-title {
  margin-top: 8px;
  font-family: $font-title;
  font-weight: 900;
  font-size: 21px;
  color: $color-ink;
}

.acp__empty-sub {
  font-size: 15px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.acp__quick {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;

  button {
    padding: 7px 14px;
    border: 1px solid rgba(212, 179, 116, 0.5);
    border-radius: $radius-pill;
    background: rgba(var(--sy-surface-rgb), 0.6);
    font-family: $font-body;
    font-size: 14px;
    color: rgba(var(--sy-text-rgb), 0.78);
    transition: border-color $duration-fast $ease-out-expo, color $duration-fast $ease-out-expo;

    &:hover {
      border-color: $color-gold;
      color: var(--sy-accent);
    }
  }
}

/* 消息气泡 */
.acp-msg {
  display: flex;
  gap: 8px;
  max-width: 100%;
}

.acp-msg--user {
  justify-content: flex-end;
}

.acp-msg__avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgba(212, 179, 116, 0.5);
}

.acp-msg__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 82%;
  min-width: 0;
}

.acp-msg--user .acp-msg__body {
  align-items: flex-end;
}

.acp-msg--user .acp-msg__body {
  padding: 8px 12px;
  border-radius: $radius-md;
  background: linear-gradient(120deg, $color-primary, #6e141c);
  color: $color-text-light;
  font-family: $font-body;
  font-size: 15px;
  line-height: 1.7;
}

.acp-msg__md {
  font-family: $font-body;
  font-size: 15px;
  line-height: 1.85;
  color: rgba(var(--sy-text-rgb), 0.86);
  word-break: break-word;

  /* 标题统一用正文字体（与对话页一致） */
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    font-family: $font-body;
  }

  :deep(p) {
    margin: 0 0 8px;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  :deep(th),
  :deep(td) {
    border: 1px solid rgba(212, 179, 116, 0.4);
    padding: 4px 6px;
  }
}

.acp-msg__time {
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.4);
}

.acp-msg--user .acp-msg__time {
  color: rgba(245, 241, 232, 0.7);
}

.acp-msg__caret {
  display: inline-block;
  width: 7px;
  height: 15px;
  vertical-align: -2px;
  background: var(--sy-accent);
  animation: acpBlink 1s steps(2) infinite;
}

@keyframes acpBlink {
  50% {
    opacity: 0;
  }
}

.acp-msg__bubble-typing {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 10px 14px;
  border-radius: $radius-md;
  background: rgba(var(--sy-text-rgb), 0.06);
}

/* 打字中圆点（本组件局部，不依赖全局 .dot） */
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(var(--sy-text-rgb), 0.5);
  animation: acpDot 1.2s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.15s;
  }

  &:nth-child(3) {
    animation-delay: 0.3s;
  }
}

@keyframes acpDot {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

/* 输入区 */
.acp__input {
  padding: 10px 12px 12px;
  border-top: 1px solid rgba(212, 179, 116, 0.28);
}

.acp__box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 6px 6px 12px;
  border: 1px solid rgba(212, 179, 116, 0.45);
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.7);

  &:focus-within {
    border-color: $color-gold;
  }
}

.acp__field {
  flex: 1;
  min-width: 0;
  max-height: 84px;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  font-family: $font-body;
  font-size: 15px;
  line-height: 1.6;
  color: var(--sy-text-dark);
}

.acp__send {
  @include flex-center;
  flex: none;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #f7dd93, $color-gold 60%, #b9882f);
  color: $color-ink;
  cursor: pointer;
  transition: filter $duration-fast $ease-out-expo, transform $duration-fast $ease-out-expo;

  &:hover {
    filter: brightness(1.06);
    transform: translateX(1px);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.acp__more {
  display: block;
  width: 100%;
  margin-top: 8px;
  font-family: $font-body;
  font-size: 14px;
  letter-spacing: 0.06em;
  color: $color-gold;
  text-align: center;
  transition: color $duration-fast $ease-out-expo;

  &:hover {
    color: var(--sy-accent);
  }
}

/* 出入场 */
.acp-enter-from,
.acp-leave-to {
  opacity: 0;
  transform: translateY(18px) scale(0.97);
}

.acp-enter-active,
.acp-leave-active {
  transition:
    opacity 0.28s $ease-out-expo,
    transform 0.28s $ease-out-expo;
}

@media (max-width: 480px) {
  .ai-chat-popup {
    right: 12px;
    left: 12px;
    width: auto;
    bottom: 120px;
  }
}

/* ============================================================
 * 字体统一：浮层对话窗 = 思源宋体（与对话页一致）
 * ========================================================== */
.ai-chat-popup,
.ai-chat-popup :deep(*) {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', 'SimSun', serif !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.ai-chat-popup :deep(code),
.ai-chat-popup :deep(pre) {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace !important;
}

/* 标题：半粗 */
.ai-chat-popup .acp__name {
  font-weight: 600;
}

/* 正文：常规 400 + 行高 1.6 */
.ai-chat-popup :deep(.acp-msg__md),
.ai-chat-popup :deep(.acp-msg__md p) {
  font-weight: 400;
  line-height: 1.6;
}
</style>
