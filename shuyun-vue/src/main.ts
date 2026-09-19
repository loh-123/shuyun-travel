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

app.mount('#app')
