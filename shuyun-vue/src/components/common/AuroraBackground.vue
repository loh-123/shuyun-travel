<script setup lang="ts">
/**
 * 极光氛围背景（AuroraBackground）
 *
 * 真实极光帷幕效果（参考冰岛极光 / 演唱会背景）：
 * ① SVG filter 扭曲：feTurbulence 生成噪声 + feDisplacementMap 让光带被噪声扭曲，
 *    并对 baseFrequency 做 SMIL 动画 → 光带无时无刻在"扭曲流动"（不是静态模糊团）
 * ② 三色层叠：青绿 / 青蓝 / 紫 三层，用 opacity 循环交替 → 24s 完成一轮颜色流转
 * ③ 细光线：26 条 2px 宽的垂直细光条（随机位置/高度/时长）→ 清晰的"光线痕迹"
 *
 * 性能：纯 CSS + SVG 动画（无 JS 逐帧）、filter 只作用在一个 <g> 上、
 * numOctaves=3、scale=80；移动端与 prefers-reduced-motion 下自动弱化。
 */

/** 细光线数量 */
const STREAK_COUNT = 26

interface Streak {
  /** 水平位置（%） */
  left: number
  /** 高度（%） */
  height: number
  /** 顶部位置（%） */
  top: number
  /** 动画延迟（秒） */
  delay: number
  /** 动画时长（秒） */
  dur: number
}

/** 随机生成细光线（每次挂载随机一次，静态数据 → 无 JS 逐帧） */
const streaks: Streak[] = Array.from({ length: STREAK_COUNT }, () => ({
  left: Math.random() * 100,
  height: 30 + Math.random() * 40,
  top: Math.random() * 30,
  delay: Math.random() * 3,
  dur: 2 + Math.random() * 3,
}))

/** 单条细光线的内联样式 */
function streakStyle(s: Streak): Record<string, string> {
  return {
    left: `${s.left}%`,
    top: `${s.top}%`,
    height: `${s.height}%`,
    animationDelay: `${s.delay}s`,
    animationDuration: `${s.dur}s`,
  }
}
</script>

<template>
  <div class="aurora-bg" aria-hidden="true">
    <!-- ① 主极光帷幕：噪声扭曲 + 三色层叠 -->
    <svg class="aurora-svg" xmlns="http://www.w3.org/2000/svg" width="100%" height="100%">
      <defs>
        <!-- 极光扭曲滤镜：噪声 + 位移映射，并对频率做动画 → 持续流动 -->
        <filter id="aurora-warp" x="-30%" y="-30%" width="160%" height="160%">
          <feTurbulence type="fractalNoise" baseFrequency="0.01 0.05" numOctaves="3" seed="2" result="noise">
            <animate
              attributeName="baseFrequency"
              dur="20s"
              values="0.008 0.045; 0.02 0.09; 0.008 0.045"
              repeatCount="indefinite"
            />
          </feTurbulence>
          <!-- scale 即“波动幅度”：加大后帷幕扭动更明显（更像极光 / 水面起伏） -->
          <feDisplacementMap in="SourceGraphic" in2="noise" scale="240" xChannelSelector="R" yChannelSelector="G" />
        </filter>

        <!-- 青绿主色 -->
        <linearGradient id="aurora-teal" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="rgba(74, 216, 240, 0)" />
          <stop offset="30%" stop-color="rgba(74, 216, 240, 0.6)" />
          <stop offset="60%" stop-color="rgba(0, 255, 180, 0.8)" />
          <stop offset="100%" stop-color="rgba(74, 216, 240, 0)" />
        </linearGradient>

        <!-- 青蓝副色 -->
        <linearGradient id="aurora-cyan" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="rgba(100, 200, 255, 0)" />
          <stop offset="45%" stop-color="rgba(100, 200, 255, 0.55)" />
          <stop offset="100%" stop-color="rgba(74, 216, 240, 0)" />
        </linearGradient>

        <!-- 紫色点缀 -->
        <linearGradient id="aurora-purple" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="rgba(140, 100, 200, 0)" />
          <stop offset="50%" stop-color="rgba(140, 100, 200, 0.55)" />
          <stop offset="100%" stop-color="rgba(180, 130, 220, 0)" />
        </linearGradient>
      </defs>

      <!-- filter 只作用在一个 <g> 上：三层共享一次滤波，省 GPU -->
      <g filter="url(#aurora-warp)">
        <g class="layer layer--teal">
          <rect x="-10%" y="-20%" width="120%" height="140%" fill="url(#aurora-teal)" />
        </g>
        <g class="layer layer--cyan">
          <rect x="5%" y="-15%" width="90%" height="130%" fill="url(#aurora-cyan)" />
        </g>
        <g class="layer layer--purple">
          <rect x="-20%" y="-10%" width="140%" height="120%" fill="url(#aurora-purple)" />
        </g>
      </g>
    </svg>

    <!-- ② 细光线：清晰的垂直光条（真极光的"光线痕迹"） -->
    <div class="aurora-streaks">
      <div v-for="(s, i) in streaks" :key="i" class="streak" :style="streakStyle(s)" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.aurora-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  /* 不阻挡任何交互 */
  pointer-events: none;
  /* 底部一层深蓝，模拟极光自地平线升起；整体压到克制亮度 */
  background: radial-gradient(ellipse at 50% 100%, rgba(30, 60, 90, 0.28) 0%, transparent 70%);
  opacity: 0.75;
}

