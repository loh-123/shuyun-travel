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
import { useUserStore } from '@/stores/user'
import { getLenis } from '@/composables/useLenis'
import GoldButton from '@/components/common/GoldButton.vue'
import SectionTitle from '@/components/common/SectionTitle.vue'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
interface ImageItem {
  id: number
  imageUrl: string
}

interface SpotVO {
  id: number
  name: string
  cityId?: number
  cityName?: string
  type?: string
  level?: string
  address?: string
  longitude?: number | string | null
  latitude?: number | string | null
  coverImage?: string
  description?: string
  openTime?: string
  ticketInfo?: string
  score?: number
  tags?: string
  images?: ImageItem[]
  recommends?: SpotVO[]
}

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

const detail = ref<SpotVO | null>(null)
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

/* ------------------------------------------------------------
 * 计算属性
 * ---------------------------------------------------------- */
/** 画廊：封面 + 图片表（去重、去空） */
const gallery = computed<string[]>(() => {
  const d = detail.value
  if (!d) {
    return []
  }
  const urls = [d.coverImage, ...(d.images ?? []).map((i) => i.imageUrl)].filter(Boolean) as string[]
  return Array.from(new Set(urls))
})

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
    { label: '评分', value: d.score != null ? `${d.score}` : '—' },
    { label: '等级', value: d.level || '—' },
    { label: '开放时间', value: d.openTime || '—' },
    { label: '门票', value: d.ticketInfo || '—' },
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

/* ------------------------------------------------------------
 * 数据加载
 * ---------------------------------------------------------- */
