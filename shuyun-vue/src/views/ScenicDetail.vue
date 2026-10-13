<script setup lang="ts">
/**
 * 景点详情（杂志感）
 * - 100vh Hero（Ken Burns + 滚动视差 + 图片模糊到清晰）
 * - sticky 概览栏 / 不等大画廊（Lightbox）/ 左右分栏内容区 / 拼图图片墙 / 底部大字 CTA
 * - 复用公共设计系统：variables、mixins、GoldButton、SectionTitle、useLenis
 *
 * 数据事实（重要）：
 *   - 后端暂无 /api/weather：先尝试真实接口，失败自动落到模拟数据（后端补齐后前端无需改动）
 *   - longitude / latitude 字段存在但库里为空：位置卡片用「地址」+ 高德 URI 跳转（不需要 key），
 *     如需内嵌真实小地图，需要申请高德 JS API Key
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import request from '@/api/request'
import { getScenicDetail, type ScenicDetailVO } from '@/api/scenic'
import { useUserStore } from '@/stores/user'
import { t } from '@/i18n'
import { getLenis } from '@/composables/useLenis'
import GoldButton from '@/components/common/GoldButton.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import SectionTitle from '@/components/common/SectionTitle.vue'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
// 类型统一来自 @/api/scenic（ScenicDetailVO / ScenicSpotVO）

interface WeatherInfo {
  city: string
  temp: string
  text: string
  tip: string
  icon: 'sun' | 'cloud' | 'rain'
}

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const rootEl = ref<HTMLElement | null>(null)
const heroEl = ref<HTMLElement | null>(null)
const overviewEl = ref<HTMLElement | null>(null)
const burstEl = ref<HTMLCanvasElement | null>(null)

const detail = ref<ScenicDetailVO | null>(null)
const loading = ref(true)
const notFound = ref(false)
const collected = ref(false)
const heroLoaded = ref(false)
const stuck = ref(false)

/** 天气（接口缺失时用模拟数据） */
const weather = ref<WeatherInfo | null>(null)
const weatherIsMock = ref(false)

/** Lightbox */
const lbOpen = ref(false)
const lbIndex = ref(0)

/** 相关影像（视频）弹层 */
const videoOpen = ref(false)

/* ------------------------------------------------------------
 * 计算属性
 * ---------------------------------------------------------- */
/**
 * 图片集合：优先用接口返回的 images（字符串数组）；为空则回落到封面图。
 * 用于首屏轮播 + 图片墙。
 */
const gallery = computed<string[]>(() => {
  const d = detail.value
  if (!d) {
    return []
  }
  const imgs = (d.images ?? []).filter(Boolean) as string[]
  const base = imgs.length ? imgs : d.coverImage ? [d.coverImage] : []
  return Array.from(new Set(base))
})

/** 首屏轮播下标 */
const heroIndex = ref(0)
/** 首屏轮播定时器 */
let heroTimer = 0

/** 开始首屏轮播（多于 1 张时每 5s 自动切换） */
function startHeroCarousel() {
  window.clearInterval(heroTimer)
  heroIndex.value = 0
  if (gallery.value.length <= 1) {
    return
  }
  heroTimer = window.setInterval(() => {
    heroIndex.value = (heroIndex.value + 1) % gallery.value.length
  }, 5000)
}

/** 图片兜底占位图（内联 SVG，避免额外请求） */
const IMG_FALLBACK =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="450">' +
      '<rect width="100%" height="100%" fill="#F0EBE0"/>' +
      '<text x="50%" y="50%" fill="#D4B374" font-size="60" font-family="serif" text-anchor="middle" dominant-baseline="middle">蜀</text>' +
      '</svg>',
  )

/** 图片加载失败：换成占位图（每张只处理一次，避免死循环） */
function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') {
    return
  }
  img.dataset.fallback = '1'
  img.src = IMG_FALLBACK
}

const tags = computed(() =>
  (detail.value?.tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean),
)

/** 概览栏四项 */
const overviewItems = computed(() => {
  const d = detail.value
  if (!d) {
    return []
  }
  return [
    { label: 'detail.score', value: d.score != null ? `${d.score}` : '—' },
    { label: 'detail.level', value: d.level || '—' },
    { label: 'detail.openTime', value: d.openTime || '—' },
    { label: 'detail.ticket', value: d.ticketInfo || '—' },
  ]
})

/** 同城推荐（拼图图片墙用，最多 3 条） */
const recommends = computed(() => detail.value?.recommends?.slice(0, 3) ?? [])

/** 位置：有经纬度用 marker，否则用地址搜索（都不需要 key） */
const mapLink = computed(() => {
  const d = detail.value
  if (!d) {
    return ''
  }
  if (d.longitude && d.latitude) {
    return `https://uri.amap.com/marker?position=${d.longitude},${d.latitude}&name=${encodeURIComponent(d.name)}`
  }
  return `https://uri.amap.com/search?keyword=${encodeURIComponent(d.address || d.name)}`
})

/**
 * 视频嵌入地址：
 * - 已是 player.bilibili.com / YouTube embed → 原样
 * - B站普通视频页 → https://player.bilibili.com/player.html?bvid=BVxxx
 * - YouTube watch → embed
 * - 其它 → 空（由调用方新窗口打开）
 */
const videoEmbedUrl = computed(() => {
  const url = detail.value?.videoUrl || ''
  if (!url) {
    return ''
  }
  if (/player\.bilibili\.com|youtube\.com\/embed/.test(url)) {
    return url
  }
  const bili = url.match(/bilibili\.com\/video\/(BV[0-9A-Za-z]+)/)
  if (bili) {
    return `https://player.bilibili.com/player.html?bvid=${bili[1]}&high_quality=1&danmaku=0`
  }
  const yt = url.match(/youtube\.com\/watch\?v=([\w-]+)/)
  if (yt) {
    return `https://www.youtube.com/embed/${yt[1]}`
  }
  return ''
})

