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

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 常量
 * ---------------------------------------------------------- */
/** 主导航 */
const MENUS = [
  { label: '首页', path: '/' },
  { label: '景点', path: '/scenic' },
  { label: '文化', path: '/culture' },
  { label: 'AI管家', path: '/assistant' },
]

/** 有整屏 Hero 的路由前缀（导航栏在这些页面顶部透明悬浮） */
const HERO_PREFIXES = ['/scenic', '/culture', '/profile']

/** 快速导航 */
const QUICK_LINKS = [
  { label: '首页', path: '/' },
  { label: '景点', path: '/scenic' },
  { label: '文化', path: '/culture' },
  { label: 'AI管家', path: '/assistant' },
  { label: '个人中心', path: '/profile' },
]

/** 资源中心（暂为占位入口，点击给出行提示） */
const RESOURCES = ['景点大全', '文化图谱', '川味地图', '非遗名录', '帮助中心']

/** 友情链接 */
const FRIEND_LINKS = ['四川省文化和旅游厅', '成都文旅', '四川非遗']

/** 社交图标（内联 SVG，保持线性 1.5px 风格） */
const SOCIALS = [
  {
    name: '微信',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 4.5C5.4 4.5 2.5 6.9 2.5 9.8c0 1.7.9 3.2 2.4 4.2L4.2 16.6l2.6-1.3c.7.2 1.4.3 2.2.3"/><path d="M15 8.2c3.3 0 6 2.2 6 4.9 0 1.5-.8 2.8-2.1 3.7l.5 2.2-2.4-1.2c-.6.2-1.3.3-2 .3-3.3 0-6-2.2-6-4.9S11.7 8.2 15 8.2z"/></svg>',
  },
  {
    name: '微博',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13c0-3.3 3.1-6 7-6 3.9 0 7 2.7 7 6s-3.1 6-7 6c-3.9 0-7-2.7-7-6z"/><circle cx="10" cy="13" r="2.2"/><path d="M17.5 4.5c2 0 3.5 1.5 3.5 3.5"/></svg>',
  },
  {
    name: 'B站',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="12" rx="3"/><path d="M7.5 3.5 10 7M16.5 3.5 14 7M9 12v2M15 12v2"/></svg>',
  },
  {
    name: '小红书',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="3" width="15" height="18" rx="3"/><path d="M9 8.5h6M9 12h6M9 15.5h3.5"/></svg>',
  },
]

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 滚动超过 100px */
const scrolled = ref(false)
/** 滚动超过 500px（显示回到顶部） */
const showTop = ref(false)

const drawerOpen = ref(false)
const searchOpen = ref(false)
const userMenuOpen = ref(false)
const langMenuOpen = ref(false)

/** 语言：zh / en（当前仅记忆偏好，文案暂为中文） */
const lang = ref<'zh' | 'en'>('zh')

/** 搜索 */
const searchInput = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const searching = ref(false)
const searchResults = ref<{ id: number; name: string; cityName?: string; type?: string; coverImage?: string }[]>([])
let searchTimer: number | undefined

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

/** 用户头像（没有就用首字母） */
const avatarUrl = computed(() => userStore.user?.avatar || '')
const initial = computed(() => userStore.user?.username?.charAt(0)?.toUpperCase() || '蜀')

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
  if (route.fullPath !== path) {
    router.push(path)
  }
}

/** 个人中心内部面板（我的行程 / 我的收藏…）通过 query 指定 */
function goProfile(tab: string) {
  go(`/profile?tab=${tab}`)
}

function openSearch() {
  searchOpen.value = true
  nextTick(() => searchInput.value?.focus())
}

/** 搜索：300ms 防抖后调景点接口（后端支持 keyword） */
watch(keyword, (kw) => {
  window.clearTimeout(searchTimer)
  const text = kw.trim()
  if (!text) {
    searchResults.value = []
    return
  }
  searchTimer = window.setTimeout(async () => {
    searching.value = true
    try {
      const res = await request.get('/scenic/list', {
        silent: true,
        params: { page: 1, size: 6, keyword: text },
      })
      searchResults.value = res.data?.records ?? []
    } catch {
      searchResults.value = []
    } finally {
      searching.value = false
    }
  }, 300)
})

