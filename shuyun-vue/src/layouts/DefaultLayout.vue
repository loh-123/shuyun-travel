<script setup lang="ts">
/**
 * 全局布局：导航栏 + 页脚 + 回到顶部
 *
 * 导航栏
 * - 有整屏 Hero 的页面（首页/景点/文化/个人中心）：顶部透明 + 白字，滚动 100px 后转磨砂玻璃并 80→64px 收缩
 * - 其余页面（AI管家/登录）：直接使用磨砂玻璃，避免白字压在浅色底上看不清
 * - 底部金色渐变线、hover 下划线从左到右展开、当前项金色 + 金点
 * - 用户下拉 / 语言切换 / 搜索浮层 / 小屏全屏抽屉（蜀锦纹理）
 *
 * 页脚：深色四栏 + 金色装饰线 + 滚动到视口时四栏从左到右依次淡入
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { getLenis } from '@/composables/useLenis'
import { locale, setLocale, t, type Locale } from '@/i18n'
import { useTheme } from '@/composables/useTheme'
import BrandLogo from '@/components/common/BrandLogo.vue'
import AiFab from '@/components/common/AiFab.vue'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 常量
 * ---------------------------------------------------------- */
/**
 * 主导航尾部项（首页与「蜀地风物」Mega Menu 入口在模板中单独渲染）
 * label 存 i18n 的 key，模板里用 t() 取词
 */
const MENUS = [
  { label: 'nav.map3d', path: '/map3d' },
  { label: 'nav.assistant', path: '/assistant' },
]

/** 「蜀地风物」全屏 Mega Menu 的四张卡片 */
const MEGA_CARDS = [
  {
    labelKey: 'nav.scenic',
    sloganKey: 'mega.sloganScenic',
    path: '/scenic',
    image: '/images/scenic/jiuzhaigoufengjingmingshengqu/jiuzhaigoufengjingmingshengqu_cover.jpg',
    count: 100,
    unit: '处',
  },
  {
    labelKey: 'nav.food',
    sloganKey: 'mega.sloganFood',
    path: '/food',
    image: '/images/food/mapodoufu/mapodoufu_dish.jpg',
    count: 100,
    unit: '道',
  },
  {
    labelKey: 'nav.culture',
    sloganKey: 'mega.sloganCulture',
    path: '/culture',
    image: '/images/culture/chuanju/chuanju_cover.jpg',
    count: 50,
    unit: '项',
  },
  {
    labelKey: 'nav.heritage',
    sloganKey: 'mega.sloganHeritage',
    path: '/heritage',
    image: '/images/heritage/shujinzhizaojiyi/shujinzhizaojiyi_1.jpg',
    count: 100,
    unit: '项',
  },
]

/** 有整屏 Hero 的路由前缀（导航栏在这些页面顶部透明悬浮） */
const HERO_PREFIXES = ['/scenic', '/culture', '/food', '/heritage', '/person', '/profile']

/** 快速导航 */
const QUICK_LINKS = [
  { label: 'nav.home', path: '/' },
  { label: 'nav.scenic', path: '/scenic' },
  { label: 'nav.food', path: '/food' },
  { label: 'nav.culture', path: '/culture' },
  { label: 'nav.heritage', path: '/heritage' },
  { label: 'nav.map3d', path: '/map3d' },
  { label: 'nav.assistant', path: '/assistant' },
  { label: 'nav.profile', path: '/profile' },
]

/** 资源中心（暂为占位入口，点击给出行提示） */
const RESOURCES = [
  'footer.res.scenic',
  'footer.res.culture',
  'footer.res.food',
  'footer.res.heritage',
  'footer.res.help',
]

/** 友情链接 */
const FRIEND_LINKS = ['footer.friend1', 'footer.friend2', 'footer.friend3']

/** 社交图标（内联 SVG，保持线性 1.5px 风格） */
const SOCIALS = [
  {
    name: 'wechat',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4.5C5.4 4.5 2.5 6.9 2.5 9.8c0 1.7.9 3.2 2.4 4.2L4.2 16.6l2.6-1.3c.7.2 1.4.3 2.2.3"/><path d="M15 8.2c3.3 0 6 2.2 6 4.9 0 1.5-.8 2.8-2.1 3.7l.5 2.2-2.4-1.2c-.6.2-1.3.3-2 .3-3.3 0-6-2.2-6-4.9S11.7 8.2 15 8.2z"/></svg>',
  },
  {
    name: 'weibo',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13c0-3.3 3.1-6 7-6 3.9 0 7 2.7 7 6s-3.1 6-7 6c-3.9 0-7-2.7-7-6z"/><circle cx="10" cy="13" r="2.2"/><path d="M17.5 4.5c2 0 3.5 1.5 3.5 3.5"/></svg>',
  },
  {
    name: 'bilibili',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="12" rx="3"/><path d="M7.5 3.5 10 7M16.5 3.5 14 7M9 12v2M15 12v2"/></svg>',
  },
  {
    name: 'red',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="3" width="15" height="18" rx="3"/><path d="M9 8.5h6M9 12h6M9 15.5h3.5"/></svg>',
  },
]

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 主题（白天 / 夜间），切换按钮位于语言切换右侧 */
const { themeMode, toggleTheme } = useTheme()

/** 滚动超过 100px */
const scrolled = ref(false)
/** 滚动超过 500px（显示回到顶部） */
const showTop = ref(false)

const drawerOpen = ref(false)
const searchOpen = ref(false)
const userMenuOpen = ref(false)
const langMenuOpen = ref(false)

/** 蜀地风物 Mega Menu 展开状态 */
const megaOpen = ref(false)
/** Mega Menu 悬停计时器 */
let megaTimer: number | undefined

/** 是否管理员（决定「后台管理」入口指向） */
const isAdmin = computed(() => userStore.user?.role === 'ADMIN')
/** 问候语：你好，<账号名> */
const greeting = computed(() => `${t('nav.greeting')}${userStore.user?.username ?? ''}`)
/** 头像悬停收起计时器 */
let userMenuTimer: number | undefined

/** 搜索 */
interface SearchCity {
  id: number
  name: string
  count: number
}
interface SearchScenic {
  id: number
  name: string
  cityName?: string
  type?: string
  coverImage?: string
  score?: number
}
interface SearchCulture {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
}

const searchInput = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const searching = ref(false)
const hotKeywords = ref<string[]>([])
const searchResult = ref<{ cities: SearchCity[]; scenics: SearchScenic[]; cultures: SearchCulture[] }>({
  cities: [],
  scenics: [],
  cultures: [],
})
/** 本地搜索历史（最多 6 条） */
const searchHistory = ref<string[]>([])
/** 键盘选中项下标（-1 表示未选中） */
const activeIndex = ref(-1)
let searchTimer: number | undefined

const HISTORY_KEY = 'shuyun_search_history'

/** 页脚滚动入场上下文 */
let ctx: gsap.Context | null = null

/* ------------------------------------------------------------
 * 计算属性
 * ---------------------------------------------------------- */
/** 当前页面顶部是否有整屏 Hero */
const hasHero = computed(() => {
  const p = route.path
  if (p === '/') {
    return true
  }
  return HERO_PREFIXES.some((x) => p === x || p.startsWith(`${x}/`))
})

