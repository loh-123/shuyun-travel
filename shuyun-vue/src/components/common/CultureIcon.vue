<script setup lang="ts">
/**
 * 文化图标（8 个手绘线性图标）
 * - 统一 viewBox 48×48、stroke-width 1.5、round 端点，颜色跟随 currentColor
 * - 通过 name 切换：川剧变脸 / 蜀绣 / 川菜 / 茶马古道 / 三国文化 / 三星堆 / 大熊猫 / 酒文化
 * - 传英文 key 也可（opera / embroidery / cuisine / teaHorse / threeKingdoms / sanxingdui / panda / liquor）
 */
import { computed } from 'vue'

/** 单个笔画节点 */
interface IconNode {
  t: 'path' | 'circle' | 'ellipse'
  d?: string
  cx?: number
  cy?: number
  r?: number
  rx?: number
  ry?: number
}

/** 中文名 → 图标 */
const ICONS: Record<string, IconNode[]> = {
  川剧变脸: [
    { t: 'path', d: 'M24 7c7 0 11 5 11 12v5c0 9-5 16-11 18-6-2-11-9-11-18v-5c0-7 4-12 11-12z' },
    { t: 'path', d: 'M17 21h5M26 21h5' },
    { t: 'path', d: 'M18 31c3 3 9 3 12 0' },
    { t: 'path', d: 'M24 7V2M17 8l-3-4M31 8l3-4' },
  ],
  蜀绣: [
    { t: 'path', d: 'M32 6L15 23' },
    { t: 'circle', cx: 33, cy: 5, r: 2 },
    { t: 'path', d: 'M15 23c-4 5-2 11 3 13s11 1 13-4' },
    { t: 'path', d: 'M31 32l6 6M34 35l-2 2' },
  ],
  川菜: [
    { t: 'path', d: 'M8 26h32c0 8-7 15-16 15S8 34 8 26z' },
    { t: 'path', d: 'M5 26h38' },
    { t: 'path', d: 'M28 4l-3 16M35 4l-3 16' },
  ],
  茶马古道: [
    { t: 'path', d: 'M14 23h20v8a7 7 0 01-7 7h-6a7 7 0 01-7-7v-8z' },
    { t: 'path', d: 'M14 20c0-3 2-4 5-4h10c3 0 5 1 5 4' },
    { t: 'path', d: 'M34 26c5 0 7 1.5 7 4.5S39 35 34 35' },
    { t: 'path', d: 'M14 26c-5 0-7 1.5-7 4.5S9 35 14 35' },
    { t: 'path', d: 'M22 16v-4h4v4' },
  ],
  三国文化: [
    { t: 'path', d: 'M24 4v20' },
    { t: 'path', d: 'M16 24h16' },
    { t: 'path', d: 'M24 24v12' },
    { t: 'path', d: 'M20 40h8' },
    { t: 'path', d: 'M21 8h6' },
  ],
  三星堆: [
    { t: 'path', d: 'M8 19c0-6 7-10 16-10s16 4 16 10v5c0 9-7 16-16 18C15 40 8 33 8 24v-5z' },
    { t: 'circle', cx: 16, cy: 23, r: 4 },
    { t: 'circle', cx: 32, cy: 23, r: 4 },
    { t: 'path', d: 'M18 33h12M24 9V4' },
  ],
  大熊猫: [
    { t: 'circle', cx: 24, cy: 27, r: 13 },
    { t: 'circle', cx: 13, cy: 14, r: 5 },
    { t: 'circle', cx: 35, cy: 14, r: 5 },
    { t: 'ellipse', cx: 18, cy: 24, rx: 3.4, ry: 4.6 },
    { t: 'ellipse', cx: 30, cy: 24, rx: 3.4, ry: 4.6 },
    { t: 'path', d: 'M21 34c2 1.6 4 1.6 6 0' },
  ],
  酒文化: [
    { t: 'path', d: 'M18 9h12v4c4 1.6 6 5 6 10v13a6 6 0 01-6 6H18a6 6 0 01-6-6V23c0-5 2-8.4 6-10V9z' },
    { t: 'path', d: 'M13 7h22' },
    { t: 'path', d: 'M18 25h12' },
    { t: 'path', d: 'M20 31h8' },
  ],
}

/** 英文 key → 中文名 */
const ALIAS: Record<string, string> = {
  opera: '川剧变脸',
  embroidery: '蜀绣',
  cuisine: '川菜',
  teaHorse: '茶马古道',
  threeKingdoms: '三国文化',
  sanxingdui: '三星堆',
  panda: '大熊猫',
  liquor: '酒文化',
}

/** 兜底图标：菱形印章 */
const FALLBACK: IconNode[] = [
  { t: 'path', d: 'M24 6l18 18-18 18L6 24z' },
  { t: 'path', d: 'M24 16l8 8-8 8-8-8z' },
]

interface Props {
  /** 文化名称（中文名或英文 key） */
  name: string
}

const props = defineProps<Props>()

const nodes = computed<IconNode[]>(() => {
  const key = ALIAS[props.name] ?? props.name
  return ICONS[key] ?? FALLBACK
})
</script>

<template>
  <svg
    class="culture-icon"
    viewBox="0 0 48 48"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <template v-for="(n, i) in nodes" :key="i">
      <path v-if="n.t === 'path'" :d="n.d" />
      <circle v-else-if="n.t === 'circle'" :cx="n.cx" :cy="n.cy" :r="n.r" />
      <ellipse v-else :cx="n.cx" :cy="n.cy" :rx="n.rx" :ry="n.ry" />
    </template>
  </svg>
</template>

<style scoped lang="scss">
.culture-icon {
  display: block;
  width: 100%;
  height: auto;
}
</style>