/* ------------------------------------------------------------
 * 数据加载
 * ---------------------------------------------------------- */
async function loadDetail() {
  loading.value = true
  notFound.value = false
  try {
    const data = await getScenicDetail(route.params.id as string)
    detail.value = data ?? null
    notFound.value = !detail.value
    if (detail.value) {
      heroLoaded.value = false
      startHeroCarousel()
      await Promise.all([checkCollected(), loadWeather(detail.value.cityName || '成都')])
      recordHistory()
    }
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

/**
 * 记录浏览足迹（未登录不记录；失败静默，不影响页面）
 */
function recordHistory() {
  if (!userStore.isLogin || !detail.value) {
    return
  }
  request
    .post('/user/history', { targetType: 'SCENIC', targetId: detail.value.id }, { silent: true })
    .catch(() => {
      // 足迹记录失败不影响浏览
    })
}

/** 查询是否已收藏 */
async function checkCollected() {
  if (!userStore.isLogin || !detail.value) {
    return
  }
  try {
    const res = await request.get('/user/collection/check', {
      silent: true,
      params: { targetType: 'SCENIC', targetId: detail.value.id },
    })
    collected.value = res.data === true
  } catch {
    collected.value = false
  }
}

/**
 * 天气：先调 /api/weather，失败则用模拟数据
 * （后端补齐该接口后，这里无需任何改动）
 */
async function loadWeather(city: string) {
  try {
    const res = await request.get('/weather', { silent: true, params: { city } })
    if (res.data) {
      weather.value = res.data as WeatherInfo
      weatherIsMock.value = false
      return
    }
  } catch {
    // 接口不存在 → 走模拟
  }
  weather.value = { city, temp: '22', text: '多云', tip: '微风，适合出行', icon: 'cloud' }
  weatherIsMock.value = true
}

/* ------------------------------------------------------------
 * 收藏 / 分享
 * ---------------------------------------------------------- */
async function onCollect() {
  const d = detail.value
  if (!d) {
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning(t('detail.loginFirst'))
    router.push('/login')
    return
  }

  try {
    if (collected.value) {
      await request.delete('/user/collection', { params: { targetType: 'SCENIC', targetId: d.id } })
      collected.value = false
      ElMessage.success(t('detail.unCollected'))
    } else {
      await request.post('/user/collection', { targetType: 'SCENIC', targetId: d.id })
      collected.value = true
      burstGold() // 心形粒子
      ElMessage.success(t('detail.collectedOk'))
    }
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

async function onShare() {
  const url = window.location.href
  const title = detail.value?.name ?? '蜀韵·智游'
  try {
    if (navigator.share) {
      await navigator.share({ title, url })
      return
    }
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('detail.linkCopied'))
  } catch {
    ElMessage.info(t('detail.copyHint'))
  }
}

/* ------------------------------------------------------------
 * 心形粒子爆发（Canvas）
 * ---------------------------------------------------------- */
let burstRaf = 0

function burstGold() {
  const cv = burstEl.value
  if (!cv) {
    return
  }
  const dpr = window.devicePixelRatio || 1
  const w = cv.clientWidth
  const h = cv.clientHeight
  cv.width = w * dpr
  cv.height = h * dpr
  const ctx = cv.getContext('2d')
  if (!ctx) {
    return
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  const parts = Array.from({ length: 26 }, () => {
    const angle = Math.random() * Math.PI * 2
    const speed = 1.4 + Math.random() * 3.6
    return {
      x: w / 2,
      y: h / 2,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed - 1.4,
      r: 1.4 + Math.random() * 2.2,
      life: 1,
    }
  })

  cancelAnimationFrame(burstRaf)
  const step = () => {
    ctx.clearRect(0, 0, w, h)
    let alive = false
    parts.forEach((p) => {
      p.x += p.vx
      p.y += p.vy
      p.vy += 0.13
      p.life -= 0.018
      if (p.life > 0) {
        alive = true
        ctx.globalAlpha = Math.max(0, p.life)
        ctx.fillStyle = '#D4B374'
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fill()
      }
    })
    ctx.globalAlpha = 1
    if (alive) {
      burstRaf = requestAnimationFrame(step)
    } else {
      ctx.clearRect(0, 0, w, h)
    }
  }
  burstRaf = requestAnimationFrame(step)
}

/* ------------------------------------------------------------
 * Lightbox
 * ---------------------------------------------------------- */
function openLightbox(i: number) {
  lbIndex.value = i
  lbOpen.value = true
}

function closeLightbox() {
  lbOpen.value = false
}

/** 打开相关影像：可内嵌播放则弹层，否则新窗口打开原链接 */
function openVideo() {
  const url = detail.value?.videoUrl
  if (!url) {
    return
  }
  if (videoEmbedUrl.value) {
    videoOpen.value = true
  } else {
    window.open(url, '_blank', 'noopener')
  }
}

function lbStep(delta: number) {
  const n = gallery.value.length
  if (!n) {
    return
  }
  lbIndex.value = (lbIndex.value + delta + n) % n
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && videoOpen.value) {
    videoOpen.value = false
    return
  }
  if (!lbOpen.value) {
    return
  }
  if (e.key === 'Escape') {
    closeLightbox()
  } else if (e.key === 'ArrowRight') {
    lbStep(1)
  } else if (e.key === 'ArrowLeft') {
    lbStep(-1)
  }
}

// Lightbox 打开时锁滚动（同时暂停 Lenis）
watch(lbOpen, (open) => {
  const lenis = getLenis()
  if (open) {
    document.body.style.overflow = 'hidden'
    lenis?.stop()
  } else {
    document.body.style.overflow = ''
    lenis?.start()
  }
})

// 相关影像弹层打开时同样锁滚动
watch(videoOpen, (open) => {
  const lenis = getLenis()
  if (open) {
    document.body.style.overflow = 'hidden'
    lenis?.stop()
  } else {
    document.body.style.overflow = ''
    lenis?.start()
  }
})

/* ------------------------------------------------------------
 * 跳转
 * ---------------------------------------------------------- */
function goDetail(id: number) {
  router.push(`/scenic/${id}`)
}

function goAssistant() {
  router.push('/assistant')
}

function scrollToOverview() {
  const top = (heroEl.value?.offsetHeight ?? window.innerHeight) + 68
  getLenis()?.scrollTo(top, { duration: 1.1 })
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null
let stuckTrigger: ScrollTrigger | null = null
let heroLoadedFallback = 0

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  await loadDetail()

  // 图片加载后由 @load 触发「模糊 → 清晰」；这里再加一道兜底（缓存/异常时也能清晰）
  heroLoadedFallback = window.setTimeout(() => {
    heroLoaded.value = true
  }, 1200)

  ctx = gsap.context(() => {
    // Hero：内容上移淡出 + 背景慢速位移（视差）
    if (heroEl.value) {
      gsap
        .timeline({
          scrollTrigger: { trigger: heroEl.value, start: 'top top', end: 'bottom top', scrub: true },
        })
        .to('.detail-hero__content', { yPercent: -40, opacity: 0, ease: 'none' }, 0)
        .to('.detail-hero__actions', { yPercent: -24, opacity: 0, ease: 'none' }, 0)
        .to('.detail-hero__media', { yPercent: 12, ease: 'none' }, 0)
    }

    // 画廊逐张
    gsap.from('.mosaic__cell', {
      y: 44,
      opacity: 0,
      duration: 0.9,
      stagger: 0.1,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.mosaic', start: 'top 82%', once: true },
    })

    // 内容区（正文 + 右栏卡片）
    gsap.from('.detail-reveal', {
      y: 40,
      opacity: 0,
      duration: 1,
      stagger: 0.12,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.detail-body', start: 'top 72%', once: true },
    })

    // 图片墙：逐张放大淡入
    gsap.from('.puzzle__cell', {
      scale: 0.9,
      opacity: 0,
      duration: 1,
      stagger: 0.14,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.puzzle', start: 'top 80%', once: true },
    })

    // 底部 CTA
    gsap.from('.detail-cta__inner > *', {
      y: 40,
      opacity: 0,
      duration: 1,
      stagger: 0.15,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.detail-cta', start: 'top 78%', once: true },
    })
  }, rootEl.value ?? undefined)

  // 概览栏吸顶检测
  if (overviewEl.value) {
    stuckTrigger = ScrollTrigger.create({
      trigger: overviewEl.value,
      start: 'top 68px',
      end: 'bottom 68px',
      onToggle: (self) => {
        stuck.value = self.isActive
      },
    })
  }

  ScrollTrigger.refresh()
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  window.clearTimeout(heroLoadedFallback)
  window.clearInterval(heroTimer)
  cancelAnimationFrame(burstRaf)
  ctx?.revert()
  ctx = null
  stuckTrigger?.kill()
  stuckTrigger = null
})

// 切换景点（同城推荐点击）时重新加载
watch(
  () => route.params.id,
  (id) => {
    if (id) {
      window.scrollTo({ top: 0 })
      heroLoaded.value = false
      loadDetail()
    }
  },
)
</script>

<template>
  <section ref="rootEl" class="sy-page detail-page">
    <StateBlock v-if="loading" state="loading" />

    <template v-if="detail">
      <!-- ================= 1. Hero（100vh） ================= -->
      <header ref="heroEl" class="detail-hero">
        <div class="detail-hero__media">
          <img
            v-for="(url, i) in gallery"
            :key="url"
            class="detail-hero__img"
            :class="{ 'is-active': i === heroIndex, 'is-loaded': heroLoaded }"
            :src="url"
            :alt="detail.name"
            :fetchpriority="i === 0 ? 'high' : 'auto'"
            decoding="async"
            @load="heroLoaded = true"
            @error="onImgError"
          />
        </div>

        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container detail-hero__inner">
          <div class="detail-hero__content">
            <nav class="detail-hero__crumb">
              <router-link to="/">{{ t('nav.home') }}</router-link>
              <span class="detail-hero__sep">/</span>
              <router-link to="/scenic">{{ t('nav.scenic') }}</router-link>
              <span class="detail-hero__sep">/</span>
              <span>{{ detail.name }}</span>
            </nav>

            <h1 class="detail-hero__title">{{ detail.name }}</h1>

            <div class="detail-hero__meta">
              <span v-if="detail.cityName">{{ detail.cityName }}</span>
              <i class="detail-hero__dot" />
              <span v-if="detail.type">{{ detail.type }}</span>
              <i v-if="detail.score != null" class="detail-hero__dot" />
              <span v-if="detail.score != null" class="detail-hero__score">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                  <path
                    d="M12 2l2.9 6.3 6.9.8-5 4.7 1.3 6.8L12 17.4 5.9 20.6 7.2 13.8l-5-4.7 6.9-.8z"
                  />
                </svg>
                {{ detail.score }}
              </span>
            </div>
          </div>

          <!-- 右下：收藏 / 分享 -->
          <div class="detail-hero__actions">
            <button
              type="button"
              class="hero-act"
              :class="{ 'is-on': collected }"
              :aria-pressed="collected"
              @click="onCollect"
            >
              <canvas ref="burstEl" class="hero-act__burst" aria-hidden="true" />
              <svg
                viewBox="0 0 24 24"
                width="18"
                height="18"
                :fill="collected ? 'currentColor' : 'none'"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M12 20s-7-4.6-7-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.6C19 15.4 12 20 12 20z"
                />
              </svg>
              <span>{{ collected ? t('detail.collected') : t('detail.collect') }}</span>
            </button>

            <button type="button" class="hero-act" @click="onShare">
              <svg
                viewBox="0 0 24 24"
                width="18"
                height="18"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 16V4M8 8l4-4 4 4M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5" />
              </svg>
              <span>{{ t('detail.share') }}</span>
            </button>
          </div>
        </div>

        <button type="button" class="detail-hero__scroll" @click="scrollToOverview">
          <span class="detail-hero__arrow">
            <svg
              viewBox="0 0 24 24"
              width="20"
              height="20"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 4v16M5 13l7 7 7-7" />
            </svg>
          </span>
          <span class="detail-hero__scroll-text">{{ t('detail.scrollText') }}</span>
        </button>
      </header>

      <!-- ================= 2. 概览栏（sticky） ================= -->
      <div ref="overviewEl" class="detail-overview" :class="{ 'is-stuck': stuck }">
        <div class="sy-container detail-overview__inner">
          <div v-for="(item, i) in overviewItems" :key="item.label" class="detail-overview__item">
            <span class="detail-overview__label">{{ t(item.label) }}</span>
            <span class="detail-overview__value">{{ item.value }}</span>
            <i v-if="i < overviewItems.length - 1" class="detail-overview__sep" />
          </div>
        </div>
      </div>

      <!-- ================= 2.5 实用信息（图标卡片网格） ================= -->
      <section class="sy-container detail-info">
        <div class="detail-info__card">
          <span class="detail-info__icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-6-5.3-6-10a6 6 0 1 1 12 0c0 4.7-6 10-6 10z" /><circle cx="12" cy="11" r="2.2" /></svg>
          </span>
          <div class="detail-info__text">
            <span class="detail-info__label">地址</span>
            <span class="detail-info__value">{{ detail.address || '—' }}</span>
          </div>
        </div>

        <div class="detail-info__card">
          <span class="detail-info__icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></svg>
          </span>
          <div class="detail-info__text">
            <span class="detail-info__label">开放时间</span>
            <span class="detail-info__value">{{ detail.openTime || '—' }}</span>
          </div>
        </div>

        <div class="detail-info__card">
          <span class="detail-info__icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1a2 2 0 0 0 0 6v1a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-1a2 2 0 0 0 0-6z" /></svg>
          </span>
          <div class="detail-info__text">
            <span class="detail-info__label">门票</span>
            <span class="detail-info__value">{{ detail.ticketInfo || '—' }}</span>
          </div>
        </div>

        <div class="detail-info__card">
          <span class="detail-info__icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12h17M12 3.5c2.5 2.6 2.5 14.4 0 17M12 3.5c-2.5 2.6-2.5 14.4 0 17" /></svg>
          </span>
          <div class="detail-info__text">
            <span class="detail-info__label">官网</span>
            <a v-if="detail.website" class="detail-info__value detail-info__link" :href="detail.website" target="_blank" rel="noopener">查看官网</a>
            <span v-else class="detail-info__value">—</span>
          </div>
        </div>
      </section>

      <!-- ================= 3. 画像画廊 ================= -->
      <section v-if="gallery.length" class="sy-container detail-section">
        <SectionTitle
          eyebrow="Gallery"
          variant="on-light"
          decoration
          :title="t('detail.galleryTitle')"
          :desc="t('detail.galleryDesc')"
        />

        <div class="mosaic">
          <button
            v-for="(url, i) in gallery"
            :key="url"
            type="button"
            class="mosaic__cell"
            :aria-label="t('detail.viewImage', { index: i + 1 })"
            @click="openLightbox(i)"
          >
            <img class="mosaic__img" :src="url" alt="" loading="lazy" decoding="async" @error="onImgError" />
            <span class="mosaic__no">{{ String(i + 1).padStart(2, '0') }}</span>
          </button>
        </div>
      </section>

      <!-- ================= 4. 内容区（左右分栏） ================= -->
      <section v-if="detail.description" class="sy-container detail-body">
        <div class="detail-body__main detail-reveal">
          <h2 class="detail-body__title">{{ t('detail.about', { name: detail.name }) }}</h2>
          <p class="detail-intro">{{ detail.description }}</p>

          <div v-if="tags.length" class="detail-tags">
            <span v-for="tag in tags" :key="tag" class="detail-tag">{{ tag }}</span>
          </div>

          <p v-if="detail.website" class="detail-site">
            官网：<a :href="detail.website" target="_blank" rel="noopener">{{ detail.website }}</a>
          </p>
        </div>

        <aside class="detail-body__aside">
          <!-- 天气 -->
          <div v-if="weather" class="panel detail-reveal">
            <h3 class="panel__title">{{ t('detail.weather') }}</h3>
            <div class="weather">
              <span class="weather__icon" :class="`weather__icon--${weather.icon}`">
                <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
                  <circle cx="12" cy="12" r="4" />
                  <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19" />
                </svg>
              </span>
              <div class="weather__info">
                <p class="weather__temp">{{ weather.temp }}<em>℃</em></p>
                <p class="weather__text">{{ weather.city }} · {{ weather.text }}</p>
              </div>
            </div>
            <p class="weather__tip">{{ weather.tip }}</p>
            <p v-if="weatherIsMock" class="panel__note">{{ t('detail.weatherMock') }}</p>
          </div>

          <!-- 位置 -->
          <div class="panel detail-reveal">
            <h3 class="panel__title">{{ t('detail.location') }}</h3>
            <div class="map">
              <span class="map__pin" />
              <span class="map__grid" />
            </div>
            <p class="map__addr">{{ detail.address || t('detail.addressPending') }}</p>
            <a class="map__link sy-btn-text" :href="mapLink" target="_blank" rel="noopener">
              {{ t('detail.openInMap') }}
            </a>
          </div>

          <!-- 周边推荐 -->
          <div v-if="recommends.length" class="panel detail-reveal">
            <h3 class="panel__title">{{ t('detail.sameCity') }}</h3>
            <ul class="reco-list">
              <li v-for="r in recommends" :key="r.id" class="reco-item" @click="goDetail(r.id)">
                <img class="reco-item__img" :src="r.coverImage" alt="" loading="lazy" @error="onImgError" />
                <div class="reco-item__info">
                  <span class="reco-item__name">{{ r.name }}</span>
                  <span class="reco-item__meta">{{ r.type }} · {{ r.score }} 分</span>
                </div>
                <span class="reco-item__arrow">→</span>
              </li>
            </ul>
          </div>
        </aside>

        <GoldButton block class="detail-body__cta detail-reveal" @click="goAssistant">
          {{ t('detail.planNow') }}
          <template #icon-right>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </template>
        </GoldButton>
      </section>

      <!-- ================= 4.5 相关影像 ================= -->
      <section v-if="detail.videoUrl" class="sy-container detail-video-sec">
        <p class="sy-eyebrow">Video</p>
        <h2 class="sy-page-title">相关影像</h2>
        <button type="button" class="detail-video" @click="openVideo">
          <img class="detail-video__poster" :src="detail.coverImage" alt="" loading="lazy" decoding="async" @error="onImgError" />
          <span class="detail-video__play" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          </span>
          <span class="detail-video__text">点击播放</span>
        </button>
      </section>

      <!-- ================= 5. 图片墙（拼图） ================= -->
      <section v-if="recommends.length === 3" class="sy-container detail-section">
        <SectionTitle
          eyebrow="Nearby"
          variant="on-light"
          decoration
          :title="t('detail.sameCityGallery')"
          :desc="t('detail.sameCityGalleryDesc')"
        />

        <div class="puzzle">
          <button
            v-for="(r, i) in recommends"
            :key="r.id"
            type="button"
            class="puzzle__cell"
            :class="`puzzle__cell--${i}`"
            @click="goDetail(r.id)"
          >
            <img class="puzzle__img" :src="r.coverImage" alt="" loading="lazy" decoding="async" @error="onImgError" />
            <span class="puzzle__name">{{ r.name }}</span>
          </button>
        </div>
      </section>

      <!-- ================= 5.5 用户评论（占位） ================= -->
      <section class="sy-container detail-comments">
        <h2 class="detail-body__title">用户评论</h2>
        <p class="detail-comments__empty">暂无评论，登录后可发布</p>
      </section>

      <!-- ================= 6. 底部 CTA ================= -->
      <section class="detail-cta">
        <div class="sy-container detail-cta__inner">
          <p class="detail-cta__eyebrow">Sichuan Awaits</p>
          <h2 class="detail-cta__title">{{ t('detail.ctaTitle') }}</h2>
          <GoldButton size="lg" @click="goAssistant">{{ t('detail.ctaButton') }}</GoldButton>
        </div>
      </section>
    </template>

    <!-- 不存在 -->
    <StateBlock
      v-else-if="!loading"
      state="error"
      error-text="景点信息不存在"
      action-text="返回景点列表"
      :action="() => router.push('/scenic')"
    />

    <!-- ================= Lightbox ================= -->
    <Teleport to="body">
      <transition name="lb">
        <div v-if="lbOpen" class="lightbox" @click.self="closeLightbox">
          <button type="button" class="lightbox__close" :aria-label="t('common.close')" @click="closeLightbox">
            ✕
          </button>

          <button type="button" class="lightbox__nav lightbox__nav--prev" :aria-label="t('detail.prevImage')" @click.stop="lbStep(-1)">
            ‹
          </button>

          <img class="lightbox__img" :src="gallery[lbIndex]" alt="" @click.stop />

          <button type="button" class="lightbox__nav lightbox__nav--next" :aria-label="t('detail.nextImage')" @click.stop="lbStep(1)">
            ›
          </button>

          <p class="lightbox__counter">{{ lbIndex + 1 }} / {{ gallery.length }}</p>
        </div>
      </transition>
    </Teleport>

    <!-- ================= 相关影像弹层 ================= -->
    <Teleport to="body">
      <transition name="lb">
        <div v-if="videoOpen" class="detail-vlightbox" @click.self="videoOpen = false">
          <button type="button" class="detail-vlightbox__close" :aria-label="t('common.close')" @click="videoOpen = false">
            ✕
          </button>
          <div class="detail-vlightbox__frame">
            <iframe :src="videoEmbedUrl" allowfullscreen frameborder="0" title="相关影像" />
          </div>
        </div>
      </transition>
    </Teleport>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;
/* ============================================================
 * 页面外壳
 * ========================================================== */
.detail-page {
  padding: 0;
  background: $color-paper;
}

.detail-loading {
  min-height: 4px;
}

/* ============================================================
 * 1. Hero（100vh）
 * ========================================================== */
.detail-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  height: $hero-detail-height;
  min-height: 520px;
  overflow: hidden;
  isolation: isolate;
}

/* 外层负责滚动视差，内层负责 Ken Burns —— 两层分离，避免 transform 互相覆盖 */
.detail-hero__media {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  will-change: transform;
}

.detail-hero__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  // 轮播：非当前图透明
  opacity: 0;
  // 加载前模糊，加载后清晰
  filter: blur(26px) saturate(1.1);
  transition:
    opacity 1.2s $ease-out-expo,
    filter 1.4s $ease-out-expo,
    transform 1.4s $ease-out-expo;

  &.is-active {
    opacity: 1;
  }

  &.is-loaded {
    filter: blur(0) saturate(1);
    animation: hero-kenburns 26s ease-in-out infinite alternate;
  }
}

