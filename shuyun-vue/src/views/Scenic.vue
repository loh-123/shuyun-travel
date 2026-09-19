<script setup lang="ts">
/**
 * 景点导览（列表页）
 * - 顶部 hero（45vh）+ sticky 磨砂筛选栏 + 卡片网格 + 自定义分页 + 空状态
 * - 全部样式只引用公共设计系统（variables / mixins / 公共组件）
 * - 动效：GSAP + ScrollTrigger（入场、sticky 检测、筛选切换），
 *         IntersectionObserver 负责图片懒加载 + 卡片逐个淡入（80ms 间隔）
 *
 * 数据说明：
 *   - type 实际取值只有「自然风光 / 人文古迹 / 博物馆」，因此胶囊按真实值绑定
 *   - 后端 /api/scenic/list 暂无 sort 参数，「排序」在当前页数据上做客户端排序
 *   - 无「人气」字段，人气排序用「等级权重 + 评分」近似占位
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import request from '@/api/request'
import GoldButton from '@/components/common/GoldButton.vue'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型与常量
 * ---------------------------------------------------------- */
interface ScenicItem {
  id: number
  name: string
  cityId?: number
  cityName?: string
  type?: string
  level?: string
  coverImage?: string
  description?: string
  score?: number
  tags?: string
}

interface CityItem {
  id: number
  name: string
}

/** 换成本地大图：/images/hero-jiuzhaigou.webp */
const HERO_BG =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1920&q=80'
const HERO_BG_2X =
  'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=2560&q=80'

/** 每页条数 */
const PAGE_SIZE = 8

/** 类型胶囊：value 与 scenic_spot.type 真实取值一致，label 用短标签 */
const TYPES = [
  { label: '全部', value: '' },
  { label: '自然', value: '自然风光' },
  { label: '人文', value: '人文古迹' },
  { label: '博物馆', value: '博物馆' },
  { label: '红色', value: '红色旅游' },
  { label: '亲子', value: '亲子' },
  { label: '古镇', value: '古镇' },
]

/** 排序方式 */
const SORTS: { label: string; key: 'default' | 'score' | 'hot' }[] = [
  { label: '综合', key: 'default' },
  { label: '评分', key: 'score' },
  { label: '人气', key: 'hot' },
]

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const router = useRouter()

const heroEl = ref<HTMLElement | null>(null)
const filterEl = ref<HTMLElement | null>(null)
const gridEl = ref<HTMLElement | null>(null)
const cityDdEl = ref<HTMLElement | null>(null)

const cities = ref<CityItem[]>([])
const list = ref<ScenicItem[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: PAGE_SIZE,
  cityId: undefined as number | undefined,
  type: '',
  keyword: '',
})

const sortKey = ref<'default' | 'score' | 'hot'>('default')
const cityOpen = ref(false)
const drawer = ref(false)
const stuck = ref(false)

/* ------------------------------------------------------------
 * 计算属性
 * ---------------------------------------------------------- */