/** 是否使用磨砂玻璃（滚动过 或 该页没有 Hero） */
const solid = computed(() => scrolled.value || !hasHero.value)

/** AI 管家页隐藏左上角品牌 LOGO（避免遮挡页面文字） */
const hideNavLogo = computed(() => route.path.startsWith('/assistant'))

/** 用户头像：仅取后端返回的自定义头像；未设置时以「人形」线性图标占位（不再使用 DiceBear 卡通图） */
const avatarUrl = computed(() => userStore.user?.avatar || '')

/** 拍平候选结果，供 ↑↓ 选择 / Enter 打开 */
const flatResults = computed(() => [
  ...searchResult.value.cities.map((c) => ({ kind: 'city' as const, id: c.id, label: c.name })),
  ...searchResult.value.scenics.map((s) => ({ kind: 'scenic' as const, id: s.id, label: s.name })),
  ...searchResult.value.cultures.map((c) => ({ kind: 'culture' as const, id: c.id, label: c.name })),
])

/** 是否有任何结果 */
const hasResult = computed(() => flatResults.value.length > 0)

/** 当前键盘选中的是否就是这一条（用于高亮） */
function isActiveResult(kind: 'city' | 'scenic' | 'culture', id: number) {
  const item = flatResults.value[activeIndex.value]
  return !!item && item.kind === kind && item.id === id
}

/* ------------------------------------------------------------
 * 滚动与高亮
 * ---------------------------------------------------------- */
function onScroll() {
  scrolled.value = window.scrollY > 100
  showTop.value = window.scrollY > 500
}

/** 菜单高亮（含子路由） */
function isActive(path: string) {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

/* ------------------------------------------------------------
 * 交互
 * ---------------------------------------------------------- */
function go(path: string) {
  drawerOpen.value = false
  searchOpen.value = false
  userMenuOpen.value = false
  langMenuOpen.value = false
  window.clearTimeout(megaTimer)
  megaOpen.value = false
  if (route.fullPath !== path) {
    router.push(path)
  }
}

/* ---------------- 蜀地风物 Mega Menu ---------------- */
/** 当前路由是否属于 Mega Menu 四个栏目之一（用于入口高亮） */
const megaActive = computed(() => MEGA_CARDS.some((c) => isActive(c.path)))

/** 悬停进入：0.15s 后展开 */
function onMegaEnter() {
  window.clearTimeout(megaTimer)
  megaTimer = window.setTimeout(() => {
    megaOpen.value = true
  }, 150)
}

/** 悬停离开：0.3s 后收起 */
function onMegaLeave() {
  window.clearTimeout(megaTimer)
  megaTimer = window.setTimeout(() => {
    megaOpen.value = false
  }, 300)
}

/** 点击菜单文字：手动展开 / 收起 */
function toggleMega() {
  window.clearTimeout(megaTimer)
  megaOpen.value = !megaOpen.value
}

/** 小屏抽屉里点击「蜀地风物」：关抽屉 + 展开面板 */
function openMegaFromDrawer() {
  window.clearTimeout(megaTimer)
  drawerOpen.value = false
  megaOpen.value = true
}

/** 点击卡片：收起面板并跳转 */
function goMega(path: string) {
  window.clearTimeout(megaTimer)
  megaOpen.value = false
  go(path)
}

/** 点击头像：已登录进入个人中心，未登录跳转登录页 */
function onUserClick() {
  go(userStore.isLogin ? '/profile' : '/login')
}

/** 鼠标进入头像区域：已登录则展开下拉 */
function onUserEnter() {
  window.clearTimeout(userMenuTimer)
  if (userStore.isLogin) {
    userMenuOpen.value = true
  }
}

/** 鼠标离开头像区域：延迟收起，便于移动到菜单项 */
function onUserLeave() {
  window.clearTimeout(userMenuTimer)
  userMenuTimer = window.setTimeout(() => {
    userMenuOpen.value = false
  }, 180)
}

/** 「后台管理」：管理员进入管理后台，普通用户进入用户后台 */
function goConsole() {
  userMenuOpen.value = false
  router.push(isAdmin.value ? '/admin/dashboard' : '/user/overview')
}

/** 账号设置：跳用户后台设置页 */
function goAccountSettings() {
  userMenuOpen.value = false
  router.push('/user/settings')
}

function openSearch() {
  searchOpen.value = true
  loadHistory()
  activeIndex.value = -1
  nextTick(() => searchInput.value?.focus())
}

/** 关键词高亮：先转义再包 <em>，避免 XSS */
function highlight(text: string, kw: string) {
  const safe = (text || '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] ?? c))
  if (!kw) {
    return safe
  }
  const key = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(key, 'gi'), (m) => `<em class="hl">${m}</em>`)
}

/** 读 / 存本地搜索历史 */
function loadHistory() {
  try {
    searchHistory.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]') as string[]
  } catch {
    searchHistory.value = []
  }
}

function pushHistory(text: string) {
  if (!text) {
    return
  }
  const next = [text, ...searchHistory.value.filter((x) => x !== text)].slice(0, 6)
  searchHistory.value = next
  localStorage.setItem(HISTORY_KEY, JSON.stringify(next))
}

function clearHistory() {
  searchHistory.value = []
  localStorage.removeItem(HISTORY_KEY)
}

/** 用某个词发起搜索（热门词 / 历史词） */
function searchBy(text: string) {
  keyword.value = text
}

/** 打开某条结果 */
function openResult(item: { kind: 'city' | 'scenic' | 'culture'; id: number; label: string }) {
  pushHistory(keyword.value.trim() || item.label)
  if (item.kind === 'city') {
    go(`/scenic?cityId=${item.id}`)
    return
  }
  if (item.kind === 'scenic') {
    go(`/scenic/${item.id}`)
    return
  }
  go(`/culture/${item.id}`)
}

/** 查看全部景点结果（带关键词跳到列表页） */
function viewAllScenics() {
  const kw = keyword.value.trim()
  if (!kw) {
    return
  }
  pushHistory(kw)
  go(`/scenic?keyword=${encodeURIComponent(kw)}`)
}

/** 浮层内键盘操作 */
function onSearchKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, flatResults.value.length - 1)
    return
  }
  if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, -1)
    return
  }
  if (e.key === 'Enter') {
    e.preventDefault()
    const item = flatResults.value[activeIndex.value]
    if (item) {
      openResult(item)
    } else {
      viewAllScenics()
    }
  }
}

/** 聚合搜索：300ms 防抖，一次拿回市州 / 景点 / 文化三类结果 */
watch(keyword, (kw) => {
  window.clearTimeout(searchTimer)
  activeIndex.value = -1
  const text = kw.trim()
  if (!text) {
    searchResult.value = { cities: [], scenics: [], cultures: [] }
    return
  }
  searchTimer = window.setTimeout(async () => {
    searching.value = true
    try {
      const res = await request.get('/search', { silent: true, params: { keyword: text, limit: 5 } })
      hotKeywords.value = res.data?.hotKeywords ?? []
      searchResult.value = {
        cities: res.data?.cities ?? [],
        scenics: res.data?.scenics ?? [],
        cultures: res.data?.cultures ?? [],
      }
    } catch {
      searchResult.value = { cities: [], scenics: [], cultures: [] }
    } finally {
      searching.value = false
    }
  }, 300)
})

