<script setup lang="ts">
/**
 * 关键指标项（MetricItem）
 *
 * - 首屏：数字从 0 滚动到目标值（gsap）
 * - 刷新：value 变化时数字平滑滚到新值，并闪烁高亮一下（动态更新）
 * - 可点击：点击时向上抛出 click，由大屏进行联动 / 跳转
 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import gsap from 'gsap'

const props = defineProps<{
  /** 指标名称 */
  label: string
  /** 目标数值 */
  value: number
  /** 迷你趋势序列 */
  spark: number[]
  /** 强调色 */
  color: string
  /** 是否选中高亮 */
  active?: boolean
  /** 是否可点击（可跳转后台详情） */
  clickable?: boolean
}>()

const emit = defineEmits<{ (e: 'click'): void }>()

/** 根节点样式：以 CSS 变量注入指标强调色，供 hover 态复用 */
const rootStyle = computed<Record<string, string>>(() => ({
  '--mi-color': props.color,
  '--mi-tint': `${props.color}14`,
}))

/** 显示中的数值（被 gsap 逐帧改写） */
const display = reactive({ num: 0 })
/** 刷新闪烁标记 */
const bumping = ref(false)

let tween: gsap.core.Tween | null = null
let bumpTimer: number | undefined

/** 从当前值平滑滚到目标值 */
function rollTo(target: number, duration: number) {
  tween?.kill()
  tween = gsap.to(display, { num: target, duration, ease: 'power2.out', snap: { num: 1 } })
}

/** 刷新时闪一下 */
function bump() {
  bumping.value = true
  window.clearTimeout(bumpTimer)
  bumpTimer = window.setTimeout(() => {
    bumping.value = false
  }, 720)
}

onMounted(() => {
  rollTo(props.value, 1.4)
})

// 数据刷新：滚到新值 + 闪烁
watch(
  () => props.value,
  (v, old) => {
    if (v === old) return
    rollTo(v, 0.8)
    bump()
  },
)

onUnmounted(() => {
  tween?.kill()
  tween = null
  window.clearTimeout(bumpTimer)
})

