<script setup lang="ts">
/**
 * 指标面板（StatCard）
 *
 * 数据控制台风格的统计卡：技术标签（eyebrow）+ 主数值（滚动动画）+ 名称 +
 * 底部信息条（环比 chip / 备注 + 近 7 日走势迷你面积图）。
 *
 * - trend：环比昨日百分比（正涨负跌）
 * - spark：近 7 日序列（旧→新），由后端真实聚合，用于迷你走势图
 * - hint：无环比时的补充说明（如"样本 12 段会话""优质率 60%"）
 *
 * @example
 * <StatCard title="总行程数" eyebrow="TRIP · TOTAL" :value="12" icon="trip" :trend="8.2" :spark="spark" />
 * <StatCard title="优质对话数" eyebrow="CHAT · FLAGGED" :value="3" icon="check" :show-trend="false" hint="优质率 60%" />
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import gsap from 'gsap'
import AdminIcon from './AdminIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 名称 */
    title: string
    /** 数值 */
    value: number
    /** 图标名 */
    icon: string
    /** 技术标签（英文，默认取 title） */
    eyebrow?: string
    /** 环比昨日（%），正为涨、负为跌 */
    trend?: number
    /** 图标强调色 */
    accent?: string
    /** 数值后缀（如 天 / 轮 / 个） */
    suffix?: string
    /** 小数位数（0 表示整数并千分位） */
    decimals?: number
    /** 是否展示环比 */
    showTrend?: boolean
    /** 近 7 日走势序列（旧→新） */
    spark?: number[]
    /** 无环比时的补充说明 */
    hint?: string
  }>(),
  {
    eyebrow: '',
    trend: 0,
    accent: '#d4b374',
    suffix: '',
    decimals: 0,
    showTrend: true,
    spark: () => [],
    hint: '',
  },
)

/* ---------------- 数值滚动动画 ---------------- */
const display = ref(0)
let tween: gsap.core.Tween | null = null

function animateTo(target: number) {
  tween?.kill()
  const state = { v: display.value }
  tween = gsap.to(state, {
    v: target,
    duration: 1.4,
    ease: 'power2.out',
    onUpdate: () => {
      display.value = state.v
    },
  })
}

onMounted(() => animateTo(props.value))
watch(
  () => props.value,
  (v) => animateTo(v),
)
onUnmounted(() => tween?.kill())

/** 主数值文本 */
const displayNum = computed(() =>
  props.decimals > 0 ? display.value.toFixed(props.decimals) : Math.round(display.value).toLocaleString('en-US'),
)

/** 环比：正为涨 */
const isUp = computed(() => props.trend >= 0)
const trendText = computed(() => `${Math.abs(props.trend).toFixed(1)}%`)

/** 图标底色（强调色 + 透明度） */
const iconStyle = computed(() => ({ color: props.accent, background: `${props.accent}1f` }))

/* ---------------- 迷你走势图几何 ---------------- */
/** 渐变 id（每实例唯一，避免多卡冲突） */
let seq = 0
const gradId = `stat-spark-${(seq += 1)}-${Math.random().toString(36).slice(2, 7)}`

const W = 100
const H = 30

const sparkGeom = computed(() => {
  const data = props.spark
  if (!data || data.length < 2) {
    return null
  }
  const pad = 3.5
  const max = Math.max(...data, 1)
  const min = Math.min(...data, 0)
  const range = max - min || 1
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * W
    const y = H - pad - ((v - min) / range) * (H - pad * 2)
    return [Number(x.toFixed(1)), Number(y.toFixed(1))] as const
  })
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x} ${y}`).join(' ')
  const area = `${line} L${W} ${H} L0 ${H} Z`
  const last = pts[pts.length - 1]
  return { line, area, lastX: last[0], lastY: last[1] }
})
</script>

<template>
  <div class="stat">
    <!-- 顶部：技术标签 + 图标 -->
    <div class="stat__head">
      <span class="stat__eyebrow">{{ eyebrow || title }}</span>
      <span class="stat__icon" :style="iconStyle">
        <AdminIcon :name="icon" :size="22" :stroke="1.5" />
      </span>
    </div>

    <!-- 主数值 -->
    <p class="stat__value">
      {{ displayNum }}<em v-if="suffix" class="stat__unit">{{ suffix }}</em>
    </p>
    <p class="stat__label">{{ title }}</p>

    <!-- 底部：环比 / 备注 + 迷你走势 -->
    <div v-if="showTrend || sparkGeom || hint" class="stat__foot">
      <span v-if="showTrend" class="stat__delta" :class="isUp ? 'is-up' : 'is-down'">
        <AdminIcon :name="isUp ? 'arrow-up' : 'arrow-down'" :size="12" :stroke="2.4" />
        <b>{{ trendText }}</b>
        <em>较昨日</em>
      </span>
      <span v-else-if="hint" class="stat__hint">{{ hint }}</span>

      <svg
        v-if="sparkGeom"
        class="stat__spark"
        :style="{ color: accent }"
        :viewBox="`0 0 ${W} ${H}`"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <defs>
          <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="currentColor" stop-opacity="0.3" />
            <stop offset="1" stop-color="currentColor" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path :d="sparkGeom.area" :fill="`url(#${gradId})`" />
        <path
          :d="sparkGeom.line"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
          stroke-linecap="round"
          vector-effect="non-scaling-stroke"
        />
        <circle :cx="sparkGeom.lastX" :cy="sparkGeom.lastY" r="2" fill="currentColor" />
      </svg>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;
@use '@/styles/mixins' as *;

.stat {
  display: flex;
  flex-direction: column;
  padding: 18px 20px 16px;
  cursor: default;

  @include admin-card;
  @include admin-tech-frame;
  @include admin-lift;
}

/* ---------------- 顶部 ---------------- */
.stat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat__eyebrow {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: $admin-text-muted;
  @include text-ellipsis(1);
}

.stat__icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
}

/* ---------------- 主数值 ---------------- */
.stat__value {
  margin-top: 12px;
  font-family: $admin-font-num;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.05;
  color: $admin-text;
  letter-spacing: 0.01em;
}

.stat__unit {
  margin-left: 3px;
  font-size: 0.46em;
  font-style: normal;
  font-weight: 600;
  color: $admin-text-muted;
}

.stat__label {
  margin-top: 3px;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: $admin-text-muted;
}

/* ---------------- 底部信息条 ---------------- */
.stat__foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  min-height: 30px;
}

.stat__delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;

  em {
    margin-left: 1px;
    font-style: normal;
    font-weight: 400;
    color: $admin-text-muted;
  }

  &.is-up {
    color: $admin-green-up;
  }

  &.is-down {
    color: $admin-red-down;
  }
}

.stat__hint {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: $admin-text-muted;
}

.stat__spark {
  display: block;
  width: 84px;
  height: 30px;
  flex: 0 0 auto;
  opacity: 0.95;
}
</style>
