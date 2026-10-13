<script setup lang="ts">
/**
 * 后台 · 景点管理
 *
 * 功能：筛选（关键词 / 城市 / 类型 / 等级）、景点列表、新增 / 编辑抽屉
 * （含封面上传、详情图多图上传、坐标拾取）、删除。
 *
 * 接口：
 *   GET    /api/admin/scenic/list
 *   POST   /api/admin/scenic
 *   PUT    /api/admin/scenic/{id}
 *   DELETE /api/admin/scenic/{id}
 *   POST   /api/admin/upload/image
 *   GET    /api/city/list（市州下拉）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import adminRequest from '@/admin/api/adminRequest'
import DataTable from '@/admin/components/DataTable.vue'
import type { DataColumn, Pagination } from '@/admin/components/DataTable.vue'

/** 景点行 */
interface ScenicRow {
  id: number
  name: string
  cityId?: number
  cityName?: string
  type?: string
  level?: string
  address?: string
  longitude?: number
  latitude?: number
  coverImage?: string
  description?: string
  openTime?: string
  ticketInfo?: string
  score?: number
  tags?: string
  recommendReason?: string
  viewCount?: number
  collectCount?: number
  createTime?: string
  images?: string[]
}

/** 市州选项 */
interface CityOption {
  id: number
  name: string
}

/** 列定义 */
const columns: DataColumn[] = [
  { key: 'id', label: 'ID', width: '64px' },
  { key: 'coverImage', label: '封面', width: '110px' },
  { key: 'name', label: '景点名称', width: '200px' },
  { key: 'cityName', label: '所属城市', width: '100px' },
  { key: 'type', label: '类型', width: '110px', align: 'center' },
  { key: 'level', label: '等级', width: '80px', align: 'center' },
  { key: 'score', label: '评分', width: '150px' },
  { key: 'viewCount', label: '浏览量', width: '100px', align: 'right' },
  { key: 'collectCount', label: '收藏数', width: '90px', align: 'right' },
  { key: 'createTime', label: '创建时间', width: '170px' },
  { key: 'action', label: '操作', width: '180px', align: 'center' },
]

/** 类型选项（与数据库 scenic_spot.type 取值一致） */
const TYPE_OPTIONS = ['自然风光', '人文古迹', '博物馆', '红色旅游', '亲子', '古镇']
/** 等级选项 */
const LEVEL_OPTIONS = ['5A', '4A', '3A']

/* ------------------------------ 列表状态 ------------------------------ */
const rows = ref<ScenicRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const removingId = ref<number | null>(null)
const pagination = computed<Pagination>(() => ({ page: page.value, size: size.value, total: total.value }))

/* ------------------------------ 筛选条件 ------------------------------ */
const keyword = ref('')
const cityFilter = ref<'' | number>('')
const typeFilter = ref('')
const levelFilter = ref('')
const cities = ref<CityOption[]>([])

/** 加载市州下拉 */
async function loadCities() {
  try {
    const res = await adminRequest.get('/city/list')
    cities.value = res.data ?? []
  } catch {
    cities.value = []
  }
}