/** 语言切换（暂只记忆偏好） */
function switchLang(next: 'zh' | 'en') {
  lang.value = next
  localStorage.setItem('shuyun_lang', next)
  langMenuOpen.value = false
  if (next === 'en') {
    ElMessage.info('English 版本正在准备中，已记住你的偏好')
  }
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
}

/** 键盘：Esc 关闭浮层 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    searchOpen.value = false
    drawerOpen.value = false
    userMenuOpen.value = false
    langMenuOpen.value = false
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
  ElMessage.info(`「${name}」正在建设中`)
}

/** 资源中心入口：能跳的跳，其余给出建设提示 */
function onResource(name: string) {
  if (name === '景点大全') {
    go('/scenic')
    return
  }
  if (name === '文化图谱') {
    go('/culture')
    return
  }
  resourceTip(name)
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)

  lang.value = (localStorage.getItem('shuyun_lang') as 'zh' | 'en') || 'zh'
  onScroll()

  // 页脚四栏从左到右依次淡入
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
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  window.clearTimeout(searchTimer)
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
  },
)
</script>

<template>
  <div class="layout">
    <!-- ================= 导航栏 ================= -->
    <header class="nav" :class="{ 'is-solid': solid }">
      <div class="nav__inner">
        <!-- Logo：篆刻印章 + 品牌名 -->
        <div class="nav__logo" @click="go('/')">
          <span class="nav__seal">蜀</span>
          <span class="nav__brand">蜀韵·智游</span>
        </div>

        <!-- 主导航 -->
        <nav class="nav__menu">
          <a
            v-for="m in MENUS"
            :key="m.path"
            class="nav__item"
            :class="{ 'is-active': isActive(m.path) }"
            @click="go(m.path)"
          >
            <span class="nav__item-text">{{ m.label }}</span>
            <span class="nav__item-line" aria-hidden="true" />
            <span class="nav__item-dot" aria-hidden="true" />
          </a>
        </nav>

        <!-- 右侧操作区 -->
        <div class="nav__actions">
          <!-- 搜索 -->
          <button type="button" class="nav__icon" title="搜索（景点 / 城市）" @click="openSearch">
            <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
              <circle cx="11" cy="11" r="6" />
              <path d="M20 20l-4.3-4.3" />
            </svg>
          </button>

          <!-- 语言切换 -->
          <div class="nav-lang">
            <button
              type="button"
              class="nav__icon nav-lang__btn"
              :title="lang === 'zh' ? '当前：中文' : 'Current: English'"
              @click.stop="langMenuOpen = !langMenuOpen"
            >
              <span class="nav-lang__text">{{ lang === 'zh' ? '中' : 'EN' }}</span>
              <svg class="nav-lang__caret" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
            <transition name="nav-drop">
              <div v-if="langMenuOpen" class="nav-drop nav-lang__menu">
                <button type="button" :class="{ 'is-on': lang === 'zh' }" @click="switchLang('zh')">
                  中文 <em>ZH</em>
                </button>
                <button type="button" :class="{ 'is-on': lang === 'en' }" @click="switchLang('en')">
                  English <em>EN</em>
                </button>
              </div>
            </transition>
          </div>

          <!-- 用户 -->
          <div v-if="userStore.isLogin" class="nav-user">
            <button type="button" class="nav-user__btn" title="账号" @click.stop="userMenuOpen = !userMenuOpen">
              <img v-if="avatarUrl" class="nav-user__avatar" :src="avatarUrl" alt="" />
              <span v-else class="nav-user__avatar nav-user__avatar--text">{{ initial }}</span>
            </button>

            <transition name="nav-drop">
              <div v-if="userMenuOpen" class="nav-drop nav-user__menu">
                <div class="nav-drop__head">
                  <img v-if="avatarUrl" class="nav-drop__avatar" :src="avatarUrl" alt="" />
                  <span v-else class="nav-drop__avatar nav-drop__avatar--text">{{ initial }}</span>
                  <span class="nav-drop__meta">
                    <b>{{ userStore.user?.username }}</b>
                    <em>{{ userStore.user?.email }}</em>
                  </span>
                </div>
                <button type="button" @click="go('/profile')">个人中心</button>
                <button type="button" @click="goProfile('trips')">我的行程</button>
                <button type="button" @click="goProfile('collection')">我的收藏</button>
                <span class="nav-drop__sep" />
                <button type="button" class="is-danger" @click="logout">退出登录</button>
              </div>
            </transition>
          </div>

          <button v-else type="button" class="nav__login" @click="go('/login')">登录</button>

          <!-- 汉堡按钮（小屏） -->
          <button type="button" class="nav__burger" title="菜单" @click="drawerOpen = true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 底部金色渐变线 -->
      <span class="nav__line" aria-hidden="true" />
    </header>

    <!-- ================= 内容 ================= -->
    <main class="layout__main" :class="{ 'is-flat': !hasHero }">
      <slot />
    </main>

    <!-- ================= 页脚 ================= -->
    <footer class="footer">
      <span class="footer__line" aria-hidden="true" />

      <div class="sy-container footer__inner">
        <!-- 1. 品牌 -->
        <div class="footer__col footer__col--brand">
          <div class="footer__logo">
            <span class="footer__seal">蜀</span>
            <span class="footer__name">蜀韵·智游</span>
          </div>
          <p class="footer__slogan">AI 懂你的巴蜀旅行管家</p>
          <ul class="socials">
            <li v-for="s in SOCIALS" :key="s.name" :title="s.name">
              <span class="socials__icon" v-html="s.svg" />
            </li>
          </ul>
        </div>

        <!-- 2. 快速导航 -->
        <div class="footer__col">
          <h4 class="footer__title">快速导航</h4>
          <ul class="footer__list">
            <li v-for="l in QUICK_LINKS" :key="l.path" class="is-link" @click="go(l.path)">
              {{ l.label }}
            </li>
          </ul>
        </div>

        <!-- 3. 资源中心 -->
        <div class="footer__col">
          <h4 class="footer__title">资源中心</h4>
          <ul class="footer__list">
            <li v-for="r in RESOURCES" :key="r" class="is-link" @click="onResource(r)">
              {{ r }}
            </li>
          </ul>
        </div>

        <!-- 4. 联系我们 -->
        <div class="footer__col">
          <h4 class="footer__title">联系我们</h4>
          <ul class="footer__list footer__list--contact">
            <li>邮箱：hello@shuyun-travel.com</li>
            <li>电话：028-8888 8888</li>
            <li>地址：四川省成都市武侯区</li>
            <li>备案：蜀ICP备2026000000号</li>
          </ul>
        </div>
      </div>

      <div class="footer__bottom">
        <p class="footer__copy">© 2026 蜀韵·智游 shuyun-travel · 保留所有权利</p>
        <p class="footer__icp">蜀ICP备2026000000号 · 川公网安备 51010000000000号</p>
        <p class="footer__friends">
          友情链接：
          <a v-for="f in FRIEND_LINKS" :key="f" href="javascript:;">{{ f }}</a>
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
                placeholder="搜索景点或城市（如 九寨沟 / 成都）"
              />
              <button type="button" class="search__esc" @click="searchOpen = false">ESC</button>
            </div>

            <ul v-if="searchResults.length" class="search__list">
              <li v-for="s in searchResults" :key="s.id" @click="go(`/scenic/${s.id}`)">
                <img class="search__cover" :src="s.coverImage" alt="" loading="lazy" />
                <span class="search__info">
                  <b>{{ s.name }}</b>
                  <em>{{ s.cityName }} · {{ s.type }}</em>
                </span>
                <span class="search__arrow">→</span>
              </li>
            </ul>

            <p v-else-if="keyword && !searching" class="search__empty">没有找到匹配的景点，换个关键词试试</p>
            <p v-else-if="!keyword" class="search__tip">
              试试：
              <span v-for="t in ['九寨沟', '三星堆', '大熊猫', '都江堰']" :key="t" @click="keyword = t">{{ t }}</span>
            </p>
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
              <span class="drawer__seal">蜀</span>
              <span class="drawer__name">蜀韵·智游</span>
            </div>

            <nav class="drawer__menu">
              <button
                v-for="m in MENUS"
                :key="m.path"
                type="button"
                class="drawer__item"
                :class="{ 'is-active': isActive(m.path) }"
                @click="go(m.path)"
              >
                {{ m.label }}
              </button>
            </nav>

            <div class="drawer__foot">
              <template v-if="userStore.isLogin">
                <button type="button" class="drawer__user" @click="go('/profile')">
                  <img v-if="avatarUrl" class="drawer__avatar" :src="avatarUrl" alt="" />
                  <span v-else class="drawer__avatar drawer__avatar--text">{{ initial }}</span>
                  {{ userStore.user?.username }}
                </button>
                <button type="button" class="drawer__logout" @click="logout">退出登录</button>
              </template>
              <button v-else type="button" class="drawer__login" @click="go('/login')">登录 / 注册</button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- ================= 回到顶部 ================= -->
    <transition name="drop-fade">
      <button v-if="showTop" type="button" class="to-top" title="回到顶部" @click="toTop">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 19V5M6 11l6-6 6 6" />
        </svg>
      </button>
    </transition>
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

