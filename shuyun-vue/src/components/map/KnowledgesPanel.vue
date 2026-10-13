<script setup lang="ts">
/**
 * 蜀地知多少 · 四川之最（KnowledgesPanel）
 *
 * /map3d 右栏顶部面板：**左右两栏**各展示一条四川地理/人文冷知识（共 10 条）。
 * - 两栏各自独立轮播（间隔不同 → 永不同步，动态感更强）
 * - 切换动画沿用 Transition：淡出上滑 + 淡入下滑
 * - 两栏不会同时显示同一条（自动跳过另一栏当前条目）
 * - 每条提供「定位 / 视频 / 简介」三个按钮
 * - 悬停暂停全部、移开恢复；右上角刷新回到起始条目
 * - 底部每栏各自的 10 个进度点可点击跳转
 * - 「定位」通过 mapBus 通知 3D 场景飞行/高亮/落标记
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { mapBus } from './mapBus'

/** 单条冷知识 */
interface Knowledge {
  /** 分类标签 */
  tag: string
  /** 主标题 */
  title: string
  /** 副标题（数值 · 地区） */
  value: string
  /** 经度 */
  lng: number
  /** 纬度 */
  lat: number
  /** 所属市州（用于地图高亮） */
  cityName: string
  /** 简介链接（百科等） */
  introUrl: string
  /** 视频链接（B 站 / 央视等） */
  videoUrl: string
}

/** 10 条四川之最（前端写死） */
const knowledges: Knowledge[] = [
  {
    tag: '海拔最高峰',
    title: '贡嘎山',
    value: '7556m · 甘孜州',
    lng: 101.88,
    lat: 29.59,
    cityName: '甘孜',
    introUrl: 'https://baike.baidu.com/item/%E8%B4%A1%E5%98%8E%E5%B1%B1/507005',
    videoUrl: 'https://www.bilibili.com/video/BV19C4y1c7F8/',
  },
  {
    tag: '最深峡谷',
    title: '大渡河峡谷',
    value: '2600m · 雅安 / 凉山',
    lng: 102.65,
    lat: 29.3,
    cityName: '雅安',
    introUrl: 'https://baike.baidu.com/item/%E5%9B%9B%E5%B7%9D%E5%A4%A7%E6%B8%A1%E6%B2%B3%E5%B3%A1%E8%B0%B7%E5%9B%BD%E5%AE%B6%E5%9C%B0%E8%B4%A8%E5%85%AC%E5%9B%AD/8003112',
    videoUrl: 'https://www.bilibili.com/video/BV11x4y1w7EY/',
  },
  {
    tag: '最大湖泊',
    title: '泸沽湖',
    value: '50 km² · 凉山州',
    lng: 100.78,
    lat: 27.7,
    cityName: '凉山',
    introUrl: 'https://baike.baidu.com/item/%E6%B3%B8%E6%B2%BD%E6%B9%96/396555',
    videoUrl: 'https://tv.cctv.com/2020/05/21/VIDELVoDSzBb6DmNWUHFguxa200521.shtml',
  },
  {
    tag: '最古老水利',
    title: '都江堰',
    value: '2280 年 · 成都市',
    lng: 103.61,
    lat: 30.99,
    cityName: '成都',
    introUrl: 'https://baike.baidu.com/item/%E9%83%BD%E6%B1%9F%E5%A0%B0/122963',
    videoUrl: 'https://tv.cctv.com/2022/10/18/VIDE1Z6eAztFqbcVaM6tIPjn221018.shtml',
  },
  {
    tag: '最高县城',
    title: '理塘',
    value: '4014m · 甘孜州',
    lng: 100.27,
    lat: 29.99,
    cityName: '甘孜',
    introUrl: 'https://baike.baidu.com/item/%E7%90%86%E5%A1%98%E5%8E%BF/1685709',
    videoUrl: 'https://www.bilibili.com/video/BV1VR4y1b7RG/',
  },
  {
    tag: '最大竹林',
    title: '蜀南竹海',
    value: '7 万亩 · 宜宾市',
    lng: 105.02,
    lat: 28.48,
    cityName: '宜宾',
    introUrl: 'https://baike.baidu.com/item/%E8%9C%80%E5%8D%97%E7%AB%B9%E6%B5%B7%E6%97%85%E6%B8%B8%E5%BA%A6%E5%81%87%E5%8C%BA/60205021',
    videoUrl: 'https://www.bilibili.com/video/av418878500/',
  },
  {
    tag: '最长古柏道',
    title: '翠云廊',
    value: '300 里 · 广元市',
    lng: 105.55,
    lat: 32.21,
    cityName: '广元',
    introUrl: 'https://baike.baidu.com/item/%E7%BF%A0%E4%BA%91%E5%BB%8A%E5%8F%A4%E8%9C%80%E9%81%93/63252159',
    videoUrl: 'https://www.bilibili.com/video/BV1cVpienEM8/',
  },
  {
    tag: '国家级非遗',
    title: '蜀地非遗',
    value: '153 项 · 全省',
    lng: 104.06,
    lat: 30.67,
    cityName: '成都',
    introUrl: 'https://wlt.sc.gov.cn/scwlt/hydt/2024/10/19/b821a8764b1c46f796f3fa2e66b68c96.shtml',
    videoUrl: 'https://w.yangshipin.cn/video?type=0&vid=b000009civ5',
  },
  {
    tag: '最古老茶馆',
    title: '彭镇老茶馆',
    value: '百年 · 成都市',
    lng: 103.92,
    lat: 30.58,
    cityName: '成都',
    introUrl: 'https://baike.baidu.com/item/%E5%BD%AD%E9%95%87%E8%80%81%E8%8C%B6%E9%A6%86/23409140',
    videoUrl: 'https://www.bilibili.com/video/BV1uzXzYNE8N/',
  },
  {
    tag: '最大摩崖石刻',
    title: '乐山大佛',
    value: '71m · 乐山市',
    lng: 103.77,
    lat: 29.54,
    cityName: '乐山',
    introUrl: 'https://baike.baidu.com/item/%E4%B9%90%E5%B1%B1%E5%A4%A7%E4%BD%9B/142192',
    videoUrl: 'https://www.bilibili.com/video/BV16WNEz7EZ5/',
  },
]

