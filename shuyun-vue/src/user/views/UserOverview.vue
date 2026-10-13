<script setup lang="ts">
/**
 * 用户后台 · 概览
 *
 * 展示当前用户自己的数据画像：概览数字 + 最近浏览 + 内容统计。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import request from '@/api/request'
import { formatDateTime } from '@/user/utils/format'

interface Stats {
  cities: number
  collections: number
  trips: number
  chats: number
}
interface HistoryItem {
  id: number
  targetType: string
  targetId: number
  targetName: string
  subTitle?: string
  coverImage?: string
  viewTime?: string
}

const router = useRouter()

const stats = ref<Stats>({ cities: 0, collections: 0, trips: 0, chats: 0 })
const history = ref<HistoryItem[]>([])
const noteCount = ref(0)
const noteChars = ref(0)

/** 概览卡片 */
const CARDS: { key: keyof Stats; label: string; icon: string }[] = [
  { key: 'cities', label: '去过的城市', icon: '🏙' },
  { key: 'collections', label: '收藏景点', icon: '❤' },
  { key: 'trips', label: '生成行程', icon: '🗺' },
  { key: 'chats', label: 'AI 对话', icon: '💬' },
]

/** 加载概览数据 */
async function load() {
  try {
    const res = await request.get('/user/stats', { silent: true })
    stats.value = res.data ?? stats.value
  } catch {
    // 静默
  }
}

/** 加载最近浏览 */
async function loadHistory() {
  try {
    const res = await request.get('/user/history', { silent: true, params: { page: 1, size: 6 } })
    history.value = res.data?.records ?? []
  } catch {
    history.value = []
  }
}

/** 加载笔记统计 */
async function loadNoteSummary() {
  try {
    const res = await request.get('/user/note/summary', { silent: true })
    noteCount.value = res.data?.count ?? 0
    noteChars.value = res.data?.chars ?? 0
  } catch {
    noteCount.value = 0
    noteChars.value = 0
  }
}

onMounted(() => {
  load()
  loadHistory()
  loadNoteSummary()
})

function openHistory(item: HistoryItem) {
  if (item.targetType === 'CULTURE') {
    router.push(`/culture/${item.targetId}`)
  } else {
    router.push(`/scenic/${item.targetId}`)
  }
}
</script>

<template>
  <div class="ov">
    <!-- 概览数字 -->
    <section class="ov__stats">
      <button
        v-for="c in CARDS"
        :key="c.key"
        type="button"
        class="ov__stat"
        @click="router.push(c.key === 'trips' ? '/user/trips' : c.key === 'collections' ? '/user/collections' : '/user/history')"
      >
        <span class="ov__stat-icon">{{ c.icon }}</span>
        <b class="ov__stat-num">{{ stats[c.key] }}</b>
        <span class="ov__stat-label">{{ c.label }}</span>
      </button>
    </section>

    <div class="ov__row">
      <!-- 最近浏览 -->
      <section class="panel">
        <header class="panel__head">
          <h3 class="panel__title">最近浏览</h3>
          <button type="button" class="panel__more" @click="router.push('/user/history')">全部 →</button>
        </header>
        <ul class="ov__recent">
          <li v-for="h in history" :key="h.id" class="ov__recent-item" @click="openHistory(h)">
            <img v-if="h.coverImage" class="ov__recent-img" :src="h.coverImage" alt="" loading="lazy" />
            <span class="ov__recent-info">
              <b>{{ h.targetName || '未命名' }}</b>
              <em>{{ h.subTitle || '—' }} · {{ formatDateTime(h.viewTime) }}</em>
            </span>
          </li>
          <li v-if="!history.length" class="ov__empty">还没有浏览记录</li>
        </ul>
      </section>

      <!-- 内容统计 -->
      <section class="panel">
        <header class="panel__head">
          <h3 class="panel__title">我的内容</h3>
        </header>
        <div class="ov__content">
          <div class="ov__content-item">
            <b>{{ noteCount }}</b>
            <span>篇笔记</span>
          </div>
          <div class="ov__content-item">
            <b>{{ noteChars }}</b>
            <span>累计字数</span>
          </div>
        </div>
        <button type="button" class="ov__link" @click="router.push('/user/notes')">查看我的笔记 →</button>
        <button type="button" class="ov__link" @click="router.push('/user/feedback')">提交问题反馈 →</button>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.ov__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: clamp(12px, 1.6vw, 20px);
  margin-bottom: 24px;
}

.ov__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px 16px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  cursor: pointer;
  @include hover-lift(-6px, 1.01);
}

.ov__stat-icon {
  font-size: 26px;
}

.ov__stat-num {
  margin-top: 8px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(26px, 2.6vw, 38px);
  color: var(--sy-accent);
}

.ov__stat-label {
  margin-top: 4px;
  font-size: $fs-small;
  color: rgba(var(--sy-text-rgb), 0.6);
}

.ov__row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: clamp(16px, 2vw, 24px);
}

.panel {
  padding: clamp(18px, 2vw, 26px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  box-shadow: $shadow-md;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 18px;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
}

.panel__more {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $color-gold;
  cursor: pointer;

  &:hover {
    color: var(--sy-accent);
  }
}

.ov__recent {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 0;
  margin: 0;
  list-style: none;
}

.ov__recent-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.14);
  }
}

.ov__recent-img {
  width: 54px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
}

.ov__recent-info {
  display: flex;
  flex-direction: column;
  min-width: 0;

  b {
    font-size: 14px;
    color: $color-text-dark;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 12px;
    color: rgba(var(--sy-text-rgb), 0.5);
    @include text-ellipsis(1);
  }
}

.ov__empty {
  padding: 30px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.ov__content {
  display: flex;
  gap: 20px;
  margin-bottom: 18px;
}

.ov__content-item {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 16px;
  border-radius: $radius-md;
  background: rgba(212, 179, 116, 0.12);
  text-align: center;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: 30px;
    color: var(--sy-accent);
  }

  span {
    margin-top: 4px;
    font-size: 12px;
    color: rgba(var(--sy-text-rgb), 0.6);
  }
}

.ov__link {
  display: block;
  width: 100%;
  padding: 10px 0;
  margin-top: 10px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-md;
  background: transparent;
  font-size: 13px;
  color: var(--sy-accent);
  cursor: pointer;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
}

@media (max-width: 1000px) {
  .ov__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .ov__row {
    grid-template-columns: 1fr;
  }
}
</style>
