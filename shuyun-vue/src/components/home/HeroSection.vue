<script setup lang="ts">
/**
 * 首屏 · 蜀韵管家（智能体入口页）
 * - 一句话自我介绍 + 可用对话输入框 + 3 个示例问题
 * - 输入框：回车 / 点击右侧金色按钮 → 跳转 /assistant 并带入问题（自动填入输入框）
 * - 示例问题：点击 → 跳转 /assistant 并自动提交
 * - 背景由 Home.vue 的统一 fixed 舞台提供（dark 色调），此处只负责内容层
 */
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{ active?: boolean }>()

const router = useRouter()

/** 输入框内容 */
const keyword = ref('')

/** 示例问题（胶囊按钮） */
const EXAMPLE_QUESTIONS = [
  '帮我规划成都三日游',
  '讲一讲三星堆的故事',
  '今天九寨沟适合去吗',
]

/** 金色粒子（氛围，纯 CSS 动画） */
const particles = Array.from({ length: 16 }, (_, i) => ({
  id: i,
  left: `${Math.round((i * 6.3 + 7) % 100)}%`,
  size: `${(i % 3) + 2}px`,
  duration: `${8 + (i % 5) * 2.4}s`,
  delay: `${(i % 7) * 0.9}s`,
}))

const rootEl = ref<HTMLElement | null>(null)
/** 入场动画是否已播放（加载动画结束后由父组件把 active 置为 true） */
const revealed = ref(false)

function play() {
  if (props.active) {
    revealed.value = true
  }
}

onMounted(play)
watch(() => props.active, play)

/** 输入框提交：跳转 /assistant 并自动填入问题 */
function submit() {
  const q = keyword.value.trim()
  if (q) {
    router.push({ path: '/assistant', query: { q } })
  } else {
    router.push('/assistant')
  }
}

/** 示例问题：跳转 /assistant 并自动提交 */
function askExample(q: string) {
  router.push({ path: '/assistant', query: { q, auto: '1' } })
}

/** 向下箭头：滚到第二屏 */
function scrollToNext() {
  const next = rootEl.value?.parentElement?.nextElementSibling as HTMLElement | undefined
  next?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div ref="rootEl" class="agent" :class="{ 'is-in': revealed }">
    <!-- ============ 金色粒子 ============ -->
    <div class="agent__particles" aria-hidden="true">
      <span
        v-for="p in particles"
        :key="p.id"
        class="agent__particle"
        :style="{
          left: p.left,
          width: p.size,
          height: p.size,
          animationDuration: p.duration,
          animationDelay: p.delay,
        }"
      />
    </div>

    <!-- ============ 智能体入口 ============ -->
    <div class="sy-container agent__inner">
      <!-- 状态徽标：一眼看出「这是一个 AI 智能体」 -->
      <p class="agent__badge">
        <span class="agent__badge-dot" aria-hidden="true" />
        AI 旅行智能体 · 在线
      </p>

      <!-- 大标题 -->
      <h1 class="agent__title">你好，我是蜀韵管家</h1>

      <!-- 副标题 -->
      <p class="agent__subtitle">一个懂巴蜀文化的 AI 旅行智能体</p>

      <!-- 对话输入框（真能用的入口） -->
      <form class="agent__ask" @submit.prevent="submit">
        <input
          v-model="keyword"
          class="agent__input"
          type="text"
          name="agent-q"
          autocomplete="off"
          placeholder="问问我：规划成都三日游 / 讲讲三星堆 / 今天九寨沟适合去吗"
          @keydown.enter.prevent="submit"
        />
        <button type="submit" class="agent__send" title="发送" aria-label="发送">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 12h15" />
            <path d="M13 6l6 6-6 6" />
          </svg>
        </button>
      </form>

      <!-- 示例问题 -->
      <div class="agent__examples">
        <button
          v-for="q in EXAMPLE_QUESTIONS"
          :key="q"
          type="button"
          class="agent__chip"
          @click="askExample(q)"
        >
          {{ q }}
        </button>
      </div>
    </div>

    <!-- ============ 底部：向下探索 ============ -->
    <button class="agent__foot" @click="scrollToNext">
      <span class="agent__arrow">
        <svg
          viewBox="0 0 24 24"
          width="40"
          height="40"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 4v16" />
          <path d="M5 13l7 7 7-7" />
        </svg>
      </span>
      <span class="agent__foot-text">向下探索 · 蜀韵管家的知识底座</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.agent {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* ---------------- 粒子 ---------------- */
.agent__particles {
  position: absolute;
  inset: 0;
  z-index: $z-content;
  pointer-events: none;
}

.agent__particle {
  position: absolute;
  bottom: -10px;
  border-radius: 50%;
  background: $color-gold;
  opacity: 0;
  animation-name: floatUp;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes floatUp {
  0% {
    transform: translateY(0) scale(0.6);
    opacity: 0;
  }
  15% {
    opacity: 0.7;
  }
  85% {
    opacity: 0.3;
  }
  100% {
    transform: translateY(-92vh) scale(1);
    opacity: 0;
  }
}

/* ---------------- 内容 ---------------- */
.agent__inner {
  position: relative;
  z-index: $z-content;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 让开固定导航栏，避免内容被压住 */
  padding-top: $nav-height;
  text-align: center;
}

/* 状态徽标 */
.agent__badge {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 9px 20px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-pill;
  background: rgba(16, 30, 48, 0.32);
  color: $color-gold;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  backdrop-filter: blur(6px);
}

.agent__badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6ee7a8;
  box-shadow: 0 0 0 0 rgba(110, 231, 168, 0.6);
  animation: agentPulse 2s ease-out infinite;
}

@keyframes agentPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(110, 231, 168, 0.55);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(110, 231, 168, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(110, 231, 168, 0);
  }
}

