import { onMounted, onUnmounted, type Ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

/** 参与滚动的字段：num 会被逐帧改写直到 target */
export interface CountUpItem {
  num: number
  target: number
}

export interface UseCountUpOptions {
  /** 时长（秒） */
  duration?: number
  /** 缓动 */
  ease?: string
  /** ScrollTrigger 触发元素，默认在滚动进入时播放 */
  trigger?: Ref<HTMLElement | null>
  /** ScrollTrigger start */
  start?: string
  /** 是否只播一次 */
  once?: boolean
}

/**
 * 数字滚动
 *
 * 把 items 里每一项的 `num` 从 0 滚到 `target`，进入视口时触发。
 * 由于 items 是响应式的，模板里 `{{ item.num }}` 会自动更新。
 *
 * @example
 * const stats = ref([{ num: 0, target: 21, label: '市州' }])
 * useCountUp(stats, { trigger: statsEl, duration: 2 })
 */
export function useCountUp(items: Ref<CountUpItem[]>, options: UseCountUpOptions = {}) {
  const { duration = 2, ease = 'power2.out', start = 'top 78%', once = true } = options

  const tweens: gsap.core.Tween[] = []

  onMounted(() => {
    items.value.forEach((item) => {
      const tween = gsap.fromTo(
        item,
        { num: 0 },
        {
          num: item.target,
          duration,
          ease,
          // 只滚整数
          snap: { num: 1 },
          scrollTrigger: {
            trigger: options.trigger?.value ?? undefined,
            start,
            once,
          },
        },
      )
      tweens.push(tween)
    })
  })

  onUnmounted(() => {
    tweens.forEach((tween) => {
      tween.scrollTrigger?.kill()
      tween.kill()
    })
    tweens.length = 0
  })

  /** 重新播放一次（回到 0 再滚一遍） */
  function restart() {
    tweens.forEach((tween) => {
      tween.restart()
    })
  }

  return { restart }
}
