<script setup lang="ts">
/**
 * 文化详情
 * - 全屏 Hero（大标题 + 分类 / 标签）
 * - 内容区 65 / 35：左侧长文（导语金色底纹、大字号、行高 1.9、中间插配图），
 *   右侧视频（Lightbox 内嵌）、相关景点、传承人、标签云
 * - 底部：同分类其他文化推荐 + 「让 AI 带你了解」金色按钮
 * 样式全部复用公共设计系统（variables / mixins / GoldButton / GhostButton / CultureIcon）
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

import request from '@/api/request'
import { getCultureDetail, listCulture, type CultureDetailVO, type CultureVO } from '@/api/culture'
import { listAllPerson, listPersonByCulture, type CulturePersonVO } from '@/api/person'
import { useUserStore } from '@/stores/user'
import GoldButton from '@/components/common/GoldButton.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import GhostButton from '@/components/common/GhostButton.vue'
import PersonCards, { type PersonCardItem } from '@/components/common/PersonCards.vue'
import { t } from '@/i18n'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
// 类型统一来自 @/api/culture（CultureDetailVO / CultureVO）

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const rootEl = ref<HTMLElement | null>(null)
const heroEl = ref<HTMLElement | null>(null)

const detail = ref<CultureDetailVO | null>(null)
const all = ref<CultureVO[]>([])
const loading = ref(true)
const notFound = ref(false)

/** 相关传承人（按 culture_id 查询 culture_person 表） */
const relatedPersons = ref<CulturePersonVO[]>([])

/** 传承人姓名 -> id 映射（用于把纯文本人物名解析为可跳转的传承人） */
const personMap = ref<Record<string, number>>({})

/** 视频 Lightbox */
const videoOpen = ref(false)

/* ------------------------------------------------------------
 * 计算属性
 * ---------------------------------------------------------- */
/** 正文段落 */
const paragraphs = computed(() =>
  (detail.value?.content || '')
    .split('\n')
    .map((p) => p.trim())
    .filter(Boolean),
)

const tags = computed(() =>
  (detail.value?.tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean),
)

/** 图片集（已是 /images/culture/... 完整路径） */
const cultureImages = computed(() => (detail.value?.images ?? []).filter(Boolean) as string[])