/** 语言切换（界面文案，景点/文化正文保留中文原文） */
function switchLang(next: Locale) {
  setLocale(next)
  langMenuOpen.value = false
  ElMessage.info(t(next === 'en' ? 'lang.toEn' : 'lang.toZh'))
}

function logout() {
  userStore.logout()
  userMenuOpen.value = false
  ElMessage.success('已退出登录')
  router.push('/login')
}

/** 点击空白处收起下拉 */
function onDocClick(e: MouseEvent) {
  const el = e.target as HTMLElement
  if (!el.closest('.nav-user')) {
    userMenuOpen.value = false
  }
  if (!el.closest('.nav-lang')) {
    langMenuOpen.value = false
  }
  // 点击 Mega Menu / 入口 / 小屏抽屉以外的区域 → 收起面板
  if (!el.closest('.nav-mega') && !el.closest('.mega') && !el.closest('.drawer')) {
    window.clearTimeout(megaTimer)
    megaOpen.value = false
  }
}

/** 键盘：Esc 关闭浮层 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    searchOpen.value = false
    drawerOpen.value = false
    userMenuOpen.value = false
    langMenuOpen.value = false
    window.clearTimeout(megaTimer)
    megaOpen.value = false
  }
}

/** 回到顶部（优先交给 Lenis） */
function toTop() {
  const lenis = getLenis()
  if (lenis) {
    lenis.scrollTo(0, { duration: 1.2 })
  } else {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function resourceTip(name: string) {
  ElMessage.info(t('lang.tip', { name }))
}

/** 资源中心入口：能跳的跳，其余给出建设提示 */
function onResource(key: string) {
  if (key === 'footer.res.scenic') {
    go('/scenic')
    return
  }
  if (key === 'footer.res.culture') {
    go('/culture')
    return
  }
  resourceTip(t(key))
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)

  onScroll()

  // 页脚四栏从左到右依次淡入（对话页隐藏页脚时跳过，避免空选择器告警）
  if (!route.meta.hideFooter) {
    ctx = gsap.context(() => {
      gsap.from('.footer__col', {
        y: 40,
        opacity: 0,
        duration: 0.8,
        stagger: 0.12,
        ease: 'power3.out',
        scrollTrigger: { trigger: '.footer', start: 'top 88%', once: true },
      })
    })
  }
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  window.clearTimeout(searchTimer)
  window.clearTimeout(userMenuTimer)
  window.clearTimeout(megaTimer)
  ctx?.revert()
  ctx = null
})

// 路由变化时收起所有浮层
watch(
  () => route.path,
  () => {
    drawerOpen.value = false
    searchOpen.value = false
    userMenuOpen.value = false
    langMenuOpen.value = false
    window.clearTimeout(megaTimer)
    megaOpen.value = false
  },
)
</script>