const cityLabel = computed(() => {
  if (query.cityId === undefined) {
    return '全部市州'
  }
  return cities.value.find((c) => c.id === query.cityId)?.name ?? '全部市州'
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

/** 列表数据：排序已由后端按 sort 参数完成（hot=浏览量、score=评分、default=综合） */
const displayList = computed(() => list.value)

/** 分页页码（超出 7 页时用省略号收敛） */
const pageList = computed<(number | 'gap')[]>(() => {
  const last = totalPages.value
  const cur = query.page
  if (last <= 7) {
    return Array.from({ length: last }, (_, i) => i + 1)
  }
  if (cur <= 4) {
    return [1, 2, 3, 4, 5, 'gap', last]
  }
  if (cur >= last - 3) {
    return [1, 'gap', last - 4, last - 3, last - 2, last - 1, last]
  }
  return [1, 'gap', cur - 1, cur, cur + 1, 'gap', last]
})

function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/* ------------------------------------------------------------
 * 数据加载
 * ---------------------------------------------------------- */
async function loadCities() {
  try {
    const res = await request.get('/city/list', { silent: true })
    if (Array.isArray(res.data)) {
      cities.value = res.data
    }
  } catch {
    // 忽略：城市下拉为空不影响其它筛选
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/scenic/list', {
      silent: true,
      params: {
        page: query.page,
        size: query.size,
        cityId: query.cityId || undefined,
        type: query.type || undefined,
        keyword: query.keyword || undefined,
        sort: sortKey.value,
      },
    })
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 重新拉取并播放「淡出 → 淡入」切换动画
 * @param animate 是否播放动画（首次进入不播，走入场时间线）
 */
async function reload(animate = true) {
  if (animate && gridEl.value) {
    await gsap.to(gridEl.value, { opacity: 0, scale: 0.98, duration: 0.22, ease: 'power2.out' })
  }

  await loadList()
  await nextTick()

  bindLazy()
  ScrollTrigger.refresh()

  if (animate && gridEl.value) {
    gsap.fromTo(
      gridEl.value,
      { opacity: 0, scale: 0.98 },
      { opacity: 1, scale: 1, duration: 0.45, ease: 'power2.out' },
    )
  }
}

/* ------------------------------------------------------------
 * 筛选交互
 * ---------------------------------------------------------- */
function selectCity(id: number | undefined) {
  query.cityId = id
  query.page = 1
  cityOpen.value = false
  reload()
}

function selectType(value: string) {
  if (query.type === value) {
    return
  }
  query.type = value
  query.page = 1
  reload()
}

function onSearch() {
  query.page = 1
  reload()
}

/** 切换排序：交由后端排序后重新拉取 */
function selectSort(key: 'default' | 'score' | 'hot') {
  if (sortKey.value === key) {
    return
  }
  sortKey.value = key
  query.page = 1
  reload()
}

function onReset() {
  query.cityId = undefined
  query.type = ''
  query.keyword = ''
  query.page = 1
  reload()
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === query.page) {
    return
  }
  query.page = page
  reload()
  // 回到列表顶部（避开 hero 与 sticky 栏）
  const top = (heroEl.value?.offsetHeight ?? 0) + 68
  window.scrollTo({ top, behavior: 'smooth' })
}

function goDetail(id: number) {
  router.push(`/scenic/${id}`)
}

/* ------------------------------------------------------------
 * 图片懒加载 + 卡片逐个淡入（IntersectionObserver，间隔 80ms）
 * ---------------------------------------------------------- */
let io: IntersectionObserver | null = null
/** 本批次已入场序号（每次重新渲染归零，保证间隔始终从 0 开始） */
let batchIndex = 0

function teardownLazy() {
  io?.disconnect()
  io = null
}

function bindLazy() {
  teardownLazy()
  const grid = gridEl.value
  if (!grid) {
    return
  }
  batchIndex = 0

  const cards = Array.from(grid.querySelectorAll<HTMLElement>('[data-lazy]'))
  if (!cards.length) {
    return
  }

  io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) {
          return
        }
        const card = entry.target as HTMLElement

        // 1) 懒加载封面
        const img = card.querySelector<HTMLImageElement>('img[data-src]')
        if (img?.dataset.src) {
          img.src = img.dataset.src
          img.removeAttribute('data-src')
        }

        // 2) 逐个淡入：每张延迟 80ms
        gsap.to(card, {
          opacity: 1,
          y: 0,
          duration: 0.7,
          delay: batchIndex * 0.08,
          ease: 'power3.out',
        })
        batchIndex += 1

        io?.unobserve(card)
      })
    },
    { threshold: 0.08, rootMargin: '0px 0px -60px 0px' },
  )

  cards.forEach((card) => {
    gsap.set(card, { opacity: 0, y: 32 })
    io?.observe(card)
  })
}

/* ------------------------------------------------------------
 * 下拉：点击外部关闭
 * ---------------------------------------------------------- */
