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
import { useUserStore } from '@/stores/user'
import GoldButton from '@/components/common/GoldButton.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import GhostButton from '@/components/common/GhostButton.vue'

gsap.registerPlugin(ScrollTrigger)

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
interface RelatedScenic {
  id: number
  name: string
  cityName?: string
  type?: string
  coverImage?: string
  score?: number
}

interface Inheritor {
  name: string
  title: string
  intro: string
}

interface CultureDetail {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  content?: string
  videoUrl?: string
  tags?: string
  relatedScenics?: RelatedScenic[]
  inheritors?: Inheritor[]
}

interface CultureItem {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  content?: string
}

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const rootEl = ref<HTMLElement | null>(null)
const heroEl = ref<HTMLElement | null>(null)

const detail = ref<CultureDetail | null>(null)
const all = ref<CultureItem[]>([])
const loading = ref(true)
const notFound = ref(false)

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

/** 正文中插入的配图：取关联景点的封面（最多 2 张） */
const inlineFigures = computed(() => (detail.value?.relatedScenics ?? []).slice(0, 2))

/** 同分类的其他文化（最多 3 条） */
const sameCategory = computed(() => {
  const cur = detail.value
  if (!cur) {
    return []
  }
  return all.value.filter((c) => c.category === cur.category && c.id !== cur.id).slice(0, 3)
})

/** 卡片简介：正文两段拼接（约 150 字，由 CSS 限 3 行） */
function introOf(item: CultureItem) {
  const text = (item.content || item.summary || '').replace(/\n/g, '')
  return text || item.summary || ''
}

/** video_url 是否为可内嵌地址（B站 player / YouTube embed） */
const videoEmbeddable = computed(() =>
  /player\.bilibili\.com|youtube\.com\/embed/.test(detail.value?.videoUrl || ''),
)

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadAll() {
  try {
    const res = await request.get('/culture/list', { silent: true })
    all.value = Array.isArray(res.data) ? res.data : []
  } catch {
    all.value = []
  }
}