.layout__main {
  flex: 1;

  /* 没有整屏 Hero 的页面（AI 管家 / 登录）需要让开固定导航栏 */
  &.is-flat {
    padding-top: 80px;
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
    background: rgba(14, 42, 71, 0.85);
    backdrop-filter: blur(20px);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);

    .nav__line {
      opacity: 1;
    }

    .nav__inner {
      height: 64px;
    }
  }
}

.nav__inner {
  display: flex;
  align-items: center;
  gap: clamp(12px, 2vw, 36px);
  height: 80px;
  padding-inline: clamp(20px, 4vw, 80px);
  transition: height $duration-slow $ease-out-expo;
}

/* ---------------- Logo ---------------- */
.nav__logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

/* 篆刻印章：hover 轻微旋转 + 放大（0.5s） */
.nav__seal {
  @include flex-center;
  width: 40px;
  height: 40px;
  border: 1.5px solid $color-gold;
  border-radius: 6px;
  background: rgba(140, 31, 40, 0.92);
  font-family: $font-title;
  font-weight: 900;
  font-size: 22px;
  color: $color-gold;
  transition:
    transform 0.5s $ease-out-expo,
    box-shadow 0.5s $ease-out-expo;
}

.nav__logo:hover .nav__seal {
  transform: rotate(-8deg) scale(1.12);
  box-shadow: 0 0 0 4px rgba(201, 168, 106, 0.2);
}