/** 加载列表 */
async function load() {
  loading.value = true
  try {
    const res = await adminRequest.get('/admin/scenic/list', {
      params: {
        keyword: keyword.value || undefined,
        cityId: cityFilter.value === '' ? undefined : cityFilter.value,
        type: typeFilter.value || undefined,
        level: levelFilter.value || undefined,
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
  cityFilter.value = ''
  typeFilter.value = ''
  levelFilter.value = ''
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

/* ------------------------------ 新增 / 编辑抽屉 ------------------------------ */
interface ScenicForm {
  id: number | null
  name: string
  cityId: number | null
  type: string
  level: string
  address: string
  longitude: string
  latitude: string
  coverImage: string
  description: string
  openTime: string
  ticketInfo: string
  score: number
  tags: string
  recommendReason: string
  images: string[]
}

const drawerOpen = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const imageUploading = ref(false)
const form = ref<ScenicForm>(emptyForm())

function emptyForm(): ScenicForm {
  return {
    id: null,
    name: '',
    cityId: null,
    type: '自然风光',
    level: '4A',
    address: '',
    longitude: '',
    latitude: '',
    coverImage: '',
    description: '',
    openTime: '',
    ticketInfo: '',
    score: 5,
    tags: '',
    recommendReason: '',
    images: [],
  }
}

function openCreate() {
  form.value = emptyForm()
  drawerOpen.value = true
}

function openEdit(row: ScenicRow) {
  form.value = {
    id: row.id,
    name: row.name ?? '',
    cityId: row.cityId ?? null,
    type: row.type ?? '自然风光',
    level: row.level ?? '4A',
    address: row.address ?? '',
    longitude: row.longitude != null ? String(row.longitude) : '',
    latitude: row.latitude != null ? String(row.latitude) : '',
    coverImage: row.coverImage ?? '',
    description: row.description ?? '',
    openTime: row.openTime ?? '',
    ticketInfo: row.ticketInfo ?? '',
    score: row.score ?? 5,
    tags: row.tags ?? '',
    recommendReason: row.recommendReason ?? '',
    images: [...(row.images ?? [])],
  }
  drawerOpen.value = true
}

/** 保存（新增 / 编辑） */
async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写景点名称')
    return
  }
  if (form.value.cityId == null) {
    ElMessage.warning('请选择所属城市')
    return
  }
  if (!form.value.coverImage) {
    ElMessage.warning('请上传封面图')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      longitude: form.value.longitude || null,
      latitude: form.value.latitude || null,
    }
    if (form.value.id) {
      await adminRequest.put(`/admin/scenic/${form.value.id}`, payload)
    } else {
      await adminRequest.post('/admin/scenic', payload)
    }
    ElMessage.success('保存成功')
    drawerOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 删除（软删除 + 动画） */
async function removeRow(row: ScenicRow) {
  try {
    await ElMessageBox.confirm(`确定删除景点「${row.name}」吗？该操作为软删除。`, '删除确认', {
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
    await adminRequest.delete(`/admin/scenic/${row.id}`)
    ElMessage.success('已删除')
    await load()
  } finally {
    removingId.value = null
  }
}

/** 查看：跳转前台景点详情页 */
function viewRow(row: ScenicRow) {
  window.open(`/scenic/${row.id}`, '_blank')
}

/* ------------------------------ 图片上传 ------------------------------ */
/** 通用图片上传，返回可访问地址 */
async function uploadImage(file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  const res: any = await adminRequest.post('/admin/upload/image', fd)
  return res.data as string
}

/** 选择封面 */
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

/** 拖拽上传封面 */
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

/** 选择详情图（多图） */
function pickImages() {
  if (imageUploading.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.multiple = true
  input.onchange = async () => {
    const files = Array.from(input.files ?? [])
    if (!files.length) return
    imageUploading.value = true
    try {
      for (const file of files) {
        form.value.images.push(await uploadImage(file))
      }
    } catch {
      ElMessage.error('图片上传失败')
    } finally {
      imageUploading.value = false
    }
  }
  input.click()
}

/** 拖拽上传详情图 */
async function onImagesDrop(e: DragEvent) {
  const files = Array.from(e.dataTransfer?.files ?? [])
  if (!files.length) return
  imageUploading.value = true
  try {
    for (const file of files) {
      form.value.images.push(await uploadImage(file))
    }
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    imageUploading.value = false
  }
}

function removeImage(index: number) {
  form.value.images.splice(index, 1)
}

/* ------------------------------ 坐标拾取 ------------------------------ */
function pickCoordinate() {
  window.open('https://lbs.amap.com/tools/picker', '_blank')
}

/* ------------------------------ 标签输入 ------------------------------ */
const tagInput = ref('')

const tagList = computed(() => {
  return (form.value.tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
})

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

/* ------------------------------ 工具 ------------------------------ */
function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function fmt(value?: string) {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 评分星星（四舍五入到整星） */
function stars(score?: number) {
  const filled = Math.round(score ?? 0)
  return Array.from({ length: 5 }, (_, i) => i < filled)
}

onMounted(() => {
  loadCities()
  load()
})
</script>

<template>
  <div class="sm">
    <!-- 顶部标题栏 -->
    <header class="sm__head">
      <div>
        <h2 class="sm__title">景点管理</h2>
        <p class="sm__sub">共 {{ total }} 个景点</p>
      </div>
      <button type="button" class="sm__add" @click="openCreate">+ 新增景点</button>
    </header>

    <!-- 筛选栏 -->
    <section class="sm__filter">
      <input v-model="keyword" class="sm__input" type="text" placeholder="搜索景点名称" @keyup.enter="search" />
      <select v-model="cityFilter" class="sm__select">
        <option value="">全部市州</option>
        <option v-for="c in cities" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select v-model="typeFilter" class="sm__select">
        <option value="">全部类型</option>
        <option v-for="t in TYPE_OPTIONS" :key="t" :value="t">{{ t }}</option>
      </select>
      <select v-model="levelFilter" class="sm__select">
        <option value="">全部等级</option>
        <option v-for="l in LEVEL_OPTIONS" :key="l" :value="l">{{ l }}</option>
      </select>
      <button type="button" class="sm__btn" @click="search">查询</button>
      <button type="button" class="sm__btn sm__btn--ghost" @click="resetFilters">重置</button>
    </section>

    <!-- 表格 -->
    <section class="sm__panel">
      <DataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :removing-key="removingId"
        row-key="id"
        empty-text="暂无景点"
        @page-change="changePage"
      >
        <!-- 封面缩略图（hover 放大） -->
        <template #coverImage="{ row }">
          <div class="sm__thumb">
            <img v-if="row.coverImage" :src="row.coverImage" :alt="row.name" />
            <span v-else class="sm__thumb-empty">无图</span>
          </div>
        </template>

        <template #name="{ row }">{{ row.name }}</template>
        <template #cityName="{ row }">{{ row.cityName || '—' }}</template>

        <!-- 类型标签 -->
        <template #type="{ row }">
          <span v-if="row.type" class="sm__tag">{{ row.type }}</span>
          <span v-else>—</span>
        </template>

        <!-- 等级金色标签 -->
        <template #level="{ row }">
          <span v-if="row.level" class="sm__level">{{ row.level }}</span>
          <span v-else>—</span>
        </template>

        <!-- 评分金色星星 -->
        <template #score="{ row }">
          <div class="sm__score">
            <span
              v-for="(on, i) in stars(row.score)"
              :key="i"
              class="sm__star"
              :class="{ 'is-on': on }"
            >
              ★
            </span>
            <span class="sm__score-num">{{ row.score ?? '—' }}</span>
          </div>
        </template>

        <template #viewCount="{ row }">{{ row.viewCount ?? 0 }}</template>
        <template #collectCount="{ row }">{{ row.collectCount ?? 0 }}</template>
        <template #createTime="{ row }">{{ fmt(row.createTime) }}</template>

        <!-- 操作 -->
        <template #action="{ row }">
          <div class="sm__actions">
            <button type="button" class="sm__link" @click="openEdit(row)">编辑</button>
            <button type="button" class="sm__link" @click="viewRow(row)">查看</button>
            <button type="button" class="sm__link sm__link--danger" @click="removeRow(row)">删除</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 新增 / 编辑抽屉 -->
    <transition name="sm-drawer">
      <div v-if="drawerOpen" class="sm__drawer-mask" @click.self="drawerOpen = false">
        <aside class="sm__drawer">
          <header class="sm__drawer-head">
            <h3>{{ form.id ? '编辑景点' : '新增景点' }}</h3>
            <button type="button" class="sm__drawer-close" @click="drawerOpen = false">×</button>
          </header>

          <div class="sm__drawer-body">
            <div class="sm__field">
              <label class="sm__label">景点名称 <i>*</i></label>
              <input v-model="form.name" class="sm__input sm__input--block" type="text" placeholder="请输入景点名称" />
            </div>

            <div class="sm__row">
              <div class="sm__field">
                <label class="sm__label">所属城市 <i>*</i></label>
                <select v-model.number="form.cityId" class="sm__select sm__select--block">
                  <option :value="null">请选择城市</option>
                  <option v-for="c in cities" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
              </div>
              <div class="sm__field">
                <label class="sm__label">景点类型</label>
                <select v-model="form.type" class="sm__select sm__select--block">
                  <option v-for="t in TYPE_OPTIONS" :key="t" :value="t">{{ t }}</option>
                </select>
              </div>
            </div>

            <div class="sm__row">
              <div class="sm__field">
                <label class="sm__label">景区等级</label>
                <select v-model="form.level" class="sm__select sm__select--block">
                  <option v-for="l in LEVEL_OPTIONS" :key="l" :value="l">{{ l }}</option>
                </select>
              </div>
              <div class="sm__field">
                <label class="sm__label">评分</label>
                <input v-model.number="form.score" class="sm__input sm__input--block" type="number" min="0" max="5" step="0.1" />
              </div>
            </div>

            <div class="sm__field">
              <label class="sm__label">地址</label>
              <input v-model="form.address" class="sm__input sm__input--block" type="text" placeholder="请输入详细地址" />
            </div>

            <div class="sm__row">
              <div class="sm__field">
                <label class="sm__label">经度</label>
                <input v-model="form.longitude" class="sm__input sm__input--block" type="text" placeholder="如 103.918" />
              </div>
              <div class="sm__field">
                <label class="sm__label">纬度</label>
                <input v-model="form.latitude" class="sm__input sm__input--block" type="text" placeholder="如 33.260" />
              </div>
              <button type="button" class="sm__pick" @click="pickCoordinate">拾取坐标</button>
            </div>

            <!-- 封面 -->
            <div class="sm__field">
              <label class="sm__label">封面图 <i>*</i></label>
              <div
                class="sm__cover"
                :class="{ 'is-drag': coverUploading }"
                @click="pickCover"
                @dragover.prevent
                @drop.prevent="onCoverDrop"
              >
                <img v-if="form.coverImage" :src="form.coverImage" class="sm__cover-img" alt="封面" />
                <div v-else class="sm__upload-hint">
                  <span>{{ coverUploading ? '上传中…' : '点击或拖拽上传封面' }}</span>
                  <small>支持 jpg / png / webp / gif</small>
                </div>
              </div>
              <button v-if="form.coverImage" type="button" class="sm__mini" @click="form.coverImage = ''">
                移除封面
              </button>
            </div>

            <!-- 详情图 -->
            <div class="sm__field">
              <label class="sm__label">详情图（可多张）</label>
              <div class="sm__gallery" @dragover.prevent @drop.prevent="onImagesDrop">
                <div v-for="(img, i) in form.images" :key="img + i" class="sm__gallery-item">
                  <img :src="img" alt="详情图" />
                  <button type="button" class="sm__gallery-remove" @click="removeImage(i)">×</button>
                </div>
                <button type="button" class="sm__gallery-add" @click="pickImages">
                  {{ imageUploading ? '上传中…' : '+ 添加' }}
                </button>
              </div>
            </div>

            <div class="sm__field">
              <label class="sm__label">简介</label>
              <textarea v-model="form.description" class="sm__textarea" rows="3" placeholder="景点简介" />
            </div>

            <div class="sm__row">
              <div class="sm__field">
                <label class="sm__label">开放时间</label>
                <input v-model="form.openTime" class="sm__input sm__input--block" type="text" placeholder="如 08:00-18:00" />
              </div>
              <div class="sm__field">
                <label class="sm__label">门票</label>
                <input v-model="form.ticketInfo" class="sm__input sm__input--block" type="text" placeholder="如 80 元" />
              </div>
            </div>

            <!-- 标签 -->
            <div class="sm__field">
              <label class="sm__label">标签</label>
              <div class="sm__tags-input">
                <span v-for="tag in tagList" :key="tag" class="sm__chip">
                  {{ tag }}
                  <button type="button" @click="removeTag(tag)">×</button>
                </span>
                <input v-model="tagInput" class="sm__tag-input" type="text" placeholder="回车添加标签" @keyup.enter="addTag" />
              </div>
            </div>

            <div class="sm__field">
              <label class="sm__label">推荐理由</label>
              <textarea v-model="form.recommendReason" class="sm__textarea" rows="2" placeholder="一句话推荐理由" />
            </div>
          </div>

          <footer class="sm__drawer-foot">
            <button type="button" class="sm__btn sm__btn--ghost" @click="drawerOpen = false">取消</button>
            <button type="button" class="sm__btn" :disabled="saving" @click="save">
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

.sm__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.sm__title {
  font-size: 26px;
  font-weight: 700;
  color: $admin-text;
}

.sm__sub {
  margin-top: 6px;
  font-size: 14px;
  color: $admin-text-muted;
}

.sm__add {
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

/* 筛选栏 */
.sm__filter {
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

.sm__input,
.sm__select {
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

.sm__select {
  color-scheme: dark;
  cursor: pointer;
}

.sm__input--block,
.sm__select--block {
  width: 100%;
}

.sm__btn {
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

.sm__btn--ghost {
  border-color: $admin-border;
  background: transparent;
  color: $admin-text-muted;

  &:hover {
    color: $admin-gold;
  }
}

.sm__panel {
  padding: 20px;
  @include admin-card;
}

/* 封面缩略图 */
.sm__thumb {
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

.sm__thumb-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: $admin-text-muted;
}

/* 标签与等级 */
.sm__tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(74, 125, 181, 0.16);
  font-size: 12px;
  color: #8fb6db;
}

.sm__level {
  display: inline-block;
  padding: 3px 10px;
  border: 1px solid $admin-gold-line;
  border-radius: 6px;
  background: $admin-gold-soft;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: $admin-gold;
}

/* 评分 */
.sm__score {
  display: flex;
  gap: 2px;
  align-items: center;
}

.sm__star {
  font-size: 14px;
  color: rgba(122, 138, 160, 0.4);

  &.is-on {
    color: $admin-gold;
  }
}

.sm__score-num {
  margin-left: 6px;
  font-family: $admin-font-num;
  font-size: 13px;
  color: $admin-text;
}

.sm__actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.sm__link {
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    color: #e0c07c;
  }
}

.sm__link--danger {
  color: $admin-red-down;

  &:hover {
    color: #e57c81;
  }
}

/* 抽屉 */
.sm__drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(6, 12, 22, 0.66);
  backdrop-filter: blur(4px);
}

.sm__drawer {
  display: flex;
  flex-direction: column;
  width: min(600px, 100%);
  height: 100%;
  border-left: 1px solid $admin-gold-line;
  background: $admin-panel;
  box-shadow: $admin-shadow;
}

.sm__drawer-head {
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

.sm__drawer-close {
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

.sm__drawer-body {
  flex: 1;
  padding: 22px 24px;
  overflow-y: auto;
}

.sm__drawer-foot {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: 1px solid $admin-border;
}

.sm__field {
  margin-bottom: 18px;
}

.sm__label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: $admin-text-muted;

  i {
    color: $admin-red-down;
    font-style: normal;
  }
}

.sm__row {
  display: flex;
  gap: 14px;

  .sm__field {
    flex: 1;
  }
}

.sm__pick {
  align-self: flex-end;
  height: 40px;
  padding: 0 14px;
  border: 1px solid $admin-gold-line;
  border-radius: $admin-radius-sm;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: $admin-gold-soft;
  }
}

/* 封面拖拽区 */
.sm__cover {
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

.sm__cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sm__upload-hint {
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

.sm__mini {
  margin-top: 8px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: $admin-red-down;
  cursor: pointer;
}

/* 详情图 */
.sm__gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.sm__gallery-item {
  position: relative;
  width: 88px;
  height: 66px;
  overflow: hidden;
  border: 1px solid $admin-border;
  border-radius: 8px;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.sm__gallery-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 50%;
  background: rgba(6, 12, 22, 0.7);
  font-size: 13px;
  line-height: 1;
  color: #fff;
  cursor: pointer;
}

.sm__gallery-add {
  width: 88px;
  height: 66px;
  border: 1px dashed $admin-gold-line;
  border-radius: 8px;
  background: rgba(var(--admin-mask-rgb), 0.5);
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;

  &:hover {
    background: $admin-gold-soft;
  }
}

.sm__textarea {
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

/* 标签输入 */
.sm__tags-input {
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

.sm__chip {
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

.sm__tag-input {
  flex: 1;
  min-width: 120px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: $admin-text;
  outline: none;
}

/* 抽屉过渡 */
.sm-drawer-enter-active,
.sm-drawer-leave-active {
  transition: opacity 0.3s $admin-ease;
}

.sm-drawer-enter-active .sm__drawer,
.sm-drawer-leave-active .sm__drawer {
  transition: transform 0.34s $admin-ease;
}

.sm-drawer-enter-from,
.sm-drawer-leave-to {
  opacity: 0;
}

.sm-drawer-enter-from .sm__drawer,
.sm-drawer-leave-to .sm__drawer {
  transform: translateX(100%);
}

/* 小屏：抽屉全屏，行内字段换行 */
@media (max-width: 768px) {
  .sm__drawer {
    width: 100%;
  }

  .sm__row {
    flex-direction: column;
  }

  .sm__pick {
    align-self: stretch;
  }
}
</style>
