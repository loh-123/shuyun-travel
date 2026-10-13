<script setup lang="ts">
/**
 * AI 对话词云（WordCloudPanel）
 *
 * 不依赖 echarts-wordcloud：将词条按权重缩放大字，随机布局在容器内，
 * 每次 words 变化即重新排列；金 / 青双色交替。
 * 词条可点击，抛出 pick 事件（大屏据此做跳转），并高亮当前选中词。
 */
import { ref, watch } from 'vue'

/** 词条 */
interface Word {
  name: string
  value: number
}

const props = defineProps<{
  /** 词条列表 */
  words: Word[]
}>()

const emit = defineEmits<{ (e: 'pick', word: string): void }>()

const boxEl = ref<HTMLDivElement | null>(null)

/** 当前选中词（点击高亮） */
const activeWord = ref('')

/** 点击词条 */
function pick(name: string) {
  activeWord.value = name
  emit('pick', name)
}

/** 布局结果 */
interface PlacedWord extends Word {
  x: number
  y: number
  size: number
  color: string
}

const placed = ref<PlacedWord[]>([])

/** 颜色池：金 / 青交替 */
const COLORS = ['#d4b374', '#4ad8f0', '#e0c07c', '#7fdcf0', '#d9b27a']

/** 依据权重计算字号 */
function sizeOf(value: number, min: number, max: number): number {
  const range = max - min || 1
  const ratio = (value - min) / range
  return Math.round(21 + ratio * 39)
}

/** 依据权重重新排布 */
function layout() {
  const words = props.words || []
  if (!words.length) {
    placed.value = []
    return
  }
  const values = words.map((w) => w.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const width = boxEl.value?.clientWidth || 320
  const height = boxEl.value?.clientHeight || 240

  placed.value = words.map((w, i) => {
    const size = sizeOf(w.value, min, max)
    const x = 6 + Math.random() * Math.max(4, width - size * 2.2 - 12)
    const y = 6 + Math.random() * Math.max(4, height - size - 12)
    return { ...w, x, y, size, color: COLORS[i % COLORS.length] }
  })
}

watch(
  () => props.words,
  () => layout(),
  { deep: true },
)
</script>

<template>
  <div ref="boxEl" class="wc">
    <span
      v-for="w in placed"
      :key="w.name"
      class="wc__word"
      :class="{ 'is-active': activeWord === w.name }"
      :style="{
        left: w.x + 'px',
        top: w.y + 'px',
        fontSize: w.size + 'px',
        color: w.color,
        textShadow: `0 0 12px ${w.color}66`,
      }"
      @click="pick(w.name)"
    >
      {{ w.name }}
    </span>
  </div>
</template>

<style scoped lang="scss">
@use '@/screen/styles/screen-tokens' as *;

.wc {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.wc__word {
  position: absolute;
  font-family: $screen-font-num;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition:
    left 0.8s $screen-ease,
    top 0.8s $screen-ease,
    font-size 0.6s $screen-ease,
    opacity 0.6s $screen-ease;
  animation: wc-appear 0.6s $screen-ease both;

  &:hover {
    opacity: 1;
    filter: brightness(1.25);
  }

  &.is-active {
    text-decoration: underline;
    text-underline-offset: 4px;
    filter: brightness(1.3);
  }
}

@keyframes wc-appear {
  from {
    opacity: 0;
    transform: scale(0.7);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