@keyframes hero-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.detail-hero__inner {
  position: relative;
  z-index: $z-content;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: clamp(20px, 3vw, 56px);
  width: 100%;
  padding-block: clamp(80px, 16vh, 180px) clamp(56px, 10vh, 120px);
}

.detail-hero__content {
  min-width: 0;
}

/* 面包屑 */
.detail-hero__crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.78);

  a:hover {
    color: $color-gold;
  }
}

.detail-hero__sep {
  color: $color-gold;
}

/* 景点名称：超大字号 + 文字投影 */
.detail-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero-xl;
  line-height: 1;
  letter-spacing: -0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.detail-hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: clamp(16px, 2.4vh, 30px);
  font-family: $font-body;
  font-size: clamp(14px, 1.3vw, 20px);
  letter-spacing: 0.2em;
  color: rgba(245, 241, 232, 0.9);
  text-shadow: $shadow-text-soft;
}

.detail-hero__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: $color-gold;
}

.detail-hero__score {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: $font-en;
  color: $color-gold;
}

/* 右下：收藏 / 分享 */
.detail-hero__actions {
  display: flex;
  flex-shrink: 0;
  gap: 12px;
}

.hero-act {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 22px;
  border: 1px solid rgba(212, 179, 116, 0.7);
  border-radius: $radius-pill;
  background: rgba(20, 26, 34, 0.34);
  backdrop-filter: blur(10px);
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-text-light;
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    color: $color-gold;
    transform: translateY(-2px);
  }

  // 已收藏：金色填充
  &.is-on {
    border-color: $color-gold;
    background: $color-gold;
    color: $color-ink;
  }
}

