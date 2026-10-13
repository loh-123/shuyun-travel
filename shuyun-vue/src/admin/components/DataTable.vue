<script setup lang="ts" generic="T extends Record<string, any>">
/**
 * 通用数据表格（DataTable）
 *
 * <p>后台所有列表页的统一基座：深色控制台风格 + 金色描边表头。
 * 完全自定义外观，不依赖 Element Plus 表格组件。</p>
 *
 * 能力：列插槽自定义、选择列、骨架屏加载、空状态（蜀锦纹样）、
 * 行错位淡入（每行延迟 30ms）、删除行淡出收缩、自定义分页。
 *
 * @example
 * <DataTable
 *   :columns="cols"
 *   :data="rows"
 *   :loading="loading"
 *   :pagination="{ page, size, total }"
 *   v-model:selected="selected"
 *   @page-change="onPage"
 * >
 *   <template #status="{ row }">...</template>
 * </DataTable>
 */
import { computed } from 'vue'

/** 列配置 */
export interface DataColumn {
  /** 字段名（未指定 slot 时同时作为插槽名） */
  key: string
  /** 表头文案 */
  label: string
  /** 列宽（CSS 值，如 '120px'） */
  width?: string
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 自定义插槽名（默认取 key） */
  slot?: string
}

/** 分页配置 */
export interface Pagination {
  /** 当前页（从 1 开始） */
  page: number
  /** 每页大小 */
  size: number
  /** 总条数 */
  total: number
}

const props = withDefaults(
  defineProps<{
    /** 列定义 */
    columns: DataColumn[]
    /** 行数据（推荐） */
    data?: T[]
    /** 行数据（兼容旧用法，data 为空时兜底） */
    rows?: T[]
    /** 是否加载中（显示骨架屏） */
    loading?: boolean
    /** 空状态文案 */
    emptyText?: string
    /** 分页配置（传入才显示分页条） */
    pagination?: Pagination
    /** 是否显示选择列 */
    selectable?: boolean
    /** 行唯一键字段名 */
    rowKey?: string
    /** 已选中行的 key 集合（v-model:selected） */
    selected?: Array<string | number>
    /** 正在删除的行 key（触发淡出收缩动画） */
    removingKey?: string | number | null
  }>(),
  {
    loading: false,
    emptyText: '暂无数据',
    selectable: false,
    rowKey: 'id',
    selected: () => [],
    removingKey: null,
  },
)

const emit = defineEmits<{
  /** 选中项变化 */
  (e: 'update:selected', keys: Array<string | number>): void
  /** 分页切换 */
  (e: 'page-change', page: number): void
}>()

/** 实际渲染的数据（兼容 data / rows 两种写法） */
const list = computed<T[]>(() => props.data ?? props.rows ?? [])

/** 取行的唯一 key */
function keyOf(row: T): string | number {
  return row[props.rowKey] as string | number
}

/** 该行是否被选中 */
function isSelected(row: T): boolean {
  return props.selected.includes(keyOf(row))
}

/** 是否全选 */
const allSelected = computed(() => list.value.length > 0 && list.value.every(isSelected))

/** 是否半选（部分选中） */
const indeterminate = computed(() => props.selected.length > 0 && !allSelected.value)

/** 切换单行选中 */
function toggleRow(row: T) {
  const key = keyOf(row)
  const next = isSelected(row)
    ? props.selected.filter((k) => k !== key)
    : [...props.selected, key]
  emit('update:selected', next)
}

/** 切换全选 */
function toggleAll() {
  emit('update:selected', allSelected.value ? [] : list.value.map(keyOf))
}

/** 列渲染用的插槽名 */
function slotName(col: DataColumn): string {
  return col.slot || col.key
}

/* ------------------------------ 分页 ------------------------------ */
const pageCount = computed(() => {
  if (!props.pagination) return 1
  return Math.max(1, Math.ceil(props.pagination.total / props.pagination.size))
})

const currentPage = computed(() => props.pagination?.page ?? 1)

/** 页码序列（过多时以省略号折叠） */
const pageList = computed<(number | '...')[]>(() => {
  const total = pageCount.value
  const cur = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: (number | '...')[] = [1]
  const start = Math.max(2, cur - 1)
  const end = Math.min(total - 1, cur + 1)
  if (start > 2) pages.push('...')
  for (let i = start; i <= end; i++) pages.push(i)
  if (end < total - 1) pages.push('...')
  pages.push(total)
  return pages
})

