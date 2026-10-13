<script setup lang="ts">
/**
 * 用户后台 · 浏览历史
 *
 * 分页展示浏览足迹，支持删除单条与清空全部。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import request from '@/api/request'
import { formatDateTime } from '@/user/utils/format'

interface HistoryItem {
  id: number
  targetType: string
  targetId: number
  targetName?: string
  coverImage?: string
  subTitle?: string
  viewTime?: string
}

const router = useRouter()

const list = ref<HistoryItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/history', {
      silent: true,
      params: { page: page.value, size },
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

function changePage(next: number) {
  if (next < 1 || next > pageCount.value) {
    return
  }
  page.value = next
  load()
}

/** 打开对应详情 */
function open(item: HistoryItem) {
  if (item.targetType === 'CULTURE') {
    router.push(`/culture/${item.targetId}`)
  } else {
    router.push(`/scenic/${item.targetId}`)
  }
}

/** 删除一条 */
async function remove(item: HistoryItem) {
  try {
    await request.delete(`/user/history/${item.id}`)
    ElMessage.success('已移除该足迹')
    await load()
  } catch {
    // 提示由拦截器处理
  }
}

/** 清空全部 */
async function clearAll() {
  if (!total.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定清空全部浏览历史吗？清空后不可恢复。', '提示', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await request.delete('/user/history')
    ElMessage.success('已清空')
    page.value = 1
    await load()
  } catch {
    // 提示由拦截器处理
  }
}

onMounted(load)
</script>

<template>
  <div class="hi">
    <header class="hi__head">
      <span class="hi__count">共 {{ total }} 条浏览记录</span>
      <button type="button" class="hi__clear" @click="clearAll">清空历史</button>
    </header>

    <p v-if="loading" class="hi__state">加载中…</p>
    <p v-else-if="!list.length" class="hi__state">还没有浏览记录，去景点逛逛吧</p>

    <ul v-else class="hi__list">
      <li v-for="h in list" :key="h.id" class="hi__item">
        <img v-if="h.coverImage" class="hi__img" :src="h.coverImage" alt="" loading="lazy" @click="open(h)" />
        <span class="hi__info">
          <b @click="open(h)">{{ h.targetName || '未命名' }}</b>
          <em>{{ h.targetType === 'CULTURE' ? '文化' : '景点' }} · {{ h.subTitle || '—' }}</em>
          <span class="hi__time">{{ formatDateTime(h.viewTime) }}</span>
        </span>
        <button type="button" class="hi__del" title="移除" @click="remove(h)">✕</button>
      </li>
    </ul>

    <div v-if="pageCount > 1" class="hi__pager">
      <button type="button" :disabled="page === 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ pageCount }}</span>
      <button type="button" :disabled="page === pageCount" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.hi__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.hi__count {
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.hi__clear {
  padding: 7px 18px;
  border: 1px solid rgba(122, 26, 34, 0.4);
  border-radius: $radius-pill;
  background: transparent;
  font-size: 13px;
  color: var(--sy-accent);
  cursor: pointer;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.1);
  }
}

.hi__state {
  padding: 60px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.hi__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 0;
  margin: 0;
  list-style: none;
}

.hi__item {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  transition: box-shadow $duration-fast $ease-out-expo;

  &:hover {
    box-shadow: $shadow-sm;
  }
}

.hi__img {
  width: 72px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  cursor: pointer;
}

.hi__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;

  b {
    font-size: 15px;
    color: $color-text-dark;
    cursor: pointer;
    @include text-ellipsis(1);
  }

  em {
    margin-top: 3px;
    font-style: normal;
    font-size: 12px;
    color: rgba(var(--sy-text-rgb), 0.5);
  }
}

.hi__time {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.4);
}

.hi__del {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: rgba(var(--sy-text-rgb), 0.4);
  cursor: pointer;

  &:hover {
    color: var(--sy-accent);
    background: rgba(122, 26, 34, 0.1);
  }
}

.hi__pager {
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
