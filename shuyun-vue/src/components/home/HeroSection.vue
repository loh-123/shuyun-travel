<script setup lang="ts">
/**
 * 首屏 Hero
 * - 主标题「蜀韵智游」使用真实字形轮廓（SVG path）错落排版：
 *   金色描边 + 逐字「书写」动画（stroke-dashoffset）+ 鼠标视差
 * - 副标题逐字浮现；右侧竖排诗句；右下角印章；金色粒子；底部呼吸箭头
 * - 背景由 Home.vue 的统一 fixed 舞台负责（便于整屏交叉淡入）
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import gsap from 'gsap'
import { TITLE_GLYPHS } from './title-paths'

const props = defineProps<{ active?: boolean }>()

const rootEl = ref<HTMLElement | null>(null)
const titleEl = ref<HTMLElement | null>(null)
const subtitleEl = ref<HTMLElement | null>(null)
const poemEl = ref<HTMLElement | null>(null)
const sealEl = ref<HTMLElement | null>(null)
const lineEl = ref<HTMLElement | null>(null)

/** 副标题逐字浮现 */
const subtitleChars = 'AI 懂你的巴蜀旅行管家'.split('')
/** 竖排诗句 */
const poemLines = ['九天开出一成都', '万户千门入画图']

/** 金色粒子（位置/大小/时长随机化） */
const particles = Array.from({ length: 16 }, (_, i) => ({
  id: i,
  left: `${Math.round((i * 6.3 + 7) % 100)}%`,
  size: `${(i % 3) + 2}px`,
  duration: `${8 + (i % 5) * 2.4}s`,
  delay: `${(i % 7) * 0.9}s`,
}))

/** 每字的视差 setter（3 ~ 8px） */
const parallaxSetters: { x: (v: number) => void; y: (v: number) => void; depth: number }[] = []
let revealed = false

function onMouseMove(e: MouseEvent) {
  const nx = (e.clientX / window.innerWidth - 0.5) * 2
  const ny = (e.clientY / window.innerHeight - 0.5) * 2
  parallaxSetters.forEach((s, i) => {
    // 奇偶反向，四个字形成层次
    const dir = i % 2 === 0 ? 1 : -1
    s.x(nx * s.depth * dir)
    s.y(ny * s.depth * dir)
  })
}

/**
 * 首屏入场
 * 1) 主标题逐字「书写」：先描边画轮廓，再填充浮现
 * 2) 副标题逐字、诗句、印章、金线依次到位
 */
function playReveal() {
  if (revealed) {
    return
  }
  revealed = true

  const paths = titleEl.value
    ? Array.from(titleEl.value.querySelectorAll<SVGPathElement>('.hero__glyph path'))
    : []
  const subs = subtitleEl.value
    ? Array.from(subtitleEl.value.querySelectorAll('.hero__sub-char'))
    : []

  const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })

  // ---- 逐字书写（描边 → 填充）----
  paths.forEach((p, i) => {
    const len = p.getTotalLength()
    gsap.set(p, { strokeDasharray: len, strokeDashoffset: len, fillOpacity: 0 })
    tl.to(p, { strokeDashoffset: 0, duration: 1.35, ease: 'power2.inOut' }, i * 0.16).to(
      p,
      { fillOpacity: 1, duration: 0.7, ease: 'power1.out' },
      i * 0.16 + 1.05,
    )
  })

  // ---- 其余元素 ----
  tl.from(subs, { opacity: 0, y: 12, duration: 0.6, stagger: 0.08 }, 0.5)
    .from(lineEl.value, { scaleX: 0, duration: 1, ease: 'power2.inOut' }, 0)
    .from(poemEl.value, { opacity: 0, x: 24, duration: 1 }, 0.6)
    .from(sealEl.value, { opacity: 0, scale: 0.6, duration: 0.9, ease: 'back.out(1.6)' }, 0.8)

  return tl
}