<template>
  <div class="layout" :class="{ 'is-user-menu-open': userMenuOpen }">
    <!-- ================= 导航栏 ================= -->
    <header class="nav" :class="{ 'is-solid': solid }">
      <div class="nav__inner">
        <!-- Logo：太阳神鸟徽标，点击回首页（AI 管家页隐藏，避免遮挡文字） -->
        <div v-if="!hideNavLogo" class="nav__logo" @click="go('/')">
          <BrandLogo :size="144" tooltip clickable />
        </div>

        <!-- 主导航：首页 | 蜀地风物(Mega Menu) | 3D数据大屏 | AI管家 -->
        <nav class="nav__menu">
          <a class="nav__item" :class="{ 'is-active': isActive('/') }" @click="go('/')">
            <span class="nav__item-text">{{ t('nav.home') }}</span>
            <span class="nav__item-line" aria-hidden="true" />
            <span class="nav__item-dot" aria-hidden="true" />
          </a>

          <button
            type="button"
            class="nav__item nav-mega"
            :class="{ 'is-active': megaOpen || megaActive }"
            @mouseenter="onMegaEnter"
            @mouseleave="onMegaLeave"
            @click="toggleMega"
          >
            <span class="nav__item-text">{{ t('nav.folk') }}</span>
            <svg class="nav-mega__caret" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="nav__item-line" aria-hidden="true" />
            <span class="nav__item-dot" aria-hidden="true" />
          </button>

          <a
            v-for="m in MENUS"
            :key="m.path"
            class="nav__item"
            :class="{ 'is-active': isActive(m.path) }"
            @click="go(m.path)"
          >
            <span class="nav__item-text">{{ t(m.label) }}</span>
            <span class="nav__item-line" aria-hidden="true" />
            <span class="nav__item-dot" aria-hidden="true" />
          </a>
        </nav>

        <!-- 右侧操作区 -->
        <div class="nav__actions">
          <!-- 搜索 -->
          <button type="button" class="nav__icon" :title="t('nav.searchTitle')" @click="openSearch">
            <svg viewBox="0 0 24 24" width="38" height="38" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <circle cx="11" cy="11" r="6" />
              <path d="M20 20l-4.3-4.3" />
            </svg>
          </button>

          <!-- 语言切换 -->
          <div class="nav-lang">
            <button
              type="button"
              class="nav__icon nav-lang__btn"
              :title="t(locale === 'zh' ? 'nav.langCurrentZh' : 'nav.langCurrentEn')"
              @click.stop="langMenuOpen = !langMenuOpen"
            >
              <span class="nav-lang__text">{{ locale === 'zh' ? '中' : 'EN' }}</span>
              <svg class="nav-lang__caret" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
            <transition name="nav-drop">
              <div v-if="langMenuOpen" class="nav-drop nav-lang__menu">
                <button type="button" :class="{ 'is-on': locale === 'zh' }" @click="switchLang('zh')">
                  {{ t('nav.langZh') }} <em>ZH</em>
                </button>
                <button type="button" :class="{ 'is-on': locale === 'en' }" @click="switchLang('en')">
                  {{ t('nav.langEn') }} <em>EN</em>
                </button>
              </div>
            </transition>
          </div>

          <!-- 主题切换：白天 / 夜间（位于语言切换右侧） -->
          <button
            type="button"
            class="nav__icon nav-theme"
            :title="`${t('nav.themeTitle')}：${themeMode === 'night' ? t('nav.themeNight') : t('nav.themeDay')}`"
            @click="toggleTheme"
          >
            <svg v-if="themeMode === 'night'" viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
            </svg>
            <svg v-else viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="4.6" />
              <path d="M12 1.8v3M12 19.2v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M1.8 12h3M19.2 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
            </svg>
          </button>

          <!-- 用户：登录/注册融合入口 + 已登录悬停下拉 -->
          <div class="nav-user" @mouseenter="onUserEnter" @mouseleave="onUserLeave">
            <button
              type="button"
              class="nav-user__btn"
              :title="userStore.isLogin ? greeting : t('nav.register')"
              @click="onUserClick"
            >
              <img v-if="avatarUrl" class="nav-user__avatar" :src="avatarUrl" alt="" />
              <span v-else class="nav-user__avatar nav-user__avatar--ghost">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4 21a8 8 0 0 1 16 0" />
                </svg>
              </span>
              <span v-if="userStore.isLogin" class="nav-user__hello">{{ greeting }}</span>
            </button>

            <transition name="nav-drop">
              <div v-if="userStore.isLogin && userMenuOpen" class="nav-drop nav-user__menu">
                <div class="nav-drop__head">
                  <img v-if="avatarUrl" class="nav-drop__avatar" :src="avatarUrl" alt="" />
                  <span v-else class="nav-drop__avatar nav-drop__avatar--ghost"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg></span>
                  <span class="nav-drop__meta">
                    <b>{{ greeting }}</b>
                    <em>{{ userStore.user?.email }}</em>
                  </span>
                </div>
                <button type="button" @click="goConsole">{{ t('nav.admin') }}</button>
                <button type="button" @click="go('/profile')">{{ t('nav.profile') }}</button>
                <button type="button" @click="goAccountSettings">{{ t('nav.accountSettings') }}</button>
                <span class="nav-drop__sep" />
                <button type="button" class="is-danger" @click="logout">{{ t('nav.logout') }}</button>
              </div>
            </transition>
          </div>

          <!-- 汉堡按钮（小屏） -->
          <button type="button" class="nav__burger" :title="t('nav.menuTitle')" @click="drawerOpen = true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 底部金色渐变线 -->
      <span class="nav__line" aria-hidden="true" />
    </header>

    <!-- ================= 蜀地风物 · 全屏 Mega Menu ================= -->
    <transition name="mega">
      <section
        v-if="megaOpen"
        class="mega"
        aria-label="蜀地风物"
        @mouseenter="onMegaEnter"
        @mouseleave="onMegaLeave"
      >
        <div class="mega__inner">
          <header class="mega__head">
            <h2 class="mega__title">{{ t('nav.folk') }}</h2>
            <p class="mega__sub">{{ t('mega.sub') }}</p>
          </header>

          <div class="mega__grid">
            <button
              v-for="c in MEGA_CARDS"
              :key="c.path"
              type="button"
              class="mega-card"
              @click="goMega(c.path)"
            >
              <span class="mega-card__media">
                <img class="mega-card__img" :src="c.image" :alt="t(c.labelKey)" loading="lazy" decoding="async" />
              </span>
              <span class="mega-card__body">
                <span class="mega-card__top">
                  <span class="mega-card__name">{{ t(c.labelKey) }}</span>
                  <span class="mega-card__arrow" aria-hidden="true">→</span>
                </span>
                <span class="mega-card__count">{{ c.count }} {{ c.unit }}</span>
                <span class="mega-card__slogan">{{ t(c.sloganKey) }}</span>
              </span>
            </button>
          </div>
        </div>
      </section>
    </transition>

    <!-- ================= 内容 ================= -->
    <main class="layout__main" :class="{ 'is-flat': !hasHero }">
      <slot />
    </main>

    <!-- ================= 页脚（对话页 /assistant 隐藏，见路由 meta.hideFooter） ================= -->
    <footer v-if="!route.meta.hideFooter" class="footer">
      <span class="footer__line" aria-hidden="true" />

      <div class="sy-container footer__inner">
        <!-- 1. 品牌 -->
        <div class="footer__col footer__col--brand">
          <div class="footer__logo">
            <BrandLogo :size="56" />
            <span class="footer__name sy-brand-text">{{ t('app.name') }}</span>
          </div>
          <p class="footer__slogan">{{ t('footer.slogan') }}</p>
          <ul class="socials">
            <li v-for="s in SOCIALS" :key="s.name" :title="t(`social.${s.name}`)">
              <span class="socials__icon" v-html="s.svg" />
            </li>
          </ul>
        </div>

        <!-- 2. 快速导航 -->
        <div class="footer__col">
          <h4 class="footer__title">{{ t('footer.quick') }}</h4>
          <ul class="footer__list">
            <li v-for="l in QUICK_LINKS" :key="l.path" class="is-link" @click="go(l.path)">
              {{ t(l.label) }}
            </li>
          </ul>
        </div>

        <!-- 3. 资源中心 -->
        <div class="footer__col">
          <h4 class="footer__title">{{ t('footer.resource') }}</h4>
          <ul class="footer__list">
            <li v-for="r in RESOURCES" :key="r" class="is-link" @click="onResource(r)">
              {{ t(r) }}
            </li>
          </ul>
        </div>

        <!-- 4. 联系我们 -->
        <div class="footer__col">
          <h4 class="footer__title">{{ t('footer.contact') }}</h4>
          <ul class="footer__list footer__list--contact">
            <li>{{ t('footer.email') }}: hello@shuyun-travel.com</li>
            <li>{{ t('footer.phone') }}: 028-8888 8888</li>
            <li>{{ t('footer.address') }}: {{ t('footer.addressValue') }}</li>
            <li>{{ t('footer.icp') }}: {{ t('footer.icpValue') }}</li>
          </ul>
        </div>
      </div>

      <div class="footer__bottom">
        <p class="footer__copy">{{ t('footer.copyright') }}</p>
        <p class="footer__icp">{{ t('footer.icpValue') }}</p>
        <p class="footer__friends">
          {{ t('footer.friends') }}
          <a v-for="f in FRIEND_LINKS" :key="f" href="javascript:;">{{ t(f) }}</a>
        </p>
      </div>
    </footer>

    <!-- ================= 搜索浮层 ================= -->
    <Teleport to="body">
      <transition name="drop-fade">
        <div v-if="searchOpen" class="search" @click.self="searchOpen = false">
          <div class="search__panel">
            <div class="search__head">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                <circle cx="11" cy="11" r="6" />
                <path d="M20 20l-4.3-4.3" />
              </svg>
              <input
                ref="searchInput"
                v-model="keyword"
                class="search__input"
                type="text"
                :placeholder="t('search.placeholder')"
                @keydown="onSearchKeydown"
              />
              <button type="button" class="search__esc" @click="searchOpen = false">ESC</button>
            </div>

            <!-- ---------- 有输入：分组结果 ---------- -->
            <div v-if="keyword" class="search__body">
              <!-- 市州 -->
              <section v-if="searchResult.cities.length" class="search__group">
                <p class="search__group-title">{{ t('search.city') }}</p>
                <ul class="search__list">
                  <li
                    v-for="c in searchResult.cities"
                    :key="`city-${c.id}`"
                    :class="{ 'is-active': isActiveResult('city', c.id) }"
                    @click="openResult({ kind: 'city', id: c.id, label: c.name })"
                  >
                    <span class="search__badge">州</span>
                    <span class="search__info">
                      <b v-html="highlight(c.name, keyword.trim())" />
                      <em>{{ t('search.cityCount', { count: c.count }) }}</em>
                    </span>
                    <span class="search__arrow">→</span>
                  </li>
                </ul>
              </section>

              <!-- 景点 -->
              <section v-if="searchResult.scenics.length" class="search__group">
                <p class="search__group-title">
                  {{ t('search.scenic') }}
                  <button type="button" class="search__more" @click="viewAllScenics">{{ t('search.viewAll') }}</button>
                </p>
                <ul class="search__list">
                  <li
                    v-for="s in searchResult.scenics"
                    :key="`scenic-${s.id}`"
                    :class="{ 'is-active': isActiveResult('scenic', s.id) }"
                    @click="openResult({ kind: 'scenic', id: s.id, label: s.name })"
                  >
                    <img class="search__cover" :src="s.coverImage" alt="" loading="lazy" />
                    <span class="search__info">
                      <b v-html="highlight(s.name, keyword.trim())" />
                      <em>{{ s.cityName }} · {{ s.type }}</em>
                    </span>
                    <span class="search__arrow">→</span>
                  </li>
                </ul>
              </section>

              <!-- 文化 -->
              <section v-if="searchResult.cultures.length" class="search__group">
                <p class="search__group-title">{{ t('search.culture') }}</p>
                <ul class="search__list">
                  <li
                    v-for="c in searchResult.cultures"
                    :key="`culture-${c.id}`"
                    :class="{ 'is-active': isActiveResult('culture', c.id) }"
                    @click="openResult({ kind: 'culture', id: c.id, label: c.name })"
                  >
                    <img class="search__cover" :src="c.coverImage" alt="" loading="lazy" />
                    <span class="search__info">
                      <b v-html="highlight(c.name, keyword.trim())" />
                      <em>{{ c.category }}</em>
                    </span>
                    <span class="search__arrow">→</span>
                  </li>
                </ul>
              </section>

              <p v-if="searching" class="search__empty">{{ t('search.searching') }}</p>
              <p v-else-if="!hasResult" class="search__empty">{{ t('search.empty', { keyword }) }}</p>

              <p class="search__hint">{{ t('search.hint') }}</p>
            </div>

            <!-- ---------- 无输入：搜索历史记录 + 热门搜索 ---------- -->
            <div v-else class="search__body">
              <section class="search__group">
                <p class="search__group-title">
                  {{ t('search.recent') }}
                  <button v-if="searchHistory.length" type="button" class="search__more" @click="clearHistory">{{ t('search.clear') }}</button>
                </p>
                <div v-if="searchHistory.length" class="search__chips">
                  <button v-for="h in searchHistory" :key="h" type="button" @click="searchBy(h)">
                    {{ h }}
                  </button>
                </div>
                <p v-else class="search__no-history">{{ t('search.noHistory') }}</p>
              </section>

              <section class="search__group">
                <p class="search__group-title">{{ t('search.hot') }}</p>
                <div class="search__chips">
                  <button
                    v-for="word in hotKeywords.length ? hotKeywords : ['九寨沟', '三星堆', '大熊猫', '都江堰']"
                    :key="word"
                    type="button"
                    @click="searchBy(word)"
                  >
                    {{ word }}
                  </button>
                </div>
              </section>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- ================= 小屏全屏抽屉 ================= -->
    <Teleport to="body">
      <transition name="drawer">
        <div v-if="drawerOpen" class="drawer">
          <div class="drawer__bg" aria-hidden="true" />

          <button type="button" class="drawer__close" title="关闭" @click="drawerOpen = false">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>

          <div class="drawer__inner">
            <div class="drawer__logo">
              <BrandLogo :size="48" />
              <span class="drawer__name">{{ t('app.name') }}</span>
            </div>

            <nav class="drawer__menu">
              <button
                type="button"
                class="drawer__item"
                :class="{ 'is-active': isActive('/') }"
                @click="go('/')"
              >
                {{ t('nav.home') }}
              </button>

              <button
                type="button"
                class="drawer__item"
                :class="{ 'is-active': megaActive }"
                @click.stop="openMegaFromDrawer"
              >
                {{ t('nav.folk') }}
              </button>

              <button
                v-for="m in MENUS"
                :key="m.path"
                type="button"
                class="drawer__item"
                :class="{ 'is-active': isActive(m.path) }"
                @click="go(m.path)"
              >
                {{ t(m.label) }}
              </button>
            </nav>

            <div class="drawer__foot">
              <template v-if="userStore.isLogin">
                <button type="button" class="drawer__user" @click="go('/profile')">
                  <img v-if="avatarUrl" class="drawer__avatar" :src="avatarUrl" alt="" />
                  <span v-else class="drawer__avatar drawer__avatar--ghost"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg></span>
                  {{ userStore.user?.username }}
                </button>
                <button type="button" class="drawer__logout" @click="logout">{{ t('nav.logout') }}</button>
              </template>
              <button v-else type="button" class="drawer__login" @click="go('/login')">{{ t('nav.register') }}</button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- ================= 回到顶部 ================= -->
    <transition name="drop-fade">
      <button v-if="showTop" type="button" class="to-top" :title="t('common.backTop')" @click="toTop">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 19V5M6 11l6-6 6 6" />
        </svg>
      </button>
    </transition>

    <!-- ================= 蜀韵管家智能体入口（全站前台常驻；/assistant 页隐藏） ================= -->
    <AiFab v-if="route.path !== '/assistant'" />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;