/** 左右两栏的切换间隔（毫秒）：短间隔 + 互不相同 → 两栏永不同步 */
const LEFT_INTERVAL = 2600
const RIGHT_INTERVAL = 2000

/** 起始下标错开，避免开局两栏相同 */
const START_LEFT = 0
const START_RIGHT = 4

/** 左 / 右栏当前下标 */
const leftIndex = ref(START_LEFT)
const rightIndex = ref(START_RIGHT)
const leftItem = computed(() => knowledges[leftIndex.value])
const rightItem = computed(() => knowledges[rightIndex.value])

/** 两栏各自的定时器 */
let leftTimer: number | null = null
let rightTimer: number | null = null

/** 停止全部轮播 */
function stopTimers() {
  if (leftTimer !== null) {
    window.clearInterval(leftTimer)
    leftTimer = null
  }
  if (rightTimer !== null) {
    window.clearInterval(rightTimer)
    rightTimer = null
  }
}

/** 下一处下标：跳过与另一栏相同的条目，保证两栏不同时显示同一条 */
function step(current: number, other: number): number {
  let n = (current + 1) % knowledges.length
  if (n === other) {
    n = (n + 1) % knowledges.length
  }
  return n
}

/** 启动两栏独立轮播 */
function startTimers() {
  stopTimers()
  leftTimer = window.setInterval(() => {
    leftIndex.value = step(leftIndex.value, rightIndex.value)
  }, LEFT_INTERVAL)
  rightTimer = window.setInterval(() => {
    rightIndex.value = step(rightIndex.value, leftIndex.value)
  }, RIGHT_INTERVAL)
}

/** 进度点跳转 */
function goLeft(i: number) {
  leftIndex.value = i
}
function goRight(i: number) {
  rightIndex.value = i
}

/** 刷新：回到起始条目并重置两栏计时 */
function reset() {
  leftIndex.value = START_LEFT
  rightIndex.value = START_RIGHT
  startTimers()
}

/** 悬停暂停 / 移开恢复 */
function pauseAll() {
  stopTimers()
}
function resumeAll() {
  startTimers()
}

