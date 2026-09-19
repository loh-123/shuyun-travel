<script setup lang="ts">
/**
 * 文化详情页
 * - 数据来自 GET /api/culture/{id}（游客可访问）
 * - 另取 GET /api/culture/list 用于「同类别」「全部文化」与上一篇/下一篇导航
 * - 正文按换行拆成段落渲染
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/api/request'

interface CultureItem {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  content?: string
  videoUrl?: string
  tags?: string
}

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const notFound = ref(false)
const culture = ref<CultureItem | null>(null)
const all = ref<CultureItem[]>([])

/** 标签数组 */
const tags = computed(() => splitTags(culture.value?.tags))

/** 正文段落 */
const paragraphs = computed(() =>
  (culture.value?.content || '')
    .split('\n')
    .map((p) => p.trim())
    .filter(Boolean),
)

/** 同类别其他条目（最多 4 条） */
const siblings = computed(() => {
  const cur = culture.value
  if (!cur) {
    return []
  }
  return all.value.filter((i) => i.category === cur.category && i.id !== cur.id).slice(0, 4)
})

/** 上一篇 / 下一篇 */
const nav = computed(() => {
  const cur = culture.value
  if (!cur || !all.value.length) {
    return { prev: null as CultureItem | null, next: null as CultureItem | null }
  }
  const idx = all.value.findIndex((i) => i.id === cur.id)
  if (idx < 0) {
    return { prev: null, next: null }
  }
  return {
    prev: idx > 0 ? all.value[idx - 1] : null,
    next: idx < all.value.length - 1 ? all.value[idx + 1] : null,
  }
})

function splitTags(value?: string) {
  return (value || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

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
    culture.value = res.data ?? null
    notFound.value = !culture.value
  } catch {
    culture.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}

/** 切换条目时回到顶部并重新加载 */
watch(
  () => route.params.id,
  (id) => {
    if (id) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      loadDetail(Number(id))
    }
  },
)

onMounted(() => {
  loadAll()
  loadDetail(Number(route.params.id))
})
</script>

<template>
  <section class="cdetail">
    <div v-loading="loading" class="cdetail__loading" />

    <template v-if="culture">
      <!-- ============ 头图 ============ -->
      <header class="cdetail__hero">
        <img
          v-if="culture.coverImage"
          class="cdetail__cover"
          :src="culture.coverImage"
          :alt="culture.name"
          loading="lazy"
          decoding="async"
        />
        <div class="sy-overlay">
          <div class="sy-overlay__gradient" />
          <div class="sy-overlay__brocade" />
          <div class="sy-overlay__noise" />
        </div>

        <div class="cdetail__hero-inner">
          <button class="cdetail__back" @click="router.push('/culture')">← 返回文化寻踪</button>
          <span v-if="culture.category" class="cdetail__badge">{{ culture.category }}</span>
          <h1 class="cdetail__title">{{ culture.name }}</h1>
          <div class="cdetail__tags">
            <span v-for="t in tags" :key="t" class="cdetail__tag">{{ t }}</span>
          </div>
        </div>
      </header>

      <!-- ============ 正文 + 侧栏 ============ -->
      <div class="cdetail__body">
        <article class="cdetail__article">
          <p v-if="culture.summary" class="cdetail__lead">{{ culture.summary }}</p>

          <div v-if="paragraphs.length" class="cdetail__content">
            <p v-for="(p, i) in paragraphs" :key="i">{{ p }}</p>
          </div>
          <p v-else class="cdetail__content">
            该文化条目的正文正在补充中，敬请期待。
          </p>

          <!-- 视频（有地址时才显示） -->
          <div v-if="culture.videoUrl" class="cdetail__video">
            <h2 class="cdetail__sub">相关影像</h2>
            <video :src="culture.videoUrl" controls playsinline />
          </div>

          <!-- 上一篇 / 下一篇 -->
          <nav class="cdetail__nav">
            <button
              class="cdetail__nav-btn"
              :disabled="!nav.prev"
              @click="nav.prev && goDetail(nav.prev.id)"
            >
              <span class="cdetail__nav-label">上一篇</span>
              <span class="cdetail__nav-name">{{ nav.prev?.name || '已是第一篇' }}</span>
            </button>
            <button
              class="cdetail__nav-btn cdetail__nav-btn--right"
              :disabled="!nav.next"
              @click="nav.next && goDetail(nav.next.id)"
            >
              <span class="cdetail__nav-label">下一篇</span>
              <span class="cdetail__nav-name">{{ nav.next?.name || '已是最后一篇' }}</span>
            </button>
          </nav>
        </article>

        <aside class="cdetail__aside">
          <!-- 同类别 -->
          <section v-if="siblings.length" class="cdetail__panel">
            <h2 class="cdetail__sub">同类别文化</h2>
            <ul class="cdetail__list">
              <li v-for="s in siblings" :key="s.id" @click="goDetail(s.id)">
                <span class="cdetail__list-name">{{ s.name }}</span>
                <span class="cdetail__list-arrow">→</span>
              </li>
            </ul>
          </section>

          <!-- 全部文化 -->
          <section class="cdetail__panel">
            <h2 class="cdetail__sub">八大文化</h2>
            <ul class="cdetail__list">
              <li
                v-for="s in all"
                :key="s.id"
                :class="{ 'is-current': s.id === culture.id }"
                @click="goDetail(s.id)"
              >
                <span class="cdetail__list-name">{{ s.name }}</span>
                <span class="cdetail__list-cat">{{ s.category }}</span>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="未找到该文化条目">
      <el-button type="primary" @click="router.push('/culture')">返回文化寻踪</el-button>
    </el-empty>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.cdetail {
  min-height: 60vh;
  background: $color-paper;
}

