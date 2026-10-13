<script setup lang="ts">
/**
 * 后台 · AI 对话管理
 *
 * 功能：顶部统计卡片、筛选（关键词 / 意图 / 时间范围）、会话列表、
 * 详情抽屉（微信式对话气泡）、标记优质/训练语料、删除、多选导出 JSON 训练语料。
 *
 * 接口：
 *   GET    /api/admin/ai/chat/stats
 *   GET    /api/admin/ai/chat/list
 *   GET    /api/admin/ai/chat/{sessionId}
 *   POST   /api/admin/ai/chat/{sessionId}/mark
 *   DELETE /api/admin/ai/chat/{sessionId}
 *   GET    /api/admin/ai/chat/export?ids=
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import RadialStatCard from '@/admin/components/RadialStatCard.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 会话行 */
interface SessionRow {
  sessionId: string
  userId?: number
  username?: string
  avatar?: string
  firstQuestion?: string
  messageCount?: number
  durationSeconds?: number
  intent?: string
  markType?: number
  lastTime?: string
}

/** 对话消息 */
interface ChatMessage {
  id: number
  role: string
  content: string
  createTime?: string
}

/** 列定义 */
const columns: DataColumn[] = [
  { key: 'idx', label: 'ID', width: '64px', align: 'center' },
  { key: 'sessionId', label: '会话 ID', width: '160px' },
  { key: 'user', label: '用户', width: '150px' },
  { key: 'firstQuestion', label: '首条提问', width: '280px' },
  { key: 'messageCount', label: '消息数', width: '80px', align: 'center' },
  { key: 'durationSeconds', label: '时长', width: '90px', align: 'center' },
  { key: 'intent', label: '意图分类', width: '110px', align: 'center' },
  { key: 'lastTime', label: '时间', width: '160px' },
  { key: 'action', label: '操作', width: '200px', align: 'center' },
]

/** 意图选项 */
const INTENT_OPTIONS = ['行程规划', '文化科普', '天气关怀', '美食推荐', '其他']

/* ------------------------------ 统计 ------------------------------ */
const stats = ref({ total: 0, today: 0, avgRounds: 0, quality: 0, spark: [] as number[] })

/** 近 7 日走势：今日 vs 昨日的环比 */
const chatTrend = computed(() => {
  const s = stats.value.spark
  if (!Array.isArray(s) || s.length < 2) {
    return 0
  }
  const today = s[s.length - 1] ?? 0
  const yesterday = s[s.length - 2] ?? 0
  if (yesterday <= 0) {
    return today > 0 ? 100 : 0
  }
  return ((today - yesterday) / yesterday) * 100
})

/** 优质对话占比（%） */
const qualityRate = computed(() =>
  stats.value.total > 0 ? Math.round((stats.value.quality / stats.value.total) * 1000) / 10 : 0,
)

/** 顶部 4 张卡的径向进度环百分比（按各自目标折算） */
const ring = computed(() => ({
  total: Math.min(100, (stats.value.total / 500) * 100),
  today: Math.min(100, (stats.value.today / 30) * 100),
  rounds: Math.min(100, (stats.value.avgRounds / 2) * 100),
  quality: stats.value.total > 0 ? (stats.value.quality / stats.value.total) * 100 : 0,
}))

async function loadStats() {
  try {
    const res = await adminRequest.get('/admin/ai/chat/stats')
    stats.value = { total: 0, today: 0, avgRounds: 0, quality: 0, ...res.data }
  } catch {
    // 拦截器已提示
  }
}

/* ------------------------------ 列表 ------------------------------ */
const rows = ref<SessionRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const selected = ref<string[]>([])
const removingId = ref<string | null>(null)
const pagination = computed<Pagination>(() => ({ page: page.value, size: size.value, total: total.value }))

const keyword = ref('')
const intent = ref('')
const dateStart = ref('')
const dateEnd = ref('')