/** 定位：通知 3D 场景飞行 / 高亮 / 落标记（并携带视频/简介链接，供地图上的定位弹窗使用） */
function locate(item: Knowledge) {
  mapBus.emit('locate', {
    lng: item.lng,
    lat: item.lat,
    cityName: item.cityName,
    title: item.title,
    introUrl: item.introUrl,
    videoUrl: item.videoUrl,
  })
}

onMounted(startTimers)
onBeforeUnmount(stopTimers)
</script>

<template>
  <section class="kw" @mouseenter="pauseAll" @mouseleave="resumeAll">
    <div class="kw__inner">
      <i class="kw__corner kw__corner--tl" aria-hidden="true" />
      <i class="kw__corner kw__corner--tr" aria-hidden="true" />
      <i class="kw__corner kw__corner--bl" aria-hidden="true" />
      <i class="kw__corner kw__corner--br" aria-hidden="true" />

      <!-- 标题栏：脉冲圆点 + 标题 + 刷新 -->
      <header class="kw__head">
        <span class="kw__dot" aria-hidden="true" />
        <h3 class="kw__title">蜀地知多少</h3>
        <button type="button" class="kw__refresh" title="刷新" aria-label="刷新" @click="reset">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <path d="M4 12a8 8 0 1 1 2.34 5.66" />
            <path d="M4 20v-4h4" />
          </svg>
        </button>
      </header>

      <!-- 内容区：左右两栏 -->
      <div class="kw__body">
        <!-- 左栏 -->
        <div class="kw__col">
          <Transition name="swap" mode="out-in">
            <div :key="`L-${leftIndex}`" class="kw__content">
              <p class="kw__tag">{{ leftItem.tag }}</p>
              <p class="kw__name">{{ leftItem.title }}</p>
              <p class="kw__value">{{ leftItem.value }}</p>
              <button type="button" class="kw__locate" @click="locate(leftItem)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                  <path d="M12 21s-6-5.2-6-10a6 6 0 1 1 12 0c0 4.8-6 10-6 10Z" />
                  <circle cx="12" cy="11" r="2.2" />
                </svg>
                在地图上定位 →
              </button>
            </div>
          </Transition>
          <div class="kw__dots">
            <button
              v-for="(k, i) in knowledges"
              :key="`dl-${k.title}`"
              type="button"
              class="kw__dot-item"
              :class="{ 'is-active': i === leftIndex }"
              :aria-label="`左栏第 ${i + 1} 条`"
              @click="goLeft(i)"
            />
          </div>
        </div>

        <i class="kw__divider" aria-hidden="true" />

        <!-- 右栏 -->
        <div class="kw__col">
          <Transition name="swap" mode="out-in">
            <div :key="`R-${rightIndex}`" class="kw__content">
              <p class="kw__tag">{{ rightItem.tag }}</p>
              <p class="kw__name">{{ rightItem.title }}</p>
              <p class="kw__value">{{ rightItem.value }}</p>
              <button type="button" class="kw__locate" @click="locate(rightItem)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                  <path d="M12 21s-6-5.2-6-10a6 6 0 1 1 12 0c0 4.8-6 10-6 10Z" />
                  <circle cx="12" cy="11" r="2.2" />
                </svg>
                在地图上定位 →
              </button>
            </div>
          </Transition>
          <div class="kw__dots">
            <button
              v-for="(k, i) in knowledges"
              :key="`dr-${k.title}`"
              type="button"
              class="kw__dot-item"
              :class="{ 'is-active': i === rightIndex }"
              :aria-label="`右栏第 ${i + 1} 条`"
              @click="goRight(i)"
            />
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* 外层切角描边（与右侧其他面板保持一致） */
.kw {
  position: relative;
  flex: none;
  clip-path: polygon(
    12px 0,
    calc(100% - 12px) 0,
    100% 12px,
    100% calc(100% - 12px),
    calc(100% - 12px) 100%,
    12px 100%,
    0 calc(100% - 12px),
    0 12px
  );
  background: rgba(58, 140, 220, 0.4);
}

