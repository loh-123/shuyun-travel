<script setup lang="ts">
/**
 * 后台 · 内容审核
 *
 * 功能：顶部统计卡片、Tab 切换（用户笔记 / 用户反馈 / 邮箱订阅）。
 * 笔记：通过 / 拒绝（填写理由）/ 删除；反馈：标记已处理 / 回复（邮件）/ 删除；订阅：导出。
 *
 * 接口：
 *   GET    /api/admin/audit/stats
 *   GET    /api/admin/audit/note/list       PUT /note/{id}/approve  PUT /note/{id}/reject  DELETE /note/{id}
 *   GET    /api/admin/audit/feedback/list   PUT /feedback/{id}/handle                      DELETE /feedback/{id}
 *   GET    /api/admin/audit/subscription/list   GET /subscription/export
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 笔记行 */
interface NoteRow {
  id: number
  title?: string
  content?: string
  username?: string
  auditStatus?: number
  rejectReason?: string
  createTime?: string
}
/** 反馈行 */
interface FeedbackRow {
  id: number
  username?: string
  contact?: string
  type?: string
  content?: string
  status?: number
  reply?: string
  createTime?: string
}
/** 订阅行 */
interface SubscriptionRow {
  id: number
  email: string
  source?: string
  createTime?: string
}

type TabKey = 'note' | 'feedback' | 'sub'
const activeTab = ref<TabKey>('note')

/* ------------------------------ 笔记 ------------------------------ */
const noteColumns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'title', label: '标题', width: '180px' },
  { key: 'summary', label: '内容摘要' },
  { key: 'username', label: '作者', width: '110px' },
  { key: 'auditStatus', label: '状态', width: '100px', align: 'center' },
  { key: 'createTime', label: '时间', width: '160px' },
  { key: 'action', label: '操作', width: '200px', align: 'center' },
]
const noteRows = ref<NoteRow[]>([])
const noteLoading = ref(false)
const noteTotal = ref(0)
const notePage = ref(1)
const noteStatus = ref<'all' | 0 | 1 | 2>('all')
const notePagination = computed<Pagination>(() => ({ page: notePage.value, size: 10, total: noteTotal.value }))

async function loadNotes() {
  noteLoading.value = true
  try {
    const res = await adminRequest.get('/admin/audit/note/list', {
      params: {
        status: noteStatus.value === 'all' ? undefined : noteStatus.value,
        page: notePage.value,
        size: 10,
      },
    })
    noteRows.value = res.data?.records ?? []
    noteTotal.value = res.data?.total ?? 0
  } catch {
    noteRows.value = []
    noteTotal.value = 0
  } finally {
    noteLoading.value = false
  }
}

async function approveNote(row: NoteRow) {
  await adminRequest.put(`/admin/audit/note/${row.id}/approve`)
  ElMessage.success('已通过')
  await loadNotes()
}

/* 拒绝弹窗 */
const rejectOpen = ref(false)
const rejectTarget = ref<NoteRow | null>(null)
const rejectReason = ref('')

function openReject(row: NoteRow) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectOpen.value = true
}

async function confirmReject() {
  if (!rejectTarget.value) return
  await adminRequest.put(`/admin/audit/note/${rejectTarget.value.id}/reject`, { reason: rejectReason.value })
  ElMessage.success('已拒绝，已发送站内信通知作者')
  rejectOpen.value = false
  await loadNotes()
}

async function removeNote(row: NoteRow) {
  try {
    await ElMessageBox.confirm('确定删除该笔记吗？该操作为软删除。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await adminRequest.delete(`/admin/audit/note/${row.id}`)
  ElMessage.success('已删除')
  await loadNotes()
}

/* ------------------------------ 反馈 ------------------------------ */
const feedbackColumns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'username', label: '姓名', width: '110px' },
  { key: 'contact', label: '邮箱', width: '190px' },
  { key: 'content', label: '内容' },
  { key: 'status', label: '状态', width: '100px', align: 'center' },
  { key: 'createTime', label: '时间', width: '160px' },
  { key: 'action', label: '操作', width: '220px', align: 'center' },
]
const feedbackRows = ref<FeedbackRow[]>([])
const feedbackLoading = ref(false)
const feedbackTotal = ref(0)
const feedbackPage = ref(1)
const feedbackStatus = ref<'all' | 0 | 1>('all')
const feedbackPagination = computed<Pagination>(() => ({ page: feedbackPage.value, size: 10, total: feedbackTotal.value }))

