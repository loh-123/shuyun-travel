<script setup lang="ts">
/**
 * 用户后台 · 我的笔记（仅查看）
 *
 * 支持关键词搜索与分页。
 */
import { computed, onMounted, ref } from 'vue'

import request from '@/api/request'
import { formatDateTime } from '@/user/utils/format'

interface Note {
  id: number
  title?: string
  content?: string
  targetName?: string
  tags?: string
  createTime?: string
}

const list = ref<Note[]>([])
const total = ref(0)
const page = ref(1)
const size = 8
const keyword = ref('')
const loading = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/note', {
      silent: true,
      params: { page: page.value, size, keyword: keyword.value || undefined },
    })
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next: number) {
  if (next < 1 || next > pageCount.value) {
    return
  }
  page.value = next
  load()
}

/** 正文摘要 */
function summary(text?: string) {
  if (!text) {
    return ''
  }
  const plain = text.replace(/[#>*`\-]/g, ' ').replace(/\s+/g, ' ').trim()
  return plain.length > 80 ? `${plain.slice(0, 80)}…` : plain
}

onMounted(load)
</script>

<template>
  <div class="nt">
    <header class="nt__head">
      <input
        v-model="keyword"
        class="nt__search"
        type="text"
        placeholder="搜索标题 / 正文 / 标签"
        @keyup.enter="search"
      />
      <button type="button" class="nt__search-btn" @click="search">搜索</button>
    </header>

    <p v-if="loading" class="nt__state">加载中…</p>
    <p v-else-if="!list.length" class="nt__state">还没有笔记</p>

    <div v-else class="nt__list">
      <article v-for="n in list" :key="n.id" class="nt__item">
        <h3 class="nt__title">{{ n.title || '未命名笔记' }}</h3>
        <p class="nt__summary">{{ summary(n.content) }}</p>
        <div class="nt__meta">
          <span v-if="n.targetName" class="nt__link">关联 · {{ n.targetName }}</span>
          <span v-if="n.tags" class="nt__tags">{{ n.tags }}</span>
          <span class="nt__time">{{ formatDateTime(n.createTime) }}</span>
        </div>
      </article>
    </div>

    <div v-if="pageCount > 1" class="nt__pager">
      <button type="button" :disabled="page === 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ pageCount }}</span>
      <button type="button" :disabled="page === pageCount" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.nt__head {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.nt__search {
  flex: 1;
  height: 42px;
  padding: 0 16px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.85);
  font-size: 14px;
  outline: none;

  &:focus {
    border-color: $color-gold;
  }
}

.nt__search-btn {
  padding: 0 22px;
  border: 1px solid $color-gold;
  border-radius: $radius-md;
  background: rgba(212, 179, 116, 0.16);
  font-size: 14px;
  color: var(--sy-accent);
  cursor: pointer;

  &:hover {
    background: rgba(212, 179, 116, 0.3);
  }
}

.nt__state {
  padding: 60px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.nt__list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: clamp(14px, 1.8vw, 20px);
}

.nt__item {
  padding: 18px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  transition: box-shadow $duration-fast $ease-out-expo;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.nt__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 16px;
  color: var(--sy-accent);
  @include text-ellipsis(1);
}

.nt__summary {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(var(--sy-text-rgb), 0.62);
  @include text-ellipsis(3);
}

.nt__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.45);
}

.nt__link {
  color: $color-gold;
}

.nt__tags {
  padding: 2px 8px;
  border-radius: $radius-pill;
  background: rgba(212, 179, 116, 0.16);
  color: var(--sy-accent);
}

.nt__time {
  margin-left: auto;
}

.nt__pager {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.6);

  button {
    padding: 7px 16px;
    border: 1px solid $color-border-gold-light;
    border-radius: $radius-pill;
    background: transparent;
    font-size: 13px;
    color: var(--sy-accent);
    cursor: pointer;

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}
</style>
