<script setup lang="ts">
/**
 * 用户后台 · 我的行程（仅查看）
 *
 * 展示自己生成的行程，可展开查看每日明细。
 */
import { onMounted, ref } from 'vue'

import request from '@/api/request'
import { formatDateTime } from '@/user/utils/format'

interface TripDay {
  id: number
  dayIndex?: number
  date?: string
  scenicName?: string
  stayDuration?: string
  transport?: string
  food?: string
  tips?: string
}
interface Trip {
  id: number
  title?: string
  days?: number
  companion?: string
  interests?: string
  budget?: number
  status?: number
  coverImage?: string
  createTime?: string
  dayList?: TripDay[]
}

const trips = ref<Trip[]>([])
const loading = ref(false)
/** 展开的行程ID集合 */
const expanded = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/trip', { silent: true })
    trips.value = res.data ?? []
  } catch {
    trips.value = []
  } finally {
    loading.value = false
  }
}

function toggle(id: number) {
  const i = expanded.value.indexOf(id)
  if (i >= 0) {
    expanded.value.splice(i, 1)
  } else {
    expanded.value.push(id)
  }
}

onMounted(load)
</script>

<template>
  <div class="tr">
    <p v-if="loading" class="tr__state">加载中…</p>
    <p v-else-if="!trips.length" class="tr__state">还没有行程，去 AI 管家规划一条吧</p>

    <article v-for="t in trips" :key="t.id" class="tr__item">
      <header class="tr__head" @click="toggle(t.id)">
        <img v-if="t.coverImage" class="tr__cover" :src="t.coverImage" alt="" loading="lazy" />
        <div class="tr__info">
          <h3 class="tr__title">{{ t.title || '未命名行程' }}</h3>
          <p class="tr__meta">
            <span v-if="t.days">{{ t.days }} 天</span>
            <span v-if="t.companion">同行 · {{ t.companion }}</span>
            <span v-if="t.budget">预算 ￥{{ t.budget }}</span>
            <span>{{ formatDateTime(t.createTime) }}</span>
          </p>
        </div>
        <span class="tr__arrow" :class="{ 'is-open': expanded.includes(t.id) }">▾</span>
      </header>

      <div v-if="expanded.includes(t.id)" class="tr__days">
        <div v-for="d in t.dayList" :key="d.id" class="tr__day">
          <span class="tr__day-idx">D{{ d.dayIndex }}</span>
          <div class="tr__day-body">
            <b>{{ d.scenicName || '自由活动' }}</b>
            <em>
              <span v-if="d.date">📅 {{ d.date }}</span>
              <span v-if="d.stayDuration">⏱ {{ d.stayDuration }}</span>
              <span v-if="d.transport">🚌 {{ d.transport }}</span>
            </em>
            <p v-if="d.food" class="tr__day-line">🍜 {{ d.food }}</p>
            <p v-if="d.tips" class="tr__day-line">💡 {{ d.tips }}</p>
          </div>
        </div>
        <p v-if="!t.dayList?.length" class="tr__day-empty">暂无每日明细</p>
      </div>
    </article>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.tr__state {
  padding: 60px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.tr__item {
  margin-bottom: 14px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  overflow: hidden;
  transition: box-shadow $duration-normal $ease-out-expo;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.tr__head {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 16px;
  cursor: pointer;
}

.tr__cover {
  width: 76px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
}

.tr__info {
  flex: 1;
  min-width: 0;
}

.tr__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 17px;
  color: var(--sy-accent);
  @include text-ellipsis(1);
}

.tr__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.tr__arrow {
  font-size: 14px;
  color: $color-gold;
  transition: transform $duration-fast $ease-out-expo;

  &.is-open {
    transform: rotate(180deg);
  }
}

.tr__days {
  padding: 4px 16px 16px;
  border-top: 1px dashed rgba(212, 179, 116, 0.4);
}

.tr__day {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(212, 179, 116, 0.16);

  &:last-child {
    border-bottom: none;
  }
}

.tr__day-idx {
  flex: 0 0 auto;
  padding: 2px 10px;
  height: fit-content;
  border-radius: $radius-pill;
  background: rgba(122, 26, 34, 0.1);
  font-size: 12px;
  font-weight: 700;
  color: var(--sy-accent);
}

.tr__day-body {
  min-width: 0;

  b {
    font-size: 14px;
    color: $color-text-dark;
  }

  em {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 4px;
    font-style: normal;
    font-size: 12px;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.tr__day-line {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.62);
}

.tr__day-empty {
  padding: 16px 0;
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}
</style>