/* 心形粒子的 canvas（比按钮大一圈，粒子可以飞出去） */
.hero-act__burst {
  position: absolute;
  inset: -20px;
  width: calc(100% + 40px);
  height: calc(100% + 40px);
  pointer-events: none;
}

/* 左下：向下探索 */
.detail-hero__scroll {
  position: absolute;
  left: 50%;
  bottom: clamp(18px, 2.6vh, 34px);
  z-index: $z-content;
  display: flex;
  align-items: center;
  gap: 10px;
  transform: translateX(-50%);
  font-size: $fs-small;
  letter-spacing: 0.28em;
  color: rgba(245, 241, 232, 0.85);
}

.detail-hero__arrow {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid $color-gold-line;
  border-radius: 50%;
  color: $color-gold;
  animation: hero-breathe 2s ease-in-out infinite;
}

@keyframes hero-breathe {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(7px);
    opacity: 1;
  }
}

/* ============================================================
 * 2. 概览栏（sticky）
 * ========================================================== */
.detail-overview {
  position: sticky;
  top: $nav-height;
  z-index: $z-header - 1;
  border-bottom: 1px solid transparent;
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  @include glass(rgba(var(--sy-paper-rgb), 0.62), 18px);

  &.is-stuck {
    border-bottom-color: $color-border-gold;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
    background: rgba(var(--sy-paper-rgb), 0.88);
  }
}