async function loadFeedback() {
  feedbackLoading.value = true
  try {
    const res = await adminRequest.get('/admin/audit/feedback/list', {
      params: {
        status: feedbackStatus.value === 'all' ? undefined : feedbackStatus.value,
        page: feedbackPage.value,
        size: 10,
      },
    })
    feedbackRows.value = res.data?.records ?? []
    feedbackTotal.value = res.data?.total ?? 0
  } catch {
    feedbackRows.value = []
    feedbackTotal.value = 0
  } finally {
    feedbackLoading.value = false
  }
}

/** 标记已处理 */
async function handleFeedback(row: FeedbackRow) {
  await adminRequest.put(`/admin/audit/feedback/${row.id}/handle`, { status: 1, reply: row.reply })
  ElMessage.success('已标记为已处理')
  await loadFeedback()
}

/* 回复弹窗 */
const replyOpen = ref(false)
const replyTarget = ref<FeedbackRow | null>(null)
const replyText = ref('')
const notifyEmail = ref(true)

function openReply(row: FeedbackRow) {
  replyTarget.value = row
  replyText.value = row.reply || ''
  notifyEmail.value = true
  replyOpen.value = true
}

async function confirmReply() {
  if (!replyTarget.value) return
  if (notifyEmail.value) {
    // 回复并发送邮件
    await adminRequest.post(`/admin/audit/feedback/${replyTarget.value.id}/reply`, { reply: replyText.value })
    ElMessage.success('已回复并发送邮件')
  } else {
    // 仅记录回复，不发送邮件
    await adminRequest.put(`/admin/audit/feedback/${replyTarget.value.id}/handle`, {
      status: 1,
      reply: replyText.value,
    })
    ElMessage.success('已回复')
  }
  replyOpen.value = false
  await loadFeedback()
}

async function removeFeedback(row: FeedbackRow) {
  try {
    await ElMessageBox.confirm('确定删除该反馈吗？该操作为软删除。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await adminRequest.delete(`/admin/audit/feedback/${row.id}`)
  ElMessage.success('已删除')
  await loadFeedback()
}

/* ------------------------------ 订阅 ------------------------------ */
const subColumns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'email', label: '邮箱' },
  { key: 'source', label: '来源', width: '160px' },
  { key: 'createTime', label: '订阅时间', width: '180px' },
  { key: 'action', label: '操作', width: '100px', align: 'center' },
]
const subRows = ref<SubscriptionRow[]>([])
const subLoading = ref(false)
const subTotal = ref(0)
const subPage = ref(1)
const subPagination = computed<Pagination>(() => ({ page: subPage.value, size: 10, total: subTotal.value }))

async function loadSubscriptions() {
  subLoading.value = true
  try {
    const res = await adminRequest.get('/admin/audit/subscription/list', {
      params: { page: subPage.value, size: 10 },
    })
    subRows.value = res.data?.records ?? []
    subTotal.value = res.data?.total ?? 0
  } catch {
    subRows.value = []
    subTotal.value = 0
  } finally {
    subLoading.value = false
  }
}

const exporting = ref(false)

