<script setup lang="ts">
/**
 * 后台图标组件（内联 SVG，线性 1.6px 风格）
 *
 * 说明：项目未引入 Remix Icon / Lucide 依赖，这里用同风格的内联 SVG 自绘，
 * 避免新增依赖；如需替换为图标库，仅需改动本组件。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 图标名 */
    name: string
    /** 尺寸（px） */
    size?: number
    /** 线宽 */
    stroke?: number
  }>(),
  {
    size: 20,
    stroke: 1.6,
  },
)

/** 图标内层绘制指令 */
const ICONS: Record<string, string> = {
  dashboard:
    '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  users:
    '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  scenic:
    '<circle cx="17.5" cy="6.5" r="1.5"/><path d="m3 20 6-10 4 6 2.5-3.5L21 20z"/>',
  culture:
    '<path d="M12 7v14"/><path d="M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z"/>',
  trip:
    '<circle cx="6" cy="19" r="2.5"/><circle cx="18" cy="5" r="2.5"/><path d="M8.5 19h6.5a3.5 3.5 0 0 0 0-7h-6a3.5 3.5 0 0 1 0-7h5.5"/>',
  ai:
    '<path d="M12 3v2.5"/><rect x="3" y="7" width="18" height="12" rx="3"/><circle cx="9" cy="13" r="1"/><circle cx="15" cy="13" r="1"/><path d="M9 16.5h6"/>',
  behavior:
    '<path d="M3 3v18h18"/><rect x="7" y="10" width="3" height="7" rx="1"/><rect x="12" y="6" width="3" height="11" rx="1"/><rect x="17" y="13" width="3" height="4" rx="1"/>',
  audit:
    '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>',
  backup:
    '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  collapse: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
  logout:
    '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
  chevron: '<path d="m6 9 6 6 6-6"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 4v5h-5"/>',
  calendar:
    '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  'arrow-up': '<path d="M12 19V5"/><path d="m6 11 6-6 6 6"/>',
  'arrow-down': '<path d="M12 5v14"/><path d="m6 13 6 6 6-6"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  settings:
    '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9 2 2 0 1 1-2.8 2.8 1.7 1.7 0 0 0-2.9 1.2 2 2 0 1 1-4 0 1.7 1.7 0 0 0-2.9-1.2 2 2 0 1 1-2.8-2.8A1.7 1.7 0 0 0 3.2 15a2 2 0 1 1 0-4 1.7 1.7 0 0 0 1.1-2.9 2 2 0 1 1 2.8-2.8 1.7 1.7 0 0 0 2.9-1.1 2 2 0 1 1 4 0 1.7 1.7 0 0 0 2.9 1.1 2 2 0 1 1 2.8 2.8 1.7 1.7 0 0 0 1.1 2.9 2 2 0 1 1 0 4z"/>',
  lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
}

/** 完整 svg 字符串 */
const svg = computed(() => {
  const body = ICONS[props.name] ?? ''
  return `<svg viewBox="0 0 24 24" width="${props.size}" height="${props.size}" fill="none" stroke="currentColor" stroke-width="${props.stroke}" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`
})
</script>

<template>
  <span class="admin-icon" v-html="svg" />
</template>

<style scoped>
.admin-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 0;
}
</style>