.kw__inner {
  position: relative;
  margin: 1px;
  clip-path: polygon(
    12px 0,
    calc(100% - 12px) 0,
    100% 12px,
    100% calc(100% - 12px),
    calc(100% - 12px) 100%,
    12px 100%,
    0 calc(100% - 12px),
    0 12px
  );
  background: #0f2540;
  box-shadow: inset 0 0 30px rgba(58, 140, 220, 0.1);
}

.kw__inner::after {
  content: '';
  position: absolute;
  inset: 4px;
  border: 1px solid rgba(58, 140, 220, 0.15);
  pointer-events: none;
}

/* 四角小三角 */
.kw__corner {
  position: absolute;
  width: 0;
  height: 0;
  border: 4px solid transparent;
  pointer-events: none;

  &--tl {
    top: 6px;
    left: 6px;
    border-top-color: #4ad8f0;
    border-left-color: #4ad8f0;
  }

  &--tr {
    top: 6px;
    right: 6px;
    border-top-color: #4ad8f0;
    border-right-color: #4ad8f0;
  }

  &--bl {
    bottom: 6px;
    left: 6px;
    border-bottom-color: #4ad8f0;
    border-left-color: #4ad8f0;
  }

  &--br {
    right: 6px;
    bottom: 6px;
    border-right-color: #4ad8f0;
    border-bottom-color: #4ad8f0;
  }
}

/* 标题栏 */
.kw__head {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 44px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(58, 140, 220, 0.3);
  background: rgba(58, 140, 220, 0.12);
}

/* 脉冲圆点 */
.kw__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4ad8f0;
  animation: kw-dot 1.6s ease-in-out infinite;
}

@keyframes kw-dot {
  0%,
  100% {
    opacity: 0.5;
    box-shadow: 0 0 0 rgba(74, 216, 240, 0);
  }

  50% {
    opacity: 1;
    box-shadow: 0 0 10px rgba(74, 216, 240, 0.9);
  }
}

.kw__title {
  flex: 1;
  overflow: hidden;
  font-family: $font-body;
  font-size: 25px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #ffffff;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 刷新按钮 */
.kw__refresh {
  display: inline-flex;
  flex: none;
  padding: 2px;
  border: none;
  background: transparent;
  color: #7a95b8;
  cursor: pointer;
  transition:
    color 0.3s ease,
    transform 0.4s ease;

  &:hover {
    color: #d4b374;
    transform: rotate(180deg);
  }
}

/* 内容区：两栏并排 */
.kw__body {
  display: flex;
  gap: 16px;
  min-height: 214px;
  padding: 20px 20px 14px;
}

.kw__col {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding-bottom: 20px;
}

/* 两栏之间的竖向分隔线 */
.kw__divider {
  flex: none;
  width: 1px;
  margin: 2px 0 22px;
  background: linear-gradient(180deg, transparent, rgba(58, 140, 220, 0.35), transparent);
}

.kw__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
}

.kw__tag {
  margin-bottom: 12px;
  font-size: 14px;
  letter-spacing: 0.1em;
  color: #d4b374;
}

.kw__name {
  margin-bottom: 8px;
  font-family: $font-title;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.15;
  color: #ffffff;
  text-shadow: 0 0 14px rgba(212, 179, 116, 0.35);
}

.kw__value {
  margin-bottom: 16px;
  font-family: $font-mono;
  font-size: 17px;
  color: #4ad8f0;
}

/* 定位按钮 */
.kw__locate {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  padding: 6px 14px;
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: 4px;
  background: transparent;
  font-family: $font-body;
  font-size: 14px;
  color: #d4b374;
  cursor: pointer;
  transition:
    background-color 0.3s ease,
    border-color 0.3s ease;

  &:hover {
    border-color: #d4b374;
    background: rgba(201, 168, 106, 0.15);
  }
}

/* 每栏各自的 10 个进度点 */
.kw__dots {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.kw__dot-item {
  width: 6px;
  height: 6px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: rgba(122, 149, 184, 0.4);
  cursor: pointer;
  transition: all 0.3s ease;

  &.is-active {
    width: 16px;
    background: #d4b374;
  }
}

/* 切换动画：淡出上滑 + 淡入下滑 */
.swap-enter-active,
.swap-leave-active {
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.swap-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.swap-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
