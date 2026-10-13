<script setup lang="ts">
/**
 * 品牌徽标（BrandLogo）—— 太阳神鸟 · 玉璧团章
 *
 * 由外到内三层结构：
 * 1. 玉璧外框：双圈金环 + 四方如意云纹环带（温润、有礼器感）
 * 2. 太阳神鸟：4 只神鸟逆时针绕飞 + 12 道旋转光芒
 * 3. 中心：金色圆盘（仿金沙金饰抛光金面）+ 红色「蜀」字
 *
 * 纯 SVG 手写：任意分辨率清晰、便于改色、打包体积小（不依赖图片资源）。
 *
 * Props
 * - size      图标边长（px），默认 72
 * - tooltip   是否在图标下方显示悬浮提示（默认 false）
 * - label     悬浮提示文案，默认「蜀韵·智游」
 * - clickable 是否显示手型光标（默认 false）
 *
 * 神鸟画法：由「身体椭圆 + 尖喙三角 + 上扬翅膀 + 分叉尾羽」4 个同色图元
 * 叠合而成 —— 同色填充自然融合为一个剪影，避免手绘单 path 的自交变形。
 *
 * 「蜀」字用字体渲染，优先篆书字体（浙江拙楷 / 汉仪篆书），
 * 系统未安装时依次回退到思源宋体、楷体、宋体。
 */
import { useId } from 'vue'

withDefaults(
  defineProps<{
    /** 图标边长（px） */
    size?: number
    /** 是否显示 hover 悬浮提示 */
    tooltip?: boolean
    /** 悬浮提示文案 */
    label?: string
    /** 是否可点击（显示手型光标） */
    clickable?: boolean
  }>(),
  { size: 72, tooltip: false, label: '蜀韵·智游', clickable: false },
)

/** defs 内的 id 需全页唯一，避免同页多处复用（导航/页脚/抽屉…）时互相覆盖 */
const uid = useId()
const goldId = `brand-gold-${uid}`
const plateGoldId = `brand-plate-gold-${uid}`
const textRedId = `brand-text-red-${uid}`
const haloId = `brand-halo-${uid}`
const outerGlowId = `brand-outer-glow-${uid}`

/** 太阳神鸟相对中心缩放比：收进玉璧内圈（内圈 r=28） */
const BIRD_SCALE = 0.84
</script>