/** 删除订阅 */
async function removeSubscription(row: SubscriptionRow) {
  try {
    await ElMessageBox.confirm(`确定删除订阅「${row.email}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await adminRequest.delete(`/admin/audit/subscription/${row.id}`)
  ElMessage.success('已删除')
  await loadSubscriptions()
}
async function exportSubscriptions() {
  exporting.value = true
  try {
    const res: any = await adminRequest.get('/admin/audit/subscription/export', { responseType: 'blob' })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `邮箱订阅_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

/* ------------------------------ 工具 ------------------------------ */
function fmt(value?: string) {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 富文本转纯文本摘要 */
function summary(html?: string) {
  const text = (html || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
  return text.length > 50 ? text.slice(0, 50) + '…' : text || '—'
}

/** 笔记状态文案 */
function noteStatusText(status?: number) {
  return status === 1 ? '已通过' : status === 2 ? '已拒绝' : '待审核'
}

/** 笔记状态样式 */
function noteStatusClass(status?: number) {
  return status === 1 ? 'is-done' : status === 2 ? 'is-reject' : 'is-pending'
}

onMounted(() => {
  loadNotes()
  loadFeedback()
  loadSubscriptions()
})
</script>

<template>
  <div class="ca">
    <header class="ca__head">
      <h2 class="ca__title">内容审核</h2>
      <p class="ca__sub">审核用户笔记、处理反馈与查看邮箱订阅</p>
    </header>

    <!-- Tab -->
    <div class="ca__tabs">
      <button type="button" class="ca__tab" :class="{ 'is-active': activeTab === 'note' }" @click="activeTab = 'note'">
        用户笔记
      </button>
      <button type="button" class="ca__tab" :class="{ 'is-active': activeTab === 'feedback' }" @click="activeTab = 'feedback'">
        用户反馈
      </button>
      <button type="button" class="ca__tab" :class="{ 'is-active': activeTab === 'sub' }" @click="activeTab = 'sub'">
        邮箱订阅
      </button>
    </div>

    <!-- 笔记 -->
    <transition name="ca-fade" mode="out-in">
      <section v-if="activeTab === 'note'" key="note" class="ca__panel">
        <div class="ca__toolbar">
          <div class="ca__seg">
            <button
              v-for="s in ([['all', '全部'], [0, '待审核'], [1, '已通过'], [2, '已拒绝']] as const)"
              :key="String(s[0])"
              type="button"
              class="ca__seg-btn"
              :class="{ 'is-active': noteStatus === s[0] }"
              @click="noteStatus = s[0]; notePage = 1; loadNotes()"
            >
              {{ s[1] }}
            </button>
          </div>
        </div>

        <DataTable
          :columns="noteColumns"
          :data="noteRows"
          :loading="noteLoading"
          :pagination="notePagination"
          row-key="id"
          empty-text="暂无笔记"
          @page-change="(p: number) => { notePage = p; loadNotes() }"
        >
          <template #title="{ row }">{{ row.title || '无标题' }}</template>
          <template #summary="{ row }">{{ summary(row.content) }}</template>
          <template #username="{ row }">{{ row.username || '未知用户' }}</template>
          <template #auditStatus="{ row }">
            <span class="ca__badge" :class="noteStatusClass(row.auditStatus)">{{ noteStatusText(row.auditStatus) }}</span>
          </template>
          <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>
          <template #action="{ row }">
            <div class="ca__actions">
              <button type="button" class="ca__link" @click="approveNote(row)">通过</button>
              <button type="button" class="ca__link ca__link--warn" @click="openReject(row)">拒绝</button>
              <button type="button" class="ca__link ca__link--danger" @click="removeNote(row)">删除</button>
            </div>
          </template>
        </DataTable>
      </section>

      <!-- 反馈 -->
      <section v-else-if="activeTab === 'feedback'" key="feedback" class="ca__panel">
        <div class="ca__toolbar">
          <div class="ca__seg">
            <button
              v-for="s in ([['all', '全部'], [0, '待处理'], [1, '已处理']] as const)"
              :key="String(s[0])"
              type="button"
              class="ca__seg-btn"
              :class="{ 'is-active': feedbackStatus === s[0] }"
              @click="feedbackStatus = s[0]; feedbackPage = 1; loadFeedback()"
            >
              {{ s[1] }}
            </button>
          </div>
        </div>

        <DataTable
          :columns="feedbackColumns"
          :data="feedbackRows"
          :loading="feedbackLoading"
          :pagination="feedbackPagination"
          row-key="id"
          empty-text="暂无反馈"
          @page-change="(p: number) => { feedbackPage = p; loadFeedback() }"
        >
          <template #username="{ row }">{{ row.username || '未知用户' }}</template>
          <template #contact="{ row }">{{ row.contact || '—' }}</template>
          <template #content="{ row }">
            <span class="ca__content" :title="row.content">{{ row.content }}</span>
          </template>
          <template #status="{ row }">
            <span class="ca__badge" :class="row.status === 1 ? 'is-done' : 'is-pending'">
              {{ row.status === 1 ? '已处理' : '待处理' }}
            </span>
          </template>
          <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>
          <template #action="{ row }">
            <div class="ca__actions">
              <button type="button" class="ca__link" @click="handleFeedback(row)">标记已处理</button>
              <button type="button" class="ca__link" @click="openReply(row)">回复</button>
              <button type="button" class="ca__link ca__link--danger" @click="removeFeedback(row)">删除</button>
            </div>
          </template>
        </DataTable>
      </section>

      <!-- 订阅 -->
      <section v-else key="sub" class="ca__panel">
        <div class="ca__toolbar ca__toolbar--right">
          <button type="button" class="ca__export" :disabled="exporting" @click="exportSubscriptions">
            {{ exporting ? '导出中…' : '导出 Excel' }}
          </button>
        </div>

        <DataTable
          :columns="subColumns"
          :data="subRows"
          :loading="subLoading"
          :pagination="subPagination"
          row-key="id"
          empty-text="暂无订阅"
          @page-change="(p: number) => { subPage = p; loadSubscriptions() }"
        >
          <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>
          <template #action="{ row }">
            <div class="ca__actions">
              <button type="button" class="ca__link ca__link--danger" @click="removeSubscription(row)">删除</button>
            </div>
          </template>
        </DataTable>
      </section>
    </transition>

    <!-- 拒绝理由弹窗 -->
    <transition name="ca-mask">
      <div v-if="rejectOpen" class="ca__mask" @click.self="rejectOpen = false">
        <div class="ca__dialog">
          <h3 class="ca__dialog-title">拒绝笔记</h3>
          <p class="ca__dialog-text">请填写拒绝理由，供作者参考：</p>
          <textarea v-model="rejectReason" class="ca__textarea" rows="3" placeholder="如：内容与旅行主题无关" />
          <div class="ca__dialog-actions">
            <button type="button" class="ca__btn ca__btn--ghost" @click="rejectOpen = false">取消</button>
            <button type="button" class="ca__btn" @click="confirmReject">确认拒绝</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 回复反馈弹窗 -->
    <transition name="ca-mask">
      <div v-if="replyOpen" class="ca__mask" @click.self="replyOpen = false">
        <div class="ca__dialog">
          <h3 class="ca__dialog-title">回复反馈</h3>
          <p class="ca__dialog-meta">{{ replyTarget?.username }} · {{ replyTarget?.contact || '无联系方式' }}</p>
          <textarea v-model="replyText" class="ca__textarea" rows="4" placeholder="填写回复内容…" />
          <label class="ca__check">
            <input v-model="notifyEmail" type="checkbox" />
            <span>同时发送邮件通知（当前为占位实现，仅记录日志）</span>
          </label>
          <div class="ca__dialog-actions">
            <button type="button" class="ca__btn ca__btn--ghost" @click="replyOpen = false">取消</button>
            <button type="button" class="ca__btn" @click="confirmReply">发送回复</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.ca__head {
  margin-bottom: 20px;
}

.ca__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.ca__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.ca__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin-bottom: 20px;
}

/* Tab */
.ca__tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  border-bottom: 1px solid $admin-border;
}

.ca__tab {
  position: relative;
  padding: 10px 20px;
  border: none;
  background: transparent;
  font-size: 15px;
  color: $admin-text-muted;
  cursor: pointer;
  transition: color 0.3s $admin-ease;

  &::after {
    content: '';
    position: absolute;
    right: 16px;
    bottom: -1px;
    left: 16px;
    height: 2px;
    background: $admin-gold;
    transform: scaleX(0);
    transition: transform 0.3s $admin-ease;
  }

  &:hover {
    color: $admin-gold;
  }

  &.is-active {
    color: $admin-gold;

    &::after {
      transform: scaleX(1);
    }
  }
}

.ca__panel {
  padding: 20px;
  @include admin-card;
}

.ca__toolbar {
  display: flex;
  margin-bottom: 16px;

  &--right {
    justify-content: flex-end;
  }
}

.ca__seg {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
}

.ca__seg-btn {
  padding: 6px 14px;
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

.ca__export {
  padding: 9px 20px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: rgba(212, 179, 116, 0.2);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.ca__content {
  display: inline-block;
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

/* 状态徽标 */
.ca__badge {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;

  &.is-pending {
    background: rgba(212, 179, 116, 0.16);
    color: $admin-gold;
  }

  &.is-done {
    background: rgba(58, 90, 64, 0.3);
    color: #8fcea6;
  }

  &.is-reject {
    background: rgba(208, 92, 98, 0.16);
    color: $admin-red-down;
  }
}

.ca__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.ca__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }
}

.ca__link--warn {
  color: #d9c07f;
}

.ca__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 弹窗 */
.ca__mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(6, 12, 22, 0.72);
  backdrop-filter: blur(6px);
}

.ca__dialog {
  width: min(440px, 100%);
  padding: 26px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.ca__dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: $admin-text;
}

.ca__dialog-text {
  margin-top: 12px;
  font-size: 14px;
  color: $admin-text;
}

.ca__dialog-meta {
  margin-top: 8px;
  font-size: 13px;
  color: $admin-gold;
}

.ca__textarea {
  width: 100%;
  margin-top: 12px;
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

.ca__check {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;

  input {
    accent-color: #{$admin-gold};
  }
}

.ca__dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
}

.ca__btn {
  padding: 9px 20px;
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

.ca__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;
}

/* 过渡 */
.ca-fade-enter-active,
.ca-fade-leave-active {
  transition:
    opacity 0.25s $admin-ease,
    transform 0.25s $admin-ease;
}

.ca-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.ca-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.ca-mask-enter-active,
.ca-mask-leave-active {
  transition: opacity 0.25s $admin-ease;
}

.ca-mask-enter-from,
.ca-mask-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 1200px) {
  .ca__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .ca__stats {
    grid-template-columns: 1fr;
  }
}
</style>
