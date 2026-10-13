/**
 * 主题（白天 / 夜间）轻量实现
 *
 * 设计
 * - 用 `<html data-theme="day|night">` 驱动全局 CSS 变量（见 styles/global.scss）
 * - 白天：亮暖底色 + 泛金光；夜间：浅蓝色调，刻意不做纯黑
 * - 选择持久化到 localStorage，首屏挂载前应用，避免闪烁
 * - 与 i18n 一致，用模块级 ref 承载当前主题，模板内自动追踪响应式
 *
 * 用法
 * - main.ts：initTheme() 首屏应用
 * - 组件内：const { themeMode, toggleTheme } = useTheme()
 */
import { ref } from 'vue'

export type ThemeMode = 'day' | 'night'

const STORAGE_KEY = 'shuyun_theme'

/** 读取本地保存的主题，缺省为白天 */
function readTheme(): ThemeMode {
  return localStorage.getItem(STORAGE_KEY) === 'night' ? 'night' : 'day'
}

/** 当前主题（响应式） */
export const themeMode = ref<ThemeMode>(readTheme())

/** 把主题写到 <html>，同时同步浏览器原生控件配色（滚动条 / 表单） */
export function applyTheme(mode: ThemeMode) {
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode === 'night' ? 'dark' : 'light'
}

/** 设置主题并持久化 */
export function setTheme(mode: ThemeMode) {
  themeMode.value = mode
  localStorage.setItem(STORAGE_KEY, mode)
  applyTheme(mode)
}

/** 白天 / 夜间互切 */
export function toggleTheme() {
  setTheme(themeMode.value === 'day' ? 'night' : 'day')
}

/** 首屏初始化（main.ts 调用，需在 mount 之前） */
export function initTheme() {
  applyTheme(themeMode.value)
}

/** 组件内使用：返回响应式的当前主题与切换方法 */
export function useTheme() {
  return { themeMode, setTheme, toggleTheme, initTheme }
}
