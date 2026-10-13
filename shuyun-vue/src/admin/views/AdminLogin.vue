<script setup lang="ts">
/**
 * 后台登录页（AdminLogin）
 *
 * - 全屏深色四川山水 + 蜀锦纹理背景
 * - 左侧 60%：品牌展示，右侧 40%：磨砂玻璃登录表单
 * - 调用 POST /api/auth/login，校验 role === 'ADMIN'，非管理员拒绝登录
 * - 登录成功写入 localStorage.admin_token / admin_user，跳转 /admin/dashboard
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'
import AdminIcon from '@/admin/components/AdminIcon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import adminRequest, { ADMIN_TOKEN_KEY, ADMIN_USER_KEY } from '@/admin/api/adminRequest'

const router = useRouter()

/** 表单模型 */
const form = reactive({
  account: '',
  password: '',
  captcha: '',
})

const loading = ref(false)
const showPassword = ref(false)

/** 左侧装饰背景图 */
const BG = 'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1600&q=80'

/* ------------------------------------------------------------
 * 图形验证码（前端本地校验，仅用于演示）
 * ---------------------------------------------------------- */
const captchaCode = ref('')

function refreshCaptcha() {
  const chars = 'ABCDEFGHJKMNPQRSTUVWXY3456789'
  captchaCode.value = Array.from(
    { length: 4 },
    () => chars[Math.floor(Math.random() * chars.length)],
  ).join('')
  form.captcha = ''
}

/* ------------------------------------------------------------
 * 登录
 * ---------------------------------------------------------- */
async function onLogin() {
  if (!form.account || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!form.captcha || form.captcha.toUpperCase() !== captchaCode.value) {
    ElMessage.warning('验证码不正确')
    refreshCaptcha()
    return
  }

  loading.value = true
  try {
    const res = await adminRequest.post('/auth/login', {
      account: form.account,
      password: form.password,
    })
    const { token, user } = res.data as { token: string; user: { role?: string; username?: string } }

    // 非管理员拒绝
    if (user?.role !== 'ADMIN') {
      ElMessage.error('该账号无后台管理权限')
      return
    }

    localStorage.setItem(ADMIN_TOKEN_KEY, token)
    localStorage.setItem(ADMIN_USER_KEY, JSON.stringify(user))
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch {
    // 错误提示由响应拦截器统一处理
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
  gsap.from('.login__left-inner > *', {
    y: 30,
    opacity: 0,
    duration: 0.9,
    stagger: 0.14,
    ease: 'power3.out',
  })
  gsap.from('.login__card', {
    y: 40,
    opacity: 0,
    duration: 0.9,
    delay: 0.2,
    ease: 'power3.out',
  })
})
</script>

<template>
  <div class="login">
    <!-- ============ 左：装饰区 ============ -->
    <section class="login__left" :style="{ backgroundImage: `url(${BG})` }">
      <div class="login__mask" />
      <div class="login__brocade" />

      <div class="login__left-inner">
        <div class="login__seal">
          <BrandLogo :size="72" />
        </div>
        <h1 class="login__title">蜀韵·智游</h1>
        <p class="login__subtitle">管 理 控 制 台</p>
        <p class="login__desc">以数据为眼看巴蜀，用智能守护每一次出发。</p>

        <ul class="login__points">
          <li>全域数据看板 · 实时洞察</li>
          <li>用户 / 景点 / 文化 · 一体化管理</li>
          <li>AI 对话与用户行为 · 精细运营</li>
        </ul>
      </div>
    </section>

    <!-- ============ 右：登录表单 ============ -->
    <section class="login__right">
      <div class="login__card">
        <p class="login__eyebrow">SHUYUN · ADMIN</p>
        <h2 class="login__heading">欢迎回来</h2>
        <p class="login__tip">请使用管理员账号登录后台</p>

        <label class="field">
          <span class="field__icon"><AdminIcon name="user" :size="18" /></span>
          <input v-model="form.account" type="text" placeholder="用户名 / 邮箱" autocomplete="username" />
        </label>

        <label class="field">
          <span class="field__icon"><AdminIcon name="lock" :size="18" /></span>
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码"
            autocomplete="current-password"
            @keyup.enter="onLogin"
          />
          <button type="button" class="field__eye" title="显示/隐藏密码" @click="showPassword = !showPassword">
            <AdminIcon name="eye" :size="17" />
          </button>
        </label>

        <div class="field field--captcha">
          <span class="field__icon"><AdminIcon name="check" :size="18" /></span>
          <input
            v-model="form.captcha"
            type="text"
            maxlength="4"
            placeholder="验证码"
            @keyup.enter="onLogin"
          />
          <button type="button" class="captcha" title="点击刷新" @click="refreshCaptcha">
            {{ captchaCode }}
          </button>
        </div>

        <button type="button" class="login__submit" :disabled="loading" @click="onLogin">
          {{ loading ? '登 录 中…' : '登 录' }}
        </button>

        <p class="login__foot">© 2026 蜀韵·智游 · 后台管理系统</p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/admin/styles/admin-tokens' as *;

.login {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background: $admin-bg;
  color: $admin-text;
}

/* ---------------- 左侧 ---------------- */
.login__left {
  position: relative;
  flex: 0 0 60%;
  overflow: hidden;
  background-size: cover;
  background-position: center;
}

.login__mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(155deg, rgba(122, 26, 34, 0.55) 0%, rgba(10, 22, 40, 0.9) 72%);
}

