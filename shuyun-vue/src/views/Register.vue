<script setup lang="ts">
/**
 * 注册页（Register）· 高端精致极简风
 *
 * 字段顺序：用户名 → 密码 → 确认密码 → 邮箱 → 验证码 → 注册
 * - 与登录页完全一致的极简样式（透明底 + 底部细线 + 深红直角按钮）
 * - 实时校验：失焦/输入即校验，底部线变绿（通过）/ 变红（失败）并提示
 * - 验证码：调用后端 /api/auth/send-email-code 真实发送，前端 60 秒倒计时防重复
 */
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { sendEmailCode } from '@/api/auth'
import { t } from '@/i18n'

const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  email: '',
  code: '',
})

/** 是否已点击过提交（用于空值时的必填提示） */
const submitted = ref(false)
const loading = ref(false)
const showPwd = ref(false)

/** 左侧诗句背景图（与登录页一致） */
const POEM_BG = 'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1400&q=80'

type FieldKey = keyof typeof form

/** 各字段校验规则（空值由必填逻辑单独处理） */
const validators: Record<FieldKey, (v: string) => boolean> = {
  username: (v) => /^[A-Za-z0-9_]{4,20}$/.test(v),
  password: (v) => /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(v),
  confirm: (v) => v === form.password,
  email: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v),
  code: (v) => /^\d{4}$/.test(v),
}

/** 各字段提示词条（必填复用占位符文案，格式错误用 err* 文案） */
const MESSAGES: Record<FieldKey, { required: string; invalid: string }> = {
  username: { required: 'reg.username', invalid: 'reg.errUsername' },
  password: { required: 'reg.password', invalid: 'reg.errPassword' },
  confirm: { required: 'reg.confirm', invalid: 'reg.errConfirm' },
  email: { required: 'reg.email', invalid: 'reg.errEmail' },
  code: { required: 'reg.code', invalid: 'reg.errCode' },
}

/** 单字段状态：''（中性）| 'ok' | 'error' */
function fieldState(key: FieldKey): '' | 'ok' | 'error' {
  const v = form[key].trim()
  if (!v) return submitted.value ? 'error' : ''
  return validators[key](form[key]) ? 'ok' : 'error'
}

/** 单字段错误文案（无错误返回空） */
function fieldError(key: FieldKey): string {
  const v = form[key].trim()
  if (!v) return submitted.value ? t(MESSAGES[key].required) : ''
  return validators[key](form[key]) ? '' : t(MESSAGES[key].invalid)
}

/** 输入框状态 class */
function stateClass(key: FieldKey): string {
  const s = fieldState(key)
  return s === 'ok' ? 'is-ok' : s === 'error' ? 'is-error' : ''
}

/** 全部字段校验通过才可提交 */
const canSubmit = computed(() =>
  (Object.keys(validators) as FieldKey[]).every((k) => form[k].trim() && validators[k](form[k])),
)

/* ------------------------------ 邮箱验证码（60s 倒计时） ------------------------------ */
const countdown = ref(0)
let cdTimer: number | null = null

function stopCountdown() {
  if (cdTimer !== null) {
    window.clearInterval(cdTimer)
    cdTimer = null
  }
  countdown.value = 0
}

async function sendCode() {
  if (countdown.value > 0) return
  if (!validators.email(form.email)) {
    submitted.value = true
    ElMessage.warning(t('reg.errEmail'))
    return
  }
  try {
    // 调用后端真实发送邮箱验证码
    await sendEmailCode(form.email)
    ElMessage.success(t('reg.sendOk'))
    countdown.value = 60
    cdTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) stopCountdown()
    }, 1000)
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

onBeforeUnmount(stopCountdown)

