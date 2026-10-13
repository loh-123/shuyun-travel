<script setup lang="ts">
/**
 * 登录页（Login）· 高端精致极简风
 *
 * 设计参考：Apple ID 登录页 / 无印良品 / 观夏
 * - 直角（border-radius 0）、无阴影、无渐变
 * - 输入框：透明底 + 仅底部 1px 细线（聚焦变深红 2px）
 * - 登录按钮：深红直角 + 字距拉开
 * - 右侧纯色宣纸米背景
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { ADMIN_TOKEN_KEY, ADMIN_USER_KEY } from '@/admin/api/adminRequest'
import { t } from '@/i18n'
import ComingSoonModal from '@/components/common/ComingSoonModal.vue'

const router = useRouter()
const userStore = useUserStore()

const loginForm = reactive({ account: '', password: '' })
const errors = reactive({ account: '', password: '' })
const loading = ref(false)
const showPwd = ref(false)

/** 左侧诗句背景图（Unsplash 占位） */
const POEM_BG = 'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1400&q=80'

/**
 * 第三方登录入口（占位）
 * 微信 / QQ / 手机短信均需企业认证与平台审核，暂未开放，
 * 点击弹出「即将上线」提示，不跳转任何外部页面。
 */
const SOCIALS = [
  { key: 'wechat', label: 'login.wechat', title: 'login.modalWechatTitle', body: 'login.modalWechatBody' },
  { key: 'qq', label: 'login.qq', title: 'login.modalQqTitle', body: 'login.modalQqBody' },
  { key: 'phone', label: 'login.phone', title: 'login.modalPhoneTitle', body: 'login.modalPhoneBody' },
]

/** 「即将上线」弹窗状态 */
const soon = reactive({ visible: false, title: '', content: '' })

/** 第三方登录点击：弹出「即将上线」提示（占位，不跳外部） */
function onSocial(item: { title: string; body: string }) {
  soon.title = t(item.title)
  soon.content = t(item.body)
  soon.visible = true
}

/** 表单校验 */
function validate(): boolean {
  errors.account = loginForm.account.trim() ? '' : t('login.errAccount')
  errors.password = loginForm.password ? '' : t('login.errPassword')
  return !errors.account && !errors.password
}

