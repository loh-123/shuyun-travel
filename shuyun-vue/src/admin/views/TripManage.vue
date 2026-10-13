<script setup lang="ts">
/**
 * 后台 · 行程管理
 *
 * 功能：顶部统计卡片（简约款：环比 + 动态 sparkline）、筛选（关键词 / 天数区间 / 同行类型）、
 * 行程列表、详情抽屉（每日明细 el-timeline 时间轴）、导出 PDF、删除。
 *
 * 接口：
 *   GET    /api/admin/trip/stats
 *   GET    /api/admin/trip/list?page=&size=&keyword=&days=&companion=
 *   GET    /api/admin/trip/{id}
 *   DELETE /api/admin/trip/{id}
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import SparkStatCard from '@/admin/components/SparkStatCard.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 行程行 */
interface TripRow {
  id: number
  userId?: number
  username?: string
  avatar?: string
  title?: string
  days?: number
  companion?: string
  interests?: string
  budget?: number
  status?: number
  coverImage?: string
  createTime?: string
  dayCount?: number
  scenicCount?: number
}

/** 每日明细 */
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

/** 列定义 */
const columns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'title', label: '行程标题', width: '220px' },
  { key: 'user', label: '所属用户', width: '160px' },
  { key: 'days', label: '天数', width: '80px', align: 'center' },
  { key: 'companion', label: '同行人', width: '100px', align: 'center' },
  { key: 'interests', label: '兴趣标签', width: '240px' },
  { key: 'createTime', label: '创建时间', width: '170px' },
  { key: 'action', label: '操作', width: '160px', align: 'center' },
]

/** 同行类型选项 */
const COMPANION_OPTIONS = ['独自', '情侣', '亲子', '父母', '朋友']
/** 天数区间选项 */
const DAYS_OPTIONS = [
  { label: '1-2 天', value: '1-2' },
  { label: '3-4 天', value: '3-4' },
  { label: '5-7 天', value: '5-7' },
  { label: '7 天以上', value: '7+' },
]

/* ------------------------------ 统计卡片 ------------------------------ */
const stats = ref({
  total: 0,
  today: 0,
  avgDays: 0,
  avgScenic: 0,
  totalTrend: 0,
  todayTrend: 0,
  avgDaysTrend: 0,
  avgScenicTrend: 0,
  /** 近 7 日每日新增行程（旧→新） */
  spark: [] as number[],
})

async function loadStats() {
  try {
    const res = await adminRequest.get('/admin/trip/stats')
    stats.value = { ...stats.value, ...res.data }
  } catch {
    // 提示由拦截器处理
  }
}

/** 四张卡片 sparkline 的基础序列（旧→新）；无真实序列时用固定波形占位 */
const sparkSeries = computed(() => ({
  total: stats.value.spark?.length >= 2 ? stats.value.spark : [12, 18, 15, 22, 19, 26, 21, 28],
  today: stats.value.spark?.length >= 2 ? stats.value.spark : [6, 9, 7, 12, 8, 14, 10, 13],
  avgDays: [3.6, 4.1, 3.8, 4.4, 4.0, 4.6, 4.2, 4.5],
  avgSpots: [3.2, 3.8, 3.5, 4.2, 3.9, 4.6, 4.1, 4.8],
}))

/* ------------------------------ 列表 ------------------------------ */
const rows = ref<TripRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const removingId = ref<number | null>(null)
const pagination = computed<Pagination>(() => ({ page: page.value, size: size.value, total: total.value }))

const keyword = ref('')
const daysFilter = ref('')
const companion = ref('')