onMounted(() => {
  const chars = titleEl.value
    ? Array.from(titleEl.value.querySelectorAll<HTMLElement>('.hero__char'))
    : []
  chars.forEach((el, i) => {
    parallaxSetters.push({
      x: gsap.quickTo(el, 'x', { duration: 0.7, ease: 'power3.out' }),
      y: gsap.quickTo(el, 'y', { duration: 0.7, ease: 'power3.out' }),
      depth: 3 + i * 1.6,
    })
  })
  window.addEventListener('mousemove', onMouseMove, { passive: true })

  if (props.active) {
    playReveal()
  }
})

// 加载动画结束后由父组件把 active 置为 true
watch(
  () => props.active,
  (v) => {
    if (v) {
      playReveal()
    }
  },
)

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
})

/** 向下箭头：滚到第二屏 */
function scrollToNext() {
  const next = rootEl.value?.parentElement?.nextElementSibling as HTMLElement | undefined
  next?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div ref="rootEl" class="hero">
    <!-- ============ 金色粒子 ============ -->
    <div class="hero__particles" aria-hidden="true">
      <span
        v-for="p in particles"
        :key="p.id"
        class="hero__particle"
        :style="{
          left: p.left,
          width: p.size,
          height: p.size,
          animationDuration: p.duration,
          animationDelay: p.delay,
        }"
      />
    </div>

    <!-- ============ 金色细线（左上 → 标题） ============ -->
    <div ref="lineEl" class="hero__line" aria-hidden="true" />

    <!-- ============ 竖排诗句 ============ -->
    <div ref="poemEl" class="hero__poem">
      <span v-for="line in poemLines" :key="line" class="hero__poem-line">{{ line }}</span>
    </div>

    <!-- ============ 主标题：真实字形轮廓，错落排版 ============ -->
    <div class="sy-container hero__inner">
      <h1 ref="titleEl" class="hero__title" aria-label="蜀韵智游">
        <span v-for="(g, i) in TITLE_GLYPHS" :key="i" class="hero__char">
          <svg
            class="hero__glyph"
            :viewBox="g.viewBox"
            xmlns="http://www.w3.org/2000/svg"
            role="img"
            :aria-label="g.char"
          >
            <g :transform="g.transform">
              <path :d="g.d" />
            </g>
          </svg>
        </span>
      </h1>

      <!-- 副标题：右下方，逐字浮现 -->
      <p ref="subtitleEl" class="hero__subtitle">
        <span v-for="(c, i) in subtitleChars" :key="i" class="hero__sub-char">{{
          c === ' ' ? ' ' : c
        }}</span>
      </p>
    </div>

    <!-- ============ 右下角印章 ============ -->
    <div ref="sealEl" class="hero__seal" aria-hidden="true">
      <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
        <rect
          x="2.5"
          y="2.5"
          width="59"
          height="59"
          rx="7"
          fill="#8C1F28"
          stroke="#C9A86A"
          stroke-width="1.5"
        />
        <text
          x="32"
          y="44"
          text-anchor="middle"
          font-size="30"
          font-weight="900"
          fill="#C9A86A"
          font-family="'Source Han Serif SC', serif"
        >
          蜀
        </text>
      </svg>
    </div>

    <!-- ============ 底部：向下探索 ============ -->
    <button class="hero__foot" @click="scrollToNext">
      <span class="hero__arrow">
        <svg
          viewBox="0 0 24 24"
          width="22"
          height="22"
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
      <span class="hero__foot-text">向下探索 · 蜀地千年</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.hero {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* ---------------- 粒子 ---------------- */
.hero__particles {
  position: absolute;
  inset: 0;
  z-index: $z-content;
  pointer-events: none;
}

.hero__particle {
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
    opacity: 0.75;
  }
  85% {
    opacity: 0.35;
  }
  100% {
    transform: translateY(-92vh) scale(1);
    opacity: 0;
  }
}

/* ---------------- 金色细线 ---------------- */
.hero__line {
  position: absolute;
  top: clamp(80px, 12vh, 180px);
  left: 0;
  z-index: $z-content;
  width: clamp(120px, 22vw, 420px);
  height: 1px;
  background: linear-gradient(90deg, rgba(201, 168, 106, 0) 0%, $color-gold 100%);
  transform-origin: left;
}