async function onLogin() {
  if (!validate()) return
  loading.value = true
  try {
    // 后端返回 Result{code, data:{token, user}}，响应拦截器已剥离 AxiosResponse 外壳
    const res = await request.post('/auth/login', { ...loginForm })
    userStore.setToken(res.data.token)
    userStore.setUser(res.data.user)
    // 管理员：同步后台登录态，避免进入后台时再登录一次
    if (res.data.user?.role === 'ADMIN') {
      localStorage.setItem(ADMIN_TOKEN_KEY, res.data.token)
      localStorage.setItem(ADMIN_USER_KEY, JSON.stringify(res.data.user))
    }
    ElMessage.success(t('login.success'))
    router.push('/')
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <!-- 左侧：背景图 + 诗句 -->
    <div class="auth__left" :style="{ backgroundImage: `url(${POEM_BG})` }">
      <div class="auth__left-mask" />
      <div class="auth__poem">
        <p class="auth__poem-line">{{ t('login.poem1') }}</p>
        <p class="auth__poem-line">{{ t('login.poem2') }}</p>
        <span class="auth__poem-from">{{ t('login.poemFrom') }}</span>
      </div>
    </div>

    <!-- 右侧：表单 -->
    <div class="auth__right">
      <div class="auth__card">
        <p class="auth__eyebrow">{{ t('login.eyebrow') }}</p>
        <h2 class="auth__title">{{ t('login.welcome') }}</h2>
        <p class="auth__tip">{{ t('login.tipLogin') }}</p>

        <form class="auth__form" @submit.prevent="onLogin">
          <div class="field">
            <div class="field__wrap">
              <input
                v-model="loginForm.account"
                class="field__input"
                :class="{ 'is-error': errors.account }"
                type="text"
                autocomplete="username"
                :placeholder="t('login.account')"
                @input="errors.account = ''"
              />
            </div>
            <p v-if="errors.account" class="field__err">{{ errors.account }}</p>
          </div>

          <div class="field">
            <div class="field__wrap">
              <input
                v-model="loginForm.password"
                class="field__input"
                :class="{ 'is-error': errors.password }"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="current-password"
                :placeholder="t('login.password')"
                @input="errors.password = ''"
                @keyup.enter="onLogin"
              />
              <button
                type="button"
                class="field__eye"
                :title="showPwd ? t('login.hidePwd') : t('login.showPwd')"
                :aria-label="showPwd ? t('login.hidePwd') : t('login.showPwd')"
                @click="showPwd = !showPwd"
              >
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12Z" />
                  <circle cx="12" cy="12" r="2.6" />
                  <path v-if="!showPwd" d="M4 20 20 4" />
                </svg>
              </button>
            </div>
            <p v-if="errors.password" class="field__err">{{ errors.password }}</p>
          </div>

          <button type="button" class="btn-primary" :disabled="loading" @click="onLogin">
            {{ t('login.submit') }}
          </button>
        </form>

        <!-- 第三方登录 -->
        <div class="auth__social">
          <span class="auth__social-line" />
          <span class="auth__social-text">{{ t('login.otherWay') }}</span>
          <span class="auth__social-line" />
        </div>
        <div class="auth__socials">
          <button
            v-for="s in SOCIALS"
            :key="s.key"
            type="button"
            class="auth__social-btn"
            @click="onSocial(s)"
          >
            {{ t(s.label) }}
          </button>
        </div>

        <div class="auth__switch">
          <span>{{ t('login.noAccount') }}</span>
          <RouterLink class="auth__link" to="/register">{{ t('login.goRegister') }}</RouterLink>
        </div>
      </div>
    </div>

    <!-- 第三方登录「即将上线」提示弹窗 -->
    <ComingSoonModal v-model="soon.visible" :title="soon.title" :content="soon.content" />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* ---------------- 极简配色 ---------------- */
$_ink: #1a1a1a;
$_sub: #6b7280;
$_red: #8c1f28;
$_red-dark: #5a1018;
$_err: #c62828;
$_line: rgba(140, 31, 40, 0.55);
$_line-soft: rgba(140, 31, 40, 0.15);
$_paper: #f5f1e8;

.auth {
  position: relative;
  display: flex;
  height: calc(100vh - #{$nav-height});
  min-height: 600px;
}

/* ---------------- 左侧 50%（两栏等宽） ---------------- */
.auth__left {
  position: relative;
  flex: 0 0 50%;
  background-size: cover;
  background-position: center;
  overflow: hidden;
}

.auth__left-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, rgba(90, 16, 24, 0.5) 0%, rgba(26, 26, 26, 0.62) 100%);
}

.auth__poem {
  position: absolute;
  z-index: 2;
  bottom: 16%;
  left: 12%;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &-line {
    font-family: $font-title;
    font-weight: 700;
    font-size: clamp(42px, 4.5vw, 66px);
    line-height: 1.5;
    letter-spacing: 0.16em;
    color: #f7f3ea;
    text-shadow: 0 2px 18px rgba(0, 0, 0, 0.45);
  }

  &-from {
    margin-top: 12px;
    font-family: $font-body;
    font-size: calc(15px * 1.5);
    letter-spacing: 0.16em;
    color: #e3c9a0;
  }
}

/* ---------------- 右侧 50%（两栏等宽） ---------------- */
.auth__right {
  display: flex;
  flex: 0 0 50%;
  align-items: center;
  justify-content: center;
  padding: 60px clamp(24px, 3vw, 48px);
  background: $_paper;
}

/*
 * 右栏卡片：内部字号整体放大
 * —— 1.5（原设计放大）× 1.3（本次要求再放大 0.3 倍）= 1.95
 */
.auth__card {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 480px;
}

.auth__eyebrow {
  margin-bottom: 12px;
  font-family: $font-mono;
  font-size: calc(13px * 1.95);
  font-weight: 500;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: $_red;
}

.auth__title {
  margin-bottom: 14px;
  font-family: $font-title;
  font-weight: 700;
  font-size: calc(44px * 1.95);
  line-height: 1.15;
  letter-spacing: 0.04em;
  color: $_ink;
}

.auth__tip {
  margin-bottom: 44px;
  font-family: $font-body;
  font-size: calc(16px * 1.95);
  letter-spacing: 0.04em;
  color: $_sub;
}

/* ---------------- 表单 ---------------- */
.auth__form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.field {
  width: 100%;
}

.field__wrap {
  position: relative;
}

.field__input {
  width: 100%;
  height: 56px;
  padding: 0 32px 0 4px;
  border: none;
  border-bottom: 1px solid $_line;
  border-radius: 0;
  background: transparent;
  font-family: $font-body;
  font-size: calc(16px * 1.95);
  color: $_ink;
  outline: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  &::placeholder {
    color: rgba(140, 31, 40, 0.4);
  }

  &:focus {
    border-bottom-color: $_red;
    // 用 box-shadow 叠出第二像素，避免 border-width 变化导致布局抖动
    box-shadow: 0 1px 0 0 $_red;
  }

  &.is-error {
    border-bottom-color: $_err;
  }
}

/* 抑制 Chrome 自动填充的浅蓝底色，保持输入框透明 */
.field__input:-webkit-autofill,
.field__input:-webkit-autofill:hover,
.field__input:-webkit-autofill:focus {
  -webkit-text-fill-color: $_ink;
  transition: background-color 9999s ease-in-out 0s;
}

.field__eye {
  position: absolute;
  top: 50%;
  right: 0;
  display: inline-flex;
  padding: 2px;
  border: none;
  background: transparent;
  color: rgba(140, 31, 40, 0.55);
  cursor: pointer;
  transform: translateY(-50%);
  transition: color 0.2s ease;

  &:hover {
    color: $_red;
  }
}

.field__err {
  margin-top: 8px;
  font-family: $font-body;
  font-size: calc(12px * 1.95);
  color: $_err;
}

/* ---------------- 按钮 ---------------- */
.btn-primary {
  width: 100%;
  height: 56px;
  margin-top: 4px;
  border: none;
  border-radius: 0;
  background: $_red;
  font-family: $font-body;
  font-size: calc(16px * 1.95);
  letter-spacing: 0.2em;
  color: #ffffff;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover:not(:disabled) {
    background: $_red-dark;
  }

  &:disabled {
    background: #cccccc;
    color: #888888;
    cursor: not-allowed;
  }
}