async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/trip/list', {
      params: {
        keyword: keyword.value || undefined,
        days: daysFilter.value || undefined,
        companion: companion.value || undefined,
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
  daysFilter.value = ''
  companion.value = ''
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

/* ------------------------------ 详情抽屉 ------------------------------ */
const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<TripRow | null>(null)
const dayList = ref<TripDay[]>([])

async function openDetail(row: TripRow) {
  drawerOpen.value = true
  detail.value = row
  dayList.value = []
  detailLoading.value = true
  try {
    const res = await adminRequest.get(`/admin/trip/${row.id}`)
    detail.value = res.data ?? row
    dayList.value = res.data?.dayList ?? []
  } catch {
    dayList.value = []
  } finally {
    detailLoading.value = false
  }
}

/** 导出 PDF（浏览器打印为 PDF） */
function exportPdf() {
  if (!detail.value) return
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.warning('浏览器已拦截弹出窗口，请允许后重试')
    return
  }
  const daysHtml = dayList.value
    .map(
      (d, i) => `
      <div class="day">
        <h3>Day ${d.dayIndex ?? i + 1}${d.date ? ' · ' + d.date : ''}</h3>
        <p><b>景点：</b>${d.scenicName || '—'}${d.stayDuration ? '（停留 ' + d.stayDuration + '）' : ''}</p>
        <p><b>餐饮：</b>${d.food || '—'}</p>
        <p><b>交通：</b>${d.transport || '—'}</p>
        <p><b>小贴士：</b>${d.tips || '—'}</p>
      </div>`,
    )
    .join('')
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>行程详情</title>
    <style>
      body{font-family:'Microsoft YaHei',sans-serif;color:#0e2a47;padding:32px;}
      h1{font-size:22px;margin:0 0 6px;}
      .meta{color:#666;font-size:13px;margin-bottom:18px;}
      .overview{background:#f6f2ea;border:1px solid #d9c9a3;border-radius:8px;padding:12px 16px;margin-bottom:18px;font-size:14px;}
      .day{border-left:3px solid #d4b374;padding:6px 0 6px 14px;margin-bottom:14px;}
      .day h3{margin:0 0 6px;font-size:15px;color:#7a1a22;}
      .day p{margin:3px 0;font-size:13px;}
    </style></head><body>
    <h1>${detail.value.title || '未命名行程'}</h1>
    <div class="meta">用户：${detail.value.username || '未知用户'} ｜ 创建时间：${fmt(detail.value.createTime)}</div>
    <div class="overview">
      <b>天数：</b>${detail.value.days ?? '—'} 天　
      <b>同行：</b>${detail.value.companion || '—'}　
      <b>预算：</b>${detail.value.budget != null ? detail.value.budget + ' 元' : '—'}　
      <b>兴趣：</b>${detail.value.interests || '—'}
    </div>
    ${daysHtml || '<p>暂无每日明细</p>'}
    </body></html>`)
  win.document.close()
  win.focus()
  win.print()
}

/** 删除（软删除 + 动画） */
async function removeRow(row: TripRow) {
  try {
    await ElMessageBox.confirm(`确定删除行程「${row.title || row.id}」吗？该操作为软删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  removingId.value = row.id
  await delay(340)
  try {
    await adminRequest.delete(`/admin/trip/${row.id}`)
    ElMessage.success('已删除')
    if (detail.value?.id === row.id) drawerOpen.value = false
    await Promise.all([load(), loadStats()])
  } finally {
    removingId.value = null
  }
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

function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

onMounted(() => {
  loadStats()
  load()
})
</script>

<template>
  <div class="tm">
    <!-- 统计卡片 -->
    <section class="tm__stats">
      <SparkStatCard
        eyebrow="TRIP · TOTAL"
        label="总行程数"
        :value="stats.total"
        :trend="stats.totalTrend"
        color="#D4B374"
        :data="sparkSeries.total"
      />
      <SparkStatCard
        eyebrow="TRIP · TODAY"
        label="今日新增"
        :value="stats.today"
        :trend="stats.todayTrend"
        color="#16A34A"
        :data="sparkSeries.today"
      />
      <SparkStatCard
        eyebrow="TRIP · AVG DAYS"
        label="平均天数"
        :value="stats.avgDays"
        :decimals="1"
        suffix="天"
        :trend="stats.avgDaysTrend"
        color="#8C1F28"
        :data="sparkSeries.avgDays"
      />
      <SparkStatCard
        eyebrow="TRIP · AVG SPOTS"
        label="平均景点数"
        :value="stats.avgScenic"
        :decimals="1"
        suffix="个"
        :trend="stats.avgScenicTrend"
        color="#4A90E2"
        :data="sparkSeries.avgSpots"
      />
    </section>

    <header class="tm__head">
      <div>
        <h2 class="tm__title">行程管理</h2>
        <p class="tm__sub">共 {{ total }} 条行程</p>
      </div>
    </header>

    <!-- 筛选栏 -->
    <section class="tm__filter">
      <input v-model="keyword" class="tm__input" type="text" placeholder="搜索标题 / 用户名" @keyup.enter="search" />
      <select v-model="daysFilter" class="tm__select">
        <option value="">全部天数</option>
        <option v-for="d in DAYS_OPTIONS" :key="d.value" :value="d.value">{{ d.label }}</option>
      </select>
      <select v-model="companion" class="tm__select">
        <option value="">全部同行类型</option>
        <option v-for="c in COMPANION_OPTIONS" :key="c" :value="c">{{ c }}</option>
      </select>
      <button type="button" class="tm__btn" @click="search">查询</button>
      <button type="button" class="tm__btn tm__btn--ghost" @click="resetFilters">重置</button>
    </section>

    <!-- 表格 -->
    <section class="tm__panel">
      <DataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :removing-key="removingId"
        row-key="id"
        empty-text="暂无行程"
        @page-change="changePage"
      >
        <template #title="{ row }">{{ row.title || '未命名行程' }}</template>

        <!-- 所属用户：头像 + 用户名 -->
        <template #user="{ row }">
          <div class="tm__user">
            <img v-if="row.avatar" class="tm__avatar" :src="row.avatar" :alt="row.username" />
            <span v-else class="tm__avatar tm__avatar--letter">{{ (row.username || '?').charAt(0) }}</span>
            <span class="tm__username">{{ row.username || '未知用户' }}</span>
          </div>
        </template>

        <template #days="{ row }">{{ row.days ?? '—' }}</template>
        <template #companion="{ row }">{{ row.companion || '—' }}</template>

        <template #interests="{ row }">
          <div class="tm__tags">
            <span v-for="t in splitTags(row.interests)" :key="t" class="tm__tag">{{ t }}</span>
            <span v-if="!splitTags(row.interests).length">—</span>
          </div>
        </template>

        <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>

        <template #action="{ row }">
          <div class="tm__actions">
            <button type="button" class="tm__link" @click="openDetail(row)">查看详情</button>
            <button type="button" class="tm__link tm__link--danger" @click="removeRow(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 详情抽屉 -->
    <transition name="tm-drawer">
      <div v-if="drawerOpen" class="tm__drawer-mask" @click.self="drawerOpen = false">
        <aside class="tm__drawer">
          <header class="tm__drawer-head">
            <div class="tm__drawer-headmain">
              <h3>{{ detail?.title || '未命名行程' }}</h3>
              <p class="tm__drawer-sub">
                {{ detail?.username || '未知用户' }} · 创建于 {{ fmt(detail?.createTime) }}
              </p>
            </div>
            <button type="button" class="tm__drawer-close" @click="drawerOpen = false">×</button>
          </header>

          <div class="tm__drawer-body">
            <!-- 概览 -->
            <div v-if="detail" class="tm__overview">
              <div class="tm__ov-item"><label>天数</label><span>{{ detail.days ?? '—' }} 天</span></div>
              <div class="tm__ov-item"><label>同行</label><span>{{ detail.companion || '—' }}</span></div>
              <div class="tm__ov-item"><label>预算</label><span>{{ detail.budget != null ? detail.budget + ' 元' : '—' }}</span></div>
              <div class="tm__ov-item tm__ov-item--full">
                <label>兴趣标签</label>
                <span class="tm__tags">
                  <span v-for="t in splitTags(detail.interests)" :key="t" class="tm__tag">{{ t }}</span>
                  <span v-if="!splitTags(detail.interests).length">—</span>
                </span>
              </div>
            </div>

            <h4 class="tm__timeline-title">每日明细</h4>
            <div v-if="detailLoading" class="tm__empty">加载中…</div>
            <el-timeline v-else-if="dayList.length" class="tm__timeline">
              <el-timeline-item
                v-for="(day, i) in dayList"
                :key="day.id"
                color="#d4b374"
                placement="top"
                :timestamp="day.date"
                :hollow="false"
              >
                <div class="tm__tl-card">
                  <p class="tm__tl-day">Day {{ day.dayIndex ?? i + 1 }}</p>
                  <p class="tm__tl-row"><label>景点</label>{{ day.scenicName || '—' }}<em v-if="day.stayDuration">（停留 {{ day.stayDuration }}）</em></p>
                  <p class="tm__tl-row"><label>餐饮</label>{{ day.food || '—' }}</p>
                  <p class="tm__tl-row"><label>交通</label>{{ day.transport || '—' }}</p>
                  <p class="tm__tl-row"><label>小贴士</label>{{ day.tips || '—' }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="tm__empty">暂无每日明细</div>
          </div>

          <footer v-if="detail" class="tm__drawer-foot">
            <button type="button" class="tm__btn tm__btn--ghost" @click="exportPdf">导出 PDF</button>
            <button type="button" class="tm__btn tm__btn--danger" @click="removeRow(detail)">删除行程</button>
          </footer>
        </aside>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.tm__stats {
  display: grid;
  /* 按实际可用宽度自适应列数：够宽 4 列，否则自动折为 2 列（含后台 1.5 倍缩放场景） */
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
  margin-bottom: 22px;
  padding: 16px;
  border-radius: 12px;
  background: #fafafa;
}

.tm__head {
  margin-bottom: 18px;
}

.tm__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.tm__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.tm__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  margin-bottom: 18px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius;
  @include admin-glass;
}

.tm__input,
.tm__select {
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

.tm__select {
  color-scheme: dark;
  cursor: pointer;
}

.tm__btn {
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

.tm__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;
}

.tm__btn--danger {
  border-color: rgba(208, 92, 98, 0.5);
  background: rgba(208, 92, 98, 0.12);
  color: $admin-red-down;

  &:hover {
    background: rgba(208, 92, 98, 0.2);
  }
}

.tm__panel {
  padding: 20px;
  @include admin-card;
}

/* 用户 */
.tm__user {
  display: flex;
  gap: 10px;
  align-items: center;
}

.tm__avatar {
  width: 30px;
  height: 30px;
  border: 1px solid $admin-gold-line;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.tm__avatar--letter {
  display: inline-grid;
  place-items: center;
  background: $admin-gold-soft;
  font-size: 13px;
  font-weight: 700;
  color: $admin-gold;
}

.tm__username {
  color: $admin-text;
}

.tm__tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tm__tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: $admin-gold-soft;
  font-size: 12px;
  color: $admin-gold;
}

.tm__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.tm__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }
}

.tm__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 抽屉 */
.tm__drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(6, 12, 22, 0.66);
  backdrop-filter: blur(4px);
}

.tm__drawer {
  display: flex;
  flex-direction: column;
  width: min(720px, 100%);
  height: 100%;
  border-left: 1px solid $admin-gold-line;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.tm__drawer-head {
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

.tm__drawer-headmain {
  min-width: 0;
}

.tm__drawer-sub {
  margin-top: 6px;
  font-size: 13px;
  color: $admin-text-muted;
}

.tm__drawer-close {
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

.tm__drawer-body {
  flex: 1;
  padding: 22px 24px;
  overflow-y: auto;
}

/* 概览 */
.tm__overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px 18px;
  margin-bottom: 8px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
}

.tm__ov-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    color: $admin-text-muted;
  }

  span {
    font-size: 14px;
    color: $admin-text;
  }

  &--full {
    grid-column: 1 / -1;
  }
}

.tm__timeline-title {
  margin: 20px 0 14px;
  font-size: 15px;
  color: $admin-gold;
}

/* el-timeline 深色适配 */
.tm__timeline {
  padding-left: 2px;

  :deep(.el-timeline-item__timestamp) {
    color: $admin-text-muted;
    font-size: 12px;
  }

  :deep(.el-timeline-item__tail) {
    border-left-color: $admin-border;
  }

  :deep(.el-timeline-item__node) {
    box-shadow: 0 0 0 3px rgba(212, 179, 116, 0.18);
  }
}

.tm__tl-card {
  padding: 14px 16px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
}

.tm__tl-day {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
  color: $admin-gold;
}

.tm__tl-row {
  font-size: 13px;
  line-height: 1.9;
  color: $admin-text;

  label {
    display: inline-block;
    width: 52px;
    color: $admin-text-muted;
  }

  em {
    font-style: normal;
    color: $admin-text-muted;
  }
}

.tm__empty {
  padding: 24px 0;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

.tm__drawer-foot {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: 1px solid $admin-border;
}

/* 过渡 */
.tm-drawer-enter-active,
.tm-drawer-leave-active {
  transition: opacity 0.3s $admin-ease;
}

.tm-drawer-enter-active .tm__drawer,
.tm-drawer-leave-active .tm__drawer {
  transition: transform 0.34s $admin-ease;
}

.tm-drawer-enter-from,
.tm-drawer-leave-to {
  opacity: 0;
}

.tm-drawer-enter-from .tm__drawer,
.tm-drawer-leave-to .tm__drawer {
  transform: translateX(100%);
}

/* 响应式 */
@media (max-width: 1200px) {
  .tm__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .tm__stats {
    grid-template-columns: 1fr;
  }

  .tm__drawer {
    width: 100%;
  }

  .tm__overview {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
