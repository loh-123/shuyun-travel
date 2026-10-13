<script setup lang="ts">
/**
 * 后台 · 用户管理
 *
 * 功能：筛选（关键词 / 状态 / 角色 / 注册时间范围）、用户列表、状态开关、
 * 重置密码、软删除、批量禁用 / 批量删除、详情抽屉（含最近行为）、导出 Excel。
 *
 * 表格使用自定义 DataTable 基座（禁用 Element Plus 默认表格外观）。
 * 接口：
 *   GET    /api/admin/user/list
 *   GET    /api/admin/user/{id}
 *   PUT    /api/admin/user/{id}/status
 *   PUT    /api/admin/user/{id}/reset-password
 *   DELETE /api/admin/user/{id}
 *   GET    /api/admin/user/export
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 用户行 */
interface UserRow {
  id: number
  username: string
  email: string
  avatar?: string
  bio?: string
  role: string
  status: number
  createTime?: string
  lastLoginTime?: string
}

/** 浏览足迹（最近行为） */
interface Behavior {
  id: number
  targetType?: string
  targetName?: string
  subTitle?: string
  viewTime?: string
}

/** 列定义 */
const columns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '70px' },
  { key: 'user', label: '用户', width: '220px' },
  { key: 'email', label: '邮箱', width: '220px' },
  { key: 'role', label: '角色', width: '100px', align: 'center' },
  { key: 'status', label: '状态', width: '120px', align: 'center' },
  { key: 'createTime', label: '注册时间', width: '170px' },
  { key: 'lastLoginTime', label: '最后登录时间', width: '170px' },
  { key: 'action', label: '操作', width: '260px', align: 'center' },
]

/* ------------------------------ 列表状态 ------------------------------ */
const rows = ref<UserRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)

/** 选中行 ID */
const selected = ref<number[]>([])
/** 正在删除的行（触发淡出收缩动画） */
const removingId = ref<number | null>(null)

const pagination = computed<Pagination>(() => ({ page: page.value, size: size.value, total: total.value }))

/* ------------------------------ 筛选条件 ------------------------------ */
const keyword = ref('')
const statusFilter = ref<'all' | 0 | 1>('all')
const roleFilter = ref<'all' | 'USER' | 'ADMIN'>('all')
const dateStart = ref('')
const dateEnd = ref('')

/** 组装查询参数 */
function buildParams(withPage: boolean) {
  return {
    keyword: keyword.value || undefined,
    status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    role: roleFilter.value === 'all' ? undefined : roleFilter.value,
    startDate: dateStart.value || undefined,
    endDate: dateEnd.value || undefined,
    ...(withPage ? { page: page.value, size: size.value } : {}),
  }
}

/** 加载列表 */
async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/user/list', { params: buildParams(true) })
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
  statusFilter.value = 'all'
  roleFilter.value = 'all'
  dateStart.value = ''
  dateEnd.value = ''
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

/* ------------------------------ 行操作 ------------------------------ */
/** 切换启用 / 禁用 */
async function toggleStatus(row: UserRow) {
  const next = row.status === 1 ? 0 : 1
  try {
    await adminRequest.put(`/admin/user/${row.id}/status`, { status: next })
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 错误提示由拦截器处理
  }
}