/** 逗号分隔字符串 → 数组 */
function splitList(text?: string) {
  return (text || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 相关景点（名称，来自 relatedScenic） */
const relatedScenicList = computed(() => splitList(detail.value?.relatedScenic))
/** 相关人物（来自 relatedPerson） */
const relatedPersonList = computed(() => splitList(detail.value?.relatedPerson))

/**
 * 合并后的「相关人物 / 传承人」列表：
 * 结构化传承人（culture_person 表，带头像 / 简介 / 可跳转）优先，
 * 再补上纯文本的相关人物名（按姓名映射到传承人 id，便于点击查看详情），按姓名去重。
 */
const mergedPeople = computed<PersonCardItem[]>(() => {
  const list: PersonCardItem[] = []
  const seen = new Set<string>()
  const push = (name: string, id?: number | null, avatar?: string, brief?: string) => {
    const key = (name || '').trim()
    if (!key || seen.has(key)) {
      return
    }
    seen.add(key)
    list.push({ id: id ?? personMap.value[key] ?? null, name: key, avatar, brief })
  }
  for (const p of relatedPersons.value) {
    push(p.name, p.id, p.avatar, p.brief)
  }
  for (const name of relatedPersonList.value) {
    push(name, null, undefined, '非遗传承人')
  }
  return list
})

/** 同分类的其他文化（最多 3 条） */
const sameCategory = computed(() => {
  const cur = detail.value
  if (!cur) {
    return []
  }
  return all.value.filter((c) => c.category === cur.category && c.id !== cur.id).slice(0, 3)
})

/** 卡片简介：取 summary，截断 150 字 */
function introOf(item: CultureVO) {
  const text = (item.summary || '').replace(/\n/g, '')
  return text.length > 150 ? `${text.slice(0, 150)}…` : text
}

/**
 * 视频嵌入地址：
 * - 已是 player.bilibili.com / youtube embed → 原样
 * - B站普通视频页 → https://player.bilibili.com/player.html?bvid=BVxxx
 * - YouTube watch → embed
 * - 其它 → 空（由调用方新窗口打开）
 */
const embedUrl = computed(() => {
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

/** 图片兜底占位图 */
const IMG_FALLBACK =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="450"><rect width="100%" height="100%" fill="#F0EBE0"/><text x="50%" y="50%" fill="#D4B374" font-size="60" font-family="serif" text-anchor="middle" dominant-baseline="middle">蜀</text></svg>',
  )

/** 图片加载失败：换成占位图（每张只处理一次） */
function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  if (!img || img.dataset.fallback === '1') {
    return
  }
  img.dataset.fallback = '1'
  img.src = IMG_FALLBACK
}

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadAll() {
  try {
    const data = await listCulture()
    all.value = Array.isArray(data) ? data : []
  } catch {
    all.value = []
  }
}

async function loadDetail(id: number) {
  loading.value = true
  notFound.value = false
  try {
    const data = await getCultureDetail(id)
    detail.value = data ?? null
    notFound.value = !detail.value
    if (detail.value) {
      recordHistory()
      await loadRelatedPersons()
    }
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

/**
 * 加载全部传承人并建立「姓名 -> id」映射，用于把纯文本人物名解析为可跳转的传承人
 */
async function loadPersonMap() {
  try {
    const list = await listAllPerson(200)
    const map: Record<string, number> = {}
    for (const p of Array.isArray(list) ? list : []) {
      if (p?.name) {
        map[p.name] = p.id
      }
    }
    personMap.value = map
  } catch {
    personMap.value = {}
  }
}

/**
 * 加载同文化的传承人（culture_person 表），失败静默
 */
async function loadRelatedPersons() {
  const id = detail.value?.id
  if (!id) {
    relatedPersons.value = []
    return
  }
  try {
    const list = await listPersonByCulture(id)
    relatedPersons.value = Array.isArray(list) ? list : []
  } catch {
    relatedPersons.value = []
  }
}

/**
 * 记录浏览足迹（未登录不记录；失败静默）
 */
function recordHistory() {
  if (!userStore.isLogin || !detail.value) {
    return
  }
  request
    .post('/user/history', { targetType: 'CULTURE', targetId: detail.value.id }, { silent: true })
    .catch(() => {
      // 足迹记录失败不影响浏览
    })
}

/* ------------------------------------------------------------
 * 交互
 * ---------------------------------------------------------- */
function openVideo() {
  const url = detail.value?.videoUrl
  if (!url) {
    return
  }
  if (embedUrl.value) {
    videoOpen.value = true
  } else {
    // 未配置可内嵌源时，直接跳到外部页面
    window.open(url, '_blank', 'noopener')
  }
}

/* ---------- 相关景点 / 相关人物 ---------- */
/** 相关景点：跳景点列表并按名称搜索 */
function goScenicByName(name: string) {
  router.push({ path: '/scenic', query: { keyword: name } })
}

function goCulture(id: number) {
  router.push(`/culture/${id}`)
}

function goAssistant() {
  router.push('/assistant')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && videoOpen.value) {
    videoOpen.value = false
  }
}

// 打开视频时锁滚动
watch(videoOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

/* ------------------------------------------------------------
 * 动效
 * ---------------------------------------------------------- */
let ctx: gsap.Context | null = null

function setupAnimations() {
  ctx?.revert()
  ctx = gsap.context(() => {
    if (heroEl.value) {
      gsap.to('.cd-hero__content', {
        yPercent: -26,
        opacity: 0,
        ease: 'none',
        scrollTrigger: { trigger: heroEl.value, start: 'top top', end: 'bottom top', scrub: true },
      })
    }

    gsap.from('.cd-reveal', {
      y: 36,
      opacity: 0,
      duration: 0.9,
      stagger: 0.1,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.cd-body', start: 'top 72%', once: true },
    })

    gsap.from('.cd-p, .cd-figure', {
      y: 30,
      opacity: 0,
      duration: 0.8,
      stagger: 0.08,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.cd-article', start: 'top 78%', once: true },
    })

    gsap.from('.cd-reco__card', {
      y: 40,
      opacity: 0,
      duration: 0.9,
      stagger: 0.12,
      ease: 'power3.out',
      scrollTrigger: { trigger: '.cd-reco', start: 'top 80%', once: true },
    })
  }, rootEl.value ?? undefined)
  ScrollTrigger.refresh()
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  await Promise.all([loadAll(), loadPersonMap(), loadDetail(Number(route.params.id))])
  await nextTick()
  setupAnimations()
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  ctx?.revert()
  ctx = null
})

// 切换条目（同分类推荐点击）时重新加载
watch(
  () => route.params.id,
  (id) => {
    if (id) {
      window.scrollTo({ top: 0 })
      loadDetail(Number(id)).then(() => nextTick().then(setupAnimations))
    }
  },
)
</script>

<template>
  <section ref="rootEl" class="cd-page">
    <StateBlock v-if="loading" state="loading" />

    <template v-if="detail">
      <!-- ================= 1. 全屏 Hero ================= -->
      <header ref="heroEl" class="cd-hero">
        <img
          class="cd-hero__bg"
          :src="detail.coverImage"
          :alt="detail.name"
          fetchpriority="high"
          decoding="async"
          @error="onImgError"
        />

        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container cd-hero__content">
          <nav class="cd-hero__crumb">
            <router-link to="/">{{ t('nav.home') }}</router-link>
            <span class="cd-hero__sep">/</span>
            <router-link to="/culture">{{ t('nav.culture') }}</router-link>
            <span class="cd-hero__sep">/</span>
            <span>{{ detail.name }}</span>
          </nav>

          <div class="cd-hero__meta">
            <CultureIcon class="cd-hero__icon" :name="detail.name" />
            <span>{{ detail.category }}</span>
          </div>

          <h1 class="cd-hero__title">{{ detail.name }}</h1>

          <div v-if="tags.length" class="cd-hero__tags">
            <span v-for="t in tags" :key="t" class="cd-hero__tag">{{ t }}</span>
          </div>
        </div>
      </header>

      <!-- ================= 2. 内容区（整栏长文） ================= -->
      <div class="sy-container cd-body">
        <article class="cd-article">
          <p v-if="detail.summary" class="cd-lead cd-reveal">{{ detail.summary }}</p>

          <p v-for="(p, i) in paragraphs" :key="i" class="cd-p">{{ p }}</p>
        </article>
      </div>

      <!-- ================= 2.2 相关影像（与正文同宽） ================= -->
      <section v-if="detail.videoUrl" class="sy-container cd-video-sec">
        <p class="sy-eyebrow">Video</p>
        <h2 class="sy-page-title">相关影像</h2>
        <button type="button" class="cd-video cd-reveal" @click="openVideo">
          <img class="cd-video__poster" :src="detail.coverImage" alt="" loading="lazy" @error="onImgError" />
          <span class="cd-video__play" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          </span>
          <span class="cd-video__text">{{ t('cd.video') }}</span>
        </button>
      </section>

      <!-- ================= 2.5 图片集 ================= -->
      <section v-if="cultureImages.length" class="sy-container cd-gallery-sec">
        <p class="sy-eyebrow">Gallery</p>
        <h2 class="sy-page-title">图片集</h2>
        <div class="cd-gallery">
          <img
            v-for="(img, i) in cultureImages"
            :key="img"
            class="cd-gallery__item"
            :src="img"
            :alt="`${detail.name} 图片 ${i + 1}`"
            loading="lazy"
            decoding="async"
            @error="onImgError"
          />
        </div>
      </section>

      <!-- ================= 2.6 相关景点 / 相关人物 / 标签（全宽自适应） ================= -->
      <section
        v-if="relatedScenicList.length || mergedPeople.length || tags.length"
        class="sy-container cd-related"
      >
        <div class="cd-related__grid">
          <div v-if="relatedScenicList.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">{{ t('cd.spots') }}</h3>
            <div class="cd-rel-grid">
              <button
                v-for="name in relatedScenicList"
                :key="name"
                type="button"
                class="cd-rel-card"
                @click="goScenicByName(name)"
              >
                <span class="cd-rel-card__thumb">{{ name.slice(0, 1) }}</span>
                <span class="cd-rel-card__name">{{ name }}</span>
              </button>
            </div>
          </div>

          <div v-if="mergedPeople.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">相关人物</h3>
            <PersonCards :persons="mergedPeople" />
          </div>

          <div v-if="tags.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">{{ t('cd.tags') }}</h3>
            <div class="cd-cloud">
              <span v-for="(tag, i) in tags" :key="tag" class="cd-cloud__item" :class="`cd-cloud__item--${i % 3}`">
                {{ tag }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- ================= 3. 同分类推荐 ================= -->
      <section v-if="sameCategory.length" class="sy-container cd-reco">
        <p class="sy-eyebrow">More Culture</p>
        <h2 class="sy-page-title">{{ t('cd.sameCategory') }}</h2>

        <div class="cd-reco__grid">
          <article
            v-for="item in sameCategory"
            :key="item.id"
            class="cd-reco__card"
            @click="goCulture(item.id)"
          >
            <img class="cd-reco__img" :src="item.coverImage" alt="" loading="lazy" @error="onImgError" />
            <div class="cd-reco__body">
              <h3 class="cd-reco__name">{{ item.name }}</h3>
              <p class="cd-reco__text">{{ introOf(item) }}</p>
              <GhostButton>{{ t('cd.more') }}</GhostButton>
            </div>
          </article>
        </div>
      </section>

      <!-- ================= 4. 底部 CTA ================= -->
      <section class="cd-cta">
        <div class="sy-container cd-cta__inner">
          <p class="cd-cta__eyebrow">Ask The Butler</p>
          <h2 class="cd-cta__title">{{ t('cd.ctaTitle', { name: detail.name }) }}</h2>
          <GoldButton size="lg" @click="goAssistant">
            {{ t('cd.ctaButton') }}
            <template #icon-right>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </template>
          </GoldButton>
        </div>
      </section>
    </template>

    <!-- 不存在 -->
    <StateBlock
      v-else-if="!loading"
      state="error"
      error-text="文化信息不存在"
      action-text="返回文化列表"
      :action="() => router.push('/culture')"
    />

    <!-- ================= 视频 Lightbox ================= -->
    <Teleport to="body">
      <transition name="lb">
        <div v-if="videoOpen" class="cd-lightbox" @click.self="videoOpen = false">
          <button type="button" class="cd-lightbox__close" :aria-label="t('common.close')" @click="videoOpen = false">
            ✕
          </button>
          <div class="cd-lightbox__frame">
            <iframe :src="embedUrl" allowfullscreen frameborder="0" :title="t('cd.video')" />
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
 * 页面与 Hero
 * ========================================================== */
.cd-page {
  background: $color-paper;
}

.cd-loading {
  min-height: 4px;
}

.cd-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  height: $hero-detail-height;
  min-height: 520px;
  overflow: hidden;
  isolation: isolate;
}

.cd-hero__bg {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: cd-kenburns 26s ease-in-out infinite alternate;
}

@keyframes cd-kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

.cd-hero__content {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(80px, 14vh, 170px) clamp(48px, 9vh, 110px);
}

.cd-hero__crumb {
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

.cd-hero__sep {
  color: $color-gold;
}

.cd-hero__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: clamp(16px, 2.4vh, 28px);
  font-family: $font-en-body;
  font-size: $fs-eyebrow;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: $color-gold;
}

.cd-hero__icon {
  width: 34px;
}

.cd-hero__title {
  margin-top: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero-xl;
  line-height: 1;
  letter-spacing: 0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.cd-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: clamp(14px, 2vh, 26px);
}

.cd-hero__tag {
  padding: 4px 14px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: $radius-pill;
  font-size: $fs-tiny;
  letter-spacing: 0.08em;
  color: $color-text-light;
}

/* ============================================================
 * 内容区（65 / 35）
 * ========================================================== */
.cd-body {
  padding-block: clamp(48px, 7vw, 110px) clamp(28px, 3.6vw, 48px);
}

.cd-article {
  min-width: 0;
}

/* 导语：金色底纹的关键句 */
.cd-lead {
  padding: clamp(16px, 2vw, 26px) clamp(18px, 2.2vw, 30px);
  border-left: 3px solid $color-gold;
  border-radius: 0 $radius-md $radius-md 0;
  background: linear-gradient(90deg, rgba(212, 179, 116, 0.22), rgba(212, 179, 116, 0.06));
  font-family: $font-title;
  font-size: clamp(17px, 1.5vw, 22px);
  line-height: 1.9;
  color: var(--sy-accent);
}

/* 正文：大字号 + 行高 1.9 */
.cd-p {
  margin-top: 24px;
  font-family: $font-body;
  font-size: $fs-body-lg;
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.84);
  text-align: justify;
}

/* 文内配图 */
.cd-figure {
  margin-top: clamp(22px, 2.6vw, 34px);

  img {
    width: 100%;
    aspect-ratio: 16 / 9;
    object-fit: cover;
    border: 1px solid rgba(212, 179, 116, 0.4);
    border-radius: $radius-lg;
    box-shadow: $shadow-md;
  }

  figcaption {
    margin-top: 10px;
    font-size: $fs-small;
    letter-spacing: 0.1em;
    color: rgba(var(--sy-text-rgb), 0.5);
    text-align: center;
  }
}

/* ---------------- 侧栏 ---------------- */
.cd-aside {
  position: sticky;
  top: calc(#{$nav-height} + 28px);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.cd-panel {
  @include sy-card-window(24px);
  display: block;
  width: 100%;
  text-align: left;
}

.cd-panel__title {
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
    width: 3px;
    height: 1em;
    background: $color-gold;
  }
}

/* 视频卡（与正文同宽） */
.cd-video {
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

    .cd-video__poster {
      transform: scale(1.05);
    }

    .cd-video__play {
      transform: translate(-50%, -50%) scale(1.08);
      background: $color-gold;
      color: $color-ink;
    }
  }
}

.cd-video__poster {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.cd-video__play {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
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

.cd-video__text {
  position: absolute;
  left: 14px;
  bottom: 12px;
  font-size: $fs-small;
  letter-spacing: 0.14em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* 相关影像区：与正文同宽 */
.cd-video-sec {
  padding-block: clamp(28px, 3.6vw, 48px) clamp(12px, 1.6vw, 24px);
}

/* 相关板块：全宽自适应栅格（随屏幕列数自适应，避免留白） */
.cd-related {
  padding-block: clamp(28px, 3.6vw, 48px) clamp(40px, 6vw, 88px);
}

.cd-related__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: clamp(16px, 2vw, 24px);
}

/* 相关景点 */
.cd-spots {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cd-spot {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform $duration-normal $ease-out-expo;

  &:hover {
    transform: translateX(4px);

    b {
      color: var(--sy-accent);
    }

    .cd-spot__arrow {
      opacity: 1;
      transform: translateX(3px);
    }
  }
}

.cd-spot__img {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: $radius-md;
  border: 1px solid rgba(212, 179, 116, 0.4);
}

.cd-spot__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: $fs-small;
    letter-spacing: 0.08em;
    color: $color-text-dark;
    transition: color $duration-normal $ease-out-expo;
    @include text-ellipsis(1);
  }

  span {
    font-size: 12px;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.cd-spot__arrow {
  margin-left: auto;
  color: $color-gold;
  opacity: 0.5;
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

/* 传承人 */
.cd-people {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cd-person {
  display: flex;
  gap: 12px;
}

.cd-person__avatar {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: 50%;
  background: rgba(212, 179, 116, 0.12);
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  color: var(--sy-accent);
}

.cd-person__info {
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(14px, 1.15vw, 17px);
    letter-spacing: 0.08em;
    color: $color-ink;
  }
}

.cd-person__title {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: $color-gold;
}

.cd-person__intro {
  margin-top: 6px;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.8;
  color: rgba(var(--sy-text-rgb), 0.66);
}

/* 传承人（圆形头像卡，2 列） */
.cd-inherit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.cd-inherit-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.55);
  cursor: pointer;
  transition:
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }
}

.cd-inherit-card__avatar {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border: 2px solid rgba(212, 179, 116, 0.7);
  border-radius: 50%;
}

.cd-inherit-card__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 14px;
  letter-spacing: 0.06em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}

.cd-inherit-card__brief {
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: rgba(var(--sy-text-rgb), 0.6);
  @include text-ellipsis(2);
}

/* 标签云 */
.cd-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cd-cloud__item {
  padding: 8px 20px;
  border-radius: $radius-pill;
  font-size: $fs-tiny;
  letter-spacing: 0.08em;

  &--0 {
    border: 1px solid rgba(212, 179, 116, 0.6);
    color: var(--sy-accent);
  }

  &--1 {
    background: rgba(212, 179, 116, 0.18);
    color: $color-bamboo;
  }

  &--2 {
    border: 1px dashed rgba(122, 26, 34, 0.35);
    color: $color-ink;
  }
}

/* ============================================================
 * 同分类推荐
 * ========================================================== */
.cd-reco {
  padding-block: clamp(48px, 7vw, 110px);
}

.cd-reco__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: clamp(18px, 2.4vw, 34px);
  margin-top: clamp(24px, 3.4vw, 44px);
}