/* 主标题：56-72px，思源宋体 */
.agent__title {
  margin-top: clamp(22px, 3vh, 36px);
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(40px, 5.2vw, 72px);
  line-height: 1.16;
  letter-spacing: 0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* 副标题：20px，灰色 */
.agent__subtitle {
  margin-top: clamp(14px, 2vh, 22px);
  font-family: $font-body;
  font-size: clamp(16px, 1.35vw, 20px);
  letter-spacing: 0.1em;
  color: rgba(245, 241, 232, 0.68);
  text-shadow: $shadow-text-soft;
}

/* ---------------- 对话输入框 ---------------- */
.agent__ask {
  display: flex;
  align-items: center;
  width: min(640px, 100%);
  height: 64px;
  margin-top: clamp(28px, 4.5vh, 52px);
  padding: 6px 6px 6px 26px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-pill;
  background: rgba(14, 30, 50, 0.44);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.32);
  backdrop-filter: blur(10px);
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    background-color $duration-normal $ease-out-expo;

  &:focus-within {
    border-color: $color-gold;
    background: rgba(14, 30, 50, 0.56);
    box-shadow:
      0 18px 48px rgba(0, 0, 0, 0.36),
      0 0 0 4px rgba(212, 179, 116, 0.16);
  }
}

.agent__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: $color-text-light;
  font-family: $font-body;
  font-size: 16px;
  letter-spacing: 0.02em;

  &::placeholder {
    color: rgba(245, 241, 232, 0.42);
  }
}

/* 右侧金色发送按钮 */
.agent__send {
  flex: none;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #f7dd93 0%, $color-gold 58%, #b9882f 100%);
  color: $color-ink;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(212, 179, 116, 0.36);
  transition:
    transform $duration-fast $ease-out-expo,
    box-shadow $duration-fast $ease-out-expo,
    filter $duration-fast $ease-out-expo;

  &:hover {
    transform: translateX(2px) scale(1.05);
    filter: brightness(1.06);
    box-shadow: 0 10px 26px rgba(212, 179, 116, 0.5);
  }

  &:active {
    transform: translateX(1px) scale(0.98);
  }
}

/* ---------------- 示例问题 ---------------- */
.agent__examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: clamp(18px, 2.6vh, 28px);
}

.agent__chip {
  padding: 11px 22px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(245, 241, 232, 0.9);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    transform: translateY(-2px);
    border-color: $color-gold;
    background: rgba(212, 179, 116, 0.16);
    color: $color-text-light;
  }
}

/* ---------------- 底部 ---------------- */
.agent__foot {
  position: absolute;
  left: 50%;
  bottom: clamp(22px, 4.5vh, 52px);
  z-index: $z-content;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  transform: translateX(-50%);
  color: $color-text-light;
}

.agent__arrow {
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  border: 2px solid $color-gold-line;
  border-radius: 50%;
  color: $color-gold;
  animation: breathe 2s ease-in-out infinite;
}

@keyframes breathe {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(8px);
    opacity: 1;
  }
}

.agent__foot-text {
  font-size: $fs-small;
  letter-spacing: 0.28em;
  opacity: 0.8;
  text-shadow: $shadow-text-soft;
}

/* ---------------- 入场（加载结束后依次浮现） ---------------- */
.agent__badge,
.agent__title,
.agent__subtitle,
.agent__ask,
.agent__examples {
  opacity: 0;
  transform: translateY(20px);
  transition:
    opacity 0.75s $ease-out-expo,
    transform 0.75s $ease-out-expo;
}

.agent.is-in {
  .agent__badge {
    opacity: 1;
    transform: none;
    transition-delay: 0.05s;
  }

  .agent__title {
    opacity: 1;
    transform: none;
    transition-delay: 0.18s;
  }

  .agent__subtitle {
    opacity: 1;
    transform: none;
    transition-delay: 0.3s;
  }

  .agent__ask {
    opacity: 1;
    transform: none;
    transition-delay: 0.42s;
  }

  .agent__examples {
    opacity: 1;
    transform: none;
    transition-delay: 0.54s;
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 768px) {
  .agent__ask {
    height: 58px;
    padding-left: 18px;
  }

  .agent__send {
    width: 46px;
    height: 46px;
  }

  .agent__input {
    font-size: 14px;
  }

  .agent__examples {
    gap: 10px;
  }

  .agent__chip {
    padding: 9px 16px;
  }
}
</style>
