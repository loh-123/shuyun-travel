<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 是否已滚动（用于导航栏渐变加深） */
const scrolled = ref(false)

const menus = [
  { label: '首页', path: '/' },
  { label: '景点', path: '/scenic' },
  { label: '文化', path: '/culture' },
  { label: 'AI管家', path: '/assistant' },
]

const footerLinks = [
  { title: '快速导航', items: ['首页', '景点', '文化', 'AI管家'] },
  { title: '资源中心', items: ['帮助中心', '用户协议', '隐私政策', '联系我们'] },
  { title: '关注我们', items: ['微信公众号：蜀韵智游', '微博：@蜀韵智游', '邮箱：hello@shuyun-travel.com'] },
]

function onScroll() {
  scrolled.value = window.scrollY > 20
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function go(path: string) {
  router.push(path)
}

/** 顶部导航「快速导航」点击 */
function goByLabel(label: string) {
  const found = menus.find((m) => m.label === label)
  if (found) {
    router.push(found.path)
  }
}
</script>

<template>
  <div class="layout">
    <!-- ============ 顶部导航栏 ============ -->
    <header class="navbar" :class="{ 'is-solid': scrolled }">
      <div class="navbar__inner">
        <div class="navbar__logo" @click="go('/')">蜀韵·智游</div>

        <nav class="navbar__menu">
          <a
            v-for="m in menus"
            :key="m.path"
            class="navbar__item"
            :class="{ 'is-active': route.path === m.path }"
            @click="go(m.path)"
          >
            {{ m.label }}
          </a>
        </nav>

        <div class="navbar__right">
          <el-button
            v-if="!userStore.isLogin"
            type="primary"
            round
            size="small"
            @click="go('/login')"
          >
            登录
          </el-button>
          <el-avatar
            v-else
            class="navbar__avatar"
            :size="34"
            :src="userStore.user?.avatar || undefined"
            @click="go('/profile')"
          >
            {{ userStore.user?.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
        </div>
      </div>
    </header>

    <!-- ============ 页面内容 ============ -->
    <main class="layout__main">
      <slot />
    </main>

    <!-- ============ 底部 Footer ============ -->
    <footer class="footer">
      <div class="footer__inner">
        <div class="footer__col footer__col--brand">
          <h4 class="footer__title">关于我们</h4>
          <p class="footer__text">
            蜀韵·智游 —— 以科技焕新巴蜀文旅，用数据串联山水人文，让每一次出发都遇见诗与远方。
          </p>
        </div>

        <div v-for="col in footerLinks" :key="col.title" class="footer__col">
          <h4 class="footer__title">{{ col.title }}</h4>
          <ul class="footer__list">
            <li
              v-for="item in col.items"
              :key="item"
              :class="{ 'is-link': col.title === '快速导航' }"
              @click="col.title === '快速导航' && goByLabel(item)"
            >
              {{ item }}
            </li>
          </ul>
        </div>
      </div>

      <div class="footer__bottom">© 2026 蜀韵·智游 shuyun-travel · 保留所有权利</div>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* ---------------- 导航栏 ---------------- */
.navbar {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 100;
  background: rgba(232, 227, 216, 0.35);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  transition: background 0.4s ease, box-shadow 0.4s ease;

  /* 滚动后渐变加深 */
  &.is-solid {
    background: linear-gradient(180deg, rgba(140, 31, 40, 0.92), rgba(105, 18, 25, 0.92));
    border-bottom-color: rgba(201, 168, 106, 0.35);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.18);
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 28px;
    max-width: 1200px;
    height: 68px;
    margin: 0 auto;
    padding: 0 24px;
  }

  &__logo {
    font-family: var(--font-title);
    font-size: 22px;
    font-weight: 600;
    letter-spacing: 4px;
    color: var(--color-primary);
    cursor: pointer;
    transition: color 0.4s ease;
  }

  &__menu {
    display: flex;
    gap: 6px;
  }

  &__item {
    padding: 8px 16px;
    border-radius: 20px;
    font-size: 15px;
    letter-spacing: 1px;
    color: #3a2b23;
    cursor: pointer;
    transition: color 0.25s ease, background 0.25s ease;

    &:hover {
      color: var(--color-primary);
      background: rgba(140, 31, 40, 0.08);
    }

    &.is-active {
      color: #fff;
      background: var(--color-primary);
    }
  }

  &__right {
    display: flex;
    align-items: center;
    margin-left: auto;
  }

  &__avatar {
    cursor: pointer;
    border: 2px solid var(--color-gold);
    background: var(--color-primary);
    color: #fff;
  }

  /* 滚动（深色）状态下，文字转为浅色 */
  &.is-solid {
    .navbar__logo {
      color: var(--color-gold);
    }

    .navbar__item {
      color: rgba(255, 255, 255, 0.9);

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.16);
      }

      &.is-active {
        color: var(--color-primary);
        background: var(--color-gold);
      }
    }
  }
}

/* ---------------- 内容区 ---------------- */
.layout__main {
  flex: 1;
  padding-top: 68px;
}

/* ---------------- Footer ---------------- */
.footer {
  background: #241a1b;
  color: rgba(255, 255, 255, 0.72);

  &__inner {
    display: grid;
    grid-template-columns: 1.6fr 1fr 1fr 1.2fr;
    gap: 40px;
    max-width: 1200px;
    margin: 0 auto;
    padding: 56px 24px 32px;
  }

  &__title {
    margin-bottom: 16px;
    font-size: 16px;
    letter-spacing: 2px;
    color: var(--color-gold);
  }

  &__text {
    font-size: 14px;
    line-height: 1.9;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    font-size: 14px;
  }

  &__list li.is-link {
    cursor: pointer;
    transition: color 0.25s ease;

    &:hover {
      color: var(--color-gold);
    }
  }

  &__bottom {
    padding: 18px 24px;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    font-size: 13px;
    text-align: center;
    color: rgba(255, 255, 255, 0.45);
  }
}

@media (max-width: 900px) {
  .footer__inner {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
