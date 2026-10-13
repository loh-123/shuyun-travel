<script setup lang="ts">
/**
 * 传承人详情
 * - 首屏 Hero（60vh）：深墨蓝 + 蜀锦纹理背景，左侧头像，右侧姓名 / 代表文化 / 简介
 * - 内容区 65 / 35：左侧简介全文，右侧视频卡片 / 代表文化卡片 / 相关传承人
 * - 底部：返回按钮
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getPersonDetail,
  listPersonByCulture,
  type CulturePersonDetailVO,
  type CulturePersonVO,
} from '@/api/person'
import { onAvatarError } from '@/utils/avatar'
import GhostButton from '@/components/common/GhostButton.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import CultureIcon from '@/components/common/CultureIcon.vue'
import PersonCards from '@/components/common/PersonCards.vue'

const route = useRoute()
const router = useRouter()

const detail = ref<CulturePersonDetailVO | null>(null)
const related = ref<CulturePersonVO[]>([])
const loading = ref(true)
const notFound = ref(false)

/** 简介全文分段 */
const paragraphs = computed(() =>
  (detail.value?.brief || '')
    .split('\n')
    .map((p) => p.trim())
    .filter(Boolean),
)

/**
 * 视频嵌入地址：
 * - player.bilibili.com / youtube embed → 原样
 * - B站普通视频页 → player.bilibili.com 内嵌
 * - 其它（如央视 / 网页）→ 空，由调用方新窗口打开
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
  return ''
})

/** 是否有可内嵌的视频 */
const canEmbed = computed(() => !!embedUrl.value)

/* ------------------------------------------------------------
 * 数据
 * ---------------------------------------------------------- */
async function loadRelated(cultureId?: number, currentId?: number) {
  if (!cultureId) {
    related.value = []
    return
  }
  try {
    const list = await listPersonByCulture(cultureId)
    related.value = (Array.isArray(list) ? list : [])
      .filter((p) => p.id !== currentId)
      .slice(0, 3)
  } catch {
    related.value = []
  }
}

async function loadDetail(id: number) {
  loading.value = true
  notFound.value = false
  try {
    const data = await getPersonDetail(id)
    detail.value = data ?? null
    notFound.value = !detail.value
    if (detail.value) {
      await loadRelated(detail.value.cultureId, detail.value.id)
    }
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------------------
 * 交互
 * ---------------------------------------------------------- */
function goCulture() {
  if (detail.value?.cultureId) {
    router.push(`/culture/${detail.value.cultureId}`)
  }
}

function openVideo() {
  const url = detail.value?.videoUrl
  if (!url) {
    return
  }
  window.open(url, '_blank', 'noopener')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/heritage')
  }
}

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
onMounted(() => {
  loadDetail(Number(route.params.id))
})

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      window.scrollTo({ top: 0 })
      loadDetail(Number(id))
    }
  },
)
</script>

<template>
  <section class="pd-page">
    <StateBlock v-if="loading" state="loading" />

    <template v-if="detail">
      <!-- ================= 1. 首屏 Hero ================= -->
      <header class="pd-hero">
        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="sy-container pd-hero__inner">
          <!-- 左：头像 -->
          <figure class="pd-hero__avatar">
            <img
              :src="detail.avatar"
              :alt="detail.name"
              loading="lazy"
              decoding="async"
              @error="onAvatarError($event, detail.name)"
            />
          </figure>

          <!-- 右：信息 -->
          <div class="pd-hero__info">
            <nav class="pd-hero__crumb">
              <router-link to="/">首页</router-link>
              <span class="pd-hero__sep">/</span>
              <router-link to="/heritage">非遗</router-link>
              <span class="pd-hero__sep">/</span>
              <span>{{ detail.name }}</span>
            </nav>

            <p v-if="detail.cultureName" class="pd-hero__culture">
              <span class="pd-hero__dot">·</span>{{ detail.cultureName }}
            </p>

            <h1 class="pd-hero__name">{{ detail.name }}</h1>

            <p v-if="detail.brief" class="pd-hero__brief">{{ detail.brief }}</p>
          </div>
        </div>
      </header>

      <!-- ================= 2. 内容区（65 / 35） ================= -->
      <div class="sy-container pd-body">
        <!-- 左：简介全文 -->
        <article class="pd-article">
          <p class="sy-eyebrow">Biography</p>
          <h2 class="sy-page-title">传承人简介</h2>
          <p v-for="(p, i) in paragraphs" :key="i" class="pd-p">{{ p }}</p>
        </article>

        <!-- 右：侧栏 -->
        <aside class="pd-aside">
          <!-- 视频卡片 -->
          <div v-if="detail.videoUrl" class="pd-panel pd-panel--video">
            <h3 class="pd-panel__title">相关视频</h3>
            <div v-if="canEmbed" class="pd-video">
              <iframe
                :src="embedUrl"
                allowfullscreen
                frameborder="0"
                title="传承人视频"
                loading="lazy"
              />
            </div>
            <button v-else type="button" class="pd-video-link" @click="openVideo">
              <span class="pd-video-link__play" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </span>
              在新窗口观看视频
            </button>
          </div>

          <!-- 代表文化卡片 -->
          <button
            v-if="detail.cultureId || detail.cultureName"
            type="button"
            class="pd-panel pd-culture"
            @click="goCulture"
          >
            <h3 class="pd-panel__title">代表文化</h3>
            <div class="pd-culture__row">
              <CultureIcon class="pd-culture__icon" :name="detail.cultureName || detail.name" />
              <span class="pd-culture__name">{{ detail.cultureName }}</span>
              <span class="pd-culture__arrow">→</span>
            </div>
          </button>

          <!-- 相关传承人 -->
          <div v-if="related.length" class="pd-panel">
            <h3 class="pd-panel__title">相关传承人</h3>
            <PersonCards :persons="related" />
          </div>
        </aside>
      </div>

      <!-- ================= 3. 底部返回 ================= -->
      <div class="sy-container pd-foot">
        <GhostButton @click="goBack">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M19 12H5M11 18l-6-6 6-6" />
          </svg>
          返回
        </GhostButton>
      </div>
    </template>

    <!-- 不存在 -->
    <StateBlock
      v-else-if="!loading"
      state="error"
      error-text="传承人信息不存在"
      action-text="返回非遗列表"
      :action="() => router.push('/heritage')"
    />
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.pd-page {
  background: $color-paper;
}