.cdetail__loading {
  min-height: 4px;
}

/* ---------------- 头图 ---------------- */
.cdetail__hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  height: clamp(320px, 52vh, 560px);
  overflow: hidden;
}

.cdetail__cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cdetail__hero-inner {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 0 $gutter clamp(28px, 4vw, 56px);
}

.cdetail__back {
  display: inline-block;
  margin-bottom: clamp(16px, 2vw, 28px);
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.82);
  transition: color $duration-normal $ease-out-expo;

  &:hover {
    color: $color-gold;
  }
}

.cdetail__badge {
  display: inline-block;
  padding: 5px 16px;
  border: 1px solid rgba(201, 168, 106, 0.7);
  border-radius: $radius-pill;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: $color-gold;
}

.cdetail__title {
  margin-top: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(34px, 4.6vw, 64px);
  letter-spacing: 0.1em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.cdetail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: clamp(12px, 1.6vw, 20px);
}

.cdetail__tag {
  padding: 4px 12px;
  border-radius: $radius-sm;
  background: rgba(201, 168, 106, 0.16);
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-text-light;
}

/* ---------------- 正文 ---------------- */
.cdetail__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 300px);
  gap: clamp(32px, 4vw, 72px);
  padding: clamp(36px, 5vw, 72px) $gutter clamp(64px, 8vw, 120px);
}

.cdetail__lead {
  padding-left: clamp(14px, 1.6vw, 24px);
  border-left: 2px solid $color-gold;
  font-family: $font-title;
  font-size: clamp(17px, 1.5vw, 22px);
  line-height: 1.9;
  color: $color-primary;
}

.cdetail__content {
  margin-top: clamp(20px, 2.4vw, 34px);
  font-family: $font-body;
  font-size: $fs-body;
  line-height: 2.1;
  color: rgba(26, 26, 26, 0.82);
  text-align: justify;

  p + p {
    margin-top: 1em;
  }
}

.cdetail__sub {
  margin-bottom: 16px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(16px, 1.3vw, 20px);
  letter-spacing: 0.14em;
  color: $color-ink;
}

.cdetail__video {
  margin-top: clamp(28px, 3.4vw, 48px);

  video {
    width: 100%;
    border-radius: $radius-lg;
    box-shadow: $shadow-md;
  }
}

/* ---------------- 上一篇 / 下一篇 ---------------- */
.cdetail__nav {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: clamp(32px, 4vw, 56px);
  padding-top: clamp(20px, 2.4vw, 32px);
  border-top: 1px solid rgba(201, 168, 106, 0.4);
}

.cdetail__nav-btn {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 18px;
  border: 1px solid rgba(140, 31, 40, 0.18);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.5);
  text-align: left;
  transition:
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover:not(:disabled) {
    border-color: $color-gold;
    transform: translateY(-3px);
  }

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }

  &--right {
    align-items: flex-end;
    text-align: right;
  }
}

.cdetail__nav-label {
  font-size: 12px;
  letter-spacing: 0.2em;
  color: $color-gold;
}

.cdetail__nav-name {
  font-family: $font-title;
  font-size: clamp(15px, 1.2vw, 19px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
}

/* ---------------- 侧栏 ---------------- */
.cdetail__aside {
  display: flex;
  flex-direction: column;
  gap: clamp(24px, 3vw, 40px);
}

.cdetail__panel {
  padding: clamp(18px, 2vw, 26px);
  border: 1px solid rgba(201, 168, 106, 0.44);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.6);
  box-shadow: $shadow-md;
}

.cdetail__list {
  display: flex;
  flex-direction: column;

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 11px 0;
    border-bottom: 1px dashed rgba(140, 31, 40, 0.14);
    cursor: pointer;
    transition: color $duration-fast $ease-out-expo;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      color: $color-primary;
    }

    &.is-current {
      color: $color-primary;
      font-weight: 600;
    }
  }
}

.cdetail__list-name {
  font-family: $font-title;
  font-size: $fs-small;
  letter-spacing: 0.08em;
}

.cdetail__list-arrow,
.cdetail__list-cat {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: $color-gold;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .cdetail__body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .cdetail__nav {
    grid-template-columns: 1fr;
  }
}
</style>