/* ------------------------------ 提交注册 ------------------------------ */
async function onRegister() {
  submitted.value = true
  if (!canSubmit.value) return
  loading.value = true
  try {
    // 后端 /api/auth/register 接收 username / email / password / code
    // （确认密码为前端校验，不提交）
    await request.post('/auth/register', {
      username: form.username,
      email: form.email,
      password: form.password,
      code: form.code,
    })
    ElMessage.success(t('reg.success'))
    router.push('/login')
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
        <h2 class="auth__title">{{ t('login.create') }}</h2>
        <p class="auth__tip">{{ t('reg.tip') }}</p>

        <form class="auth__form" @submit.prevent="onRegister">
          <!-- 1. 用户名 -->
          <div class="field">
            <div class="field__wrap">
              <input
                v-model="form.username"
                class="field__input"
                :class="stateClass('username')"
                type="text"
                autocomplete="username"
                :placeholder="t('reg.username')"
              />
            </div>
            <p v-if="fieldError('username')" class="field__err">{{ fieldError('username') }}</p>
          </div>

          <!-- 2. 密码 -->
          <div class="field">
            <div class="field__wrap">
              <input
                v-model="form.password"
                class="field__input"
                :class="stateClass('password')"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="new-password"
                :placeholder="t('reg.password')"
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
            <p v-if="fieldError('password')" class="field__err">{{ fieldError('password') }}</p>
          </div>

          <!-- 3. 确认密码 -->
          <div class="field">
            <div class="field__wrap">
              <input
                v-model="form.confirm"
                class="field__input"
                :class="stateClass('confirm')"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="new-password"
                :placeholder="t('reg.confirm')"
              />
            </div>
            <p v-if="fieldError('confirm')" class="field__err">{{ fieldError('confirm') }}</p>
          </div>

          <!-- 4. 邮箱 -->
          <div class="field">
            <div class="field__wrap">
              <input
                v-model="form.email"
                class="field__input"
                :class="stateClass('email')"
                type="email"
                autocomplete="email"
                :placeholder="t('reg.email')"
              />
            </div>
            <p v-if="fieldError('email')" class="field__err">{{ fieldError('email') }}</p>
          </div>

          <!-- 5. 验证码 -->
          <div class="field">
            <div class="field__row">
              <div class="field__wrap field__wrap--grow">
                <input
                  v-model="form.code"
                  class="field__input"
                  :class="stateClass('code')"
                  type="text"
                  inputmode="numeric"
                  maxlength="4"
                  :placeholder="t('reg.code')"
                />
              </div>
              <button type="button" class="btn-code" :disabled="countdown > 0" @click="sendCode">
                {{ countdown > 0 ? t('reg.resend', { n: countdown }) : t('reg.getCode') }}
              </button>
            </div>
            <p v-if="fieldError('code')" class="field__err">{{ fieldError('code') }}</p>
          </div>

          <!-- 6. 注册 -->
          <button type="button" class="btn-primary" :disabled="!canSubmit || loading" @click="onRegister">
            {{ t('login.register') }}
          </button>
        </form>

        <div class="auth__switch">
          <span>{{ t('login.hasAccount') }}</span>
          <RouterLink class="auth__link" to="/login">{{ t('login.goLogin') }}</RouterLink>
        </div>
      </div>
    </div>
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
$_ok: #3a5a40;
$_line: rgba(140, 31, 40, 0.55);
$_paper: #f5f1e8;

.auth {
  position: relative;
  display: flex;
  height: calc(100vh - #{$nav-height});
  min-height: 640px;
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
  margin-bottom: 40px;
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

  &--grow {
    flex: 1;
  }
}

.field__row {
  display: flex;
  gap: 16px;
  align-items: center;
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
    box-shadow: 0 1px 0 0 $_red;
  }

  &.is-ok {
    border-bottom-color: $_ok;
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

/* 验证码按钮：透明底 + 深红边框 + 直角 */
.btn-code {
  flex: none;
  height: 56px;
  padding: 0 18px;
  border: 1px solid $_red;
  border-radius: 0;
  background: transparent;
  font-family: $font-mono;
  font-size: calc(14px * 1.95);
  color: $_red;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover:not(:disabled) {
    background: rgba(140, 31, 40, 0.06);
  }

  &:disabled {
    border-color: #cccccc;
    color: #999999;
    cursor: not-allowed;
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