.pd-loading {
  min-height: 4px;
}

/* ============================================================
 * Hero
 * ========================================================== */
.pd-hero {
  position: relative;
  display: flex;
  align-items: center;
  height: $hero-detail-height;
  min-height: 520px;
  overflow: hidden;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(212, 179, 116, 0.18) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(120deg, $color-ink 0%, #16324f 55%, #1d2436 100%);
  isolation: isolate;
}

.pd-hero__inner {
  position: relative;
  z-index: $z-content;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: clamp(28px, 4vw, 64px);
  align-items: center;
  width: 100%;
  padding-block: clamp(90px, 14vh, 160px) clamp(40px, 8vh, 90px);
}

.pd-hero__avatar {
  margin: 0;
  overflow: hidden;
  border: 2px solid $color-gold;
  border-radius: 8px;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.45);

  img {
    display: block;
    width: 240px;
    height: 320px;
    object-fit: cover;
  }
}

.pd-hero__info {
  min-width: 0;
}

.pd-hero__crumb {
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

.pd-hero__sep {
  color: $color-gold;
}

.pd-hero__culture {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: clamp(14px, 2vh, 22px);
  font-family: $font-body;
  font-size: 18px;
  letter-spacing: 0.12em;
  color: rgba(245, 241, 232, 0.72);
}

.pd-hero__dot {
  color: $color-gold;
}

.pd-hero__name {
  margin-top: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(38px, 4vw, 48px);
  line-height: 1.1;
  letter-spacing: 0.04em;
  background: linear-gradient(120deg, #f4e3bc 0%, $color-gold 55%, #b8935a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 2px 16px rgba(0, 0, 0, 0.4));
}

.pd-hero__brief {
  max-width: 720px;
  margin-top: clamp(16px, 2.4vh, 26px);
  font-family: $font-body;
  font-size: 17px;
  line-height: 1.9;
  color: rgba(245, 241, 232, 0.86);
}

/* ============================================================
 * 内容区 65 / 35
 * ========================================================== */
.pd-body {
  padding-block: clamp(48px, 7vw, 110px);
}

.pd-article {
  min-width: 0;
}

.pd-p {
  margin-top: 24px;
  font-family: $font-body;
  font-size: $fs-body-lg;
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.84);
  text-align: justify;
}

.pd-aside {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: clamp(16px, 2vw, 24px);
  align-items: start;
  margin-top: clamp(32px, 4vw, 56px);
}

.pd-panel--video {
  grid-column: 1 / -1;
}

.pd-panel {
  display: block;
  width: 100%;
  padding: 24px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.66);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  text-align: left;
}

.pd-panel__title {
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

/* 视频 */
.pd-video {
  width: 70%;
  margin-inline: auto;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;

  iframe {
    width: 100%;
    height: 100%;
    border: 0;
  }
}

.pd-video-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  width: 100%;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;
  background: rgba(212, 179, 116, 0.12);
  font-size: $fs-caption;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
  cursor: pointer;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.24);
  }
}

.pd-video-link__play {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 50%;
  background: $color-gold;
  color: $color-ink;
}

/* 代表文化卡 */
.pd-culture {
  cursor: pointer;
  transition:
    border-color $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    box-shadow: $shadow-md;

    .pd-culture__arrow {
      transform: translateX(4px);
    }
  }
}

.pd-culture__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pd-culture__icon {
  width: 34px;
  flex-shrink: 0;
}

.pd-culture__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.3vw, 20px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
}

.pd-culture__arrow {
  margin-left: auto;
  color: $color-gold;
  transition: transform $duration-normal $ease-out-expo;
}

/* 相关传承人 */
.pd-related {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pd-related__item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background-color $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.12);
  }
}

.pd-related__avatar {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: 50%;
}

.pd-related__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: $fs-caption;
    letter-spacing: 0.06em;
    color: $color-text-dark;
  }
}

.pd-related__brief {
  font-size: 12px;
  line-height: 1.6;
  color: rgba(var(--sy-text-rgb), 0.6);
  @include text-ellipsis(2);
}

/* 底部返回 */
.pd-foot {
  display: flex;
  justify-content: center;
  padding-block: clamp(24px, 4vw, 64px) clamp(56px, 8vw, 110px);
}

/* ============================================================
 * 响应式
 * ========================================================== */
@include responsive(lg) {
  .pd-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .pd-aside {
    position: static;
  }
}

@include responsive(md) {
  .pd-hero {
    height: auto;
    min-height: 0;
  }

  .pd-hero__inner {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
    text-align: center;
  }

  .pd-hero__avatar img {
    width: 180px;
    height: 240px;
  }

  .pd-hero__crumb,
  .pd-hero__culture {
    justify-content: center;
  }

  .pd-hero__brief {
    text-align: left;
  }
}
</style>
