import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import '@/styles/global.scss'
// Lenis 官方推荐样式（修正 scroll-behavior 等潜在冲突）
import 'lenis/dist/lenis.css'
import { initLenis } from '@/composables/useLenis'
import { initTheme } from '@/composables/useTheme'

// 首屏应用主题（白天 / 夜间），需在 mount 之前，避免闪烁
initTheme()

const app = createApp(App)

// 全局注册 Element Plus 图标组件
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 全局初始化 Lenis 平滑滚动（单例；首页整屏吸附会复用同一实例）
initLenis()

// 全局图片加载失败兜底：统一替换为 /images/placeholder.jpg（每张只处理一次）
// 采用事件捕获（img 的 error 不冒泡），可覆盖全站所有 <img>（含后台/用户后台/大屏）
const IMG_PLACEHOLDER = '/images/placeholder.jpg'
window.addEventListener(
  'error',
  (e) => {
    const el = e.target as HTMLElement | null
    if (el && el.tagName === 'IMG') {
      const img = el as HTMLImageElement
      if (img.dataset.fallback === '1') return
      img.dataset.fallback = '1'
      img.src = IMG_PLACEHOLDER
    }
  },
  true,
)

app.mount('#app')