/* ============================================================
 * 骨架
 * ========================================================== */
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 用户下拉展开时，隐藏右侧滚动指示器（「蜀韵智游」+ 金色圆点），避免与菜单重叠影响观感 */
.layout.is-user-menu-open :deep(.scroll-indicator) {
  opacity: 0;
  pointer-events: none;
}

.layout__main {
  flex: 1;

  /* 没有整屏 Hero 的页面（AI 管家 / 登录）需要让开固定导航栏 */
  &.is-flat {
    padding-top: $nav-height;
  }
}

/* ============================================================
 * 导航栏
 * ========================================================== */
.nav {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: $z-header;
  background: transparent;
  transition:
    background-color $duration-slow $ease-out-expo,
    box-shadow $duration-slow $ease-out-expo;

  /* 滚动 100px 后：磨砂玻璃 */
  &.is-solid {
    background: var(--sy-nav-solid);
    backdrop-filter: blur(20px);
    box-shadow: var(--sy-nav-shadow);

    .nav__line {
      opacity: 1;
    }
  }
}

/* 夜间模式：导航栏为淡蓝底、文字保持白色，加一层柔影提升可读性 */
html[data-theme='night'] .nav.is-solid {
  .nav__item,
  .nav__icon,
  .nav-user__hello,
  .nav__burger {
    text-shadow: 0 1px 6px rgba(8, 22, 42, 0.45);
  }
}

.nav__inner {
  display: flex;
  align-items: center;
  gap: clamp(12px, 2vw, 36px);
  height: $nav-height;
  /* 左右留白上限 32px，使 Logo 距屏幕左侧 32px */
  padding-inline: clamp(16px, 4vw, 32px);
}

/* ---------------- Logo ---------------- */
/* 品牌徽标（太阳神鸟玉璧团章），hover 效果由 BrandLogo 内部处理 */
.nav__logo {
  display: flex;
  align-items: center;
  cursor: pointer;
}

/* 放大后的徽标随视口缩放，始终略小于导航栏高度 */
.nav__logo :deep(.brand-logo__mark) {
  width: clamp(64px, 11vw, 144px);
  height: clamp(64px, 11vw, 144px);
}

/* ---------------- 菜单 ---------------- */
.nav__menu {
  display: flex;
  align-items: center;
  gap: clamp(2px, 0.6vw, 10px);
  margin-left: clamp(6px, 2vw, 26px);
}