function onDocPointerDown(e: MouseEvent) {
  if (!cityOpen.value) {
    return
  }
  const el = cityDdEl.value
  if (el && !el.contains(e.target as Node)) {
    cityOpen.value = false
  }
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
let scrollTriggerRef: ScrollTrigger | null = null
let entranceTl: gsap.core.Timeline | null = null

onMounted(async () => {
  document.addEventListener('pointerdown', onDocPointerDown)

  // 数据
  await Promise.all([loadCities(), loadList()])
  await nextTick()
  bindLazy()

  // sticky 检测：筛选栏吸顶后加深玻璃感
  if (filterEl.value) {
    scrollTriggerRef = ScrollTrigger.create({
      trigger: filterEl.value,
      start: 'top 68px',
      end: 'bottom 68px',
      onToggle: (self) => {
        stuck.value = self.isActive
      },
    })
  }

  // 入场动效：标题自下淡入，筛选栏自右滑入
  entranceTl = gsap.timeline({ defaults: { ease: 'power3.out' } })
  entranceTl
    .from('.scenic-hero__crumb', { opacity: 0, y: 20, duration: 0.6 })
    .from('.scenic-hero__title', { opacity: 0, y: 64, duration: 1 }, '-=0.35')
    .from('.scenic-hero__sub', { opacity: 0, y: 24, duration: 0.7 }, '-=0.6')
    .from(filterEl.value, { opacity: 0, x: 64, duration: 0.8 }, '-=0.55')

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointerDown)
  teardownLazy()
  scrollTriggerRef?.kill()
  scrollTriggerRef = null
  entranceTl?.kill()
  entranceTl = null
})
</script>