async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/ai/chat/list', {
      params: {
        keyword: keyword.value || undefined,
        intent: intent.value || undefined,
        startDate: dateStart.value || undefined,
        endDate: dateEnd.value || undefined,
        page: page.value,
        size: size.value,
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

function resetFilters() {
  keyword.value = ''
  intent.value = ''
  dateStart.value = ''
  dateEnd.value = ''
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

/* ------------------------------ 标记 / 删除 ------------------------------ */
async function mark(row: SessionRow, markType: number) {
  const next = row.markType === markType ? 0 : markType
  try {
    await adminRequest.post(`/admin/ai/chat/${row.sessionId}/mark`, { markType: next })
    row.markType = next
    if (detail.value && detail.value.sessionId === row.sessionId) {
      detail.value.markType = next
    }
    ElMessage.success(next === 0 ? '已取消标记' : next === 1 ? '已标记为优质对话' : '已标记为训练语料')
  } catch {
    // 拦截器已提示
  }
}

async function removeRow(row: SessionRow) {
  try {
    await ElMessageBox.confirm('确定删除该会话的全部对话记录吗？该操作为软删除。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  removingId.value = row.sessionId
  await delay(340)
  try {
    await adminRequest.delete(`/admin/ai/chat/${row.sessionId}`)
    ElMessage.success('已删除')
    selected.value = selected.value.filter((id) => id !== row.sessionId)
    await Promise.all([load(), loadStats()])
  } finally {
    removingId.value = null
  }
}

/* ------------------------------ 详情抽屉 ------------------------------ */
const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<SessionRow | null>(null)
const messages = ref<ChatMessage[]>([])

async function openDetail(row: SessionRow) {
  drawerOpen.value = true
  detail.value = row
  messages.value = []
  detailLoading.value = true
  try {
    const res = await adminRequest.get(`/admin/ai/chat/${row.sessionId}`)
    detail.value = res.data?.session ?? row
    messages.value = res.data?.messages ?? []
  } catch {
    messages.value = []
  } finally {
    detailLoading.value = false
  }
}

/* ------------------------------ 导出训练语料 ------------------------------ */
const exporting = ref(false)

/** 下载指定会话的训练语料 */
async function downloadCorpus(ids: string[]) {
  if (!ids.length) {
    ElMessage.warning('请先选择要导出的会话')
    return
  }
  exporting.value = true
  try {
    const res: any = await adminRequest.get('/admin/ai/chat/export', {
      params: { ids: ids.join(',') },
      responseType: 'blob',
    })
    const blob = new Blob([res.data], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `training_corpus_${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

/** 批量导出选中会话 */
function exportCorpus() {
  return downloadCorpus(selected.value)
}

/** 导出单个会话 */
function exportSession(sessionId: string) {
  return downloadCorpus([sessionId])
}

/** 批量删除选中会话 */
async function batchDelete() {
  if (!selected.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 段会话吗？该操作为软删除。`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await Promise.all(selected.value.map((id) => adminRequest.delete(`/admin/ai/chat/${id}`)))
  ElMessage.success('已批量删除')
  selected.value = []
  await Promise.all([load(), loadStats()])
}

/* ------------------------------ 工具 ------------------------------ */
function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function fmt(value?: string) {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 时长格式化 */
function fmtDuration(seconds?: number) {
  const s = seconds ?? 0
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  const rest = s % 60
  return `${m} 分 ${rest} 秒`
}

/** 意图强调色 */
function intentColor(name?: string) {
  switch (name) {
    case '行程规划':
      return '#d4b374'
    case '文化科普':
      return '#7a1a22'
    case '天气关怀':
      return '#4a7db5'
    case '美食推荐':
      return '#4caf7d'
    default:
      return '#7a8aa0'
  }
}

onMounted(() => {
  loadStats()
  load()
})
</script>

<template>
  <div class="am">
    <!-- 统计卡片 -->
    <section class="am__stats rstats">
      <RadialStatCard
        title="总对话数"
        eyebrow="CHAT · TOTAL"
        :value="stats.total"
        :trend="chatTrend"
        :percent="ring.total"
        color="#D4B374"
      />
      <RadialStatCard
        title="今日对话"
        eyebrow="CHAT · TODAY"
        :value="stats.today"
        :trend="chatTrend"
        :percent="ring.today"
        color="#16A34A"
      />
      <RadialStatCard
        title="平均轮次"
        eyebrow="CHAT · AVG ROUNDS"
        :value="stats.avgRounds"
        :decimals="1"
        suffix="轮"
        :show-trend="false"
        :hint="`样本 ${stats.total} 段会话`"
        :percent="ring.rounds"
        color="#8C1F28"
      />
      <RadialStatCard
        title="优质对话数"
        eyebrow="CHAT · FLAGGED"
        :value="stats.quality"
        :show-trend="false"
        :hint="`优质率 ${qualityRate}%`"
        :percent="ring.quality"
        color="#4A90E2"
      />
    </section>

    <header class="am__head">
      <div>
        <h2 class="am__title">AI 对话管理</h2>
        <p class="am__sub">共 {{ total }} 段会话</p>
      </div>
      <button type="button" class="am__export" :disabled="exporting" @click="exportCorpus">
        {{ exporting ? '导出中…' : '导出训练语料' }}
      </button>
    </header>

    <!-- 筛选栏 -->
    <section class="am__filter">
      <input v-model="keyword" class="am__input" type="text" placeholder="搜索用户名 / 关键词" @keyup.enter="search" />
      <select v-model="intent" class="am__select">
        <option value="">全部意图</option>
        <option v-for="i in INTENT_OPTIONS" :key="i" :value="i">{{ i }}</option>
      </select>
      <div class="am__range">
        <input v-model="dateStart" class="am__input am__input--date" type="date" />
        <span class="am__sep">至</span>
        <input v-model="dateEnd" class="am__input am__input--date" type="date" />
      </div>
      <button type="button" class="am__btn" @click="search">查询</button>
      <button type="button" class="am__btn am__btn--ghost" @click="resetFilters">重置</button>
    </section>

    <!-- 批量提示 -->
    <transition name="am-slide">
      <div v-if="selected.length" class="am__batch">
        <span>已选 {{ selected.length }} 段会话</span>
        <button type="button" class="am__batch-btn" :disabled="exporting" @click="exportCorpus">批量导出 JSON</button>
        <button type="button" class="am__batch-btn am__batch-btn--danger" @click="batchDelete">批量删除</button>
        <button type="button" class="am__batch-cancel" @click="selected = []">取消选择</button>
      </div>
    </transition>

    <!-- 表格 -->
    <section class="am__panel">
      <DataTable
        v-model:selected="selected"
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :removing-key="removingId"
        selectable
        row-key="sessionId"
        empty-text="暂无对话记录"
        @page-change="changePage"
      >
        <template #idx="{ index }">{{ (page - 1) * size + index + 1 }}</template>
        <template #sessionId="{ row }">
          <span class="am__sid" :title="row.sessionId">{{ row.sessionId.slice(0, 12) }}…</span>
        </template>
        <template #user="{ row }">
          <div class="am__user">
            <img v-if="row.avatar" class="am__avatar" :src="row.avatar" :alt="row.username" />
            <span v-else class="am__avatar am__avatar--letter">{{ (row.username || '?').charAt(0) }}</span>
            <span class="am__username">{{ row.username || '未知用户' }}</span>
          </div>
        </template>
        <template #firstQuestion="{ row }">
          <span class="am__question" :title="row.firstQuestion">{{ row.firstQuestion }}</span>
        </template>
        <template #messageCount="{ row }">{{ row.messageCount ?? 0 }}</template>
        <template #durationSeconds="{ row }">{{ fmtDuration(row.durationSeconds) }}</template>
        <template #intent="{ row }">
          <span
            class="am__intent"
            :style="{ color: intentColor(row.intent), background: `${intentColor(row.intent)}22` }"
          >
            {{ row.intent }}
          </span>
        </template>
        <template #lastTime="{ row }">{{ fmt(row.lastTime) }}</template>

        <template #action="{ row }">
          <div class="am__actions">
            <button type="button" class="am__link" @click="openDetail(row)">查看</button>
            <button
              type="button"
              class="am__link"
              :class="{ 'is-marked': row.markType === 1 }"
              @click="mark(row, 1)"
            >
              标记优质
            </button>
            <button type="button" class="am__link am__link--danger" @click="removeRow(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 详情抽屉 -->
    <transition name="am-drawer">
      <div v-if="drawerOpen" class="am__drawer-mask" @click.self="drawerOpen = false">
        <aside class="am__drawer">
          <header class="am__drawer-head">
            <div>
              <h3>对话详情</h3>
              <p v-if="detail" class="am__drawer-sub">
                {{ detail.username }} · {{ detail.intent }} · {{ fmtDuration(detail.durationSeconds) }}
              </p>
            </div>
            <button type="button" class="am__drawer-close" @click="drawerOpen = false">×</button>
          </header>

          <div v-if="detailLoading" class="am__empty">加载中…</div>
          <div v-else class="am__chat">
            <div
              v-for="m in messages"
              :key="m.id"
              class="am__bubble-row"
              :class="m.role === 'user' ? 'is-user' : 'is-ai'"
            >
              <div class="am__bubble">
                <p class="am__bubble-text">{{ m.content }}</p>
                <span class="am__bubble-time">{{ fmt(m.createTime) }}</span>
              </div>
            </div>
            <div v-if="!messages.length" class="am__empty">暂无消息</div>
          </div>

          <footer v-if="detail" class="am__drawer-foot">
            <button
              type="button"
              class="am__foot-btn am__foot-btn--gold"
              :class="{ 'is-active': detail.markType === 1 }"
              @click="mark(detail, 1)"
            >
              标记为优质对话
            </button>
            <button type="button" class="am__foot-btn" @click="exportSession(detail.sessionId)">
              导出为训练语料
            </button>
          </footer>
        </aside>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

/* 顶部统计卡容器：极浅灰底 + 8px 圆角 */
.rstats {
  padding: 16px;
  border-radius: 8px;
  background: #fafafa;
}

.am__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin-bottom: 22px;
}

.am__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.am__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.am__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.am__export {
  padding: 10px 22px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: transparent;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: $admin-gold-soft;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.am__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  margin-bottom: 16px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius;
  @include admin-glass;
}

.am__input,
.am__select {
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

.am__input--date {
  width: 150px;
  color-scheme: dark;
}

.am__select {
  color-scheme: dark;
  cursor: pointer;
}

.am__range {
  display: flex;
  gap: 8px;
  align-items: center;
}

.am__sep {
  color: $admin-text-muted;
}

.am__btn {
  height: 40px;
  padding: 0 20px;
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

.am__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;
}

.am__batch {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 18px;
  margin-bottom: 14px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
}

.am__batch-btn {
  padding: 6px 14px;
  border: 1px solid $admin-gold-line;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  color: $admin-text;
  cursor: pointer;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }
}

.am__batch-btn--danger {
  border-color: rgba(208, 92, 98, 0.5);
  color: $admin-red-down;

  &:hover {
    background: rgba(208, 92, 98, 0.14);
  }
}

.am__batch-cancel {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;
}

.am__panel {
  padding: 20px;
  @include admin-card;
}

/* 用户：头像 + 用户名 */
.am__user {
  display: flex;
  gap: 10px;
  align-items: center;
}

.am__avatar {
  width: 30px;
  height: 30px;
  border: 1px solid $admin-gold-line;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.am__avatar--letter {
  display: inline-grid;
  place-items: center;
  background: $admin-gold-soft;
  font-size: 13px;
  font-weight: 700;
  color: $admin-gold;
}

.am__username {
  color: $admin-text;
}

.am__sid {
  font-family: $admin-font-num;
  font-size: 13px;
  color: $admin-text-muted;
}

.am__question {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.am__intent {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
}

.am__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.am__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }

  &.is-marked {
    color: $admin-green-up;
  }
}

.am__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 抽屉 */
.am__drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(6, 12, 22, 0.66);
  backdrop-filter: blur(4px);
}

.am__drawer {
  display: flex;
  flex-direction: column;
  width: min(600px, 100%);
  height: 100%;
  border-left: 1px solid $admin-gold-line;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.am__drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid $admin-border;

  h3 {
    font-size: 18px;
    color: $admin-text;
  }
}

.am__drawer-sub {
  margin-top: 6px;
  font-size: 13px;
  color: $admin-text-muted;
}

.am__drawer-close {
  border: none;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  color: $admin-text-muted;
  cursor: pointer;

  &:hover {
    color: $admin-gold;
  }
}

/* 聊天区 */
.am__chat {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
  background: rgba(var(--admin-mask-rgb), 0.45);
}

.am__bubble-row {
  display: flex;
  margin-bottom: 14px;

  &.is-user {
    justify-content: flex-end;
  }

  &.is-ai {
    justify-content: flex-start;
  }
}

.am__bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;

  .is-user & {
    background: linear-gradient(120deg, #b98f45, $admin-gold);
    color: #241a08;
  }

  .is-ai & {
    border: 1px solid #e6ddc9;
    background: #ffffff;
    color: #1a1a1a;
  }
}

.am__bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.am__bubble-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.65;
}

.am__drawer-foot {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid $admin-border;
}

.am__foot-btn {
  flex: 1;
  padding: 10px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: transparent;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;
  transition:
    color 0.25s $admin-ease,
    border-color 0.25s $admin-ease,
    background-color 0.25s $admin-ease;

  &:hover {
    color: $admin-gold;
  }

  &.is-active {
    border-color: $admin-gold;
    background: $admin-gold-soft;
    color: $admin-gold;
  }

  &--gold {
    border-color: $admin-gold-line;
    background: $admin-gold-soft;
    color: $admin-gold;
    font-weight: 700;
  }
}

.am__empty {
  padding: 32px 0;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

/* 过渡 */
.am-slide-enter-active,
.am-slide-leave-active {
  transition:
    opacity 0.28s $admin-ease,
    transform 0.28s $admin-ease;
}

.am-slide-enter-from,
.am-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.am-drawer-enter-active,
.am-drawer-leave-active {
  transition: opacity 0.3s $admin-ease;
}

.am-drawer-enter-active .am__drawer,
.am-drawer-leave-active .am__drawer {
  transition: transform 0.34s $admin-ease;
}

.am-drawer-enter-from,
.am-drawer-leave-to {
  opacity: 0;
}

.am-drawer-enter-from .am__drawer,
.am-drawer-leave-to .am__drawer {
  transform: translateX(100%);
}

/* 响应式 */
@media (max-width: 1200px) {
  .am__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .am__stats {
    grid-template-columns: 1fr;
  }

  .am__drawer {
    width: 100%;
  }
}
</style>