/* 蜀锦纹理叠加 */
.login__brocade {
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image:
    repeating-linear-gradient(45deg, rgba(212, 179, 116, 0.5) 0 1px, transparent 1px 16px),
    repeating-linear-gradient(-45deg, rgba(212, 179, 116, 0.5) 0 1px, transparent 1px 16px);
}

.login__left-inner {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 0 10%;
}

/* 品牌徽标（外发光沿用原印章红投影） */
.login__seal {
  display: inline-flex;
  margin-bottom: 28px;
  filter: drop-shadow(0 10px 30px rgba(122, 26, 34, 0.5));
}

.login__title {
  font-size: clamp(40px, 4vw, 64px);
  font-weight: 800;
  letter-spacing: 0.16em;
  color: $admin-text;
}

.login__subtitle {
  margin-top: 14px;
  font-size: clamp(16px, 1.4vw, 22px);
  font-weight: 500;
  letter-spacing: 0.5em;
  color: $admin-gold;
}

.login__desc {
  margin-top: 26px;
  max-width: 460px;
  font-size: 15px;
  line-height: 1.9;
  color: rgba(240, 235, 224, 0.75);
}

.login__points {
  margin-top: 36px;
  padding-left: 0;
  list-style: none;

  li {
    position: relative;
    padding-left: 22px;
    margin-bottom: 14px;
    font-size: 14px;
    color: rgba(240, 235, 224, 0.68);

    &::before {
      content: '';
      position: absolute;
      top: 8px;
      left: 0;
      width: 8px;
      height: 8px;
      border-radius: 2px;
      background: $admin-gold;
      transform: rotate(45deg);
    }
  }
}

/* ---------------- 右侧 ---------------- */
.login__right {
  display: flex;
  flex: 0 0 40%;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: $admin-bg;
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: 40px 34px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius;
  box-shadow: $admin-shadow;

  @include admin-glass(rgba(18, 35, 61, 0.75), 22px);
}

.login__eyebrow {
  font-size: 12px;
  letter-spacing: 0.34em;
  color: $admin-gold;
}

.login__heading {
  margin-top: 16px;
  font-size: 30px;
  font-weight: 700;
  color: $admin-text;
}

.login__tip {
  margin-top: 8px;
  margin-bottom: 26px;
  font-size: 14px;
  color: $admin-text-muted;
}

/* 输入框 */
.field {
  display: flex;
  gap: 10px;
  align-items: center;
  height: 48px;
  padding: 0 14px;
  margin-bottom: 16px;
  border: 1px solid $admin-border;
  border-radius: $admin-radius-sm;
  background: rgba(10, 22, 40, 0.6);
  transition:
    border-color 0.3s $admin-ease,
    box-shadow 0.3s $admin-ease;

  &:focus-within {
    border-color: $admin-gold-line;
    box-shadow: 0 0 0 3px rgba(212, 179, 116, 0.12);
  }

  input {
    flex: 1;
    min-width: 0;
    border: none;
    background: transparent;
    font-size: 15px;
    color: $admin-text;
    outline: none;

    &::placeholder {
      color: $admin-text-muted;
    }
  }
}

.field__icon {
  display: flex;
  color: $admin-text-muted;
}

.field__eye {
  display: flex;
  border: none;
  background: transparent;
  color: $admin-text-muted;
  cursor: pointer;

  &:hover {
    color: $admin-gold;
  }
}

.field--captcha input {
  max-width: 120px;
}

.captcha {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 96px;
  height: 36px;
  margin-left: auto;
  border: 1px dashed $admin-gold-line;
  border-radius: 8px;
  background:
    repeating-linear-gradient(45deg, rgba(212, 179, 116, 0.16) 0 6px, transparent 6px 12px),
    rgba(122, 26, 34, 0.18);
  font-family: 'Georgia', serif;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  color: $admin-gold;
  text-indent: 6px;
  cursor: pointer;
  user-select: none;
}

/* 提交按钮：金色渐变 */
.login__submit {
  width: 100%;
  height: 50px;
  margin-top: 8px;
  border: none;
  border-radius: $admin-radius-sm;
  background: linear-gradient(120deg, #b98f45 0%, $admin-gold 45%, #e3cd97 100%);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.4em;
  color: #241a08;
  cursor: pointer;
  transition:
    transform 0.3s $admin-ease,
    box-shadow 0.3s $admin-ease,
    opacity 0.3s $admin-ease;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 14px 30px rgba(212, 179, 116, 0.3);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.login__foot {
  margin-top: 26px;
  font-size: 12px;
  color: $admin-text-muted;
  text-align: center;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .login__left {
    display: none;
  }

  .login__right {
    flex: 1 1 100%;
  }
}
</style>