.detail-overview__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: clamp(18px, 3vw, 56px);
  padding-block: clamp(14px, 1.6vw, 22px);
}

.detail-overview__item {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.detail-overview__label {
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: $color-gold;
}

.detail-overview__value {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 20px);
  letter-spacing: 0.06em;
  color: $color-ink;
}

/* 金色分隔线 */
.detail-overview__sep {
  position: absolute;
  top: 50%;
  right: clamp(-28px, -1.5vw, -9px);
  width: 1px;
  height: 16px;
  transform: translateY(-50%);
  background: linear-gradient(
    180deg,
    rgba(212, 179, 116, 0) 0%,
    $color-gold 50%,
    rgba(212, 179, 116, 0) 100%
  );
}

/* ============================================================
 * 3. 画廊（不等大网格）
 * ========================================================== */
.detail-section {
  padding-block: clamp(48px, 7vw, 110px);
}

.mosaic {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: clamp(10px, 1.2vw, 18px);
}

.mosaic__cell {
  position: relative;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  border: 1px solid rgba(212, 179, 116, 0.32);
  border-radius: $radius-lg;
  background: rgba(14, 42, 71, 0.08);
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    box-shadow: $shadow-lg;

    .mosaic__img {
      transform: scale(1.06);
    }
  }
}