/** 迷你折线点串 */
function sparkPoints(arr: number[], w = 96, h = 26): string {
  if (!arr || arr.length < 2) return ''
  const max = Math.max(...arr)
  const min = Math.min(...arr)
  const range = max - min || 1
  return arr
    .map((v, i) => {
      const x = (i / (arr.length - 1)) * w
      const y = h - ((v - min) / range) * h
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}
</script>

<template>
  <div
    class="mi"
    :class="{ 'is-active': active, 'is-clickable': clickable, 'is-bump': bumping }"
    :style="rootStyle"
    @click="clickable && emit('click')"
  >
    <!-- 四角金色小三角：让卡片更像「按钮」而非文本块 -->
    <span class="mi__corner mi__corner--tl" aria-hidden="true" />
    <span class="mi__corner mi__corner--tr" aria-hidden="true" />
    <span class="mi__corner mi__corner--bl" aria-hidden="true" />
    <span class="mi__corner mi__corner--br" aria-hidden="true" />

    <!-- 右上角箭头：暗示「可跳转」 -->
    <span v-if="clickable" class="mi__arrow" aria-hidden="true">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
        <path
          d="M5 11L11 5M11 5H6M11 5V10"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </span>

    <div class="mi__main">
      <p class="mi__value" :style="{ color, textShadow: `0 0 18px ${color}` }">
        {{ display.num.toLocaleString('en-US') }}
      </p>
      <p class="mi__label">{{ label }}</p>
    </div>

    <svg class="mi__spark" viewBox="0 0 96 26" preserveAspectRatio="none">
      <polyline :points="sparkPoints(spark)" fill="none" :stroke="color" stroke-width="2" />
    </svg>

    <!-- 右下角「查看详情」：暗示「可跳转」 -->
    <span v-if="clickable" class="mi__hint" aria-hidden="true">
      查看详情
      <svg width="15" height="15" viewBox="0 0 10 10" fill="none">
        <path
          d="M2 5H8M8 5L5 2M8 5L5 8"
          stroke="currentColor"
          stroke-width="1.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </span>
  </div>
</template>

<style scoped lang="scss">
@use '@/screen/styles/screen-tokens' as *;

.mi {
  /* 由组件通过内联 CSS 变量注入指标强调色 */
  --mi-color: #{$screen-gold};
  --mi-tint: rgba(212, 179, 116, 0.08);

  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 收紧纵向留白与行高，保证 4 张卡在面板内完整展示（不被下一个面板遮挡） */
  line-height: 1.2;
  padding: 4px 10px;
  /* 「可点击」外观：细边框 + 左侧指标色条 + 淡色底 */
  border: 1px solid rgba(58, 140, 220, 0.15);
  border-left: 2px solid var(--mi-color);
  border-radius: 6px;
  background-color: rgba(15, 37, 64, 0.6);
  background-image: linear-gradient(90deg, var(--mi-tint), transparent);
  transition:
    transform 0.3s $screen-ease,
    border-color 0.3s $screen-ease,
    background-color 0.3s $screen-ease,
    box-shadow 0.3s $screen-ease;

  /* 可点击：手型指针 + hover 上浮 / 金边 / 发光 */
  &.is-clickable {
    cursor: pointer;

    &:hover {
      transform: translateY(-4px);
      border-color: rgba(201, 168, 106, 0.6);
      background-color: rgba(15, 37, 64, 0.9);
      box-shadow: 0 8px 24px rgba(201, 168, 106, 0.15);

      .mi__value {
        filter: brightness(1.35);
      }

      .mi__arrow {
        color: $screen-gold;
        transform: translate(2px, -2px);
      }

      .mi__hint {
        opacity: 1;
        color: rgba(201, 168, 106, 0.9);
      }

      .mi__corner {
        opacity: 0.85;
      }
    }

    /* 点击反馈：轻微缩小 */
    &:active {
      transform: translateY(-4px) scale(0.98);
      transition: transform 0.15s;
    }
  }

  /* 选中高亮 */
  &.is-active {
    background-color: rgba(15, 37, 64, 0.9);
    background-image: linear-gradient(90deg, rgba(212, 179, 116, 0.18), transparent);
    box-shadow: inset 0 0 26px rgba(212, 179, 116, 0.22);
  }
}

/* 四角金色小三角：让卡片更像「按钮」而非文本块 */
.mi__corner {
  position: absolute;
  width: 0;
  height: 0;
  border: 4px solid transparent;
  opacity: 0.3;
  transition: opacity 0.3s $screen-ease;
  pointer-events: none;
}

.mi__corner--tl {
  top: 0;
  left: 0;
  border-top-color: $screen-gold;
  border-left-color: $screen-gold;
}

.mi__corner--tr {
  top: 0;
  right: 0;
  border-top-color: $screen-gold;
  border-right-color: $screen-gold;
}

.mi__corner--bl {
  bottom: 0;
  left: 0;
  border-bottom-color: $screen-gold;
  border-left-color: $screen-gold;
}

.mi__corner--br {
  bottom: 0;
  right: 0;
  border-bottom-color: $screen-gold;
  border-right-color: $screen-gold;
}

/* 右上角箭头：暗金色，hover 变亮金并向右上移动 */
.mi__arrow {
  position: absolute;
  top: 10px;
  right: 12px;
  display: inline-flex;
  line-height: 0;
  color: rgba(201, 168, 106, 0.4);
  transition: all 0.3s $screen-ease;
  pointer-events: none;
}

.mi__value {
  font-family: $screen-font-num;
  font-size: 45px;
  line-height: 1.05;
  transform-origin: left center;
  transition: filter 0.3s $screen-ease;
}

/* 刷新闪烁：数值放大回弹一下 */
.mi.is-bump .mi__value {
  animation: mi-bump 0.7s $screen-ease;
}

@keyframes mi-bump {
  0% {
    transform: scale(1);
  }
  35% {
    transform: scale(1.14);
  }
  100% {
    transform: scale(1);
  }
}

.mi__label {
  font-size: 20px;
  letter-spacing: 0.08em;
  color: $screen-text-muted;
}

.mi__spark {
  width: 90px;
  height: 20px;
  /* 右侧完全让出箭头 / 「查看详情」区域，避免与折线重叠 */
  margin-right: 100px;
  opacity: 0.85;
}

/* 右下角「查看详情」：与「点击柱状查看详情」同色（青色 #8fe6f7），hover 变亮金 */
.mi__hint {
  position: absolute;
  right: 14px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  /* 字号放大 0.5 倍：11px → 16.5px */
  font-size: calc(11px * 2.25);
  letter-spacing: 0.04em;
  color: #8fe6f7;
  opacity: 0.85;
  transition: all 0.3s $screen-ease;
  pointer-events: none;
}
</style>