<template>
  <span class="brand-logo" :class="{ 'is-clickable': clickable }">
    <svg
      class="brand-logo__mark"
      :width="size"
      :height="size"
      viewBox="0 0 72 72"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="蜀韵·智游"
    >
      <defs>
        <!-- 金色渐变（亮金 → 中间金 → 深金） -->
        <linearGradient :id="goldId" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#FFE6A0" />
          <stop offset="50%" stop-color="#D4B374" />
          <stop offset="100%" stop-color="#A67C2E" />
        </linearGradient>

        <!-- 中心圆盘：抛光金面（左上受光，仿金饰质感） -->
        <radialGradient :id="plateGoldId" cx="42%" cy="36%" r="70%">
          <stop offset="0%" stop-color="#FFEDB8" />
          <stop offset="55%" stop-color="#D4B374" />
          <stop offset="100%" stop-color="#A67C2E" />
        </radialGradient>

        <!-- 「蜀」字红色渐变（蜀锦红） -->
        <linearGradient :id="textRedId" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="#9A2530" />
          <stop offset="100%" stop-color="#6E141C" />
        </linearGradient>

        <!-- 底部柔和暗晕：保证图标在浅色底（亮 Hero）上也能看清 -->
        <radialGradient :id="haloId" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#0A1428" stop-opacity="0.45" />
          <stop offset="60%" stop-color="#0A1428" stop-opacity="0.3" />
          <stop offset="100%" stop-color="#0A1428" stop-opacity="0" />
        </radialGradient>

        <!-- 神鸟外发光（柔和，营造金光） -->
        <filter :id="outerGlowId" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="1.4" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- 底部暗晕 -->
      <circle cx="36" cy="36" r="36" :fill="`url(#${haloId})`" />

      <!-- ============ 一、玉璧外框 ============ -->
      <!-- 外圈：粗金环 -->
      <circle cx="36" cy="36" r="34.5" fill="none" :stroke="`url(#${goldId})`" stroke-width="1.6" />
      <!-- 内圈：细金环 -->
      <circle
        cx="36"
        cy="36"
        r="28"
        fill="none"
        :stroke="`url(#${goldId})`"
        stroke-width="0.8"
        opacity="0.55"
      />

      <!-- 四方如意云纹（置于玉璧双环之间的环带，沿径向朝外） -->
      <g class="brand-logo__clouds" :fill="`url(#${goldId})`" opacity="0.9">
        <g transform="translate(36 4.8)">
          <circle cx="-2.5" cy="0.5" r="1.3" />
          <circle cx="0" cy="-0.5" r="1.7" />
          <circle cx="2.5" cy="0.5" r="1.3" />
        </g>
        <g transform="translate(67.2 36) rotate(90)">
          <circle cx="-2.5" cy="0.5" r="1.3" />
          <circle cx="0" cy="-0.5" r="1.7" />
          <circle cx="2.5" cy="0.5" r="1.3" />
        </g>
        <g transform="translate(36 67.2) rotate(180)">
          <circle cx="-2.5" cy="0.5" r="1.3" />
          <circle cx="0" cy="-0.5" r="1.7" />
          <circle cx="2.5" cy="0.5" r="1.3" />
        </g>
        <g transform="translate(4.8 36) rotate(-90)">
          <circle cx="-2.5" cy="0.5" r="1.3" />
          <circle cx="0" cy="-0.5" r="1.7" />
          <circle cx="2.5" cy="0.5" r="1.3" />
        </g>
      </g>

      <!-- ============ 二、三：太阳神鸟 + 中心金盘红字 ============ -->
      <!-- 整体缩放收进玉璧内圈 -->
      <g :transform="`translate(36 36) scale(${BIRD_SCALE}) translate(-36 -36)`">
        <!-- 内圈：12 道太阳光芒 -->
        <g class="brand-logo__rays" transform="translate(36 36)" :fill="`url(#${goldId})`" opacity="0.95">
          <polygon
            v-for="i in 12"
            :key="`ray-${i}`"
            points="0,-14 -1.7,-21 0,-22.6 1.7,-21"
            :transform="`rotate(${(i - 1) * 30})`"
          />
        </g>

        <!-- 外圈：4 只太阳神鸟（逆时针绕飞） -->
        <g class="brand-logo__birds" :filter="`url(#${outerGlowId})`" :fill="`url(#${goldId})`">
          <g transform="translate(36 36)">
            <g v-for="i in 4" :key="`bird-${i}`" :transform="`rotate(${(i - 1) * 90})`">
              <!-- 身体 -->
              <ellipse cx="0" cy="-26.6" rx="5.4" ry="1.9" transform="rotate(-12 0 -26.6)" />
              <!-- 头 + 尖喙 -->
              <path d="M -5.4 -27.9 L -10 -26.3 L -5.2 -25.4 Z" />
              <!-- 上扬翅膀 -->
              <ellipse cx="0.6" cy="-29.7" rx="4.3" ry="1.5" transform="rotate(-30 0.6 -29.7)" />
              <!-- 分叉尾羽 -->
              <path d="M 4.4 -27.6 L 9.6 -29.6 L 6.6 -26.5 L 9.2 -24 L 4.2 -25.6 Z" />
            </g>
          </g>
        </g>

        <!-- 中心金色圆盘（仿金沙金饰：抛光金面 + 深色描边分隔光芒） -->
        <circle
          cx="36"
          cy="36"
          r="13.5"
          :fill="`url(#${plateGoldId})`"
          stroke="#3A2711"
          stroke-width="0.8"
        />

        <!-- 中心：红色「蜀」字 -->
        <text
          class="brand-logo__glyph"
          x="36"
          y="36"
          text-anchor="middle"
          dominant-baseline="central"
          font-size="20"
          font-weight="900"
          :fill="`url(#${textRedId})`"
        >
          蜀
        </text>
      </g>
    </svg>

    <!-- 悬浮提示：白色小卡片 + 深色文字，淡入淡出 -->
    <span v-if="tooltip" class="brand-logo__tip">{{ label }}</span>
  </span>
</template>

<style scoped lang="scss">
.brand-logo {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  &.is-clickable {
    cursor: pointer;
  }
}

/* 整体泛金光：SVG 外用 CSS drop-shadow 叠一层柔和金光 */
.brand-logo__mark {
  display: block;
  transform-origin: center;
  filter: drop-shadow(0 0 8px rgba(255, 230, 160, 0.5));
  transition:
    transform 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

/* hover：金光增强 + 整体缓慢旋转 */
.brand-logo:hover .brand-logo__mark {
  filter: drop-shadow(0 0 16px rgba(255, 230, 160, 0.8));
  animation: brand-logo-spin 20s linear infinite;
}

@keyframes brand-logo-spin {
  from {
    transform: scale(1.08) rotate(0deg);
  }
  to {
    transform: scale(1.08) rotate(360deg);
  }
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .brand-logo:hover .brand-logo__mark {
    animation: none;
    transform: scale(1.05);
  }
}

/* 中心「蜀」字：优先篆书，逐级回退（宋体 Heavy / 楷体 / 宋体） */
.brand-logo__glyph {
  font-family: '浙江拙楷', '汉仪篆书', 'Source Han Serif SC', 'STKaiti', 'KaiTi', 'SimSun', serif;
}

/* 悬浮提示 */
.brand-logo__tip {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  z-index: 10;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(var(--sy-surface-rgb), 0.96);
  box-shadow: 0 8px 24px rgba(14, 42, 71, 0.18);
  font-family: 'Source Han Serif SC', 'STZhongsong', 'SimSun', serif;
  font-size: 13px;
  letter-spacing: 0.12em;
  white-space: nowrap;
  color: #7a1a22;
  opacity: 0;
  transform: translate(-50%, -6px);
  pointer-events: none;
  transition:
    opacity 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.brand-logo:hover .brand-logo__tip {
  opacity: 1;
  transform: translate(-50%, 0);
}
</style>
