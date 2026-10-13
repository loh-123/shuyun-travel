<script setup lang="ts">
/**
 * 指标数字（MetricValue）
 *
 * 平滑高级的数值变化：数值变化时用补间动画「滚动计数」平滑过渡到新值（非跳变）。
 * - 首屏从 0 滚到目标值；之后每次变化从当前值滚到新值
 * - 变化瞬间变青蓝（#4AD8F0），随过渡自然恢复白色
 * - 非数字内容（纯字符串）回退为直接显示
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import gsap from 'gsap'

const props = withDefaults(
  defineProps<{
    value: string | number
    /** 是否高亮（青蓝） */
    flash?: boolean
    /** 字号 px */
    size?: number
    /** 是否居中对齐 */
    center?: boolean
  }>(),
  { flash: false, size: 28, center: false },
)

/** 当前显示文本 */
const display = ref('')
/** 补间句柄 */
let tween: gsap.core.Tween | null = null

/** 解析数值（忽略千分位等非数字字符）；非数字返回 null */
function parseNum(v: string | number): number | null {
  const n = typeof v === 'number' ? v : Number(String(v).replace(/[^\d.-]/g, ''))
  return Number.isFinite(n) ? n : null
}

/** 原值是否带千分位 */
function useSeparator(v: string | number): boolean {
  return typeof v === 'string' && v.includes(',')
}

/** 格式化（保留千分位风格） */
function format(n: number, sep: boolean): string {
  const r = Math.round(n)
  return sep ? r.toLocaleString('en-US') : String(r)
}

/** 平滑滚动到目标值 */
function rollTo(target: number, sep: boolean, from: number, duration: number) {
  tween?.kill()
  const proxy = { n: from }
  tween = gsap.to(proxy, {
    n: target,
    duration,
    ease: 'power2.out',
    onUpdate: () => {
      display.value = format(proxy.n, sep)
    },
    onComplete: () => {
      display.value = format(target, sep)
    },
  })
}

/** 应用当前 value（initial=true 表示首屏从 0 起滚） */
function apply(initial: boolean) {
  const target = parseNum(props.value)
  if (target === null) {
    tween?.kill()
    tween = null
    display.value = String(props.value)
    return
  }
  const sep = useSeparator(props.value)
  const from = initial ? 0 : (parseNum(display.value) ?? target)
  rollTo(target, sep, from, initial ? 1.4 : 1.0)
}

onMounted(() => apply(true))
watch(
  () => props.value,
  () => apply(false),
)
onBeforeUnmount(() => {
  tween?.kill()
  tween = null
})
</script>

<template>
  <b class="mv" :class="{ 'is-flash': flash, 'is-center': center }" :style="{ fontSize: `${size}px` }">
    <span>{{ display }}</span>
  </b>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.mv {
  position: relative;
  display: inline-block;
  min-width: 5ch;
  font-family: $font-mono;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: #ffffff;
  text-align: right;
  white-space: nowrap;
  transition: color 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

.mv.is-center {
  text-align: center;
}

.mv.is-flash {
  color: #4ad8f0;
}
</style>
