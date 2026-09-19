import { onMounted, onUnmounted } from 'vue'
import Lenis from 'lenis'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

let instance: Lenis | null = null
let tickerFn: ((time: number) => void) | null = null

/**
 * 初始化全局 Lenis 平滑滚动（单例，重复调用安全）
 *
 * 与 GSAP 官方推荐接法一致：用 gsap.ticker 驱动 lenis.raf，
 * 并把 lenis 的 scroll 事件同步给 ScrollTrigger。
 *
 * 注意：
 * - 页面内若存在「内部滚动区域」（如聊天列表、下拉列表），已开启
 *   `allowNestedScroll`，滚轮会交由内层元素原生处理；
 *   也可给该元素加 `data-lenis-prevent` 手动排除。
 */
export function initLenis(): Lenis {
  if (instance) {
    return instance
  }

  instance = new Lenis({
    duration: 1.1,
    smoothWheel: true,
    // 允许内层可滚元素（聊天列表 / 下拉框 / 弹窗）使用原生滚动
    allowNestedScroll: true,
  })
  instance.on('scroll', ScrollTrigger.update)

  tickerFn = (time: number) => {
    instance?.raf(time * 1000)
  }
  gsap.ticker.add(tickerFn)
  gsap.ticker.lagSmoothing(0)

  return instance
}

/** 取当前单例（未初始化时为 null） */
export function getLenis(): Lenis | null {
  return instance
}

/** 销毁单例（一般只在应用卸载时调用） */
export function destroyLenis() {
  if (tickerFn) {
    gsap.ticker.remove(tickerFn)
    tickerFn = null
  }
  instance?.destroy()
  instance = null
}

/** 滚动到指定位置 / 元素 */
export function lenisScrollTo(
  target: number | string | HTMLElement,
  options?: Parameters<Lenis['scrollTo']>[1],
) {
  instance?.scrollTo(target, options)
}

/**
 * 组合式用法（组件内）
 *
 * @param autoDestroy 组件卸载时是否销毁全局实例（默认不销毁，交由 main.ts 统一管理）
 *
 * @example
 * // main.ts
 * initLenis()
 *
 * // 组件内取实例做整屏吸附
 * const lenis = useLenis()
 */
export function useLenis(autoDestroy = false) {
  onMounted(() => {
    initLenis()
  })

  onUnmounted(() => {
    if (autoDestroy) {
      destroyLenis()
    }
  })

  return { getLenis, destroyLenis, scrollTo: lenisScrollTo }
}
