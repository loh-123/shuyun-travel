<script setup lang="ts">
/**
 * 前台分页组件（金色下划线数字 + 上下页箭头 + 条数统计）
 * - 统一前台 Scenic / Food / Heritage 等列表页分页样式
 * - v-model:page 双向绑定当前页；total / size 计算总页数
 *
 * @example
 * <Pagination v-model:page="page" :total="total" :size="12" />
 */
import { computed } from 'vue'

interface Props {
  /** 总条数 */
  total: number
  /** 当前页（v-model:page） */
  page: number
  /** 每页条数 */
  size?: number
  /** 是否显示总数文字 */
  showTotal?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 12,
  showTotal: true,
})

const emit = defineEmits<{ (e: 'update:page', page: number): void }>()

/** 总页数 */
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

/** 页码序列（超过 7 页时用省略号收敛） */
const pageList = computed<(number | 'gap')[]>(() => {
  const last = totalPages.value
  const cur = props.page
  if (last <= 7) {
    return Array.from({ length: last }, (_, i) => i + 1)
  }
  if (cur <= 4) {
    return [1, 2, 3, 4, 5, 'gap', last]
  }
  if (cur >= last - 3) {
    return [1, 'gap', last - 4, last - 3, last - 2, last - 1, last]
  }
  return [1, 'gap', cur - 1, cur, cur + 1, 'gap', last]
})

/** 跳转指定页 */
function go(page: number) {
  if (page < 1 || page > totalPages.value || page === props.page) {
    return
  }
  emit('update:page', page)
}
</script>

<template>
  <div class="sy-pagination">
    <p v-if="props.showTotal && props.total > 0" class="sy-pagination__total">共 {{ props.total }} 条</p>

    <nav v-if="totalPages > 1" class="sy-pager" aria-label="分页">
      <button
        type="button"
        class="sy-pager__arrow"
        :disabled="props.page <= 1"
        aria-label="上一页"
        @click="go(props.page - 1)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M15 6l-6 6 6 6" /></svg>
      </button>

      <template v-for="(p, i) in pageList" :key="`${p}-${i}`">
        <span v-if="p === 'gap'" class="sy-pager__gap">…</span>
        <button
          v-else
          type="button"
          class="sy-pager__num"
          :class="{ 'is-active': p === props.page }"
          @click="go(p)"
        >
          {{ p }}
        </button>
      </template>

      <button
        type="button"
        class="sy-pager__arrow"
        :disabled="props.page >= totalPages"
        aria-label="下一页"
        @click="go(props.page + 1)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6" /></svg>
      </button>
    </nav>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.sy-pagination {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(12px, 1.4vw, 18px);
  margin-top: clamp(36px, 5vw, 72px);
}

.sy-pagination__total {
  font-family: $font-body;
  font-size: $fs-caption;
  letter-spacing: 0.1em;
  color: rgba(var(--sy-text-rgb), 0.5);
}

.sy-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 1.4vw, 22px);
}

.sy-pager__num {
  position: relative;
  padding: 4px 2px;
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(var(--sy-text-rgb), 0.55);
  transition: color $duration-fast $ease-out-expo;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 1px;
    background: $color-gold;
    transform: scaleX(0);
    transform-origin: center;
    transition: transform $duration-normal $ease-out-expo;
  }

  &:hover {
    color: var(--sy-accent);
  }

  &.is-active {
    color: $color-gold;

    &::after {
      transform: scaleX(1);
    }
  }
}

.sy-pager__gap {
  font-family: $font-en;
  font-size: clamp(15px, 1.3vw, 19px);
  color: rgba(var(--sy-text-rgb), 0.35);
}

.sy-pager__arrow {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  @include gold-border(0.5);
  color: var(--sy-accent);
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover:not(:disabled) {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-2px);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}
</style>