.nav__brand {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 1.5vw, 21px);
  letter-spacing: 0.18em;
  color: $color-text-light;
}

/* ---------------- 菜单 ---------------- */
.nav__menu {
  display: flex;
  align-items: center;
  gap: clamp(2px, 0.8vw, 14px);
  margin-left: clamp(6px, 2vw, 26px);
}

.nav__item {
  position: relative;
  padding: 10px 14px;
  font-family: $font-body;
  font-size: 15px;
  letter-spacing: 0.12em;
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
  right: 14px;
  bottom: 4px;
  left: 14px;
  height: 1.5px;
  background: linear-gradient(90deg, $color-gold, rgba(201, 168, 106, 0.25));
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
  gap: 6px;
  margin-left: auto;
}

.nav__icon {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  color: rgba(245, 241, 232, 0.88);
  transition:
    color $duration-fast $ease-out-expo,
    background-color $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;

  &:hover {
    color: $color-gold;
    background: rgba(201, 168, 106, 0.14);
    transform: translateY(-2px);
  }
}

.nav-lang {
  position: relative;
}

.nav-lang__btn {
  width: auto;
  gap: 4px;
  padding-inline: 10px;
}

.nav-lang__text {
  font-family: $font-en-body;
  font-size: 13px;
  letter-spacing: 0.08em;
}

.nav-lang__caret {
  opacity: 0.7;
}