/* ---------------- 竖排诗句 ---------------- */
.hero__poem {
  position: absolute;
  top: 18vh;
  right: clamp(18px, 4vw, 72px);
  z-index: $z-content;
  display: flex;
  flex-direction: row-reverse;
  gap: clamp(10px, 1.6vw, 26px);
  writing-mode: vertical-rl;
  text-orientation: upright;
  font-family: $font-body;
  font-size: clamp(14px, 1.1vw, 18px);
  letter-spacing: 0.35em;
  color: $color-text-light;
  opacity: 0.7;
  text-shadow: $shadow-text-soft;
}

/* ---------------- 主体 ---------------- */
.hero__inner {
  position: relative;
  z-index: $z-content;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  height: 100%;
  padding-right: 12vw;
}

/* 错落排版：2×2，字号/位置/透明度各不相同
   注意用 margin 做静态偏移，把 transform 留给 GSAP 视差 */
.hero__title {
  display: grid;
  grid-template-columns: auto auto;
  align-items: start;
  column-gap: clamp(6px, 1.4vw, 30px);
  row-gap: 0;
  margin-left: clamp(0px, 3vw, 64px);
}

.hero__char {
  display: block;
  will-change: transform;

  &:nth-child(1) {
    width: clamp(96px, 13vw, 220px);
    margin-top: 0;
  }

  &:nth-child(2) {
    width: clamp(62px, 8.4vw, 146px);
    margin-top: clamp(18px, 2.8vw, 54px);
    opacity: 0.85;
  }

  &:nth-child(3) {
    width: clamp(62px, 8.4vw, 146px);
    margin-top: clamp(-18px, -1.4vw, -6px);
    margin-left: clamp(10px, 1.6vw, 34px);
    opacity: 0.72;
  }

  &:nth-child(4) {
    width: clamp(96px, 13vw, 220px);
    margin-top: clamp(4px, 0.8vw, 18px);
  }
}

/* 字形：金色描边 + 亮色填充（填充由入场动画控制） */
.hero__glyph {
  display: block;
  width: 100%;
  height: auto;

  path {
    fill: $color-text-light;
    stroke: $color-gold;
    stroke-width: 1.5;
    stroke-linejoin: round;
    // 描边不随 viewBox 缩放而变粗
    vector-effect: non-scaling-stroke;
    filter: drop-shadow(0 2px 18px rgba(0, 0, 0, 0.5));
  }
}

/* ---------------- 副标题 ---------------- */
.hero__subtitle {
  margin-top: clamp(24px, 4.5vh, 64px);
  margin-left: clamp(0px, 6vw, 140px);
  font-family: $font-body;
  font-size: clamp(18px, 1.6vw, 28px);
  letter-spacing: 0.3em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.hero__sub-char {
  display: inline-block;
  white-space: pre;
}

/* ---------------- 印章 ---------------- */
.hero__seal {
  position: absolute;
  right: clamp(20px, 5vw, 96px);
  bottom: clamp(88px, 14vh, 168px);
  z-index: $z-content;
  width: clamp(52px, 4.6vw, 76px);
  filter: drop-shadow($shadow-seal);
}

/* ---------------- 底部 ---------------- */
.hero__foot {
  position: absolute;
  left: 50%;
  bottom: clamp(24px, 5vh, 56px);
  z-index: $z-content;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transform: translateX(-50%);
  color: $color-text-light;
}

.hero__arrow {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid $color-gold-line;
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

.hero__foot-text {
  font-size: $fs-small;
  letter-spacing: 0.3em;
  opacity: 0.8;
  text-shadow: $shadow-text-soft;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .hero__inner {
    padding-right: $gutter;
  }

  .hero__poem {
    display: none;
  }

  .hero__seal {
    bottom: clamp(96px, 18vh, 200px);
  }
}

@media (max-width: 768px) {
  .hero__title {
    margin-left: 0;
  }

  .hero__subtitle {
    margin-left: 0;
    letter-spacing: 0.18em;
  }
}
</style>
