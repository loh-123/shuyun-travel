<script setup lang="ts">
/**
 * 第五屏：AI 管家
 * - 深色实景底 + 遮罩
 * - 左侧文案与进入按钮，右侧一段「对话预览」（静态示意）
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)

/** 对话预览（静态示意，真实对话在 /assistant） */
const previewMessages = [
  { role: 'user', text: '我想带孩子玩三天，别太累，怎么安排？' },
  { role: 'ai', text: '建议「成都 + 都江堰 + 青城山」：D1 市区慢逛，D2 都江堰看水利工程，D3 青城山坐索道上下。' },
]

let ctx: gsap.Context | null = null

function goAssistant() {
  router.push('/assistant')
}

onMounted(() => {
  const root = rootEl.value
  if (!root) {
    return
  }
  ctx = gsap.context(() => {
    gsap.from('.ai__animate', {
      y: 40,
      opacity: 0,
      duration: 1,
      ease: 'power3.out',
      stagger: 0.12,
      scrollTrigger: { trigger: root, start: 'top 65%', once: true },
    })
  }, root)
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  ctx?.revert()
})
</script>

<template>
  <div ref="rootEl" class="ai">
    <div class="sy-container ai__inner">
      <!-- 左：文案 + CTA -->
      <div class="ai__copy">
        <p class="sy-eyebrow ai__animate">AI Travel Butler</p>
        <h2 class="sy-title sy-on-dark ai__title ai__animate">
          会聊天的<br />
          巴蜀旅行管家
        </h2>
        <p class="sy-text sy-on-dark ai__desc ai__animate">
          告诉它几天假、和谁去、想吃什么，它会把市州、景点、非遗与季节串成一条
          真正走得通的路线 —— 不是搜索列表，而是一份可以带出门的行程。
        </p>
        <button class="sy-btn ai__animate ai__cta" @click="goAssistant">
          进入 AI 管家
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </button>
      </div>

      <!-- 右：对话预览 -->
      <div class="ai__preview ai__animate">
        <div class="ai__preview-head">
          <span class="ai__dot" />
          <span>蜀韵·智游管家</span>
        </div>
        <div class="ai__preview-body">
          <div
            v-for="(m, i) in previewMessages"
            :key="i"
            class="ai__bubble"
            :class="`ai__bubble--${m.role}`"
          >
            {{ m.text }}
          </div>
        </div>
        <div class="ai__preview-foot">示例对话 · 真实体验请进入 AI 管家</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.ai {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.ai__inner {
  position: relative;
  z-index: $z-content;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  gap: $gap-xl;
  padding-block: clamp(48px, 9vh, 120px);
}

/* ---------------- 左侧文案 ---------------- */
.ai__title {
  margin-top: 14px;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.ai__desc {
  margin-top: clamp(14px, 2vh, 26px);
  color: $color-text-light;
  opacity: 0.86;
  text-shadow: $shadow-text-soft;
}

.ai__cta {
  margin-top: clamp(24px, 3.6vh, 48px);
}

/* ---------------- 右侧对话预览 ---------------- */
.ai__preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: clamp(18px, 2vw, 28px);
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-card;
  background: rgba(20, 26, 34, 0.42);
  backdrop-filter: blur(14px);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
}

.ai__preview-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-title;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-gold;
}

.ai__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $color-gold;
  box-shadow: 0 0 12px rgba(201, 168, 106, 0.9);
}

.ai__preview-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai__bubble {
  max-width: 88%;
  padding: 12px 16px;
  border-radius: 12px;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.8;

  &--user {
    align-self: flex-end;
    background: rgba(140, 31, 40, 0.85);
    color: $color-text-light;
  }

  &--ai {
    align-self: flex-start;
    background: rgba(201, 168, 106, 0.16);
    color: $color-text-light;
    border: 1px solid rgba(201, 168, 106, 0.32);
  }
}

.ai__preview-foot {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: rgba(245, 241, 232, 0.5);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .ai__inner {
    grid-template-columns: 1fr;
    gap: $gap-lg;
  }

  .ai__preview {
    max-width: 560px;
  }
}

@media (max-width: 768px) {
  .ai__bubble {
    max-width: 100%;
  }
}
</style>