.nav__item {
  position: relative;
  padding: 8px 12px;
  font-family: $font-body;
  font-size: clamp(22px, 2.1vw, 32px);
  letter-spacing: 0.02em;
  white-space: nowrap;
  color: rgba(245, 241, 232, 0.86);
  cursor: pointer;
  transition: color $duration-normal $ease-out-expo;

  &:hover,
  &.is-active {
    color: $color-gold;
  }
}

/* hover 下划线：从左到右展开 */
.nav__item-line {
  position: absolute;
  right: 12px;
  bottom: 2px;
  left: 12px;
  height: 1.5px;
  background: linear-gradient(90deg, $color-gold, rgba(212, 179, 116, 0.25));
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform $duration-normal $ease-out-expo;
}

.nav__item:hover .nav__item-line {
  transform: scaleX(1);
}

/* 当前菜单：金色圆点 */
.nav__item-dot {
  position: absolute;
  bottom: -2px;
  left: 50%;
  width: 4px;
  height: 4px;
  margin-left: -2px;
  border-radius: 50%;
  background: $color-gold;
  opacity: 0;
  transform: scale(0.4);
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.nav__item.is-active .nav__item-dot {
  opacity: 1;
  transform: scale(1);
}

/* ---------------- 右侧操作 ---------------- */
.nav__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.nav__icon {
  @include flex-center;
  /* 操作区整体放大两倍（随视口自适应，宽屏达到 2×） */
  width: clamp(56px, 6vw, 80px);
  height: clamp(56px, 6vw, 80px);
  border-radius: $radius-lg;
  color: rgba(245, 241, 232, 0.88);
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;

  &:hover {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.14);
    transform: translateY(-2px);
  }
}

.nav-lang {
  position: relative;
}

.nav-lang__btn {
  width: auto;
  gap: 8px;
  padding-inline: 20px;
}

.nav-lang__text {
  font-family: $font-en-body;
  font-size: clamp(20px, 1.9vw, 26px);
  letter-spacing: 0.08em;
}

.nav-lang__caret {
  opacity: 0.7;
}

/* 主题切换（白天 / 夜间）：金色图标，位于语言切换右侧 */
.nav-theme {
  color: $color-gold;
}

/* 下拉菜单：磨砂玻璃 + 从上方滑入淡入 */
.nav-drop {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  z-index: $z-header + 1;
  min-width: 196px;
  padding: 8px;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(14, 42, 71, 0.86);
  backdrop-filter: blur(18px);
  box-shadow: $shadow-lg;

  button {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding: 10px 12px;
    border-radius: $radius-md;
    font-family: $font-body;
    font-size: $fs-small;
    letter-spacing: 0.08em;
    color: rgba(245, 241, 232, 0.86);
    text-align: left;
    transition:
      background-color $duration-fast $ease-out-expo,
      color $duration-fast $ease-out-expo;

    &:hover {
      background: rgba(212, 179, 116, 0.16);
      color: $color-gold;
    }

    &.is-on {
      color: $color-gold;
    }

    &.is-danger {
      color: #ff9b9b;

      &:hover {
        background: rgba(255, 120, 120, 0.14);
        color: #ffbcbc;
      }
    }

    em {
      font-style: normal;
      font-size: 11px;
      opacity: 0.5;
    }
  }
}

/* 用户下拉：直角 + 透明背景；打开时模糊并压暗身后的页面内容，隐藏重叠的文字 / 标识；内部元素整体放大 0.5× */
.nav-user__menu {
  min-width: 294px;
  padding: 12px;
  border-radius: 0;
  background: transparent;
  backdrop-filter: blur(20px) brightness(0.7) saturate(1.1);
  box-shadow: none;

  button {
    padding: 15px 18px;
    border-radius: 0;
    font-size: calc(#{$fs-small} * 1.5);
  }
}

.nav-drop-enter-active,
.nav-drop-leave-active {
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.nav-drop-enter-from,
.nav-drop-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 用户头像 */
.nav-user {
  position: relative;
}

.nav-user__btn {
  display: block;
  padding: 0;
  cursor: pointer;
}

.nav-user__avatar {
  width: clamp(52px, 5.6vw, 76px);
  height: clamp(52px, 5.6vw, 76px);
  border: 3px solid $color-gold;
  border-radius: 50%;
  object-fit: cover;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &--text {
    @include flex-center;
    background: $color-primary;
    font-family: $font-title;
    font-weight: 900;
    font-size: 16px;
    color: $color-gold;
  }
}

.nav-user__btn:hover .nav-user__avatar {
  transform: scale(1.06);
  box-shadow: 0 0 0 4px rgba(212, 179, 116, 0.18);
}

/* 未登录：空心头像（点击打开登录/注册面板） */
.nav-user__avatar--ghost {
  @include flex-center;
  background: rgba(245, 241, 232, 0.08);
  color: $color-gold;

  /* 人形图标：较上一版放大 0.5×（20 → 30px） */
  svg {
    width: 30px;
    height: 30px;
  }
}

/* 头像旁问候语：你好，<账号名> */
.nav-user__hello {
  margin-left: 16px;
  font-family: $font-body;
  font-size: clamp(20px, 1.7vw, 32px);
  letter-spacing: 0.06em;
  color: rgba(245, 241, 232, 0.88);
}

.nav-user__btn {
  display: flex;
  align-items: center;
}

@media (max-width: 900px) {
  .nav-user__hello {
    display: none;
  }
}

.nav-drop__head {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 12px 15px 15px;
}

.nav-drop__avatar {
  width: 60px;
  height: 60px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  object-fit: cover;

  &--ghost {
    @include flex-center;
    background: $color-primary;
    color: $color-gold;

    svg {
      width: 30px;
      height: 30px;
    }
  }
}

.nav-drop__meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: calc(#{$fs-small} * 1.5);
    color: $color-text-light;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 17px;
    color: rgba(245, 241, 232, 0.5);
    @include text-ellipsis(1);
  }
}

.nav-drop__sep {
  display: block;
  height: 1px;
  margin: 9px 6px;
  background: rgba(212, 179, 116, 0.24);
}

.nav__login {
  padding: 9px 22px;
  border: 1px solid $color-gold;
  border-radius: $radius-pill;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.14em;
  color: $color-gold;
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-2px);
  }
}

.nav__burger {
  @include flex-center;
  /* 置于 mixin 之后，否则会被 flex-center 的 display: flex 覆盖 */
  display: none;
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  color: rgba(245, 241, 232, 0.88);

  &:hover {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.14);
  }
}

/* 底部 1px 金色渐变线 */
.nav__line {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, $color-gold 50%, transparent);
  opacity: 0.45;
  transition: opacity $duration-slow $ease-out-expo;
}

/* ---------------- 蜀地风物：菜单入口 ---------------- */
.nav-mega {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
}

.nav-mega__caret {
  margin-top: 2px;
  transition: transform $duration-normal $ease-out-expo;
}

.nav-mega.is-active .nav-mega__caret {
  transform: rotate(180deg);
}

/* Mega 入口用"短线"表示展开态，隐去通用圆点 */
.nav-mega .nav__item-dot {
  display: none;
}

/* 展开/悬停：底部金色短线（24×2，居中） */
.nav-mega .nav__item-line {
  right: auto;
  left: 50%;
  width: 24px;
  height: 2px;
  transform: translateX(-50%) scaleX(0);
  transform-origin: center;
}

