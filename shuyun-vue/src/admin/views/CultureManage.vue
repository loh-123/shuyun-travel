<script setup lang="ts">
/**
 * 后台 · 文化管理
 *
 * 功能：筛选（关键词 / 分类）、文化列表、新增 / 编辑抽屉
 * （含封面上传、WangEditor 富文本正文、视频链接、关联景点多选、相关人物与标签）。
 *
 * 接口：
 *   GET    /api/admin/culture/list
 *   POST   /api/admin/culture
 *   PUT    /api/admin/culture/{id}
 *   DELETE /api/admin/culture/{id}
 *   POST   /api/admin/upload/image
 *   GET    /api/scenic/list（关联景点候选）
 */
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 文化行 */
interface CultureRow {
  id: number
  name: string
  category?: string
  coverImage?: string
  summary?: string
  content?: string
  videoUrl?: string
  tags?: string
  relatedScenicIds?: string
  inheritorText?: string
  viewCount?: number
  relatedScenicCount?: number
  createTime?: string
}

/** 关联景点候选 */
interface ScenicOption {
  id: number
  name: string
  cityName?: string
}

/** 列定义 */
const columns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'coverImage', label: '封面', width: '110px' },
  { key: 'name', label: '文化名称', width: '220px' },
  { key: 'category', label: '分类', width: '120px', align: 'center' },
  { key: 'relatedScenicCount', label: '相关景点数', width: '120px', align: 'center' },
  { key: 'viewCount', label: '浏览量', width: '100px', align: 'right' },
  { key: 'createTime', label: '创建时间', width: '170px' },
  { key: 'action', label: '操作', width: '140px', align: 'center' },
]

/** 分类选项（与数据库 culture.category 取值一致） */
const CATEGORY_OPTIONS = ['戏曲', '传统技艺', '饮食文化', '历史文化', '考古文明', '自然生态']

/* ------------------------------ 列表状态 ------------------------------ */
const rows = ref<CultureRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const removingId = ref<number | null>(null)
const pagination = computed<Pagination>(() => ({ page: page.value, size: size.value, total: total.value }))

/* ------------------------------ 筛选条件 ------------------------------ */
const keyword = ref('')
const categoryFilter = ref('')

async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/culture/list', {
      params: {
        keyword: keyword.value || undefined,
        category: categoryFilter.value || undefined,
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
  categoryFilter.value = ''
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

/* ------------------------------ 关联景点候选 ------------------------------ */
const scenicOptions = ref<ScenicOption[]>([])
const scenicKeyword = ref('')

/** 加载景点候选（一次性取前 200 条即可满足关联需求） */
async function loadScenicOptions() {
  try {
    const res = await adminRequest.get('/scenic/list', { params: { page: 1, size: 200 } })
    scenicOptions.value = res.data?.records ?? []
  } catch {
    scenicOptions.value = []
  }
}

/** 过滤后的景点候选 */
const filteredScenics = computed(() => {
  const kw = scenicKeyword.value.trim()
  if (!kw) return scenicOptions.value
  return scenicOptions.value.filter((s) => s.name.includes(kw))
})

/* ------------------------------ 新增 / 编辑抽屉 ------------------------------ */
interface CultureForm {
  id: number | null
  name: string
  category: string
  coverImage: string
  summary: string
  content: string
  videoUrl: string
  tags: string
  relatedScenicIds: number[]
  people: string[]
}

const drawerOpen = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const form = ref<CultureForm>(emptyForm())

function emptyForm(): CultureForm {
  return {
    id: null,
    name: '',
    category: '戏曲',
    coverImage: '',
    summary: '',
    content: '',
    videoUrl: '',
    tags: '',
    relatedScenicIds: [],
    people: [],
  }
}

/** 解析关联景点ID串 */
function parseIds(text?: string): number[] {
  return (text || '')
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => !Number.isNaN(n) && n > 0)
}

