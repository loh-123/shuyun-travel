import { onMounted, onUnmounted, ref, type Ref } from 'vue'

/** useCursor 返回值 */
export interface UseCursorReturn {
  /** 鼠标原始坐标（视口坐标） */
  x: Ref<number>
  y: Ref<number>
  /** 缓动后的坐标（每帧更新） */
  smoothX: Ref<number>
  smoothY: Ref<number>
  /** 是否已启用（触屏设备为 false） */
  enabled: Ref<boolean>
  /** 把某个元素绑定到缓动坐标上（自动写 transform） */
  bind: (el: HTMLElement | null) => void
}

export interface UseCursorOptions {
  /** 缓动系数（0~1，越大越快跟上） */
  lerp?: number
  /** 是否把元素自身中心对准坐标点 */
  centered?: boolean
}

/**
 * 鼠标位置追踪
 *
 * - 用 rAF + 线性插值（lerp）做缓动跟随，避免生硬
 * - 触屏设备（无 hover 能力）自动不启用，不产生额外开销
 * - 常规用法：把 DOM 元素 bind 进去，由 composable 直接改 transform（不触发组件重渲染）
 *
 * @example
 * const { bind, enabled } = useCursor({ lerp: 0.12 })
 * onMounted(() => bind(glowEl.value))
 */
export function useCursor(options: UseCursorOptions = {}): UseCursorReturn {
  const lerpRate = options.lerp ?? 0.12
  const centered = options.centered ?? true

  const x = ref(0)
  const y = ref(0)
  const smoothX = ref(0)
  const smoothY = ref(0)
  const enabled = ref(false)

  let rafId = 0
  let boundEl: HTMLElement | null = null

  function onMove(e: MouseEvent) {
    x.value = e.clientX
    y.value = e.clientY
  }

  function loop() {
    smoothX.value += (x.value - smoothX.value) * lerpRate
    smoothY.value += (y.value - smoothY.value) * lerpRate

    if (boundEl) {
      const tail = centered ? ' translate(-50%, -50%)' : ''
      boundEl.style.transform = `translate3d(${smoothX.value}px, ${smoothY.value}px, 0)${tail}`
    }

    rafId = requestAnimationFrame(loop)
  }

  function bind(el: HTMLElement | null) {
    boundEl = el
  }

  onMounted(() => {
    // 触屏设备不启用
    if (window.matchMedia('(hover: none)').matches) {
      return
    }
    enabled.value = true
    x.value = smoothX.value = window.innerWidth / 2
    y.value = smoothY.value = window.innerHeight / 2
    window.addEventListener('mousemove', onMove, { passive: true })
    rafId = requestAnimationFrame(loop)
  })

  onUnmounted(() => {
    if (enabled.value) {
      window.removeEventListener('mousemove', onMove)
    }
    if (rafId) {
      cancelAnimationFrame(rafId)
    }
    boundEl = null
  })

  return { x, y, smoothX, smoothY, enabled, bind }
}
