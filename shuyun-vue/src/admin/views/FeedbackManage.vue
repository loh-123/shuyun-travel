<script setup lang="ts">
/**
 * 后台 · 问题反馈管理
 *
 * 查看全部用户反馈，支持按状态 / 关键词筛选，并可回复处理。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn } from '@/admin/components/DataTable.vue'

interface FeedbackRow {
  id: number
  username: string
  type?: string
  content?: string
  contact?: string
  status: number
  reply?: string
  createTime?: string
}

const columns: DataColumn[] = [
  { key: 'username', label: '提交人', width: '120px' },
  { key: 'type', label: '类型', width: '110px' },
  { key: 'content', label: '内容' },
  { key: 'contact', label: '联系方式', width: '150px' },
  { key: 'createTime', label: '时间', width: '160px' },
  { key: 'status', label: '状态', width: '100px', align: 'center' },
  { key: 'action', label: '操作', width: '120px', align: 'center' },
]

const rows = ref<FeedbackRow[]>([])
const loading = ref(false)

/** 筛选条件 */
const statusFilter = ref<'all' | 0 | 1>('all')
const keyword = ref('')

/** 状态筛选项 */
const STATUS_OPTIONS: { label: string; value: 'all' | 0 | 1 }[] = [
  { label: '全部', value: 'all' },
  { label: '待处理', value: 0 },
  { label: '已处理', value: 1 },
]