<template>
  <section class="scenic-page">
    <!-- ================= 1. 顶部 hero ================= -->
    <header ref="heroEl" class="scenic-hero">
      <img
        class="scenic-hero__bg"
        :src="HERO_BG"
        :srcset="`${HERO_BG} 1920w, ${HERO_BG_2X} 2560w`"
        sizes="100vw"
        alt="蜀地山水"
        fetchpriority="high"
        decoding="async"
      />

      <div class="sy-overlay">
        <div class="sy-overlay__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="sy-container scenic-hero__inner">
        <nav class="scenic-hero__crumb">
          <router-link to="/">首页</router-link>
          <span class="scenic-hero__sep">/</span>
          <span>景点</span>
        </nav>

        <h1 class="scenic-hero__title">蜀地<span>秘境</span></h1>
        <p class="scenic-hero__sub">21 市州 · 千处风光 · 一步一景</p>
      </div>
    </header>

    <!-- ================= 2. sticky 筛选栏 ================= -->
    <div ref="filterEl" class="scenic-filter" :class="{ 'is-stuck': stuck }">
      <div class="sy-container scenic-filter__inner">
        <!-- 城市：自定义下拉 -->
        <div ref="cityDdEl" class="dropdown" :class="{ 'is-open': cityOpen }">
          <button
            type="button"
            class="dropdown__trigger"
            :aria-expanded="cityOpen"
            @click="cityOpen = !cityOpen"
          >
            <span class="dropdown__value">{{ cityLabel }}</span>
            <svg
              class="dropdown__caret"
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>

          <transition name="dropdown">
            <ul v-show="cityOpen" class="dropdown__panel">
              <li
                class="dropdown__option"
                :class="{ 'is-active': query.cityId === undefined }"
                @click="selectCity(undefined)"
              >
                全部市州
              </li>
              <li
                v-for="c in cities"
                :key="c.id"
                class="dropdown__option"
                :class="{ 'is-active': query.cityId === c.id }"
                @click="selectCity(c.id)"
              >
                {{ c.name }}
              </li>
            </ul>
          </transition>
        </div>

        <!-- 类型：胶囊 -->
        <div class="pills">
          <button
            v-for="t in TYPES"
            :key="t.value"
            type="button"
            class="pill"
            :class="{ 'is-active': query.type === t.value }"
            @click="selectType(t.value)"
          >
            {{ t.label }}
          </button>
        </div>

        <!-- 搜索：只有一条下划线，聚焦时变金并展开 -->
        <label class="usearch">
          <input
            v-model="query.keyword"
            class="usearch__input"
            type="text"
            placeholder="搜索景点 / 标签"
            @keyup.enter="onSearch"
          />
          <span class="usearch__line" />
        </label>

        <!-- 排序 -->
        <div class="sorts">
          <button
            v-for="s in SORTS"
            :key="s.key"
            type="button"
            class="sorts__item"
            :class="{ 'is-active': sortKey === s.key }"
            @click="selectSort(s.key)"
          >
            {{ s.label }}
          </button>
        </div>

        <!-- 小屏：折叠成「筛选」按钮 -->
        <button type="button" class="scenic-filter__more" @click="drawer = true">
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          >
            <path d="M4 7h16M7 12h10M10 17h4" />
          </svg>
          筛选
        </button>
      </div>
    </div>

    <!-- ================= 3. 景点网格 ================= -->
    <div class="sy-container scenic-body">
      <div class="scenic-body__head">
        <p class="sy-eyebrow">Scenic Spots</p>
        <h2 class="sy-page-title">蜀地秘境</h2>
        <p class="sy-page-desc">
          从都江堰的水利智慧到九寨沟的翠海叠瀑 —— 共 {{ total }} 处景点，按你的节奏慢慢挑。
        </p>
      </div>

      <div ref="gridEl" v-loading="loading" class="scenic-grid">
        <article
          v-for="(item, i) in displayList"
          :key="item.id"
          class="scenic-card"
          data-lazy
          :data-index="i"
          @click="goDetail(item.id)"
        >
          <!-- 封面 4:3 -->
          <div class="scenic-card__cover">
            <img
              class="scenic-card__img"
              :data-src="item.coverImage"
              alt=""
              loading="lazy"
              decoding="async"
            />
            <span v-if="item.level" class="scenic-card__level">{{ item.level }}</span>

            <!-- hover：查看详情 -->
            <div class="scenic-card__hover">
              <GoldButton size="sm" @click.stop="goDetail(item.id)">
                查看详情
                <template #icon-right>
                  <svg
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M5 12h14M13 6l6 6-6 6" />
                  </svg>
                </template>
              </GoldButton>
            </div>
          </div>

          <!-- 信息 -->
          <div class="scenic-card__body">
            <h3 class="scenic-card__name">{{ item.name }}</h3>

            <div class="scenic-card__meta">
              <span class="scenic-card__city">{{ item.cityName }} · {{ item.type }}</span>
              <span class="scenic-card__score">
                <svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor">
                  <path
                    d="M12 2l2.9 6.3 6.9.8-5 4.7 1.3 6.8L12 17.4 5.9 20.6 7.2 13.8l-5-4.7 6.9-.8z"
                  />
                </svg>
                {{ item.score }}
              </span>
            </div>

            <div v-if="splitTags(item.tags).length" class="scenic-card__tags">
              <span v-for="t in splitTags(item.tags)" :key="t" class="scenic-card__tag">{{ t }}</span>
            </div>

            <p class="scenic-card__desc">{{ item.description }}</p>
          </div>
        </article>
      </div>

      <!-- ================= 5. 空状态 ================= -->
      <div v-if="!loading && !displayList.length" class="scenic-empty">
        <div class="scenic-empty__mark"><span>蜀</span></div>
        <p class="scenic-empty__text">未找到符合条件的景点</p>
        <GoldButton @click="onReset">重置筛选</GoldButton>
      </div>

      <!-- ================= 4. 自定义分页 ================= -->
      <nav v-if="totalPages > 1" class="scenic-pager" aria-label="分页">
        <button
          type="button"
          class="scenic-pager__arrow"
          :disabled="query.page <= 1"
          aria-label="上一页"
          @click="goPage(query.page - 1)"
        >
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M15 6l-6 6 6 6" />
          </svg>
        </button>

        <template v-for="(p, i) in pageList" :key="`${p}-${i}`">
          <span v-if="p === 'gap'" class="scenic-pager__gap">…</span>
          <button
            v-else
            type="button"
            class="scenic-pager__num"
            :class="{ 'is-active': p === query.page }"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
        </template>

        <button
          type="button"
          class="scenic-pager__arrow"
          :disabled="query.page >= totalPages"
          aria-label="下一页"
          @click="goPage(query.page + 1)"
        >
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M9 6l6 6-6 6" />
          </svg>
        </button>
      </nav>
    </div>

    <!-- ================= 小屏筛选抽屉 ================= -->
    <transition name="drawer">
      <div v-if="drawer" class="scenic-drawer" @click.self="drawer = false">
        <div class="scenic-drawer__panel">
          <header class="scenic-drawer__head">
            <h3>筛选</h3>
            <button type="button" class="scenic-drawer__close" @click="drawer = false">关闭</button>
          </header>

          <div class="scenic-drawer__group">
            <p class="scenic-drawer__label">市州</p>
            <div class="pills pills--wrap">
              <button
                type="button"
                class="pill"
                :class="{ 'is-active': query.cityId === undefined }"
                @click="selectCity(undefined)"
              >
                全部
              </button>
              <button
                v-for="c in cities"
                :key="c.id"
                type="button"
                class="pill"
                :class="{ 'is-active': query.cityId === c.id }"
                @click="selectCity(c.id)"
              >
                {{ c.name }}
              </button>
            </div>
          </div>

          <div class="scenic-drawer__group">
            <p class="scenic-drawer__label">类型</p>
            <div class="pills pills--wrap">
              <button
                v-for="t in TYPES"
                :key="t.value"
                type="button"
                class="pill"
                :class="{ 'is-active': query.type === t.value }"
                @click="selectType(t.value)"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <div class="scenic-drawer__group">
            <p class="scenic-drawer__label">排序</p>
            <div class="pills pills--wrap">
              <button
                v-for="s in SORTS"
                :key="s.key"
                type="button"
                class="pill"
                :class="{ 'is-active': sortKey === s.key }"
                @click="selectSort(s.key)"
              >
                {{ s.label }}
              </button>
            </div>
          </div>

          <GoldButton block class="scenic-drawer__done" @click="drawer = false">完成</GoldButton>
          <button type="button" class="scenic-drawer__reset" @click="onReset">重置筛选</button>
        </div>
      </div>
    </transition>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.scenic-page {
  background: $color-paper;
}