/* 下拉菜单：磨砂玻璃 + 从上方滑入淡入 */
.nav-drop {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  z-index: $z-header + 1;
  min-width: 196px;
  padding: 8px;
  border: 1px solid rgba(201, 168, 106, 0.4);
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
      background: rgba(201, 168, 106, 0.16);
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
  width: 38px;
  height: 38px;
  border: 2px solid $color-gold;
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
  box-shadow: 0 0 0 4px rgba(201, 168, 106, 0.18);
}

.nav-drop__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px 10px;
}

.nav-drop__avatar {
  width: 40px;
  height: 40px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  object-fit: cover;

  &--text {
    @include flex-center;
    background: $color-primary;
    font-family: $font-title;
    font-weight: 900;
    color: $color-gold;
  }
}

.nav-drop__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: $fs-small;
    color: $color-text-light;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 11px;
    color: rgba(245, 241, 232, 0.5);
    @include text-ellipsis(1);
  }
}

.nav-drop__sep {
  display: block;
  height: 1px;
  margin: 6px 4px;
  background: rgba(201, 168, 106, 0.24);
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
    background: rgba(201, 168, 106, 0.14);
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
  width: min(720px, 100%);
  padding: clamp(16px, 1.6vw, 22px);
  border: 1px solid rgba(201, 168, 106, 0.45);
  border-radius: $radius-lg;
  background: rgba(14, 42, 71, 0.92);
  box-shadow: $shadow-lg;
}

.search__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(201, 168, 106, 0.28);
  color: $color-gold;
}

.search__input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: $font-body;
  font-size: clamp(15px, 1.3vw, 18px);
  color: $color-text-light;
  caret-color: $color-gold;

  &::placeholder {
    color: rgba(245, 241, 232, 0.4);
  }
}

.search__esc {
  padding: 3px 10px;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-sm;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: rgba(245, 241, 232, 0.6);
}

.search__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 52vh;
  margin-top: 10px;
  overflow-y: auto;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border-radius: $radius-md;
    cursor: pointer;
    transition: background-color $duration-fast $ease-out-expo;

    &:hover {
      background: rgba(201, 168, 106, 0.14);

      .search__arrow {
        opacity: 1;
        transform: translateX(3px);
      }
    }
  }
}

.search__cover {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-sm;
}

.search__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: $fs-small;
    color: $color-text-light;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 12px;
    color: rgba(245, 241, 232, 0.55);
  }
}

.search__arrow {
  margin-left: auto;
  color: $color-gold;
  opacity: 0.5;
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

.search__tip,
.search__empty {
  padding: 22px 6px 6px;
  font-size: $fs-small;
  color: rgba(245, 241, 232, 0.55);
  text-align: center;

  span {
    margin-inline: 4px;
    color: $color-gold;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
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
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: 50%;
  color: $color-gold;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(201, 168, 106, 0.16);
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

.drawer__seal {
  @include flex-center;
  width: 42px;
  height: 42px;
  border: 1.5px solid $color-gold;
  border-radius: 6px;
  background: rgba(140, 31, 40, 0.92);
  font-family: $font-title;
  font-weight: 900;
  font-size: 23px;
  color: $color-gold;
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
    background: rgba(201, 168, 106, 0.12);
  }

  &.is-active {
    color: $color-gold;
    background: rgba(201, 168, 106, 0.16);
  }
}

.drawer__foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding-top: 20px;
  border-top: 1px solid rgba(201, 168, 106, 0.24);
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

  &--text {
    @include flex-center;
    background: $color-primary;
    font-family: $font-title;
    font-weight: 900;
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

.footer__seal {
  @include flex-center;
  width: 36px;
  height: 36px;
  border: 1.5px solid $color-gold;
  border-radius: 6px;
  background: rgba(140, 31, 40, 0.85);
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  color: $color-gold;
}

.footer__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 18px;
  letter-spacing: 0.18em;
  color: $color-text-light;
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
  border: 1px solid rgba(201, 168, 106, 0.36);
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
  border-top: 1px solid rgba(201, 168, 106, 0.16);
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

  .nav__brand {
    display: none;
  }
}
</style>