async function loadDetail() {
  loading.value = true
  notFound.value = false
  try {
    const res = await request.get(`/scenic/${route.params.id}`, { silent: true })
    detail.value = res.data ?? null
    notFound.value = !detail.value
    if (detail.value) {
      await Promise.all([checkCollected(), loadWeather(detail.value.cityName || '成都')])
    }
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
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
    ElMessage.warning('请先登录后再收藏')
    router.push('/login')
    return
  }

  try {
    if (collected.value) {
      await request.delete('/user/collection', { params: { targetType: 'SCENIC', targetId: d.id } })
      collected.value = false
      ElMessage.success('已取消收藏')
    } else {
      await request.post('/user/collection', { targetType: 'SCENIC', targetId: d.id })
      collected.value = true
      burstGold() // 心形粒子
      ElMessage.success('已加入收藏')
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
    ElMessage.success('链接已复制，去分享吧')
  } catch {
    ElMessage.info('可复制浏览器地址栏链接分享')
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
        ctx.fillStyle = '#C9A86A'
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

function lbStep(delta: number) {
  const n = gallery.value.length
  if (!n) {
    return
  }
  lbIndex.value = (lbIndex.value + delta + n) % n
}

function onKeydown(e: KeyboardEvent) {
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
    <div v-loading="loading" class="detail-loading" />

    <template v-if="detail">
      <!-- ================= 1. Hero（100vh） ================= -->
      <header ref="heroEl" class="detail-hero">
        <div class="detail-hero__media">
          <img
            class="detail-hero__img"
            :class="{ 'is-loaded': heroLoaded }"
            :src="detail.coverImage"
            :alt="detail.name"
            fetchpriority="high"
            decoding="async"
            @load="heroLoaded = true"
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
              <router-link to="/">首页</router-link>
              <span class="detail-hero__sep">/</span>
              <router-link to="/scenic">景点</router-link>
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
              <span>{{ collected ? '已收藏' : '收藏' }}</span>
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
              <span>分享</span>
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
          <span class="detail-hero__scroll-text">向下探索</span>
        </button>
      </header>

      <!-- ================= 2. 概览栏（sticky） ================= -->
      <div ref="overviewEl" class="detail-overview" :class="{ 'is-stuck': stuck }">
        <div class="sy-container detail-overview__inner">
          <div v-for="(item, i) in overviewItems" :key="item.label" class="detail-overview__item">
            <span class="detail-overview__label">{{ item.label }}</span>
            <span class="detail-overview__value">{{ item.value }}</span>
            <i v-if="i < overviewItems.length - 1" class="detail-overview__sep" />
          </div>
        </div>
      </div>

      <!-- ================= 3. 画像画廊 ================= -->
      <section v-if="gallery.length" class="sy-container detail-section">
        <SectionTitle
          eyebrow="Gallery"
          variant="on-light"
          decoration
          title="影像"
          desc="在抵达之前，先看一遍它的样子。"
        />

        <div class="mosaic">
          <button
            v-for="(url, i) in gallery"
            :key="url"
            type="button"
            class="mosaic__cell"
            :aria-label="`查看第 ${i + 1} 张图片`"
            @click="openLightbox(i)"
          >
            <img class="mosaic__img" :src="url" alt="" loading="lazy" decoding="async" />
            <span class="mosaic__no">{{ String(i + 1).padStart(2, '0') }}</span>
          </button>
        </div>
      </section>

      <!-- ================= 4. 内容区（左右分栏） ================= -->
      <section v-if="detail.description" class="sy-container detail-body">
        <div class="detail-body__main detail-reveal">
          <h2 class="detail-body__title">关于 {{ detail.name }}</h2>
          <p class="detail-intro">{{ detail.description }}</p>

          <div v-if="tags.length" class="detail-tags">
            <span v-for="t in tags" :key="t" class="detail-tag">{{ t }}</span>
          </div>
        </div>

        <aside class="detail-body__aside">
          <!-- 天气 -->
          <div v-if="weather" class="panel detail-reveal">
            <h3 class="panel__title">实时天气</h3>
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
            <p v-if="weatherIsMock" class="panel__note">示例数据（后端 /api/weather 待接入）</p>
          </div>

          <!-- 位置 -->
          <div class="panel detail-reveal">
            <h3 class="panel__title">位置</h3>
            <div class="map">
              <span class="map__pin" />
              <span class="map__grid" />
            </div>
            <p class="map__addr">{{ detail.address || '地址待补充' }}</p>
            <a class="map__link sy-btn-text" :href="mapLink" target="_blank" rel="noopener">
              在地图 App 中打开
            </a>
          </div>

          <!-- 周边推荐 -->
          <div v-if="recommends.length" class="panel detail-reveal">
            <h3 class="panel__title">同城推荐</h3>
            <ul class="reco-list">
              <li v-for="r in recommends" :key="r.id" class="reco-item" @click="goDetail(r.id)">
                <img class="reco-item__img" :src="r.coverImage" alt="" loading="lazy" />
                <div class="reco-item__info">
                  <span class="reco-item__name">{{ r.name }}</span>
                  <span class="reco-item__meta">{{ r.type }} · {{ r.score }} 分</span>
                </div>
                <span class="reco-item__arrow">→</span>
              </li>
            </ul>
          </div>

          <GoldButton block class="detail-reveal" @click="goAssistant">
            立即规划行程
            <template #icon-right>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </template>
          </GoldButton>
        </aside>
      </section>

      <!-- ================= 5. 图片墙（拼图） ================= -->
      <section v-if="recommends.length === 3" class="sy-container detail-section">
        <SectionTitle
          eyebrow="Nearby"
          variant="on-light"
          decoration
          title="同城影像"
          desc="同一座城的另外三处风景，顺路就去看看。"
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
            <img class="puzzle__img" :src="r.coverImage" alt="" loading="lazy" decoding="async" />
            <span class="puzzle__name">{{ r.name }}</span>
          </button>
        </div>
      </section>

      <!-- ================= 6. 底部 CTA ================= -->
      <section class="detail-cta">
        <div class="sy-container detail-cta__inner">
          <p class="detail-cta__eyebrow">Sichuan Awaits</p>
          <h2 class="detail-cta__title">去蜀地，见山水</h2>
          <GoldButton size="lg" @click="goAssistant">让 AI 帮你规划</GoldButton>
        </div>
      </section>
    </template>

    <!-- 不存在 -->
    <div v-else-if="!loading" class="detail-missing">
      <el-empty description="景点不存在，或需要登录后查看">
        <GoldButton @click="router.push('/scenic')">返回景点列表</GoldButton>
      </el-empty>
    </div>

    <!-- ================= Lightbox ================= -->
    <Teleport to="body">
      <transition name="lb">
        <div v-if="lbOpen" class="lightbox" @click.self="closeLightbox">
          <button type="button" class="lightbox__close" aria-label="关闭" @click="closeLightbox">
            ✕
          </button>

          <button type="button" class="lightbox__nav lightbox__nav--prev" aria-label="上一张" @click.stop="lbStep(-1)">
            ‹
          </button>

          <img class="lightbox__img" :src="gallery[lbIndex]" alt="" @click.stop />

          <button type="button" class="lightbox__nav lightbox__nav--next" aria-label="下一张" @click.stop="lbStep(1)">
            ›
          </button>

          <p class="lightbox__counter">{{ lbIndex + 1 }} / {{ gallery.length }}</p>
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
  height: 100vh;
  min-height: 560px;
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
  width: 100%;
  height: 100%;
  object-fit: cover;
  // 加载前模糊，加载后清晰
  filter: blur(26px) saturate(1.1);
  transition:
    filter 1.4s $ease-out-expo,
    transform 1.4s $ease-out-expo;

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
  font-size: $fs-hero;
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
  border: 1px solid rgba(201, 168, 106, 0.7);
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
  top: 68px;
  z-index: $z-header - 1;
  border-bottom: 1px solid transparent;
  transition:
    background-color $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  @include glass(rgba(232, 227, 216, 0.62), 18px);

  &.is-stuck {
    border-bottom-color: $color-border-gold;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
    background: rgba(232, 227, 216, 0.88);
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
    rgba(201, 168, 106, 0) 0%,
    $color-gold 50%,
    rgba(201, 168, 106, 0) 100%
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
  grid-template-columns: repeat(4, 1fr);
  grid-auto-rows: clamp(120px, 13vw, 220px);
  gap: clamp(10px, 1.2vw, 18px);
  grid-auto-flow: dense;
}

.mosaic__cell {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.32);
  border-radius: $radius-lg;
  background: rgba(14, 42, 71, 0.08);
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  // 第一张占 2×2，第四张占两列，其余各一格
  &:nth-child(1) {
    grid-column: span 2;
    grid-row: span 2;
  }

  &:nth-child(4) {
    grid-column: span 2;
  }

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
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: clamp(32px, 5vw, 90px);
  align-items: start;
  padding-bottom: clamp(48px, 7vw, 110px);
}

.detail-body__main {
  min-width: 0;
}

.detail-body__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(26px, 3vw, 46px);
  letter-spacing: 0.06em;
  color: $color-ink;
}

/* 正文：大字排版 + 首字下沉 */
.detail-intro {
  margin-top: clamp(20px, 2.6vw, 36px);
  font-family: $font-body;
  font-size: clamp(17px, 1.5vw, 22px);
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.84);
  text-align: justify;

  &::first-letter {
    float: left;
    margin: 0.06em 12px 0 0;
    font-family: $font-title;
    font-weight: 900;
    font-size: 3.6em;
    line-height: 0.86;
    color: $color-primary;
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
  border: 1px solid rgba(201, 168, 106, 0.55);
  border-radius: $radius-pill;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-bamboo;
}

/* ---------------- 右侧栏 ---------------- */
.detail-body__aside {
  position: sticky;
  top: 150px; // 让开导航栏 68 + 概览栏
  display: flex;
  flex-direction: column;
  gap: clamp(16px, 1.8vw, 26px);
}

.panel {
  padding: clamp(18px, 2vw, 28px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: $shadow-md;
}

.panel__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.2vw, 18px);
  letter-spacing: 0.12em;
  color: $color-primary;

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
  color: rgba(26, 26, 26, 0.42);
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
  border: 1px solid rgba(201, 168, 106, 0.5);
  color: $color-gold;
  background: rgba(201, 168, 106, 0.1);
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
    color: rgba(26, 26, 26, 0.5);
  }
}