/* ============================================================
 * 1. 顶部 hero
 * ========================================================== */
.scenic-hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 45vh;
  overflow: hidden;
  isolation: isolate;
}

.scenic-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: hero-kenburns 24s ease-in-out infinite alternate;
}

@keyframes hero-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.scenic-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(56px, 9vh, 120px);
}

/* 面包屑 */
.scenic-hero__crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.78);

  a {
    transition: color $duration-fast $ease-out-expo;

    &:hover {
      color: $color-gold;
    }
  }
}

.scenic-hero__sep {
  color: $color-gold;
}

/* 大标题：艺术化排版（后半句金色描边） */
.scenic-hero__title {
  margin-top: clamp(16px, 2.4vh, 30px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero;
  line-height: 1;
  letter-spacing: -0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;

  span {
    display: inline-block;
    margin-left: 0.06em;
    color: transparent;
    -webkit-text-stroke: 1.5px $color-gold;
    text-shadow: none;
  }
}

.scenic-hero__sub {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(15px, 1.4vw, 22px);
  letter-spacing: 0.3em;
  color: rgba(245, 241, 232, 0.88);
  text-shadow: $shadow-text-soft;
}

/* ============================================================
 * 2. sticky 筛选栏
 * ========================================================== */
.scenic-filter {
  position: sticky;
  top: 68px; // 让开固定导航栏
  z-index: $z-header - 1;
  border-bottom: 1px solid transparent;
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  @include glass(rgba(232, 227, 216, 0.6), 18px);

  &.is-stuck {
    border-bottom-color: rgba(201, 168, 106, 0.42);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
    background: rgba(232, 227, 216, 0.85);
  }
}

.scenic-filter__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: clamp(12px, 1.6vw, 26px);
  padding-block: clamp(12px, 1.4vw, 20px);
}

/* 自定义下拉 */
.dropdown {
  position: relative;
  min-width: 168px;
}

.dropdown__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 11px 16px;
  border-radius: $radius-pill;
  @include gold-border(0.45);
  background: rgba(255, 255, 255, 0.5);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: $color-text-dark;
  transition:
    border-color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &:hover {
    border-color: $color-gold;
  }
}

.dropdown.is-open .dropdown__caret {
  transform: rotate(180deg);
}

.dropdown__caret {
  color: $color-gold;
  transition: transform $duration-fast $ease-out-expo;
}

.dropdown__panel {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 20;
  width: max(100%, 200px);
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
  border-radius: $radius-lg;
  @include gold-border(0.42);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
  box-shadow: $shadow-lg;
}