.mosaic__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.mosaic__no {
  position: absolute;
  left: 12px;
  bottom: 10px;
  font-family: $font-en;
  font-size: 12px;
  letter-spacing: 0.2em;
  color: $color-gold;
  text-shadow: $shadow-text-soft;
}

/* ============================================================
 * 4. 内容区（左右分栏 60 / 40）
 * ========================================================== */
.detail-body {
  padding-bottom: clamp(48px, 7vw, 110px);
}

.detail-body__main {
  min-width: 0;
}

.detail-body__title {
  position: relative;
  padding-left: 16px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(26px, 3vw, 46px);
  letter-spacing: 0.06em;
  color: $color-ink;

  // 章节标题左侧金色短竖条
  &::before {
    content: '';
    position: absolute;
    top: 0.16em;
    left: 0;
    width: 4px;
    height: 0.84em;
    border-radius: 2px;
    background: linear-gradient(180deg, $color-gold, rgba(212, 179, 116, 0.35));
  }
}

/* 正文：大字排版 + 首字下沉 */
.detail-intro {
  margin-top: 24px;
  font-family: $font-body;
  font-size: $fs-body-lg;
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.84);
  text-align: justify;

  &::first-letter {
    float: left;
    margin: 0.06em 12px 0 0;
    font-family: $font-title;
    font-weight: 900;
    font-size: 3.6em;
    line-height: 0.86;
    color: var(--sy-accent);
  }
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: clamp(20px, 2.6vw, 34px);
  clear: both;
}

.detail-tag {
  padding: 4px 14px;
  border: 1px solid rgba(212, 179, 116, 0.55);
  border-radius: $radius-pill;
  font-size: $fs-tiny;
  letter-spacing: 0.08em;
  color: $color-bamboo;
}