/* ---------------- 第三方登录 ---------------- */
.auth__social {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 36px 0 20px;
}

.auth__social-line {
  flex: 1;
  height: 1px;
  background: $_line-soft;
}

.auth__social-text {
  font-family: $font-body;
  font-size: calc(14px * 1.95);
  letter-spacing: 0.06em;
  color: $_red;
}

.auth__socials {
  display: flex;
  gap: 16px;
}

.auth__social-btn {
  flex: 1;
  height: 48px;
  border: 1px solid $_line;
  border-radius: 0;
  background: transparent;
  font-family: $font-body;
  font-size: calc(15px * 1.95);
  color: $_red;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover {
    background: rgba(140, 31, 40, 0.06);
  }
}

/* ---------------- 切换登录/注册 ---------------- */
.auth__switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 32px;
  font-family: $font-body;
  font-size: calc(15px * 1.95);
  color: $_sub;
}

.auth__link {
  color: $_red;
  text-decoration: underline;
  text-underline-offset: 4px;
  cursor: pointer;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .auth__left {
    flex-basis: 50%;
  }

  .auth__right {
    flex-basis: 50%;
  }
}

@media (max-width: 768px) {
  .auth__left {
    display: none;
  }

  .auth__right {
    flex: 1 1 100%;
    padding: 48px 24px;
  }
}
</style>
