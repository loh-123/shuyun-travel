import { onMounted, onUnmounted, type Ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

/** 滚动进入动画的配置 */
export interface ScrollRevealOptions {
  /** 起始纵向位移（px） */
  y?: number
  /** 起始透明度 */
  opacity?: number
  /** 单元素时长（秒） */
  duration?: number
  /** 交错间隔（秒） */
  stagger?: number
  /** 缓动 */
  ease?: string
  /** ScrollTrigger 的 start，默认 'top 65%' */
  start?: string
  /** 是否只播一次 */
  once?: boolean
  /** 起始延时（秒） */
  delay?: number
}

/**
 * 滚动进入动画（GSAP + ScrollTrigger）
 *
 * 元素从 `opacity 0 / translateY(y)` 进入到位，是首页各区块统一的入场方式。
 * 选择器字符串会自动限定在 root 元素内查找。
 *
 * @param root   区块根元素（作为 ScrollTrigger 的触发元素，同时用于限定选择器作用域）
 * @param targets 选择器字符串，或直接给元素数组
 *
 * @example
 * useScrollReveal(rootEl, '.data__animate', { y: 40, stagger: 0.12 })
 */
export function useScrollReveal(
  root: Ref<HTMLElement | null>,
  targets: string | Element[],
  options: ScrollRevealOptions = {},
) {
  let ctx: gsap.Context | null = null

  onMounted(() => {
    const rootEl = root.value
    if (!rootEl) {
      return
    }

    ctx = gsap.context(() => {
      const list = typeof targets === 'string' ? rootEl.querySelectorAll(targets) : targets
      if (!list || list.length === 0) {
        return
      }

      gsap.from(list, {
        y: options.y ?? 40,
        opacity: options.opacity ?? 0,
        duration: options.duration ?? 1,
        stagger: options.stagger ?? 0.1,
        delay: options.delay ?? 0,
        ease: options.ease ?? 'power3.out',
        scrollTrigger: {
          trigger: rootEl,
          start: options.start ?? 'top 65%',
          once: options.once ?? true,
        },
      })
    }, rootEl)
  })

  onUnmounted(() => {
    ctx?.revert()
  })

  /** 手动刷新 ScrollTrigger（内容变化后调用） */
  function refresh() {
    ScrollTrigger.refresh()
  }

  return { refresh }
}