.nav-mega:hover .nav__item-line,
.nav-mega.is-active .nav__item-line {
  transform: translateX(-50%) scaleX(1);
}

/* ============================================================
 * 蜀地风物 · 全屏 Mega Menu
 * ========================================================== */
.mega {
  position: fixed;
  top: $nav-height;
  left: 0;
  z-index: $z-header - 1;
  width: 100vw;
  height: 480px;
  overflow: hidden;
  background: rgba(14, 42, 71, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);

  /* 底部 1px 金色渐变线 */
  &::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 1px;
    background: linear-gradient(90deg, transparent, $color-gold 50%, transparent);
  }
}

.mega__inner {
  display: flex;
  flex-direction: column;
  gap: 26px;
  height: 100%;
  padding: 48px clamp(24px, 8vw, 120px);
}

.mega__head {
  flex: 0 0 auto;
}

.mega__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 28px;
  letter-spacing: 0.08em;
  background: linear-gradient(120deg, #f4e3bc 0%, $color-gold 55%, #b8935a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.mega__sub {
  margin-top: 8px;
  font-family: $font-body;
  font-size: 14px;
  letter-spacing: 0.22em;
  color: #7a95b8;
}

.mega__grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
  min-height: 0;
}

.mega-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.15);
  border-radius: 12px;
  background: #0f2540;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.45s $ease-out-expo,
    border-color 0.45s $ease-out-expo,
    box-shadow 0.45s $ease-out-expo;
  /* 卡片依次淡入（backwards：延迟期间隐藏，动完回归常态，不干扰 hover 位移） */
  animation: mega-card-in 0.45s $ease-out-expo backwards;

  &:nth-child(1) {
    animation-delay: 0.08s;
  }

  &:nth-child(2) {
    animation-delay: 0.16s;
  }

  &:nth-child(3) {
    animation-delay: 0.24s;
  }

  &:nth-child(4) {
    animation-delay: 0.32s;
  }

  &:hover {
    transform: translateY(-8px);
    border-color: rgba(201, 168, 106, 0.6);
    box-shadow: $shadow-lg;

    .mega-card__img {
      transform: scale(1.05);
    }

    .mega-card__arrow {
      color: $color-gold;
      transform: translateX(4px);
    }
  }
}

@keyframes mega-card-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.mega-card__media {
  display: block;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  background: #0b1b30;
}

.mega-card__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s $ease-out-expo;
}

.mega-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 20px;
}

.mega-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mega-card__name {
  font-family: $font-title;
  font-weight: 600;
  font-size: 20px;
  letter-spacing: 0.06em;
  color: #fff;
}

.mega-card__arrow {
  flex: 0 0 auto;
  color: rgba(245, 241, 232, 0.55);
  transition:
    color 0.45s $ease-out-expo,
    transform 0.45s $ease-out-expo;
}

.mega-card__count {
  font-family: $font-en-body;
  font-size: 14px;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.mega-card__slogan {
  font-family: $font-body;
  font-size: 14px;
  color: #7a95b8;
}

/* 展开动画：从顶部滑下 + 淡入 */
.mega-enter-active {
  transition:
    opacity 0.4s $ease-out-expo,
    transform 0.4s $ease-out-expo;
}

.mega-leave-active {
  transition:
    opacity 0.3s $ease-out-expo,
    transform 0.3s $ease-out-expo;
}

.mega-enter-from,
.mega-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 小屏：面板高度自适应 + 2×2 网格 */
@include responsive(md) {
  .mega {
    height: auto;
    max-height: 72vh;
    overflow-y: auto;
  }

  .mega__inner {
    gap: 18px;
    padding: 24px 20px 28px;
  }

  .mega__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .mega-card__body {
    padding: 14px;
  }

  .mega-card__name {
    font-size: 18px;
  }
}

/* ============================================================
 * 搜索浮层
 * ========================================================== */
.search {
  position: fixed;
  inset: 0;
  z-index: $z-loader + 1;
  display: flex;
  justify-content: center;
  padding: clamp(72px, 12vh, 140px) clamp(16px, 5vw, 60px);
  background: rgba(8, 14, 22, 0.72);
  backdrop-filter: blur(10px);
}

.search__panel {
  align-self: flex-start;
  /* 整体较上一版放大 1× */
  width: min(1440px, 100%);
  padding: clamp(32px, 3.2vw, 44px);
  border: 1px solid rgba(212, 179, 116, 0.55);
  /* 直角边框 + 白底 */
  border-radius: 0;
  background: #fff;
  box-shadow: $shadow-lg;
}

.search__head {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(212, 179, 116, 0.28);
  color: $color-ink;
}

.search__input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: $font-body;
  font-size: clamp(30px, 2.6vw, 36px);
  color: $color-ink;
  caret-color: $color-gold;

  &::placeholder {
    color: rgba(14, 42, 71, 0.4);
  }
}

.search__esc {
  padding: 6px 20px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: calc(#{$radius-sm} * 2);
  font-size: 22px;
  letter-spacing: 0.1em;
  color: rgba(14, 42, 71, 0.6);
}

.search__list {
  display: flex;
  flex-direction: column;
  gap: 8px;

  li {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 16px 20px;
    border-radius: calc(#{$radius-md} * 2);
    cursor: pointer;
    transition: background-color $duration-fast $ease-out-expo;

    &:hover,
    &.is-active {
      background: rgba(212, 179, 116, 0.14);

      .search__arrow {
        opacity: 1;
        transform: translateX(3px);
      }
    }
  }
}

/* 分组结果容器 */
.search__body {
  max-height: 58vh;
  overflow-y: auto;
}

.search__group {
  padding-top: 24px;

  & + .search__group {
    margin-top: 24px;
    border-top: 1px dashed rgba(212, 179, 116, 0.2);
  }
}

.search__group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  font-family: $font-title;
  font-weight: 900;
  font-size: 24px;
  letter-spacing: 0.18em;
  color: $color-ink;
}

.search__more {
  font-family: $font-body;
  font-size: 24px;
  letter-spacing: 0.06em;
  color: rgba(14, 42, 71, 0.55);
  transition: color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
  }
}

/* 历史为空时的占位提示 */
.search__no-history {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 2);
  letter-spacing: 0.06em;
  color: rgba(14, 42, 71, 0.4);
}

/* 市州徽标 */
.search__badge {
  @include flex-center;
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: calc(#{$radius-sm} * 2);
  background: $color-primary;
  font-family: $font-title;
  font-weight: 900;
  font-size: 30px;
  color: $color-gold;
}

/* 空态：历史 / 热门词 */
.search__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;

  button {
    padding: 12px 28px;
    border: 1px solid rgba(212, 179, 116, 0.6);
    border-radius: $radius-pill;
    font-family: $font-body;
    font-size: 24px;
    letter-spacing: 0.08em;
    color: rgba(14, 42, 71, 0.8);
    transition:
      border-color $duration-fast $ease-out-expo,
      background-color $duration-fast $ease-out-expo,
      color $duration-fast $ease-out-expo;

    &:hover {
      border-color: $color-gold;
      background: rgba(212, 179, 116, 0.18);
      color: $color-ink;
    }
  }
}

