/**
 * @wangeditor/editor-for-vue 未自带类型声明文件，这里做最小声明。
 * 仅声明用到的两个组件（Toolbar / Editor），避免 vue-tsc 报 TS7016。
 */
declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue'

  /** 富文本工具栏 */
  export const Toolbar: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>

  /** 富文本编辑器 */
  export const Editor: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
}