.dropdown__option {
  padding: 9px 14px;
  border-radius: $radius-md;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.06em;
  color: rgba(26, 26, 26, 0.78);
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(140, 31, 40, 0.07);
    color: $color-primary;
  }

  &.is-active {
    background: linear-gradient(120deg, $color-primary, #6e141c);
    color: $color-text-light;
  }
}

/* 下拉展开动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 类型胶囊 */
.pills {
  display: flex;
  align-items: center;
  gap: 8px;

  &--wrap {
    flex-wrap: wrap;
  }
}

.pill {
  padding: 9px 20px;
  border-radius: $radius-pill;
  border: 1px solid rgba(140, 31, 40, 0.22);
  background: transparent;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(26, 26, 26, 0.78);
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-primary;
    transform: translateY(-2px);
  }

  &.is-active {
    border-color: $color-gold;
    background: $color-gold;
    color: $color-ink;
  }
}

/* 搜索：单下划线 */
.usearch {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 200px;
  flex: 1 1 200px;
  max-width: 320px;
  padding: 8px 2px;
}

.usearch__input {
  width: 100%;
  padding: 6px 0;
  border: none;
  background: transparent;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  color: $color-text-dark;

  &::placeholder {
    color: rgba(26, 26, 26, 0.4);
  }
}

.usearch__line {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 1px;
  background: rgba(140, 31, 40, 0.28);
  transform-origin: left;
  transition:
    background-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.usearch:focus-within .usearch__line {
  background: $color-gold;
  transform: scaleX(1.04);
  box-shadow: 0 1px 8px rgba(201, 168, 106, 0.6);
}

/* 排序 */
.sorts {
  display: flex;
  align-items: center;
  gap: clamp(10px, 1.2vw, 20px);
}

.sorts__item {
  position: relative;
  padding-bottom: 3px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.12em;
  color: rgba(26, 26, 26, 0.6);
  transition: color $duration-fast $ease-out-expo;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 1px;
    background: $color-gold;
    transform: scaleX(0);
    transform-origin: left;
    transition: transform $duration-normal $ease-out-expo;
  }

  &:hover {
    color: $color-primary;
  }

  &.is-active {
    color: $color-primary;

    &::after {
      transform: scaleX(1);
    }
  }
}

/* 小屏「筛选」按钮 */
.scenic-filter__more {
  display: none;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: $radius-pill;
  border: 1px solid $color-gold;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: $color-primary;
}

/* ============================================================
 * 3. 网格
 * ========================================================== */
.scenic-body {
  padding-block: clamp(32px, 5vw, 72px) clamp(64px, 8vw, 120px);
}

.scenic-body__head {
  margin-bottom: clamp(24px, 3.4vw, 48px);
}

.scenic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: clamp(18px, 2vw, 30px);
  min-height: 200px;
}

.scenic-card {
  @include hover-lift;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover {
    .scenic-card__img {
      transform: scale(1.08);
    }

    .scenic-card__hover {
      opacity: 1;
      transform: translateY(0);
    }

    .scenic-card__name {
      color: $color-primary;
    }
  }
}

/* 封面 4:3 */
.scenic-card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(14, 42, 71, 0.12), rgba(140, 31, 40, 0.12));
}

.scenic-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.scenic-card__level {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 13px;
  border-radius: $radius-pill;
  background: rgba(14, 42, 71, 0.72);
  backdrop-filter: blur(6px);
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-gold;
}

/* hover 浮出的「查看详情」 */
.scenic-card__hover {
  position: absolute;
  inset: 0;
  @include flex-center;
  background: linear-gradient(180deg, rgba(14, 42, 71, 0.35), rgba(14, 42, 71, 0.6));
  opacity: 0;
  transform: translateY(8px);
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

/* 卡片文字 */
.scenic-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 9px;
  padding: clamp(16px, 1.8vw, 24px);
}

.scenic-card__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 1.5vw, 22px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
  transition: color $duration-normal $ease-out-expo;
}

.scenic-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.55);
}

.scenic-card__score {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: $font-en;
  font-size: 1.15em;
  color: $color-gold;
}

.scenic-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.scenic-card__tag {
  padding: 3px 10px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-sm;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-bamboo;
}

.scenic-card__desc {
  margin-top: auto;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.85;
  color: rgba(26, 26, 26, 0.66);
  @include text-ellipsis(2);
}