/* 实用信息：图标卡片网格 */
.detail-info {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: clamp(12px, 1.6vw, 22px);
  padding-block: clamp(32px, 5vw, 64px) clamp(8px, 1vw, 16px);
}

.detail-info__card {
  @include sy-card-gold;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 18px clamp(16px, 1.6vw, 22px);
}

.detail-info__icon {
  @include flex-center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1px solid rgba(122, 90, 30, 0.5);
  color: #7a5a1e;
  background: rgba(122, 90, 30, 0.12);
}

.detail-info__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.detail-info__label {
  font-size: $fs-tiny;
  letter-spacing: 0.16em;
  color: #8a6a1f;
}

.detail-info__value {
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-caption;
  line-height: 1.6;
  color: $color-text-dark;
  word-break: break-word;
}

.detail-info__link {
  color: var(--sy-accent);
  text-decoration: underline;
  text-underline-offset: 4px;

  &:hover {
    color: $color-gold;
  }
}

/* 用户评论（占位） */
.detail-comments {
  padding-block: clamp(40px, 6vw, 80px);
}

.detail-comments__empty {
  margin-top: 16px;
  padding: clamp(24px, 3vw, 40px);
  border: 1px dashed $color-border-gold;
  border-radius: $radius-lg;
  text-align: center;
  font-family: $font-body;
  font-size: $fs-body;
  color: rgba(var(--sy-text-rgb), 0.5);
}

@include responsive(md) {
  .detail-info {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* ---------------- 右侧栏 ---------------- */
.detail-body__aside {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: clamp(16px, 1.8vw, 26px);
  margin-top: clamp(32px, 4vw, 56px);
}

.detail-body__cta {
  margin-top: clamp(24px, 3vw, 40px);
}

.panel {
  @include sy-card-gold;
}

.panel__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.12em;
  color: var(--sy-accent);

  &::before {
    content: '';
    position: absolute;
    top: 0.14em;
    left: 0;
    width: 2px;
    height: 0.86em;
    background: $color-gold;
  }
}

.panel__note {
  margin-top: 10px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.42);
}

/* 天气 */
.weather {
  display: flex;
  align-items: center;
  gap: 16px;
}

.weather__icon {
  @include flex-center;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  border: 1px solid rgba(122, 90, 30, 0.5);
  color: #7a5a1e;
  background: rgba(122, 90, 30, 0.12);
}

.weather__info {
  min-width: 0;
}

.weather__temp {
  font-family: $font-en;
  font-size: clamp(26px, 2.4vw, 38px);
  line-height: 1;
  color: $color-ink;

  em {
    margin-left: 2px;
    font-size: 0.42em;
    font-style: normal;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.weather__text {
  margin-top: 4px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(var(--sy-text-rgb), 0.6);
}

.weather__tip {
  margin-top: 12px;
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.55);
}

/* 位置：装饰性小地图（内嵌真实地图需高德 JS Key） */
.map {
  position: relative;
  height: clamp(110px, 12vw, 160px);
  margin-bottom: 14px;
  border-radius: $radius-md;
  overflow: hidden;
  background: linear-gradient(160deg, rgba(14, 42, 71, 0.1), rgba(212, 179, 116, 0.14));
}

.map__grid {
  position: absolute;
  inset: 0;
  background-image:
    repeating-linear-gradient(0deg, rgba(14, 42, 71, 0.14) 0 1px, transparent 1px 28px),
    repeating-linear-gradient(90deg, rgba(14, 42, 71, 0.14) 0 1px, transparent 1px 28px);
}

.map__pin {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 14px;
  height: 14px;
  border-radius: 50% 50% 50% 0;
  transform: translate(-50%, -50%) rotate(-45deg);
  background: $color-primary;
  border: 1.5px solid $color-gold;
  box-shadow: 0 4px 14px rgba(122, 26, 34, 0.4);

  &::after {
    content: '';
    position: absolute;
    inset: -10px;
    border-radius: 50%;
    border: 1px solid rgba(212, 179, 116, 0.7);
    animation: map-pulse 2.4s ease-out infinite;
  }
}

@keyframes map-pulse {
  0% {
    transform: scale(0.6);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.map__addr {
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.8;
  color: rgba(var(--sy-text-rgb), 0.7);
}

.map__link {
  display: inline-block;
  margin-top: 10px;
  color: var(--sy-accent);
}

/* 同城推荐 */
.reco-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reco-item {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform $duration-normal $ease-out-expo;

  &:hover {
    transform: translateX(4px);

    .reco-item__name {
      color: var(--sy-accent);
    }
  }
}

.reco-item__img {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: $radius-md;
  border: 1px solid rgba(212, 179, 116, 0.4);
}

.reco-item__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.reco-item__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  color: $color-text-dark;
  transition: color $duration-normal $ease-out-expo;
  @include text-ellipsis(1);
}

.reco-item__meta {
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.5);
}

.reco-item__arrow {
  margin-left: auto;
  color: var(--sy-accent);
}

/* ============================================================
 * 5. 图片墙（拼图 grid-area）
 * ========================================================== */
.puzzle {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(2, clamp(140px, 15vw, 230px));
  gap: clamp(10px, 1.2vw, 18px);
}

.puzzle__cell {
  position: relative;
  overflow: hidden;
  border-radius: $radius-lg;
  border: 1px solid rgba(212, 179, 116, 0.3);
  background: rgba(14, 42, 71, 0.08);
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;
  will-change: transform;

  // 左侧主图占 2×2，右侧两张各占一格
  &--0 {
    grid-area: 1 / 1 / 3 / 3;
  }

  &--1 {
    grid-area: 1 / 3 / 2 / 4;
  }

  &--2 {
    grid-area: 2 / 3 / 3 / 4;
  }

  &:hover {
    border-color: $color-gold;
    box-shadow: $shadow-lg;

    .puzzle__img {
      transform: scale(1.07);
    }
  }
}

.puzzle__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.puzzle__name {
  position: absolute;
  left: 14px;
  bottom: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.1em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* ============================================================
 * 6. 底部 CTA
 * ========================================================== */
.detail-cta {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(212, 179, 116, 0.22) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(120deg, $color-ink 0%, #16324f 55%, #1d2436 100%);

  // 蜀锦纹样
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: $pattern-brocade;
    opacity: 0.07;
    mix-blend-mode: overlay;
    pointer-events: none;
  }
}

.detail-cta__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(16px, 2vw, 28px);
  padding-block: clamp(56px, 9vw, 130px);
  text-align: center;
}

.detail-cta__eyebrow {
  font-family: $font-en-body;
  font-size: $fs-eyebrow;
  letter-spacing: 0.34em;
  text-transform: uppercase;
  color: $color-gold;
}

.detail-cta__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(34px, 5vw, 84px);
  line-height: 1.05;
  letter-spacing: 0.12em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* ============================================================
 * 找不到 / Lightbox
 * ========================================================== */
.detail-missing {
  padding-block: clamp(64px, 12vw, 160px);
}

.lightbox {
  position: fixed;
  inset: 0;
  z-index: $z-loader;
  display: grid;
  place-items: center;
  background: rgba(8, 12, 18, 0.94);
  backdrop-filter: blur(6px);
}

.lightbox__img {
  max-width: 92vw;
  max-height: 82vh;
  object-fit: contain;
  border-radius: $radius-md;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6);
}