.weather__text {
  margin-top: 4px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(26, 26, 26, 0.6);
}

.weather__tip {
  margin-top: 12px;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.55);
}

/* 位置：装饰性小地图（内嵌真实地图需高德 JS Key） */
.map {
  position: relative;
  height: clamp(110px, 12vw, 160px);
  margin-bottom: 14px;
  border-radius: $radius-md;
  overflow: hidden;
  background: linear-gradient(160deg, rgba(14, 42, 71, 0.1), rgba(201, 168, 106, 0.14));
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
  box-shadow: 0 4px 14px rgba(140, 31, 40, 0.4);

  &::after {
    content: '';
    position: absolute;
    inset: -10px;
    border-radius: 50%;
    border: 1px solid rgba(201, 168, 106, 0.7);
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
  color: rgba(26, 26, 26, 0.7);
}

.map__link {
  display: inline-block;
  margin-top: 10px;
  color: $color-primary;
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
      color: $color-primary;
    }
  }
}

.reco-item__img {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: $radius-md;
  border: 1px solid rgba(201, 168, 106, 0.4);
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
  color: rgba(26, 26, 26, 0.5);
}

.reco-item__arrow {
  margin-left: auto;
  color: $color-gold;
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
  border: 1px solid rgba(201, 168, 106, 0.3);
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
    radial-gradient(120% 130% at 80% 0%, rgba(201, 168, 106, 0.22) 0%, rgba(14, 42, 71, 0) 60%),
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
  border: 1px solid rgba(201, 168, 106, 0.6);
  color: $color-gold;
  font-size: 18px;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(201, 168, 106, 0.16);
  }
}

.lightbox__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px solid rgba(201, 168, 106, 0.5);
  color: $color-gold;
  font-size: 30px;
  line-height: 1;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(201, 168, 106, 0.16);
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
 * 响应式：大屏左右分栏 / 小屏上下堆叠
 * ========================================================== */
@include responsive(lg) {
  .detail-body {
    grid-template-columns: 1fr;
  }

  .detail-body__aside {
    position: static;
  }
}

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

  /* 画廊：单列 */
  .mosaic {
    grid-template-columns: 1fr;
    grid-auto-rows: clamp(150px, 42vw, 240px);
  }

  .mosaic__cell:nth-child(1),
  .mosaic__cell:nth-child(4) {
    grid-column: span 1;
    grid-row: span 1;
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

  .lightbox__nav {
    width: 44px;
    height: 44px;
    font-size: 24px;
  }
}

</style>