/** 跳转到指定页 */
function go(page: number) {
  if (!props.pagination || page < 1 || page > pageCount.value || page === currentPage.value) return
  emit('page-change', page)
}

/** 骨架屏行数（避免空表时高度塌陷） */
const skeletonRows = 6
</script>

<template>
  <div class="dt">
    <div class="dt__scroll">
      <table class="dt__table">
        <thead>
          <tr>
            <th v-if="selectable" class="dt__check-col">
              <label class="dt__checkbox">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  :indeterminate="indeterminate"
                  @change="toggleAll"
                />
                <span class="dt__checkbox-box" />
              </label>
            </th>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ width: col.width, textAlign: col.align || 'left' }"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>

        <!-- 加载中：金色骨架屏 -->
        <tbody v-if="loading">
          <tr v-for="r in skeletonRows" :key="'sk-' + r" class="dt__skeleton-row">
            <td v-if="selectable"><span class="dt__sk dt__sk--sm" /></td>
            <td v-for="col in columns" :key="col.key">
              <span class="dt__sk" :style="{ width: r % 3 === 0 ? '60%' : '85%' }" />
            </td>
          </tr>
        </tbody>

        <!-- 空状态：蜀锦纹样 -->
        <tbody v-else-if="!list.length">
          <tr>
            <td :colspan="columns.length + (selectable ? 1 : 0)" class="dt__empty">
              <div class="dt__empty-pattern" />
              <p class="dt__empty-text">{{ emptyText }}</p>
            </td>
          </tr>
        </tbody>

        <!-- 数据行 -->
        <tbody v-else>
          <tr
            v-for="(row, index) in list"
            :key="keyOf(row)"
            class="dt__row"
            :class="{ 'is-removing': removingKey !== null && removingKey === keyOf(row) }"
            :style="{ '--row-i': index }"
          >
            <td v-if="selectable" class="dt__check-col">
              <label class="dt__checkbox">
                <input type="checkbox" :checked="isSelected(row)" @change="toggleRow(row)" />
                <span class="dt__checkbox-box" />
              </label>
            </td>
            <td
              v-for="col in columns"
              :key="col.key"
              :style="{ textAlign: col.align || 'left' }"
            >
              <slot :name="slotName(col)" :row="row" :value="row[col.key]" :index="index">
                {{ row[col.key] ?? '—' }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 自定义分页 -->
    <div v-if="pagination && pageCount > 1" class="dt__pager">
      <button type="button" class="dt__pager-btn" :disabled="currentPage <= 1" @click="go(currentPage - 1)">
        上一页
      </button>
      <div class="dt__pager-nums">
        <template v-for="(p, i) in pageList" :key="i">
          <span v-if="p === '...'" class="dt__pager-ellipsis">…</span>
          <button
            v-else
            type="button"
            class="dt__pager-num"
            :class="{ 'is-active': p === currentPage }"
            @click="go(p as number)"
          >
            {{ p }}
          </button>
        </template>
      </div>
      <button
        type="button"
        class="dt__pager-btn"
        :disabled="currentPage >= pageCount"
        @click="go(currentPage + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.dt {
  width: 100%;
}

.dt__scroll {
  width: 100%;
  overflow-x: auto;
}

.dt__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  color: $admin-text;

  th {
    padding: 14px 16px;
    font-weight: 600;
    font-size: 12px;
    color: $admin-gold;
    letter-spacing: 0.05em;
    white-space: nowrap;
    text-transform: uppercase;
    background: $admin-panel;
    border-bottom: 1px solid $admin-border;
  }

  td {
    padding: 14px 16px;
    font-size: 14px;
    color: $admin-text;
    border-bottom: 1px solid rgba(212, 179, 116, 0.08);
    white-space: nowrap;
  }
}

/* 行 hover 高亮 + 错位淡入 */
.dt__row {
  transition: background-color 0.25s $admin-ease;
  animation: dt-row-in 0.42s $admin-ease both;
  animation-delay: calc(var(--row-i) * 30ms);

  &:hover {
    background: rgba(212, 179, 116, 0.05);
  }
}