/* ============================================================
 * 4. 自定义分页
 * ========================================================== */
.scenic-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 1.4vw, 22px);
  margin-top: clamp(36px, 5vw, 72px);
}

.scenic-pager__num {
  position: relative;
  padding: 4px 2px;
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(26, 26, 26, 0.55);
  transition: color $duration-fast $ease-out-expo;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 1px;
    background: $color-gold;
    transform: scaleX(0);
    transform-origin: center;
    transition: transform $duration-normal $ease-out-expo;
  }

  &:hover {
    color: $color-primary;
  }

  &.is-active {
    color: $color-gold;

    &::after {
      transform: scaleX(1);
    }
  }
}

.scenic-pager__gap {
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(26, 26, 26, 0.35);
}

.scenic-pager__arrow {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  @include gold-border(0.5);
  color: $color-primary;
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover:not(:disabled) {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-2px);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}

/* ============================================================
 * 5. 空状态
 * ========================================================== */
.scenic-empty {
  @include flex-center(column);
  gap: 18px;
  padding-block: clamp(48px, 8vw, 110px);
  text-align: center;
}

/* 复用 Loading 的蜀锦纹样方块 */
.scenic-empty__mark {
  @include flex-center;
  width: clamp(72px, 8vw, 104px);
  height: clamp(72px, 8vw, 104px);
  border: 1px solid rgba(201, 168, 106, 0.6);
  background-color: rgba(201, 168, 106, 0.08);
  background-image: $pattern-brocade;
  animation: empty-spin 8s linear infinite;

  span {
    padding: 5px 9px;
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(24px, 2.6vw, 34px);
    color: $color-gold;
    background: $color-paper;
  }
}

@keyframes empty-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.scenic-empty__text {
  font-family: $font-title;
  font-size: clamp(16px, 1.4vw, 22px);
  letter-spacing: 0.14em;
  color: rgba(26, 26, 26, 0.6);
}

/* ============================================================
 * 小屏筛选抽屉
 * ========================================================== */
.scenic-drawer {
  position: fixed;
  inset: 0;
  z-index: $z-loader - 1;
  display: flex;
  justify-content: flex-end;
  background: rgba(14, 42, 71, 0.4);
  backdrop-filter: blur(4px);
}

.scenic-drawer__panel {
  display: flex;
  flex-direction: column;
  gap: clamp(20px, 4vw, 32px);
  width: min(86vw, 380px);
  height: 100%;
  padding: clamp(20px, 5vw, 32px);
  overflow-y: auto;
  background: $color-paper;
  box-shadow: -20px 0 50px rgba(0, 0, 0, 0.25);
}

.scenic-drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(18px, 4vw, 22px);
    letter-spacing: 0.12em;
    color: $color-primary;
  }
}

.scenic-drawer__close {
  font-size: $fs-small;
  letter-spacing: 0.12em;
  color: rgba(26, 26, 26, 0.6);
}

.scenic-drawer__label {
  margin-bottom: 10px;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-gold;
}

.scenic-drawer__done {
  margin-top: 4px;
}

.scenic-drawer__reset {
  font-size: $fs-small;
  letter-spacing: 0.12em;
  color: $color-primary;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity $duration-normal $ease-out-expo;

  .scenic-drawer__panel {
    transition: transform $duration-normal $ease-out-expo;
  }
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;

  .scenic-drawer__panel {
    transform: translateX(100%);
  }
}

/* ============================================================
 * 响应式：大屏 4 / 中屏 3 / 小屏 1
 * ========================================================== */
@include responsive(xl) {
  .scenic-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@include responsive(lg) {
  .scenic-filter__inner {
    gap: 12px;
  }

  .usearch {
    order: 9;
    min-width: 100%;
    max-width: none;
  }
}

@include responsive(md) {
  .scenic-grid {
    grid-template-columns: 1fr;
  }

  .scenic-hero__title span {
    -webkit-text-stroke-width: 1px;
  }

  // 小屏：筛选栏折叠
  .dropdown,
  .pills,
  .sorts {
    display: none;
  }

  .scenic-filter__more {
    display: inline-flex;
  }

  .usearch {
    order: 0;
    flex: 1 1 auto;
  }
}
</style>
