<script setup lang="ts">
/**
 * 非遗扩展数据链接总览（HeritageLinksPanel）
 *
 * 固定 10 条省级非遗名录 / 专题报道链接：
 * - 面板内始终展示 3 行，每 3 秒向上轮转一位（索引取模循环）
 * - 鼠标移入暂停、移出恢复；标题栏右侧刷新按钮（回到首条并重新计时）
 * - 点击整行新标签打开外部网页（非内部路由）
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import TechFrame from '@/components/map/TechFrame.vue'

interface HeritageLink {
  title: string
  subtitle: string
  url: string
}

/** 面板内固定显示行数 */
const VISIBLE = 3
/** 轮转间隔（毫秒） */
const INTERVAL = 3000

/** 非遗扩展数据链接（前端固定配置） */
const LINKS: HeritageLink[] = [
  {
    title: '四川省第五批非遗名录',
    subtitle: '52 项省级非遗项目完整清单',
    url: 'http://k.minzunet.cn/mzwhzyk/674771/682705/765186/sjfwzwhycml/765198/765698/index.html',
  },
  {
    title: '四川省第四批非遗名录',
    subtitle: '36 项省级非遗项目完整清单',
    url: 'http://k.minzunet.cn/mzwhzyk/674771/682705/765186/sjfwzwhycml/765198/765691/index.html',
  },
  {
    title: '四川省第三批非遗名录',
    subtitle: '89 项省级非遗项目完整清单',
    url: 'http://k.minzunet.cn/mzwhzyk/674771/682705/765186/sjfwzwhycml/765198/765688/index.html',
  },
  {
    title: '四川省第二批非遗名录',
    subtitle: '137 项省级非遗项目完整清单',
    url: 'http://k.minzunet.cn/mzwhzyk/674771/682705/765186/sjfwzwhycml/765198/765685/index.html',
  },
  {
    title: '四川省第一批非遗名录',
    subtitle: '189 项省级非遗项目完整清单',
    url: 'http://k.minzunet.cn/mzwhzyk/674771/682705/765186/sjfwzwhycml/765198/765670/index.html',
  },
  {
    title: '非遗里的中国 · 四川篇',
    subtitle: '中国非遗数字博物馆专题',
    url: 'https://www.ihchina.cn/art/detail/id/27574.html',
  },
  {
    title: '四川非遗保护传承成就',
    subtitle: '澎湃新闻 · 体系建设与创新实践',
    url: 'https://www.thepaper.cn/newsDetail_forward_28942041',
  },
  {
    title: '巾帼非遗手作亮相西部陆海新通道',
    subtitle: '新浪财经 · 2026 年最新报道',
    url: 'https://finance.sina.com.cn/jjxw/2026-09-20/doc-inisnsst2507902.shtml',
  },
  {
    title: '越剧《苏东坡》走进川大锦江学院',
    subtitle: '搜狐 · 非遗进校园文化交流',
    url: 'https://www.sohu.com/a/1077764989_122387568',
  },
  {
    title: '绵竹年画村：非遗绘就乡村振兴',
    subtitle: '新华网 · 年画村的传承与创新',
    url: 'http://www.sc.xinhuanet.com/20260911/6d91c8c3cad7403aab310917cf902fea/c.html',
  },
]

/** 起始索引（循环轮转） */
const startIndex = ref(0)
/** 悬停暂停标记 */
const paused = ref(false)
let timer: number | null = null

/** 当前可见的 3 条（取模循环） */
const visibleLinks = computed(() =>
  Array.from({ length: VISIBLE }, (_, i) => LINKS[(startIndex.value + i) % LINKS.length]),
)

/** 轮转一位 */
function tick() {
  startIndex.value = (startIndex.value + 1) % LINKS.length
}
function startTimer() {
  stopTimer()
  timer = window.setInterval(() => {
    if (!paused.value) tick()
  }, INTERVAL)
}
function stopTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}
/** 悬停暂停 / 移出恢复 */
function onEnter() {
  paused.value = true
}
function onLeave() {
  paused.value = false
}
/** 刷新：回到首条并重新计时 */
function refresh() {
  startIndex.value = 0
  startTimer()
}
/** 点击整行：新标签打开外部链接 */
function open(item: HeritageLink) {
  window.open(item.url, '_blank', 'noopener')
}