async function loadDetail(id: number) {
  loading.value = true
  notFound.value = false
  try {
    const res = await request.get(`/culture/${id}`, { silent: true })
    detail.value = res.data ?? null
    notFound.value = !detail.value
    if (detail.value) {
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
  if (videoEmbeddable.value) {
    videoOpen.value = true
  } else {
    // 未配置可内嵌源时，直接跳到外部页面
    window.open(url, '_blank', 'noopener')
  }
}

function goScenic(id: number) {
  router.push(`/scenic/${id}`)
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
  await Promise.all([loadAll(), loadDetail(Number(route.params.id))])
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
    <div v-loading="loading" class="cd-loading" />

    <template v-if="detail">
      <!-- ================= 1. 全屏 Hero ================= -->
      <header ref="heroEl" class="cd-hero">
        <img
          class="cd-hero__bg"
          :src="detail.coverImage"
          :alt="detail.name"
          fetchpriority="high"
          decoding="async"
        />

        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container cd-hero__content">
          <nav class="cd-hero__crumb">
            <router-link to="/">首页</router-link>
            <span class="cd-hero__sep">/</span>
            <router-link to="/culture">文化</router-link>
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

      <!-- ================= 2. 内容区（65 / 35） ================= -->
      <div class="sy-container cd-body">
        <!-- 左：长文 -->
        <article class="cd-article">
          <p v-if="detail.summary" class="cd-lead cd-reveal">{{ detail.summary }}</p>

          <template v-for="(p, i) in paragraphs" :key="i">
            <p class="cd-p">{{ p }}</p>

            <!-- 第一段后插一张配图 -->
            <figure v-if="i === 0 && inlineFigures[0]" class="cd-figure">
              <img :src="inlineFigures[0].coverImage" :alt="inlineFigures[0].name" loading="lazy" />
              <figcaption>{{ inlineFigures[0].name }} · {{ inlineFigures[0].cityName }}</figcaption>
            </figure>
          </template>

          <!-- 末尾再插一张 -->
          <figure v-if="inlineFigures[1]" class="cd-figure">
            <img :src="inlineFigures[1].coverImage" :alt="inlineFigures[1].name" loading="lazy" />
            <figcaption>{{ inlineFigures[1].name }} · {{ inlineFigures[1].cityName }}</figcaption>
          </figure>
        </article>

        <!-- 右：侧栏 -->
        <aside class="cd-aside">
          <!-- 相关视频 -->
          <button
            v-if="detail.videoUrl"
            type="button"
            class="cd-panel cd-video cd-reveal"
            @click="openVideo"
          >
            <img class="cd-video__poster" :src="detail.coverImage" alt="" loading="lazy" />
            <span class="cd-video__play" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <path d="M8 5v14l11-7z" />
              </svg>
            </span>
            <span class="cd-video__text">相关影像</span>
          </button>

          <!-- 相关景点 -->
          <div v-if="detail.relatedScenics?.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">相关景点</h3>
            <ul class="cd-spots">
              <li
                v-for="s in detail.relatedScenics"
                :key="s.id"
                class="cd-spot"
                @click="goScenic(s.id)"
              >
                <img class="cd-spot__img" :src="s.coverImage" alt="" loading="lazy" />
                <div class="cd-spot__info">
                  <b>{{ s.name }}</b>
                  <span>{{ s.cityName }} · {{ s.type }}</span>
                </div>
                <em class="cd-spot__arrow">→</em>
              </li>
            </ul>
          </div>

          <!-- 传承人 -->
          <div v-if="detail.inheritors?.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">传承人</h3>
            <ul class="cd-people">
              <li v-for="p in detail.inheritors" :key="p.name" class="cd-person">
                <span class="cd-person__avatar">{{ p.name.charAt(0) }}</span>
                <div class="cd-person__info">
                  <b>{{ p.name }}</b>
                  <span class="cd-person__title">{{ p.title }}</span>
                  <p class="cd-person__intro">{{ p.intro }}</p>
                </div>
              </li>
            </ul>
          </div>

          <!-- 标签云 -->
          <div v-if="tags.length" class="cd-panel cd-reveal">
            <h3 class="cd-panel__title">标签</h3>
            <div class="cd-cloud">
              <span v-for="(t, i) in tags" :key="t" class="cd-cloud__item" :class="`cd-cloud__item--${i % 3}`">
                {{ t }}
              </span>
            </div>
          </div>
        </aside>
      </div>

      <!-- ================= 3. 同分类推荐 ================= -->
      <section v-if="sameCategory.length" class="sy-container cd-reco">
        <p class="sy-eyebrow">More Culture</p>
        <h2 class="sy-page-title">同分类文化</h2>

        <div class="cd-reco__grid">
          <article
            v-for="item in sameCategory"
            :key="item.id"
            class="cd-reco__card"
            @click="goCulture(item.id)"
          >
            <img class="cd-reco__img" :src="item.coverImage" alt="" loading="lazy" />
            <div class="cd-reco__body">
              <h3 class="cd-reco__name">{{ item.name }}</h3>
              <p class="cd-reco__text">{{ introOf(item) }}</p>
              <GhostButton>了解详情</GhostButton>
            </div>
          </article>
        </div>
      </section>

      <!-- ================= 4. 底部 CTA ================= -->
      <section class="cd-cta">
        <div class="sy-container cd-cta__inner">
          <p class="cd-cta__eyebrow">Ask The Butler</p>
          <h2 class="cd-cta__title">让 AI 带你了解{{ detail.name }}</h2>
          <GoldButton size="lg" @click="goAssistant">
            让 AI 带你了解
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
    <div v-else-if="!loading" class="cd-missing">
      <el-empty description="未找到该文化条目">
        <GoldButton @click="router.push('/culture')">返回文化列表</GoldButton>
      </el-empty>
    </div>

    <!-- ================= 视频 Lightbox ================= -->
    <Teleport to="body">
      <transition name="lb">
        <div v-if="videoOpen" class="cd-lightbox" @click.self="videoOpen = false">
          <button type="button" class="cd-lightbox__close" aria-label="关闭" @click="videoOpen = false">
            ✕
          </button>
          <div class="cd-lightbox__frame">
            <iframe :src="detail?.videoUrl" allowfullscreen frameborder="0" title="相关影像" />
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
  height: 100vh;
  min-height: 560px;
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
  font-size: $fs-hero;
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
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: $radius-pill;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-text-light;
}

/* ============================================================
 * 内容区（65 / 35）
 * ========================================================== */
.cd-body {
  display: grid;
  grid-template-columns: minmax(0, 65fr) minmax(0, 35fr);
  gap: clamp(32px, 5vw, 90px);
  align-items: start;
  padding-block: clamp(48px, 7vw, 110px);
}

.cd-article {
  min-width: 0;
}

/* 导语：金色底纹的关键句 */
.cd-lead {
  padding: clamp(16px, 2vw, 26px) clamp(18px, 2.2vw, 30px);
  border-left: 3px solid $color-gold;
  border-radius: 0 $radius-md $radius-md 0;
  background: linear-gradient(90deg, rgba(201, 168, 106, 0.22), rgba(201, 168, 106, 0.06));
  font-family: $font-title;
  font-size: clamp(17px, 1.5vw, 22px);
  line-height: 1.9;
  color: $color-primary;
}

/* 正文：大字号 + 行高 1.9 */
.cd-p {
  margin-top: clamp(18px, 2vw, 28px);
  font-family: $font-body;
  font-size: clamp(17px, 1.45vw, 21px);
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.84);
  text-align: justify;
}

/* 文内配图 */
.cd-figure {
  margin-top: clamp(22px, 2.6vw, 34px);

  img {
    width: 100%;
    aspect-ratio: 16 / 9;
    object-fit: cover;
    border: 1px solid rgba(201, 168, 106, 0.4);
    border-radius: $radius-lg;
    box-shadow: $shadow-md;
  }

  figcaption {
    margin-top: 10px;
    font-size: $fs-small;
    letter-spacing: 0.1em;
    color: rgba(26, 26, 26, 0.5);
    text-align: center;
  }
}

/* ---------------- 侧栏 ---------------- */
.cd-aside {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  gap: clamp(16px, 1.8vw, 24px);
}

.cd-panel {
  display: block;
  width: 100%;
  padding: clamp(16px, 1.8vw, 24px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: $shadow-md;
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

/* 视频卡 */
.cd-video {
  position: relative;
  padding: 0;
  overflow: hidden;
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
  aspect-ratio: 16 / 10;
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
  border: 1px solid rgba(201, 168, 106, 0.8);
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
      color: $color-primary;
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
  border: 1px solid rgba(201, 168, 106, 0.4);
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
    color: rgba(26, 26, 26, 0.5);
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
  border: 1px solid rgba(201, 168, 106, 0.6);
  border-radius: 50%;
  background: rgba(201, 168, 106, 0.12);
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  color: $color-primary;
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
  color: rgba(26, 26, 26, 0.66);
}

/* 标签云 */
.cd-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cd-cloud__item {
  padding: 5px 14px;
  border-radius: $radius-pill;
  font-size: 12px;
  letter-spacing: 0.08em;

  &--0 {
    border: 1px solid rgba(201, 168, 106, 0.6);
    color: $color-primary;
  }

  &--1 {
    background: rgba(201, 168, 106, 0.18);
    color: $color-bamboo;
  }

  &--2 {
    border: 1px dashed rgba(140, 31, 40, 0.35);
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
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover {
    border-color: $color-gold;

    .cd-reco__img {
      transform: scale(1.06);
    }

    .cd-reco__name {
      color: $color-primary;
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
  color: rgba(26, 26, 26, 0.66);
  @include text-ellipsis(3);
}

/* ============================================================
 * 底部 CTA
 * ========================================================== */
.cd-cta {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(201, 168, 106, 0.22) 0%, rgba(14, 42, 71, 0) 60%),
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
  width: min(1100px, 100%);
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
  border: 1px solid rgba(201, 168, 106, 0.6);
  color: $color-gold;
  font-size: 18px;

  &:hover {
    background: rgba(201, 168, 106, 0.16);
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
  .cd-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .cd-aside {
    position: static;
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
</style>
