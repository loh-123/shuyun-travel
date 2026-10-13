<script setup lang="ts">
/**
 * 后台 · 数据备份与恢复
 *
 * 功能：
 *   - 顶部说明卡片
 *   - 左侧：数据表列表（勾选 / 全选 / 反选）+ 开始备份 + 备份历史卡片（下载 / 删除）
 *   - 右侧：拖拽上传 .json 备份文件 → 解析展示包含的表（勾选）+ 开始恢复
 *   - 恢复前二次确认弹窗（需输入 CONFIRM）
 *
 * 接口：
 *   GET    /api/admin/backup/tables
 *   POST   /api/admin/backup/create          备份文件为 JSON，并记录 backup_history
 *   GET    /api/admin/backup/list
 *   GET    /api/admin/backup/download/{filename}
 *   DELETE /api/admin/backup/{filename}
 *   POST   /api/admin/backup/restore
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import AdminIcon from '@/admin/components/AdminIcon.vue'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn } from '@/admin/components/DataTable.vue'

/** 数据表 */
interface TableItem {
  name: string
  rows: number
}
/** 备份文件 */
interface BackupFile {
  filename: string
  size: number
  createTime: string
}

const tableColumns: DataColumn[] = [
  { key: 'name', label: '表名' },
  { key: 'rows', label: '记录数', width: '120px', align: 'right' },
]

/* ------------------------------ 数据表 ------------------------------ */
const tables = ref<TableItem[]>([])
const tablesLoading = ref(false)
const selectedTables = ref<string[]>([])

async function loadTables() {
  tablesLoading.value = true
  try {
    const res = await adminRequest.get('/admin/backup/tables')
    tables.value = res.data ?? []
  } catch {
    tables.value = []
  } finally {
    tablesLoading.value = false
  }
}

/** 全选 */
function selectAll() {
  selectedTables.value = tables.value.map((t) => t.name)
}

/** 反选 */
function invert() {
  selectedTables.value = tables.value
    .map((t) => t.name)
    .filter((name) => !selectedTables.value.includes(name))
}

/* ------------------------------ 备份 ------------------------------ */
const backingUp = ref(false)

async function createBackup() {
  if (!selectedTables.value.length) {
    ElMessage.warning('请至少选择一张数据表')
    return
  }
  backingUp.value = true
  try {
    const res = await adminRequest.post('/admin/backup/create', { tables: selectedTables.value })
    ElMessage.success(`备份成功：${res.data?.filename ?? ''}`)
    await loadBackups()
  } finally {
    backingUp.value = false
  }
}

/* ------------------------------ 备份历史 ------------------------------ */
const backups = ref<BackupFile[]>([])
const backupsLoading = ref(false)

async function loadBackups() {
  backupsLoading.value = true
  try {
    const res = await adminRequest.get('/admin/backup/list')
    backups.value = res.data ?? []
  } catch {
    backups.value = []
  } finally {
    backupsLoading.value = false
  }
}

/** 文件大小展示 */
function fmtSize(size?: number) {
  const s = size ?? 0
  if (s < 1024) return `${s} B`
  if (s < 1024 * 1024) return `${(s / 1024).toFixed(1)} KB`
  return `${(s / 1024 / 1024).toFixed(2)} MB`
}