/* 命中关键词高亮 */
.search__panel :deep(.hl) {
  padding: 0 1px;
  background: rgba(212, 179, 116, 0.32);
  border-radius: 2px;
  font-style: normal;
  color: $color-primary;
}

.search__hint {
  padding: 28px 8px 8px;
  font-size: 22px;
  letter-spacing: 0.1em;
  color: rgba(14, 42, 71, 0.4);
  text-align: center;
}

.search__cover {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: calc(#{$radius-sm} * 2);
}

.search__info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: calc(#{$fs-small} * 2);
    color: $color-ink;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 24px;
    color: rgba(14, 42, 71, 0.55);
  }
}

.search__arrow {
  margin-left: auto;
  font-size: 32px;
  color: $color-primary;
  opacity: 0.55;
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

.search__empty {
  padding: 44px 12px 12px;
  font-size: calc(#{$fs-small} * 2);
  color: rgba(14, 42, 71, 0.55);
  text-align: center;
}

/* ============================================================
 * 小屏全屏抽屉
 * ========================================================== */
.drawer {
  position: fixed;
  inset: 0;
  z-index: $z-loader + 2;
  display: grid;
  place-items: center;
  background: rgba(14, 42, 71, 0.94);
  backdrop-filter: blur(20px);
}

/* 蜀锦纹理 */
.drawer__bg {
  position: absolute;
  inset: 0;
  background-image: $pattern-brocade;
  opacity: 0.09;
  mix-blend-mode: overlay;
  pointer-events: none;
}

.drawer__close {
  position: absolute;
  top: clamp(16px, 3vh, 32px);
  right: clamp(16px, 5vw, 40px);
  @include flex-center;
  width: 44px;
  height: 44px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: 50%;
  color: $color-gold;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
}

.drawer__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(20px, 4vh, 40px);
  width: min(86vw, 420px);
  text-align: center;
}

.drawer__logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  letter-spacing: 0.2em;
  color: $color-text-light;
}

.drawer__menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.drawer__item {
  padding: 14px;
  border-radius: $radius-md;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 5vw, 22px);
  letter-spacing: 0.2em;
  color: rgba(245, 241, 232, 0.86);
  transition:
    color $duration-normal $ease-out-expo,
    background-color $duration-normal $ease-out-expo;

  &:hover {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.12);
  }

  &.is-active {
    color: $color-gold;
    background: rgba(212, 179, 116, 0.16);
  }
}

.drawer__foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding-top: 20px;
  border-top: 1px solid rgba(212, 179, 116, 0.24);
}

.drawer__user {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: $fs-small;
  color: rgba(245, 241, 232, 0.8);
}

.drawer__avatar {
  width: 40px;
  height: 40px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  object-fit: cover;

  &--ghost {
    @include flex-center;
    background: $color-primary;
    color: $color-gold;
  }
}

.drawer__login {
  padding: 11px 34px;
  border: 1px solid $color-gold;
  border-radius: $radius-pill;
  font-size: $fs-small;
  letter-spacing: 0.14em;
  color: $color-gold;

  &:hover {
    background: $color-gold;
    color: $color-ink;
  }
}

.drawer__logout {
  font-size: $fs-small;
  color: #ff9b9b;

  &:hover {
    text-decoration: underline;
  }
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity $duration-slow $ease-out-expo;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-active .drawer__inner {
  transition: transform $duration-slow $ease-out-expo;
}

.drawer-enter-from .drawer__inner {
  transform: translateY(24px);
}

/* 搜索 / 回到顶部共用动效 */
.drop-fade-enter-active,
.drop-fade-leave-active {
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.drop-fade-enter-from,
.drop-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* ============================================================
 * 回到顶部
 * ========================================================== */
.to-top {
  position: fixed;
  right: clamp(16px, 2.4vw, 40px);
  bottom: clamp(16px, 3vh, 40px);
  z-index: $z-header;
  @include flex-center;
  width: 48px;
  height: 48px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  background: rgba(14, 42, 71, 0.4);
  backdrop-filter: blur(10px);
  color: $color-gold;
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-4px);
  }
}

/* ============================================================
 * 页脚
 * ========================================================== */
.footer {
  position: relative;
  padding: 80px 0 40px;
  overflow: hidden;
  background: $color-ink;
  color: rgba(245, 241, 232, 0.66);
}

/* 上方 1px 金色渐变线 */
.footer__line {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, $color-gold 50%, transparent);
}

.footer__inner {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr 1.2fr;
  gap: clamp(24px, 3.4vw, 56px);
}

.footer__logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.footer__name {
  /* 只定字号；金色立体效果全部交给全局 .sy-brand-text */
  font-size: 24px;
}

.footer__slogan {
  margin-top: 14px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(245, 241, 232, 0.55);
}

/* 社交图标 */
.socials {
  display: flex;
  gap: 12px;
  margin-top: 22px;
}

.socials__icon {
  @include flex-center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(212, 179, 116, 0.36);
  border-radius: 50%;
  color: rgba(245, 241, 232, 0.7);
  transition:
    color $duration-normal $ease-out-expo,
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  :deep(svg) {
    width: 19px;
    height: 19px;
  }
}

.socials li:hover .socials__icon {
  border-color: $color-gold;
  background: $color-gold;
  color: $color-ink;
  transform: translateY(-3px) scale(1.08);
}

/* 栏目标题 + 金色装饰线 */
.footer__title {
  position: relative;
  margin-bottom: 20px;
  padding-bottom: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: 15px;
  letter-spacing: 0.16em;
  color: $color-text-light;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 28px;
    height: 2px;
    background: linear-gradient(90deg, $color-gold, transparent);
  }
}

.footer__list {
  display: flex;
  flex-direction: column;
  gap: 11px;
  font-size: $fs-small;
}

.footer__list .is-link {
  cursor: pointer;
  transition:
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    color: $color-gold;
    transform: translateX(4px);
  }
}

.footer__list--contact li {
  line-height: 1.7;
  color: rgba(245, 241, 232, 0.55);
}

/* 底部条 */
.footer__bottom {
  margin-top: clamp(40px, 5vw, 64px);
  padding-top: 24px;
  border-top: 1px solid rgba(212, 179, 116, 0.16);
  text-align: center;
}

.footer__copy {
  font-size: $fs-small;
  color: rgba(245, 241, 232, 0.6);
}

.footer__icp {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(245, 241, 232, 0.4);
}

.footer__friends {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(245, 241, 232, 0.45);

  a {
    margin-left: 12px;
    color: rgba(245, 241, 232, 0.55);
    transition: color $duration-fast $ease-out-expo;

    &:hover {
      color: $color-gold;
    }
  }
}

/* ============================================================
 * 响应式
 * ========================================================== */
/* 中屏以下：菜单收进抽屉 */
@include responsive(1024px) {
  .nav__menu {
    display: none;
  }

  .nav__burger {
    display: flex;
  }

  .footer__inner {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@include responsive(md) {
  .nav__login {
    display: none;
  }

  .nav__inner {
    gap: 10px;
  }

  .footer {
    padding: 56px 0 32px;
  }

  .footer__inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 36px;
  }
}

@include responsive(sm) {
  .nav-lang {
    display: none;
  }
}
</style>