/** 删除（软删除，带淡出动画） */
async function removeRow(row: UserRow) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？该操作为软删除。`, '删除确认', {
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
    await adminRequest.delete(`/admin/user/${row.id}`)
    ElMessage.success('已删除')
    selected.value = selected.value.filter((id) => id !== row.id)
    await load()
  } finally {
    removingId.value = null
  }
}

/* ------------------------------ 重置密码 ------------------------------ */
const resetOpen = ref(false)
const resetUser = ref<UserRow | null>(null)
const newPassword = ref('')
const resetting = ref(false)

function openReset(row: UserRow) {
  resetUser.value = row
  newPassword.value = ''
  resetOpen.value = true
}

async function doReset() {
  if (!resetUser.value) return
  resetting.value = true
  try {
    const res = await adminRequest.put(`/admin/user/${resetUser.value.id}/reset-password`)
    newPassword.value = res.data ?? '123456'
    ElMessage.success('密码已重置')
  } finally {
    resetting.value = false
  }
}

/* ------------------------------ 详情抽屉 ------------------------------ */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailUser = ref<UserRow | null>(null)
const detailBehaviors = ref<Behavior[]>([])

async function openDetail(row: UserRow) {
  detailOpen.value = true
  detailUser.value = row
  detailBehaviors.value = []
  detailLoading.value = true
  try {
    const res = await adminRequest.get(`/admin/user/${row.id}`)
    detailUser.value = res.data?.user ?? row
    detailBehaviors.value = res.data?.behaviors ?? []
  } catch {
    detailBehaviors.value = []
  } finally {
    detailLoading.value = false
  }
}

/* ------------------------------ 批量操作 ------------------------------ */
async function batchDisable() {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(`确定禁用选中的 ${selected.value.length} 位用户吗？`, '批量禁用', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await Promise.all(selected.value.map((id) => adminRequest.put(`/admin/user/${id}/status`, { status: 0 })))
  ElMessage.success('已批量禁用')
  selected.value = []
  await load()
}

async function batchDelete() {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 位用户吗？该操作为软删除。`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await Promise.all(selected.value.map((id) => adminRequest.delete(`/admin/user/${id}`)))
  ElMessage.success('已批量删除')
  selected.value = []
  await load()
}

/* ------------------------------ 导出 Excel ------------------------------ */
const exporting = ref(false)