/** 解析传承人文本为「原始行」列表 */
function parsePeople(text?: string): string[] {
  return (text || '')
    .split(/\n/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 取人物原始行的姓名段 */
function peopleName(line: string): string {
  return line.split('|')[0] || line
}

function openCreate() {
  form.value = emptyForm()
  drawerOpen.value = true
}

function openEdit(row: CultureRow) {
  form.value = {
    id: row.id,
    name: row.name ?? '',
    category: row.category ?? '戏曲',
    coverImage: row.coverImage ?? '',
    summary: row.summary ?? '',
    content: row.content ?? '',
    videoUrl: row.videoUrl ?? '',
    tags: row.tags ?? '',
    relatedScenicIds: parseIds(row.relatedScenicIds),
    people: parsePeople(row.inheritorText),
  }
  drawerOpen.value = true
}

/** 保存 */
async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写文化名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.value.id,
      name: form.value.name,
      category: form.value.category,
      coverImage: form.value.coverImage,
      // 简介限制 100 字
      summary: form.value.summary.slice(0, 100),
      content: form.value.content,
      videoUrl: form.value.videoUrl,
      tags: form.value.tags,
      relatedScenicIds: form.value.relatedScenicIds.join(','),
      // 相关人物：每行一条，保持「姓名|身份|一句话」格式
      inheritorText: form.value.people.join('\n'),
    }
    if (form.value.id) {
      await adminRequest.put(`/admin/culture/${form.value.id}`, payload)
    } else {
      await adminRequest.post('/admin/culture', payload)
    }
    ElMessage.success('保存成功')
    drawerOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 删除（软删除 + 动画） */
async function removeRow(row: CultureRow) {
  try {
    await ElMessageBox.confirm(`确定删除文化条目「${row.name}」吗？该操作为软删除。`, '删除确认', {
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
    await adminRequest.delete(`/admin/culture/${row.id}`)
    ElMessage.success('已删除')
    await load()
  } finally {
    removingId.value = null
  }
}

/* ------------------------------ 封面上传 ------------------------------ */
async function uploadImage(file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  const res: any = await adminRequest.post('/admin/upload/image', fd)
  return res.data as string
}

function pickCover() {
  if (coverUploading.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    coverUploading.value = true
    try {
      form.value.coverImage = await uploadImage(file)
    } catch {
      ElMessage.error('封面上传失败')
    } finally {
      coverUploading.value = false
    }
  }
  input.click()
}

async function onCoverDrop(e: DragEvent) {
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    form.value.coverImage = await uploadImage(file)
  } catch {
    ElMessage.error('封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

/* ------------------------------ 关联景点多选 ------------------------------ */
function toggleScenic(id: number) {
  const list = form.value.relatedScenicIds
  const index = list.indexOf(id)
  if (index >= 0) {
    list.splice(index, 1)
  } else {
    list.push(id)
  }
}

/* ------------------------------ 标签 / 人物输入 ------------------------------ */
const tagInput = ref('')
const peopleInput = ref('')

const tagList = computed(() =>
  (form.value.tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean),
)

function addTag() {
  const value = tagInput.value.trim()
  if (!value) return
  if (!tagList.value.includes(value)) {
    form.value.tags = tagList.value.length ? `${form.value.tags},${value}` : value
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  form.value.tags = tagList.value.filter((t) => t !== tag).join(',')
}

function addPeople() {
  const value = peopleInput.value.trim()
  if (!value) return
  if (!form.value.people.includes(value)) {
    form.value.people.push(value)
  }
  peopleInput.value = ''
}

function removePeople(index: number) {
  form.value.people.splice(index, 1)
}

/* ------------------------------ WangEditor ------------------------------ */
const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = { placeholder: '请输入详细介绍…' }

function onEditorCreated(editor: unknown) {
  editorRef.value = editor
}

/** 关闭抽屉时销毁编辑器，避免内存泄漏 */
watch(drawerOpen, (open) => {
  if (!open && editorRef.value) {
    ;(editorRef.value as { destroy: () => void }).destroy()
    editorRef.value = null
  }
})

/* ------------------------------ 工具 ------------------------------ */
function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function fmt(value?: string) {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

onMounted(() => {
  loadScenicOptions()
  load()
})
</script>

<template>
  <div class="cm">
    <!-- 顶部标题栏 -->
    <header class="cm__head">
      <div>
        <h2 class="cm__title">文化管理</h2>
        <p class="cm__sub">共 {{ total }} 个文化条目</p>
      </div>
      <button type="button" class="cm__add" @click="openCreate">+ 新增文化</button>
    </header>

    <!-- 筛选栏 -->
    <section class="cm__filter">
      <input v-model="keyword" class="cm__input" type="text" placeholder="搜索文化名称" @keyup.enter="search" />
      <select v-model="categoryFilter" class="cm__select">
        <option value="">全部分类</option>
        <option v-for="c in CATEGORY_OPTIONS" :key="c" :value="c">{{ c }}</option>
      </select>
      <button type="button" class="cm__btn" @click="search">查询</button>
      <button type="button" class="cm__btn cm__btn--ghost" @click="resetFilters">重置</button>
    </section>

    <!-- 表格 -->
    <section class="cm__panel">
      <DataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :removing-key="removingId"
        row-key="id"
        empty-text="暂无文化条目"
        @page-change="changePage"
      >
        <template #coverImage="{ row }">
          <div class="cm__thumb">
            <img v-if="row.coverImage" :src="row.coverImage" :alt="row.name" />
            <span v-else class="cm__thumb-empty">无图</span>
          </div>
        </template>

        <template #name="{ row }">{{ row.name }}</template>

        <template #category="{ row }">
          <span v-if="row.category" class="cm__tag">{{ row.category }}</span>
          <span v-else>—</span>
        </template>

        <template #relatedScenicCount="{ row }">{{ row.relatedScenicCount ?? 0 }}</template>
        <template #viewCount="{ row }">{{ row.viewCount ?? 0 }}</template>
        <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>

        <template #action="{ row }">
          <div class="cm__actions">
            <button type="button" class="cm__link" @click="openEdit(row)">编辑</button>
            <button type="button" class="cm__link cm__link--danger" @click="removeRow(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 新增 / 编辑抽屉 -->
    <transition name="cm-drawer">
      <div v-if="drawerOpen" class="cm__drawer-mask" @click.self="drawerOpen = false">
        <aside class="cm__drawer">
          <header class="cm__drawer-head">
            <h3>{{ form.id ? '编辑文化' : '新增文化' }}</h3>
            <button type="button" class="cm__drawer-close" @click="drawerOpen = false">×</button>
          </header>

          <div class="cm__drawer-body">
            <div class="cm__row">
              <div class="cm__field">
                <label class="cm__label">名称 <i>*</i></label>
                <input v-model="form.name" class="cm__input cm__input--block" type="text" placeholder="请输入文化名称" />
              </div>
              <div class="cm__field">
                <label class="cm__label">分类</label>
                <select v-model="form.category" class="cm__select cm__select--block">
                  <option v-for="c in CATEGORY_OPTIONS" :key="c" :value="c">{{ c }}</option>
                </select>
              </div>
            </div>

            <!-- 封面 -->
            <div class="cm__field">
              <label class="cm__label">封面图</label>
              <div
                class="cm__cover"
                :class="{ 'is-drag': coverUploading }"
                @click="pickCover"
                @dragover.prevent
                @drop.prevent="onCoverDrop"
              >
                <img v-if="form.coverImage" :src="form.coverImage" class="cm__cover-img" alt="封面" />
                <div v-else class="cm__upload-hint">
                  <span>{{ coverUploading ? '上传中…' : '点击或拖拽上传封面' }}</span>
                  <small>支持 jpg / png / webp / gif</small>
                </div>
              </div>
              <button v-if="form.coverImage" type="button" class="cm__mini" @click="form.coverImage = ''">
                移除封面
              </button>
            </div>

            <!-- 简介（100 字以内） -->
            <div class="cm__field">
              <label class="cm__label">简介（100 字以内）</label>
              <textarea
                v-model="form.summary"
                class="cm__textarea"
                rows="2"
                maxlength="100"
                placeholder="一句话概括这条文化"
              />
              <span class="cm__counter">{{ form.summary.length }} / 100</span>
            </div>

            <!-- 详细介绍（WangEditor） -->
            <div class="cm__field">
              <label class="cm__label">详细介绍</label>
              <div class="cm__editor">
                <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" class="cm__editor-bar" />
                <Editor
                  v-model="form.content"
                  :default-config="editorConfig"
                  mode="default"
                  class="cm__editor-body"
                  @on-created="onEditorCreated"
                />
              </div>
            </div>

            <!-- 视频链接 -->
            <div class="cm__field">
              <label class="cm__label">视频链接</label>
              <input
                v-model="form.videoUrl"
                class="cm__input cm__input--block"
                type="text"
                placeholder="B站 / YouTube 链接"
              />
            </div>

            <!-- 相关景点（多选，可搜索） -->
            <div class="cm__field">
              <label class="cm__label">相关景点（已选 {{ form.relatedScenicIds.length }}）</label>
              <input v-model="scenicKeyword" class="cm__input cm__input--block" type="text" placeholder="搜索景点以关联" />
              <div class="cm__scenic-box">
                <label
                  v-for="s in filteredScenics"
                  :key="s.id"
                  class="cm__scenic-item"
                  :class="{ 'is-active': form.relatedScenicIds.includes(s.id) }"
                >
                  <input
                    type="checkbox"
                    :checked="form.relatedScenicIds.includes(s.id)"
                    @change="toggleScenic(s.id)"
                  />
                  <span class="cm__scenic-name">{{ s.name }}</span>
                  <span v-if="s.cityName" class="cm__scenic-city">{{ s.cityName }}</span>
                </label>
                <p v-if="!filteredScenics.length" class="cm__scenic-empty">无匹配景点</p>
              </div>
            </div>

            <!-- 相关人物 -->
            <div class="cm__field">
              <label class="cm__label">相关人物</label>
              <div class="cm__chips">
                <span v-for="(p, i) in form.people" :key="p + i" class="cm__chip">
                  {{ peopleName(p) }}
                  <button type="button" @click="removePeople(i)">×</button>
                </span>
                <input
                  v-model="peopleInput"
                  class="cm__chip-input"
                  type="text"
                  placeholder="回车添加，如 康芷林"
                  @keyup.enter="addPeople"
                />
              </div>
            </div>

            <!-- 标签 -->
            <div class="cm__field">
              <label class="cm__label">标签</label>
              <div class="cm__chips">
                <span v-for="tag in tagList" :key="tag" class="cm__chip">
                  {{ tag }}
                  <button type="button" @click="removeTag(tag)">×</button>
                </span>
                <input
                  v-model="tagInput"
                  class="cm__chip-input"
                  type="text"
                  placeholder="回车添加标签"
                  @keyup.enter="addTag"
                />
              </div>
            </div>
          </div>

          <footer class="cm__drawer-foot">
            <button type="button" class="cm__btn cm__btn--ghost" @click="drawerOpen = false">取消</button>
            <button type="button" class="cm__btn" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </footer>
        </aside>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.cm__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.cm__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.cm__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.cm__add {
  padding: 10px 22px;
  border: none;
  border-radius: $admin-radius-sm;
  background: linear-gradient(120deg, #b98f45, $admin-gold);
  font-size: 14px;
  font-weight: 700;
  color: #241a08;
  cursor: pointer;
  transition: filter 0.3s $admin-ease;

  &:hover {
    filter: brightness(1.08);
  }
}

.cm__filter {
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

.cm__input,
.cm__select {
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

.cm__select {
  color-scheme: dark;
  cursor: pointer;
}

.cm__input--block,
.cm__select--block {
  width: 100%;
}

.cm__btn {
  height: 40px;
  padding: 0 20px;
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

.cm__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;

  &:hover {
    color: $admin-gold;
  }
}

.cm__panel {
  padding: 20px;
  @include admin-card;
}

.cm__thumb {
  width: 80px;
  height: 60px;
  overflow: hidden;
  border: 1px solid $admin-border;
  border-radius: 8px;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.45s $admin-ease;
  }

  &:hover img {
    transform: scale(1.15);
  }
}

.cm__thumb-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: $admin-text-muted;
}

.cm__tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(58, 90, 64, 0.28);
  font-size: 12px;
  color: #8fcea6;
}

.cm__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.cm__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }
}

.cm__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 抽屉 */
.cm__drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(6, 12, 22, 0.66);
  backdrop-filter: blur(4px);
}

.cm__drawer {
  display: flex;
  flex-direction: column;
  width: min(600px, 100%);
  height: 100%;
  border-left: 1px solid $admin-gold-line;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.cm__drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid $admin-border;

  h3 {
    font-size: 18px;
    color: $admin-text;
  }
}

.cm__drawer-close {
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

.cm__drawer-body {
  flex: 1;
  padding: 22px 24px;
  overflow-y: auto;
}

.cm__drawer-foot {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: 1px solid $admin-border;
}

.cm__field {
  margin-bottom: 18px;
}

.cm__label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: $admin-text-muted;

  i {
    color: $admin-red-down;
    font-style: normal;
  }
}

.cm__row {
  display: flex;
  gap: 14px;

  .cm__field {
    flex: 1;
  }
}

.cm__counter {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  text-align: right;
  color: $admin-text-muted;
}

/* 封面 */
.cm__cover {
  display: grid;
  place-items: center;
  height: 168px;
  overflow: hidden;
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

.cm__cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cm__upload-hint {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  color: $admin-text-muted;

  small {
    font-size: 12px;
    opacity: 0.7;
  }
}

.cm__mini {
  margin-top: 8px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: $admin-red-down;
  cursor: pointer;
}

.cm__textarea {
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

/* 富文本编辑器容器 */
.cm__editor {
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  overflow: hidden;
}

.cm__editor-bar {
  border-bottom: 1px solid $admin-border;
}

.cm__editor-body {
  min-height: 220px;
  overflow-y: auto;
}

/* 关联景点多选 */
.cm__scenic-box {
  max-height: 180px;
  margin-top: 10px;
  padding: 6px;
  overflow-y: auto;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.5);
}

.cm__scenic-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: $admin-text-muted;
  cursor: pointer;
  transition: background-color 0.25s $admin-ease;

  &:hover {
    background: $admin-gold-soft;
  }

  &.is-active {
    color: $admin-gold;
  }

  input {
    accent-color: #{$admin-gold};
  }
}

.cm__scenic-name {
  color: $admin-text;
}

.cm__scenic-city {
  margin-left: auto;
  font-size: 12px;
  color: $admin-text-muted;
}

.cm__scenic-empty {
  padding: 14px;
  font-size: 13px;
  text-align: center;
  color: $admin-text-muted;
}

/* 标签 / 人物 chips */
.cm__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 42px;
  padding: 6px 10px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(var(--admin-mask-rgb), 0.6);
}

.cm__chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: $admin-gold-soft;
  font-size: 12px;
  color: $admin-gold;

  button {
    border: none;
    background: transparent;
    color: $admin-gold;
    cursor: pointer;
  }
}

.cm__chip-input {
  flex: 1;
  min-width: 120px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-text;
  outline: none;
}

/* 抽屉过渡 */
.cm-drawer-enter-active,
.cm-drawer-leave-active {
  transition: opacity 0.3s $admin-ease;
}

.cm-drawer-enter-active .cm__drawer,
.cm-drawer-leave-active .cm__drawer {
  transition: transform 0.34s $admin-ease;
}

.cm-drawer-enter-from,
.cm-drawer-leave-to {
  opacity: 0;
}

.cm-drawer-enter-from .cm__drawer,
.cm-drawer-leave-to .cm__drawer {
  transform: translateX(100%);
}

@media (max-width: 768px) {
  .cm__drawer {
    width: 100%;
  }

  .cm__row {
    flex-direction: column;
  }
}
</style>