.cd-reco__card {
  @include hover-lift;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover {
    border-color: $color-gold;

    .cd-reco__img {
      transform: scale(1.06);
    }

    .cd-reco__name {
      color: var(--sy-accent);
    }
  }
}

.cd-reco__img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  transition: transform 1s $ease-out-expo;
}

.cd-reco__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: clamp(16px, 1.8vw, 24px);
}

.cd-reco__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(17px, 1.5vw, 22px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
  transition: color $duration-normal $ease-out-expo;
}

.cd-reco__text {
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.85;
  color: rgba(var(--sy-text-rgb), 0.66);
  @include text-ellipsis(3);
}

/* ============================================================
 * 底部 CTA
 * ========================================================== */
.cd-cta {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(212, 179, 116, 0.22) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(120deg, $color-ink 0%, #16324f 55%, #1d2436 100%);

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

.cd-cta__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(16px, 2vw, 26px);
  padding-block: clamp(56px, 9vw, 130px);
  text-align: center;
}

.cd-cta__eyebrow {
  font-family: $font-en-body;
  font-size: $fs-eyebrow;
  letter-spacing: 0.34em;
  text-transform: uppercase;
  color: $color-gold;
}

.cd-cta__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(28px, 4vw, 64px);
  line-height: 1.1;
  letter-spacing: 0.1em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

/* ============================================================
 * 找不到 / 视频 Lightbox
 * ========================================================== */
.cd-missing {
  padding-block: clamp(64px, 12vw, 160px);
}

.cd-lightbox {
  position: fixed;
  inset: 0;
  z-index: $z-loader;
  display: grid;
  place-items: center;
  padding: clamp(16px, 4vw, 60px);
  background: rgba(8, 12, 18, 0.94);
  backdrop-filter: blur(6px);
}

.cd-lightbox__frame {
  width: min(770px, 100%);
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: $radius-md;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6);

  iframe {
    width: 100%;
    height: 100%;
    border: 0;
  }
}