/* ---------------- 主帷幕 ---------------- */
.aurora-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  /* 光叠加 */
  mix-blend-mode: screen;
}

/*
 * 三层颜色交替：24s 一轮（青绿 → 青蓝 → 紫 → 青绿）
 * 关键帧峰值分别在 0% / 33% / 66%，即 0s / 8s / 16s 依次达到最亮；
 * 故三层的 animation-delay 必须都是 0（若错开延迟会把峰值重新对齐，颜色就不会变）
 */
.layer--teal {
  animation: aurora-color-1 24s ease-in-out infinite;
}

.layer--cyan {
  animation: aurora-color-2 24s ease-in-out infinite;
}

.layer--purple {
  animation: aurora-color-3 24s ease-in-out infinite;
}

@keyframes aurora-color-1 {
  0% {
    opacity: 0.85;
  }
  33% {
    opacity: 0.2;
  }
  66% {
    opacity: 0.3;
  }
  100% {
    opacity: 0.85;
  }
}

@keyframes aurora-color-2 {
  0% {
    opacity: 0.2;
  }
  33% {
    opacity: 0.85;
  }
  66% {
    opacity: 0.3;
  }
  100% {
    opacity: 0.2;
  }
}

@keyframes aurora-color-3 {
  0% {
    opacity: 0.3;
  }
  33% {
    opacity: 0.2;
  }
  66% {
    opacity: 0.85;
  }
  100% {
    opacity: 0.3;
  }
}

/* ---------------- 细光线 ---------------- */
.aurora-streaks {
  position: absolute;
  inset: 0;
}

.streak {
  position: absolute;
  width: 2px;
  /* 极细的垂直光条，上下两端淡出 */
  background: linear-gradient(
    180deg,
    transparent 0%,
    rgba(0, 255, 180, 0.6) 30%,
    rgba(100, 255, 220, 0.8) 60%,
    transparent 100%
  );
  filter: blur(1.5px);
  mix-blend-mode: screen;
  will-change: transform, opacity;
  animation: streak-flow 3s ease-in-out infinite alternate;
}

@keyframes streak-flow {
  0% {
    transform: translateY(-38%) scaleY(0.7);
    opacity: 0.3;
  }
  100% {
    transform: translateY(26%) scaleY(1.4);
    opacity: 0.95;
  }
}

/* 小屏隐藏（SVG 滤波在移动端开销较大） */
@media (max-width: 768px) {
  .aurora-bg {
    display: none;
  }
}

/* 尊重系统「减少动态效果」：静止 */
@media (prefers-reduced-motion: reduce) {
  .layer--teal,
  .layer--cyan,
  .layer--purple,
  .streak {
    animation: none !important;
  }
}
</style>