@keyframes dt-row-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 删除：淡出 + 收缩高度 */
.dt__row.is-removing {
  animation: none;
  background: rgba(208, 92, 98, 0.08);

  td {
    animation: dt-row-out 0.34s $admin-ease forwards;
  }
}

@keyframes dt-row-out {
  from {
    opacity: 1;
    padding-top: 14px;
    padding-bottom: 14px;
  }
  to {
    opacity: 0;
    padding-top: 0;
    padding-bottom: 0;
    transform: scaleY(0.6);
  }
}

/* 选择列 */
.dt__check-col {
  width: 46px;
  text-align: center;
}

.dt__checkbox {
  position: relative;
  display: inline-flex;
  cursor: pointer;

  input {
    position: absolute;
    width: 0;
    height: 0;
    opacity: 0;
  }
}

.dt__checkbox-box {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 1px solid $admin-gold-line;
  border-radius: 4px;
  background: rgba(var(--admin-mask-rgb), 0.6);
  transition:
    background-color 0.25s $admin-ease,
    border-color 0.25s $admin-ease;

  &::after {
    content: '';
    display: block;
    width: 4px;
    height: 8px;
    margin: 1px 0 0 5px;
    border: solid #241a08;
    border-width: 0 2px 2px 0;
    transform: rotate(45deg) scale(0);
    transition: transform 0.2s $admin-ease;
  }
}

.dt__checkbox input:checked + .dt__checkbox-box {
  background: linear-gradient(120deg, #b98f45, $admin-gold);
  border-color: $admin-gold;

  &::after {
    transform: rotate(45deg) scale(1);
  }
}

.dt__checkbox input:indeterminate + .dt__checkbox-box {
  background: $admin-gold-soft;

  &::after {
    width: 8px;
    height: 0;
    margin: 6px 0 0 3px;
    border-width: 0 0 2px 0;
    transform: none;
  }
}

/* 骨架屏 */
.dt__skeleton-row td {
  border-bottom: 1px solid rgba(212, 179, 116, 0.06);
}

.dt__sk {
  display: inline-block;
  width: 85%;
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(
    90deg,
    rgba(212, 179, 116, 0.08) 25%,
    rgba(212, 179, 116, 0.22) 37%,
    rgba(212, 179, 116, 0.08) 63%
  );
  background-size: 400% 100%;
  animation: dt-shimmer 1.4s ease infinite;
}

.dt__sk--sm {
  width: 16px;
  height: 16px;
}

@keyframes dt-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* 空状态 */
.dt__empty {
  padding: 56px 0 !important;
  text-align: center;
}

.dt__empty-pattern {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border: 1px solid $admin-gold-line;
  border-radius: 10px;
  opacity: 0.6;
  background-image:
    repeating-linear-gradient(45deg, $admin-gold 0 1px, transparent 1px 12px),
    repeating-linear-gradient(-45deg, $admin-gold 0 1px, transparent 1px 12px);
}

.dt__empty-text {
  font-size: 14px;
  letter-spacing: 0.08em;
  color: $admin-text-muted;
}

/* 分页 */
.dt__pager {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
}

.dt__pager-btn {
  padding: 7px 16px;
  border: 1px solid $admin-border;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  color: $admin-gold;
  cursor: pointer;
  transition:
    border-color 0.25s $admin-ease,
    background-color 0.25s $admin-ease;

  &:hover:not(:disabled) {
    border-color: $admin-gold-line;
    background: $admin-gold-soft;
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.dt__pager-nums {
  display: flex;
  gap: 4px;
  align-items: center;
}

.dt__pager-num {
  min-width: 32px;
  padding: 6px 8px;
  border: none;
  background: transparent;
  font-family: $admin-font-num;
  font-size: 15px;
  color: $admin-text-muted;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition:
    color 0.25s $admin-ease,
    border-color 0.25s $admin-ease;

  &:hover {
    color: $admin-gold;
  }

  &.is-active {
    color: $admin-gold;
    border-bottom-color: $admin-gold;
  }
}

.dt__pager-ellipsis {
  color: $admin-text-muted;
}

/* 小屏：表格横向滚动由 .dt__scroll 承担，分页紧凑 */
@media (max-width: 768px) {
  .dt__pager {
    gap: 10px;
  }

  .dt__pager-num {
    min-width: 26px;
  }
}
</style>