.cd-lightbox__close {
  position: absolute;
  top: clamp(16px, 3vh, 40px);
  right: clamp(16px, 3vw, 48px);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(212, 179, 116, 0.6);
  color: $color-gold;
  font-size: 18px;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
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
 * 响应式：大屏分栏 / 小屏堆叠
 * ========================================================== */
@include responsive(lg) {
  .cd-related__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@include responsive(md) {
  .cd-hero {
    height: 88vh;
  }

  .cd-p,
  .cd-lead {
    font-size: 17px;
  }
}

/* ============================================================
 * 图片集 / 相关景点、相关人物
 * ========================================================== */
.cd-gallery-sec {
  padding-block: clamp(48px, 7vw, 110px);
}

.cd-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: clamp(12px, 1.6vw, 22px);
  margin-top: clamp(24px, 3.4vw, 44px);
}

.cd-gallery__item {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  box-shadow: $shadow-md;
}

/* 相关景点：自适应小卡（缩略图 + 名称） */
.cd-rel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.cd-rel-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.55);
  text-align: left;
  cursor: pointer;
  transition:
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }
}

.cd-rel-card__thumb {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: $radius-sm;
  background:
    radial-gradient(120% 120% at 20% 0%, rgba(242, 215, 155, 0.28) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(135deg, #8e2230, #5f1218);
  font-family: $font-title;
  font-weight: 900;
  font-size: 18px;
  color: $color-gold;
}

.cd-rel-card__name {
  min-width: 0;
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-caption;
  letter-spacing: 0.04em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}
</style>