onMounted(startTimer)
onBeforeUnmount(stopTimer)
</script>

<template>
  <TechFrame title="非遗扩展数据链接总览">
    <template #extra>
      <button type="button" class="hl-refresh" title="刷新" @click="refresh">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 11a8 8 0 1 0-2.3 6.4" />
          <path d="M20 4v7h-7" />
        </svg>
      </button>
    </template>

    <div class="hl" @mouseenter="onEnter" @mouseleave="onLeave">
      <div class="hl__list">
        <TransitionGroup name="link">
          <div v-for="item in visibleLinks" :key="item.url" class="hl-item" @click="open(item)">
            <span class="hl-item__ico">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
                <path d="M14 3v5h5" />
                <path d="M9 13h6M9 17h4" />
              </svg>
            </span>
            <span class="hl-item__main">
              <b class="hl-item__title">{{ item.title }}</b>
              <em class="hl-item__sub">{{ item.subtitle }}</em>
            </span>
            <span class="hl-item__arrow">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </span>
          </div>
        </TransitionGroup>
      </div>
      <p class="hl__tip">点击任意条目可跳转查看详情</p>
    </div>
  </TechFrame>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.hl {
  display: flex;
  flex-direction: column;
}

/* 固定显示 3 行（3 × 56px），超出部分滚动时被裁切 */
.hl__list {
  position: relative;
  height: 168px;
  overflow: hidden;
}

.hl-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 10px 0 12px;
  /* 收紧行高，避免继承 1.8 把两行文字撑高导致相邻行重叠 */
  line-height: 1.3;
  cursor: pointer;
  transition: background-color 0.3s $ease-out-expo;

  /* 行间 1px 虚线分割 */
  & + .hl-item {
    border-top: 1px dashed rgba(58, 140, 220, 0.1);
  }

  /* 左侧金色竖条：hover 出现 */
  &::before {
    content: '';
    position: absolute;
    top: 8px;
    bottom: 8px;
    left: 0;
    width: 3px;
    background: #d4b374;
    opacity: 0;
    transition: opacity 0.3s $ease-out-expo;
  }

  &:hover {
    background: rgba(201, 168, 106, 0.08);

    &::before {
      opacity: 1;
    }

    .hl-item__title {
      color: #d4b374;
    }

    .hl-item__arrow {
      color: #d4b374;
      transform: translateX(4px);
    }
  }
}

.hl-item__ico {
  display: inline-flex;
  flex: none;
  color: #d4b374;
}

.hl-item__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.hl-item__title {
  overflow: hidden;
  /* 规格 14px 为下限，随视口自适应（与其他面板字号体系统一） */
  font-size: clamp(16px, 1.1vw, 21px);
  font-weight: 500;
  color: #ffffff;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s $ease-out-expo;
}

.hl-item__sub {
  overflow: hidden;
  font-size: clamp(14px, 0.95vw, 18px);
  font-style: normal;
  color: #7a95b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hl-item__arrow {
  display: inline-flex;
  flex: none;
  color: #6b7280;
  transition:
    color 0.3s $ease-out-expo,
    transform 0.3s $ease-out-expo;
}

/* 底部提示 */
.hl__tip {
  margin-top: 12px;
  font-size: clamp(13px, 0.9vw, 17px);
  text-align: center;
  color: rgba(122, 149, 184, 0.7);
}

/* 轮转动画：新条目自下滑入 → 旧条目向上滑出 → 其余条目上移 */
.link-enter-active,
.link-leave-active,
.link-move {
  transition: all 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.link-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.link-leave-active {
  position: absolute;
  width: 100%;
}

.link-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 刷新按钮：hover 旋转 180° */
.hl-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: 50%;
  background: rgba(212, 179, 116, 0.12);
  color: #d4b374;
  cursor: pointer;
  transition:
    transform 0.5s $ease-out-expo,
    background-color 0.3s $ease-out-expo,
    border-color 0.3s $ease-out-expo;

  &:hover {
    border-color: #d4b374;
    background: rgba(212, 179, 116, 0.22);
    transform: rotate(180deg);
  }
}
</style>
