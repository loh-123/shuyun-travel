<script setup lang="ts">
/**
 * 侧边栏菜单图标（线性空心图标）
 *
 * - 行程 / 收藏 / 历史 / 笔记 / 反馈 / 概览 / 设置
 * - 统一 viewBox 24、stroke-width 1.5、round 端点，颜色跟随 currentColor
 * - 不使用 emoji，保持统一的「空心线性」高级质感
 */
import { computed } from 'vue'

type IconName =
  | 'overview'
  | 'trips'
  | 'collection'
  | 'collections'
  | 'history'
  | 'notes'
  | 'feedback'
  | 'settings'

/** 每个图标的笔画路径集合 */
const PATHS: Record<IconName, string[]> = {
  // 四宫格（概览）
  overview: ['M3.5 3.5h7v7h-7z', 'M13.5 3.5h7v7h-7z', 'M13.5 13.5h7v7h-7z', 'M3.5 13.5h7v7h-7z'],
  // 地图 / 路线（行程）
  trips: ['M9 20l-5-2V4l5 2 6-2 6 2 5-2v14l-5 2-6-2-6 2z', 'M9 6v14M15 4v14'],
  // 心形（收藏）
  collection: ['M12 20s-7-4.6-7-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.6C19 15.4 12 20 12 20z'],
  collections: ['M12 20s-7-4.6-7-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.6C19 15.4 12 20 12 20z'],
  // 时钟（历史）
  history: ['M12 7.5V12l3 2', 'M3.5 12a8.5 8.5 0 1 0 2.6-6.1', 'M3.2 4.6v3.6h3.6'],
  // 便签（笔记）
  notes: [
    'M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z',
    'M14 3v6h6',
    'M8.5 13h7M8.5 16.5h4.5',
  ],
  // 对话气泡（反馈）
  feedback: [
    'M20.5 11.5a8.5 8.5 0 0 1-8.5 8.5H8.2L3.5 23v-4.4A8.5 8.5 0 0 1 12 3a8.5 8.5 0 0 1 8.5 8.5z',
    'M8.5 10.5h7',
    'M8.5 14h4.5',
  ],
  // 齿轮（设置）
  settings: [
    'M12 15.4a3.4 3.4 0 1 0 0-6.8 3.4 3.4 0 0 0 0 6.8z',
    'M19.6 14.5a1.6 1.6 0 0 0 .3 1.8l.1.1a1.9 1.9 0 1 1-2.7 2.7l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5v.2a1.9 1.9 0 1 1-3.8 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a1.9 1.9 0 1 1-2.7-2.7l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3.8a1.9 1.9 0 1 1 0-3.8h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a1.9 1.9 0 1 1 2.7-2.7l.1.1a1.6 1.6 0 0 0 1.8.3h.1a1.6 1.6 0 0 0 1-1.5V3.8a1.9 1.9 0 1 1 3.8 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a1.9 1.9 0 1 1 2.7 2.7l-.1.1a1.6 1.6 0 0 0-.3 1.8v.1a1.6 1.6 0 0 0 1.5 1h.2a1.9 1.9 0 1 1 0 3.8h-.1a1.6 1.6 0 0 0-1.5 1z',
  ],
}

const props = withDefaults(
  defineProps<{
    /** 图标名（与菜单 key 一致） */
    name: string
    /** 尺寸（px） */
    size?: number
  }>(),
  { size: 20 },
)

const paths = computed(() => PATHS[props.name as IconName] ?? PATHS.overview)
</script>

<template>
  <svg
    class="pm-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    :width="size"
    :height="size"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<style scoped lang="scss">
.pm-icon {
  display: block;
}
</style>