.lightbox__close {
  position: absolute;
  top: clamp(16px, 3vh, 40px);
  right: clamp(16px, 3vw, 48px);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(212, 179, 116, 0.6);
  color: $color-gold;
  font-size: 18px;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
}

.lightbox__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px solid rgba(212, 179, 116, 0.5);
  color: $color-gold;
  font-size: 30px;
  line-height: 1;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }

  &--prev {
    left: clamp(10px, 3vw, 48px);
  }

  &--next {
    right: clamp(10px, 3vw, 48px);
  }
}

.lightbox__counter {
  position: absolute;
  bottom: clamp(16px, 3vh, 40px);
  left: 50%;
  transform: translateX(-50%);
  font-family: $font-en;
  font-size: $fs-small;
  letter-spacing: 0.24em;
  color: rgba(245, 241, 232, 0.75);
}

.lb-enter-active,
.lb-leave-active {
  transition: opacity $duration-normal $ease-out-expo;
}

.lb-enter-from,
.lb-leave-to {
  opacity: 0;
}

/* ============================================================
 * 相关影像（视频）：卡片 + 弹层
 * ========================================================== */
.detail-video-sec {
  padding-block: clamp(28px, 3.6vw, 48px) clamp(12px, 1.6vw, 24px);
}

.detail-video {
  position: relative;
  display: block;
  width: 70%;
  margin-inline: auto;
  margin-top: clamp(20px, 2.6vw, 36px);
  padding: 0;
  overflow: hidden;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.66);
  box-shadow: $shadow-md;
  cursor: pointer;
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    box-shadow: $shadow-lg;

    .detail-video__poster {
      transform: scale(1.05);
    }

    .detail-video__play {
      transform: translate(-50%, -50%) scale(1.08);
      background: $color-gold;
      color: $color-ink;
    }
  }
}

.detail-video__poster {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.detail-video__play {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  transform: translate(-50%, -50%);
  border: 1px solid rgba(212, 179, 116, 0.8);
  border-radius: 50%;
  background: rgba(20, 26, 34, 0.5);
  backdrop-filter: blur(6px);
  color: $color-gold;
  transition:
    transform $duration-normal $ease-out-expo,
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo;
}

.detail-video__text {
  position: absolute;
  left: 14px;
  bottom: 12px;
  font-size: $fs-small;
  letter-spacing: 0.14em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* 视频弹层 */
.detail-vlightbox {
  position: fixed;
  inset: 0;
  z-index: $z-loader;
  display: grid;
  place-items: center;
  padding: clamp(16px, 4vw, 64px);
  background: rgba(8, 12, 18, 0.94);
  backdrop-filter: blur(6px);
}

.detail-vlightbox__frame {
  width: min(1080px, 92vw);
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-md;
  background: #000;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6);

  iframe {
    display: block;
    width: 100%;
    height: 100%;
    border: 0;
  }
}

.detail-vlightbox__close {
  position: absolute;
  top: clamp(16px, 3vh, 40px);
  right: clamp(16px, 3vw, 48px);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(212, 179, 116, 0.6);
  color: $color-gold;
  font-size: 18px;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
}

/* ============================================================
 * 响应式：大屏左右分栏 / 小屏上下堆叠
 * ========================================================== */
@include responsive(md) {
  .detail-hero {
    height: 88vh;
  }

  .detail-hero__inner {
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-end;
    gap: 20px;
    padding-block: 96px 88px;
  }

  .detail-hero__scroll-text,
  .detail-hero__actions span {
    display: none;
  }

  /* 拼图：两行两列 */
  .puzzle {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(2, clamp(130px, 36vw, 200px));
  }

  .puzzle__cell {
    &--0 {
      grid-area: 1 / 1 / 3 / 2;
    }

    &--1 {
      grid-area: 1 / 2 / 2 / 3;
    }

    &--2 {
      grid-area: 2 / 2 / 3 / 3;
    }
  }

  .detail-video {
    width: 100%;
  }

  .lightbox__nav {
    width: 44px;
    height: 44px;
    font-size: 24px;
  }
}

/* 官网链接 */
.detail-site {
  margin-top: 18px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.6);

  a {
    color: var(--sy-accent);
    text-decoration: underline;
    text-underline-offset: 4px;
    word-break: break-all;

    &:hover {
      color: $color-gold;
    }
  }
}

</style>