/** 分页 */
const page = ref(1)
const size = 10
const total = ref(0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

/** 处理弹窗 */
const dialogOpen = ref(false)
const current = ref<FeedbackRow | null>(null)
const replyText = ref('')

/** 加载列表 */
async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/feedback', {
      params: {
        page: page.value,
        size,
        status: statusFilter.value === 'all' ? undefined : statusFilter.value,
        keyword: keyword.value || undefined,
      },
    })
    rows.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch {
    rows.value = []
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

/** 打开处理弹窗 */
function openHandle(row: FeedbackRow) {
  current.value = row
  replyText.value = row.reply || ''
  dialogOpen.value = true
}

/** 提交处理 */
async function submitHandle(done: boolean) {
  if (!current.value) {
    return
  }
  try {
    await adminRequest.put(`/admin/feedback/${current.value.id}`, {
      status: done ? 1 : 0,
      reply: replyText.value,
    })
    ElMessage.success(done ? '已标记为已处理' : '已保存')
    dialogOpen.value = false
    await load()
  } catch {
    // 提示由拦截器处理
  }
}

/** 时间格式化 */
function fmt(value?: string) {
  if (!value) {
    return '—'
  }
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

onMounted(load)
</script>

<template>
  <div class="fbm">
    <!-- 页头 -->
    <div class="fbm__head">
      <div>
        <h2 class="fbm__title">问题反馈</h2>
        <p class="fbm__sub">查看并处理用户提交的问题与建议</p>
      </div>
      <div class="fbm__filters">
        <div class="seg">
          <button
            v-for="s in STATUS_OPTIONS"
            :key="s.label"
            type="button"
            class="seg__btn"
            :class="{ 'is-active': statusFilter === s.value }"
            @click="statusFilter = s.value; search()"
          >
            {{ s.label }}
          </button>
        </div>
        <input v-model="keyword" class="fbm__search" type="text" placeholder="搜索反馈内容…" @keyup.enter="search" />
        <button type="button" class="fbm__search-btn" @click="search">查询</button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="fbm__panel">
      <DataTable :columns="columns" :rows="rows" :loading="loading" empty-text="暂无反馈">
        <template #content="{ row }">
          <span class="fbm__content" :title="row.content">{{ row.content }}</span>
        </template>
        <template #contact="{ row }">{{ row.contact || '—' }}</template>
        <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>
        <template #status="{ row }">
          <span class="badge" :class="row.status === 1 ? 'is-done' : 'is-pending'">
            {{ row.status === 1 ? '已处理' : '待处理' }}
          </span>
        </template>
        <template #action="{ row }">
          <button type="button" class="fbm__handle" @click="openHandle(row)">处理</button>
        </template>
      </DataTable>

      <div v-if="pageCount > 1" class="fbm__pager">
        <button type="button" :disabled="page === 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ pageCount }}</span>
        <button type="button" :disabled="page === pageCount" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>

    <!-- 处理弹窗 -->
    <transition name="fade">
      <div v-if="dialogOpen" class="fbm__mask" @click.self="dialogOpen = false">
        <div class="fbm__dialog">
          <h3 class="fbm__dialog-title">处理反馈</h3>
          <p class="fbm__dialog-meta">
            <span>{{ current?.username }}</span> · {{ current?.type || '其他' }}
          </p>
          <p class="fbm__dialog-content">{{ current?.content }}</p>

          <label class="fbm__dialog-label">回复内容（可选）</label>
          <textarea v-model="replyText" class="fbm__dialog-text" rows="4" placeholder="填写回复后，用户可在用户后台看到…" />

          <div class="fbm__dialog-actions">
            <button type="button" class="ghost" @click="dialogOpen = false">取消</button>
            <button type="button" class="ghost" @click="submitHandle(false)">暂不处理</button>
            <button type="button" class="primary" @click="submitHandle(true)">标记已处理</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.fbm__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.fbm__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.fbm__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.fbm__filters {
  display: flex;
  gap: 10px;
  align-items: center;
}

.seg {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
}

.seg__btn {
  padding: 7px 16px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;
  transition:
    color 0.3s $admin-ease,
    background-color 0.3s $admin-ease;

  &:hover {
    color: $admin-gold;
  }

  &.is-active {
    color: #241a08;
    background: linear-gradient(120deg, #b98f45, $admin-gold);
  }
}

.fbm__search {
  width: 220px;
  height: 40px;
  padding: 0 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  font-size: 14px;
  color: $admin-text;
  outline: none;

  &:focus {
    border-color: $admin-gold-line;
  }
}

.fbm__search-btn {
  height: 40px;
  padding: 0 18px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    background: rgba(212, 179, 116, 0.2);
  }
}

.fbm__panel {
  padding: 20px;

  @include admin-card;
}

.fbm__content {
  display: inline-block;
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.badge {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;

  &.is-pending {
    background: rgba(208, 92, 98, 0.16);
    color: $admin-red-down;
  }

  &.is-done {
    background: rgba(58, 90, 64, 0.3);
    color: #8fcea6;
  }
}

.fbm__handle {
  padding: 6px 14px;
  border: 1px solid $admin-gold-line;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    background: $admin-gold-soft;
  }
}

.fbm__pager {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  margin-top: 18px;
  font-size: 13px;
  color: $admin-text-muted;

  button {
    padding: 7px 16px;
    border: 1px solid $admin-border;
    border-radius: 8px;
    background: transparent;
    font-size: 13px;
    color: $admin-gold;
    cursor: pointer;

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}

/* 弹窗 */
.fbm__mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(6, 12, 22, 0.72);
  backdrop-filter: blur(6px);
}

.fbm__dialog {
  width: min(560px, 100%);
  padding: 26px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.fbm__dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: $admin-text;
}

.fbm__dialog-meta {
  margin-top: 6px;
  font-size: 13px;
  color: $admin-gold;
}

.fbm__dialog-content {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  font-size: 14px;
  line-height: 1.8;
  color: $admin-text;
}

.fbm__dialog-label {
  display: block;
  margin: 18px 0 8px;
  font-size: 13px;
  color: $admin-text-muted;
}

.fbm__dialog-text {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  font-family: inherit;
  font-size: 14px;
  color: $admin-text;
  resize: vertical;
  outline: none;

  &:focus {
    border-color: $admin-gold-line;
  }
}

.fbm__dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;

  button {
    padding: 9px 20px;
    border-radius: $admin-radius-sm;
    font-size: 14px;
    cursor: pointer;
  }

  .ghost {
    border: 1px solid $admin-border;
    background: transparent;
    color: $admin-text-muted;

    &:hover {
      color: $admin-gold;
    }
  }

  .primary {
    border: none;
    background: linear-gradient(120deg, #b98f45, $admin-gold);
    font-weight: 700;
    color: #241a08;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s $admin-ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