/** 下载备份 */
async function downloadBackup(file: BackupFile) {
  try {
    const res: any = await adminRequest.get(`/admin/backup/download/${file.filename}`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.filename
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

/** 删除备份 */
async function removeBackup(file: BackupFile) {
  try {
    await ElMessageBox.confirm(`确定删除备份文件「${file.filename}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await adminRequest.delete(`/admin/backup/${file.filename}`)
  ElMessage.success('已删除')
  await loadBackups()
}

/* ------------------------------ 恢复 ------------------------------ */
const restoreFile = ref<File | null>(null)
/** 备份文件内包含的全部表 */
const restoreTablesAll = ref<string[]>([])
/** 勾选要恢复的表 */
const restoreTables = ref<string[]>([])
const restoring = ref(false)
const dragging = ref(false)

/** 读取并解析备份文件 */
function handleFile(file: File) {
  if (!file.name.toLowerCase().endsWith('.json')) {
    ElMessage.warning('仅支持 .json 备份文件')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const data = JSON.parse(String(reader.result))
      restoreFile.value = file
      restoreTablesAll.value = Array.isArray(data.tables) ? [...data.tables] : []
      restoreTables.value = [...restoreTablesAll.value]
      if (!restoreTables.value.length) {
        ElMessage.warning('备份文件中未包含表信息')
      }
    } catch {
      ElMessage.error('备份文件解析失败')
    }
  }
  reader.readAsText(file)
}

function pickFile() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = () => {
    const file = input.files?.[0]
    if (file) handleFile(file)
  }
  input.click()
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function toggleRestoreTable(name: string) {
  const index = restoreTables.value.indexOf(name)
  if (index >= 0) {
    restoreTables.value.splice(index, 1)
  } else {
    restoreTables.value.push(name)
  }
}

const canRestore = computed(() => !!restoreFile.value && restoreTables.value.length > 0)

/** 二次确认弹窗 */
const confirmOpen = ref(false)
const confirmText = ref('')

function openRestoreConfirm() {
  if (!canRestore.value) {
    ElMessage.warning('请上传备份文件并选择要恢复的表')
    return
  }
  confirmText.value = ''
  confirmOpen.value = true
}

async function confirmRestore() {
  if (confirmText.value.trim() !== 'CONFIRM') {
    ElMessage.warning('请输入 CONFIRM 以确认恢复')
    return
  }
  if (!restoreFile.value) return
  confirmOpen.value = false
  restoring.value = true
  try {
    const fd = new FormData()
    fd.append('file', restoreFile.value)
    fd.append('tables', restoreTables.value.join(','))
    const res = await adminRequest.post('/admin/backup/restore', fd)
    ElMessage.success(`恢复完成，共写入 ${res.data ?? 0} 条记录`)
    await loadTables()
  } finally {
    restoring.value = false
  }
}

onMounted(() => {
  loadTables()
  loadBackups()
})
</script>

<template>
  <div class="db">
    <header class="db__head">
      <h2 class="db__title">数据备份与恢复</h2>
      <p class="db__sub">选择要备份的数据表，导出为 JSON 文件，可随时恢复数据</p>
    </header>

    <!-- 说明卡片 -->
    <section class="db__intro">
      <span class="db__intro-icon">
        <AdminIcon name="backup" :size="34" :stroke="1.4" />
      </span>
      <div>
        <h3 class="db__intro-title">数据备份与恢复</h3>
        <p class="db__intro-text">
          备份文件为 <b>JSON</b> 格式，保存在项目根目录 <b>/backup/</b> 文件夹。
          恢复会先 <b>清空目标表（TRUNCATE）</b> 再批量写入，请谨慎操作；建议恢复前先做一次全量备份。
        </p>
      </div>
    </section>

    <div class="db__grid">
      <!-- 左：备份 -->
      <section class="db__card">
        <h3 class="db__card-title">数据备份</h3>

        <div class="db__tools">
          <button type="button" class="db__mini" @click="selectAll">全选</button>
          <button type="button" class="db__mini" @click="invert">反选</button>
          <span class="db__count">已选 {{ selectedTables.length }} / {{ tables.length }}</span>
        </div>

        <DataTable
          v-model:selected="selectedTables"
          :columns="tableColumns"
          :data="tables"
          :loading="tablesLoading"
          selectable
          row-key="name"
          empty-text="暂无数据表"
        >
          <template #name="{ row }"><span class="db__tname">{{ row.name }}</span></template>
        </DataTable>

        <button type="button" class="db__primary" :disabled="backingUp" @click="createBackup">
          {{ backingUp ? '备份中…' : '开始备份' }}
        </button>

        <h4 class="db__sub-title">备份历史</h4>
        <div v-if="backupsLoading" class="db__empty">加载中…</div>
        <ul v-else-if="backups.length" class="db__history">
          <li v-for="f in backups" :key="f.filename" class="db__history-card">
            <div class="db__history-main">
              <span class="db__history-name">{{ f.filename }}</span>
              <span class="db__history-meta">{{ fmtSize(f.size) }} · {{ f.createTime }}</span>
            </div>
            <div class="db__history-ops">
              <button type="button" class="db__link" @click="downloadBackup(f)">下载</button>
              <button type="button" class="db__link db__link--danger" @click="removeBackup(f)">删除</button>
            </div>
          </li>
        </ul>
        <div v-else class="db__empty">暂无备份记录</div>
      </section>

      <!-- 右：恢复 -->
      <section class="db__card">
        <h3 class="db__card-title">数据恢复</h3>

        <div
          class="db__drop"
          :class="{ 'is-drag': dragging }"
          @click="pickFile"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <span class="db__drop-icon">
            <AdminIcon name="backup" :size="40" :stroke="1.3" />
          </span>
          <p v-if="restoreFile" class="db__drop-file">{{ restoreFile.name }}</p>
          <p v-else class="db__drop-hint">拖拽备份文件到这里，或点击上传</p>
          <small class="db__drop-tip">支持 .json 格式</small>
        </div>

        <template v-if="restoreFile">
          <p class="db__file-size">文件大小：{{ fmtSize(restoreFile.size) }}</p>

          <h4 class="db__sub-title">包含的表（勾选要恢复的表）</h4>
          <div class="db__checklist">
            <label v-for="name in restoreTablesAll" :key="name" class="db__check-item">
              <input type="checkbox" :checked="restoreTables.includes(name)" @change="toggleRestoreTable(name)" />
              <span>{{ name }}</span>
            </label>
            <p v-if="!restoreTablesAll.length" class="db__empty">备份文件中未包含表</p>
          </div>
        </template>

        <button
          type="button"
          class="db__danger"
          :disabled="restoring || !canRestore"
          @click="openRestoreConfirm"
        >
          {{ restoring ? '恢复中…' : '开始恢复' }}
        </button>
      </section>
    </div>

    <!-- 恢复二次确认弹窗 -->
    <transition name="db-mask">
      <div v-if="confirmOpen" class="db__mask" @click.self="confirmOpen = false">
        <div class="db__dialog">
          <h3 class="db__dialog-title">确认恢复数据</h3>
          <p class="db__dialog-warn">恢复将覆盖当前数据，确定继续吗？</p>
          <p class="db__dialog-hint">请输入 <b>CONFIRM</b> 以确认：</p>
          <input v-model="confirmText" class="db__dialog-input" type="text" placeholder="CONFIRM" />
          <div class="db__dialog-actions">
            <button type="button" class="db__mini" @click="confirmOpen = false">取消</button>
            <button
              type="button"
              class="db__danger db__danger--sm"
              :disabled="confirmText.trim() !== 'CONFIRM'"
              @click="confirmRestore"
            >
              确认恢复
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.db__head {
  margin-bottom: 20px;
}

.db__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.db__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

/* 说明卡片 */
.db__intro {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  padding: 22px;
  margin-bottom: 20px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius;
  background: $admin-gold-soft;
}

.db__intro-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: $admin-radius-sm;
  background: rgba(212, 179, 116, 0.18);
  color: $admin-gold;
}

.db__intro-title {
  font-size: 17px;
  color: $admin-text;
}

.db__intro-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: $admin-text-muted;

  b {
    color: $admin-gold;
  }
}

/* 两栏 */
.db__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.db__card {
  padding: 20px;
  @include admin-card;
}

.db__card-title {
  margin-bottom: 16px;
  font-size: 16px;
  color: $admin-text;
}

.db__sub-title {
  margin: 22px 0 12px;
  font-size: 14px;
  color: $admin-gold;
}

/* 工具行 */
.db__tools {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.db__mini {
  padding: 6px 14px;
  border: 1px solid $admin-border;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    background: $admin-gold-soft;
  }
}

.db__count {
  margin-left: auto;
  font-size: 13px;
  color: $admin-text-muted;
}

.db__tname {
  font-family: $admin-font-num;
  color: $admin-text;
}

.db__primary {
  width: 100%;
  margin-top: 16px;
  padding: 12px;
  border: none;
  border-radius: $admin-radius-sm;
  background: linear-gradient(120deg, #b98f45, $admin-gold);
  font-size: 15px;
  font-weight: 700;
  color: #241a08;
  cursor: pointer;
  transition: filter 0.3s $admin-ease;

  &:hover:not(:disabled) {
    filter: brightness(1.08);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.db__danger {
  width: 100%;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid rgba(208, 92, 98, 0.6);
  border-radius: $admin-radius-sm;
  background: rgba(208, 92, 98, 0.16);
  font-size: 15px;
  font-weight: 700;
  color: $admin-red-down;
  cursor: pointer;
  transition: background-color 0.3s $admin-ease;

  &:hover:not(:disabled) {
    background: rgba(208, 92, 98, 0.26);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.db__danger--sm {
  width: auto;
  margin-top: 0;
  padding: 9px 20px;
  font-size: 14px;
}

/* 备份历史卡片 */
.db__history {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
}

.db__history-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
  transition: border-color 0.3s $admin-ease;

  &:hover {
    border-color: $admin-gold-line;
  }
}

.db__history-main {
  flex: 1;
  min-width: 0;
}

.db__history-name {
  display: block;
  overflow: hidden;
  font-size: 13px;
  color: $admin-text;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.db__history-meta {
  font-size: 12px;
  color: $admin-text-muted;
}

.db__history-ops {
  display: flex;
  gap: 12px;
}

.db__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }
}

.db__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 上传区 */
.db__drop {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  padding: 20px;
  border: 1px dashed $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
  cursor: pointer;
  transition: border-color 0.3s $admin-ease;

  &:hover,
  &.is-drag {
    border-color: $admin-gold;
  }
}

.db__drop-icon {
  color: $admin-gold;
}

.db__drop-hint {
  font-size: 14px;
  color: $admin-text;
}

.db__drop-tip {
  font-size: 12px;
  color: $admin-text-muted;
}

.db__drop-file {
  font-size: 14px;
  color: $admin-gold;
}

.db__file-size {
  margin-top: 12px;
  font-size: 13px;
  color: $admin-text-muted;
}

/* 恢复表勾选 */
.db__checklist {
  max-height: 220px;
  padding: 6px;
  overflow-y: auto;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
}

.db__check-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: $admin-text;
  cursor: pointer;

  &:hover {
    background: $admin-gold-soft;
  }

  input {
    accent-color: #{$admin-gold};
  }
}

.db__empty {
  padding: 18px 0;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

/* 确认弹窗 */
.db__mask {
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

.db__dialog {
  width: min(440px, 100%);
  padding: 26px;
  border: 1px solid rgba(208, 92, 98, 0.5);
  border-radius: $admin-radius;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.db__dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: $admin-text;
}

.db__dialog-warn {
  margin-top: 12px;
  font-size: 14px;
  color: $admin-red-down;
}

.db__dialog-hint {
  margin-top: 12px;
  font-size: 13px;
  color: $admin-text-muted;

  b {
    color: $admin-gold;
  }
}

.db__dialog-input {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  padding: 0 14px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
  font-family: $admin-font-num;
  font-size: 15px;
  letter-spacing: 0.1em;
  color: $admin-text;
  outline: none;

  &:focus {
    border-color: $admin-gold-line;
  }
}

.db__dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
}

.db-mask-enter-active,
.db-mask-leave-active {
  transition: opacity 0.25s $admin-ease;
}

.db-mask-enter-from,
.db-mask-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 1000px) {
  .db__grid {
    grid-template-columns: 1fr;
  }
}
</style>