async function exportExcel() {
  exporting.value = true
  try {
    const res: any = await adminRequest.get('/admin/user/export', {
      params: buildParams(false),
      responseType: 'blob',
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `用户列表_${new Date().toISOString().slice(0, 10)}.xlsx`
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
/** 延迟 */
function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 时间格式化 */
function fmt(value?: string) {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

onMounted(load)
</script>

<template>
  <div class="um">
    <!-- 顶部标题栏 -->
    <header class="um__head">
      <div>
        <h2 class="um__title">用户管理</h2>
        <p class="um__sub">共 {{ total }} 位注册用户</p>
      </div>
      <button type="button" class="um__export" :disabled="exporting" @click="exportExcel">
        {{ exporting ? '导出中…' : '导出 Excel' }}
      </button>
    </header>

    <!-- 筛选栏（磨砂玻璃） -->
    <section class="um__filter">
      <input
        v-model="keyword"
        class="um__input um__input--search"
        type="text"
        placeholder="搜索用户名 / 邮箱"
        @keyup.enter="search"
      />
      <select v-model="statusFilter" class="um__select">
        <option value="all">全部状态</option>
        <option :value="1">正常</option>
        <option :value="0">禁用</option>
      </select>
      <select v-model="roleFilter" class="um__select">
        <option value="all">全部角色</option>
        <option value="USER">普通用户</option>
        <option value="ADMIN">管理员</option>
      </select>
      <div class="um__range">
        <input v-model="dateStart" class="um__input um__input--date" type="date" />
        <span class="um__range-sep">至</span>
        <input v-model="dateEnd" class="um__input um__input--date" type="date" />
      </div>
      <button type="button" class="um__btn" @click="search">查询</button>
      <button type="button" class="um__btn um__btn--ghost" @click="resetFilters">重置</button>
    </section>

    <!-- 批量操作栏 -->
    <transition name="um-slide">
      <div v-if="selected.length" class="um__batch">
        <span class="um__batch-text">已选 {{ selected.length }} 项</span>
        <button type="button" class="um__batch-btn" @click="batchDisable">批量禁用</button>
        <button type="button" class="um__batch-btn um__batch-btn--danger" @click="batchDelete">批量删除</button>
        <button type="button" class="um__batch-cancel" @click="selected = []">取消选择</button>
      </div>
    </transition>

    <!-- 表格 -->
    <section class="um__panel">
      <DataTable
        v-model:selected="selected"
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :removing-key="removingId"
        selectable
        row-key="id"
        empty-text="暂无用户"
        @page-change="changePage"
      >
        <!-- 头像 + 用户名 -->
        <template #user="{ row }">
          <div class="um__user">
            <img v-if="row.avatar" class="um__avatar" :src="row.avatar" :alt="row.username" />
            <span v-else class="um__avatar um__avatar--letter">{{ (row.username || '?').charAt(0) }}</span>
            <span class="um__username">{{ row.username }}</span>
          </div>
        </template>

        <!-- 角色标签 -->
        <template #role="{ row }">
          <span class="um__tag" :class="row.role === 'ADMIN' ? 'is-admin' : 'is-user'">
            {{ row.role === 'ADMIN' ? 'ADMIN' : 'USER' }}
          </span>
        </template>

        <!-- 状态开关 -->
        <template #status="{ row }">
          <div class="um__status">
            <button
              type="button"
              class="um__switch"
              :class="{ 'is-on': row.status === 1 }"
              @click="toggleStatus(row)"
            >
              <span class="um__switch-dot" />
            </button>
            <span class="um__status-text">{{ row.status === 1 ? '启用' : '禁用' }}</span>
          </div>
        </template>

        <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>
        <template #lastLoginTime="{ row }">{{ fmt(row.lastLoginTime) }}</template>

        <!-- 操作 -->
        <template #action="{ row }">
          <div class="um__actions">
            <button type="button" class="um__link" @click="openDetail(row)">查看详情</button>
            <button type="button" class="um__link" @click="openReset(row)">重置密码</button>
            <button type="button" class="um__link" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </button>
            <button type="button" class="um__link um__link--danger" @click="removeRow(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 详情抽屉 -->
    <transition name="um-drawer">
      <div v-if="detailOpen" class="um__drawer-mask" @click.self="detailOpen = false">
        <aside class="um__drawer">
          <header class="um__drawer-head">
            <h3>用户详情</h3>
            <button type="button" class="um__drawer-close" @click="detailOpen = false">×</button>
          </header>

          <div v-if="detailUser" class="um__drawer-body">
            <div class="um__drawer-profile">
              <img v-if="detailUser.avatar" class="um__avatar um__avatar--lg" :src="detailUser.avatar" />
              <span v-else class="um__avatar um__avatar--lg um__avatar--letter">
                {{ (detailUser.username || '?').charAt(0) }}
              </span>
              <div>
                <p class="um__drawer-name">{{ detailUser.username }}</p>
                <p class="um__drawer-bio">{{ detailUser.bio || '这个人很懒，什么都没写' }}</p>
              </div>
            </div>

            <dl class="um__info">
              <div><dt>用户 ID</dt><dd>{{ detailUser.id }}</dd></div>
              <div><dt>邮箱</dt><dd>{{ detailUser.email || '—' }}</dd></div>
              <div>
                <dt>角色</dt>
                <dd>
                  <span class="um__tag" :class="detailUser.role === 'ADMIN' ? 'is-admin' : 'is-user'">
                    {{ detailUser.role === 'ADMIN' ? 'ADMIN' : 'USER' }}
                  </span>
                </dd>
              </div>
              <div><dt>状态</dt><dd>{{ detailUser.status === 1 ? '正常' : '禁用' }}</dd></div>
              <div><dt>注册时间</dt><dd>{{ fmt(detailUser.createTime) }}</dd></div>
              <div><dt>最后登录</dt><dd>{{ fmt(detailUser.lastLoginTime) }}</dd></div>
            </dl>

            <h4 class="um__drawer-subtitle">最近行为记录</h4>
            <div v-if="detailLoading" class="um__behavior-empty">加载中…</div>
            <ul v-else-if="detailBehaviors.length" class="um__behaviors">
              <li v-for="b in detailBehaviors" :key="b.id" class="um__behavior">
                <span class="um__behavior-dot" />
                <div class="um__behavior-main">
                  <p class="um__behavior-name">{{ b.targetName || '未知对象' }}</p>
                  <p class="um__behavior-meta">
                    {{ b.targetType === 'CULTURE' ? '文化' : '景点' }}
                    <template v-if="b.subTitle"> · {{ b.subTitle }}</template>
                  </p>
                </div>
                <span class="um__behavior-time">{{ fmt(b.viewTime) }}</span>
              </li>
            </ul>
            <div v-else class="um__behavior-empty">暂无行为记录</div>
          </div>
        </aside>
      </div>
    </transition>

    <!-- 重置密码弹窗 -->
    <transition name="fade">
      <div v-if="resetOpen" class="um__dialog-mask" @click.self="resetOpen = false">
        <div class="um__dialog">
          <h3 class="um__dialog-title">重置密码</h3>
          <template v-if="!newPassword">
            <p class="um__dialog-text">
              确定将用户「{{ resetUser?.username }}」的密码重置为默认密码 <b>123456</b> 吗？
            </p>
            <div class="um__dialog-actions">
              <button type="button" class="um__btn um__btn--ghost" @click="resetOpen = false">取消</button>
              <button type="button" class="um__btn" :disabled="resetting" @click="doReset">
                {{ resetting ? '重置中…' : '确认重置' }}
              </button>
            </div>
          </template>
          <template v-else>
            <p class="um__dialog-text">重置成功，新密码为：</p>
            <p class="um__dialog-pwd">{{ newPassword }}</p>
            <p class="um__dialog-hint">请提醒用户尽快登录并修改密码。</p>
            <div class="um__dialog-actions">
              <button type="button" class="um__btn" @click="resetOpen = false">知道了</button>
            </div>
          </template>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.um__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.um__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.um__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

/* 导出按钮：金色描边 */
.um__export {
  padding: 10px 22px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: transparent;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;
  transition:
    background-color 0.3s $admin-ease,
    color 0.3s $admin-ease;

  &:hover:not(:disabled) {
    background: $admin-gold-soft;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* 筛选栏：磨砂玻璃 */
.um__filter {
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

.um__input,
.um__select {
  height: 40px;
  padding: 0 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  font-size: 14px;
  color: $admin-text;
  outline: none;
  transition: border-color 0.25s $admin-ease;

  &:focus {
    border-color: $admin-gold-line;
  }
}

.um__input--search {
  width: 240px;
}

.um__input--date {
  width: 150px;
  color-scheme: dark; // 让原生日期控件在深色下可读
}

.um__select {
  cursor: pointer;
  color-scheme: dark;
}

.um__range {
  display: flex;
  gap: 8px;
  align-items: center;
}

.um__range-sep {
  font-size: 13px;
  color: $admin-text-muted;
}

.um__btn {
  height: 40px;
  padding: 0 20px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-size: 14px;
  color: $admin-gold;
  cursor: pointer;
  transition: background-color 0.25s $admin-ease;

  &:hover:not(:disabled) {
    background: rgba(212, 179, 116, 0.2);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.um__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;

  &:hover {
    color: $admin-gold;
  }
}

/* 批量操作栏 */
.um__batch {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 18px;
  margin-bottom: 14px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
}

.um__batch-text {
  font-size: 14px;
  color: $admin-gold;
}

.um__batch-btn {
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

.um__batch-btn--danger {
  border-color: rgba(208, 92, 98, 0.5);
  color: $admin-red-down;

  &:hover {
    background: rgba(208, 92, 98, 0.14);
  }
}

.um__batch-cancel {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;

  &:hover {
    color: $admin-gold;
  }
}

/* 表格面板 */
.um__panel {
  padding: 20px;
  @include admin-card;
}

/* 头像 + 用户名 */
.um__user {
  display: flex;
  gap: 10px;
  align-items: center;
}

.um__avatar {
  width: 32px;
  height: 32px;
  border: 1px solid $admin-gold-line;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.um__avatar--letter {
  display: inline-grid;
  place-items: center;
  background: $admin-gold-soft;
  font-size: 14px;
  font-weight: 700;
  color: $admin-gold;
}

.um__avatar--lg {
  width: 64px;
  height: 64px;
  font-size: 24px;
}

.um__username {
  color: $admin-text;
}

/* 角色标签（颜色渐变过渡） */
.um__tag {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.05em;
  transition:
    background-color 0.35s $admin-ease,
    color 0.35s $admin-ease;

  &.is-admin {
    background: rgba(212, 179, 116, 0.18);
    color: $admin-gold;
  }

  &.is-user {
    background: rgba(122, 138, 160, 0.16);
    color: $admin-text-muted;
  }
}

/* 状态开关 */
.um__status {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}

.um__switch {
  position: relative;
  width: 38px;
  height: 20px;
  padding: 0;
  border: 1px solid $admin-border;
  border-radius: 999px;
  background: rgba(var(--admin-mask-rgb), 0.8);
  cursor: pointer;
  transition:
    background-color 0.3s $admin-ease,
    border-color 0.3s $admin-ease;

  &.is-on {
    border-color: $admin-gold;
    background: linear-gradient(120deg, #b98f45, $admin-gold);
  }
}

.um__switch-dot {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: $admin-text-muted;
  transition:
    transform 0.3s $admin-ease,
    background-color 0.3s $admin-ease;

  .um__switch.is-on & {
    background: #241a08;
    transform: translateX(18px);
  }
}

.um__status-text {
  font-size: 13px;
  color: $admin-text-muted;
}

/* 操作链接 */
.um__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  justify-content: center;
}

.um__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;
  transition: color 0.25s $admin-ease;

  &:hover {
    color: #e0c07c;
  }
}

.um__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 详情抽屉 */
.um__drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(6, 12, 22, 0.66);
  backdrop-filter: blur(4px);
}

.um__drawer {
  width: min(480px, 100%);
  height: 100%;
  overflow-y: auto;
  border-left: 1px solid $admin-gold-line;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.um__drawer-head {
  position: sticky;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid $admin-border;
  background: $admin-panel;

  h3 {
    font-size: 18px;
    color: $admin-text;
  }
}

.um__drawer-close {
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

.um__drawer-body {
  padding: 24px;
}

.um__drawer-profile {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
}

.um__drawer-name {
  font-size: 18px;
  font-weight: 700;
  color: $admin-text;
}

.um__drawer-bio {
  margin-top: 6px;
  font-size: 13px;
  color: $admin-text-muted;
}

.um__info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 20px;

  dt {
    font-size: 12px;
    color: $admin-text-muted;
  }

  dd {
    margin-top: 4px;
    font-size: 14px;
    color: $admin-text;
    word-break: break-all;
  }
}

.um__drawer-subtitle {
  margin: 26px 0 12px;
  font-size: 15px;
  color: $admin-gold;
}

.um__behaviors {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.um__behavior {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: $admin-radius-sm;
  transition: background-color 0.25s $admin-ease;

  &:hover {
    background: $admin-gold-soft;
  }
}

.um__behavior-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $admin-gold;
  flex-shrink: 0;
}

.um__behavior-main {
  flex: 1;
  min-width: 0;
}

.um__behavior-name {
  font-size: 14px;
  color: $admin-text;
}

.um__behavior-meta {
  margin-top: 2px;
  font-size: 12px;
  color: $admin-text-muted;
}

.um__behavior-time {
  font-size: 12px;
  color: $admin-text-muted;
  white-space: nowrap;
}

.um__behavior-empty {
  padding: 20px 0;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

/* 重置密码弹窗 */
.um__dialog-mask {
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

.um__dialog {
  width: min(420px, 100%);
  padding: 26px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.um__dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: $admin-text;
}

.um__dialog-text {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.8;
  color: $admin-text;

  b {
    color: $admin-gold;
  }
}

.um__dialog-pwd {
  margin-top: 12px;
  padding: 14px;
  border-radius: $admin-radius-sm;
  background: $admin-gold-soft;
  font-family: $admin-font-num;
  font-size: 24px;
  letter-spacing: 0.2em;
  text-align: center;
  color: $admin-gold;
}

.um__dialog-hint {
  margin-top: 10px;
  font-size: 12px;
  color: $admin-text-muted;
}

.um__dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 22px;
}

/* 过渡 */
.um-slide-enter-active,
.um-slide-leave-active {
  transition:
    opacity 0.28s $admin-ease,
    transform 0.28s $admin-ease;
}

.um-slide-enter-from,
.um-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.um-drawer-enter-active,
.um-drawer-leave-active {
  transition: opacity 0.3s $admin-ease;
}

.um-drawer-enter-active .um__drawer,
.um-drawer-leave-active .um__drawer {
  transition: transform 0.34s $admin-ease;
}

.um-drawer-enter-from,
.um-drawer-leave-to {
  opacity: 0;
}

.um-drawer-enter-from .um__drawer,
.um-drawer-leave-to .um__drawer {
  transform: translateX(100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s $admin-ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 小屏：抽屉全屏 */
@media (max-width: 768px) {
  .um__input--search {
    width: 100%;
  }

  .um__drawer {
    width: 100%;
  }

  .um__info {
    grid-template-columns: 1fr;
  }
}
</style>
