<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 当前模式：登录 / 注册 */
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)

const loginForm = reactive({
  account: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
})

/** 左侧诗句背景图（Unsplash 占位） */
const POEM_BG = 'https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=1400&q=80'

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
}

async function onLogin() {
  if (!loginForm.account || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    // 后端返回 Result{code, data:{token, user}}，响应拦截器已剥离 AxiosResponse 外壳
    const res = await request.post('/auth/login', { ...loginForm })
    userStore.setToken(res.data.token)
    userStore.setUser(res.data.user)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  if (!registerForm.username || !registerForm.email || !registerForm.password) {
    ElMessage.warning('请填写完整的注册信息')
    return
  }
  loading.value = true
  try {
    await request.post('/auth/register', { ...registerForm })
    ElMessage.success('注册成功，请登录')
    // 注册成功后自动切回登录模式，并回填用户名
    loginForm.account = registerForm.username
    loginForm.password = ''
    mode.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <!-- ---------- 左侧：背景图 + 诗句 ---------- -->
    <div class="auth__left" :style="{ backgroundImage: `url(${POEM_BG})` }">
      <!-- 遮罩层复用全局 .sy-overlay，仅把冷调渐变换成蜀锦红调 -->
      <div class="sy-overlay">
        <div class="auth__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="auth__poem">
        <p class="auth__poem-line">九天开出一成都</p>
        <p class="auth__poem-line">万户千门入画图</p>
        <span class="auth__poem-from">—— 李白《上皇西巡南京歌》</span>
      </div>
    </div>

    <!-- ---------- 右侧：表单 ---------- -->
    <div class="auth__right">
      <div class="auth__card">
        <p class="auth__eyebrow">Shuyun · Zhiyou</p>
        <h2 class="auth__title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
        <p class="auth__tip">
          {{ mode === 'login' ? '登录蜀韵·智游，开启智能旅程' : '注册后即可收藏、规划与对话' }}
        </p>

        <!-- 登录 -->
        <template v-if="mode === 'login'">
          <el-input
            v-model="loginForm.account"
            size="large"
            placeholder="用户名 / 邮箱"
            class="auth__field"
          />
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            placeholder="密码"
            show-password
            class="auth__field"
            @keyup.enter="onLogin"
          />
          <el-button
            type="primary"
            size="large"
            round
            class="auth__submit"
            :loading="loading"
            @click="onLogin"
          >
            登 录
          </el-button>
          <div class="auth__switch">
            <span>没有账号？</span>
            <el-button link type="primary" @click="toggleMode">去注册</el-button>
          </div>
        </template>

        <!-- 注册 -->
        <template v-else>
          <el-input
            v-model="registerForm.username"
            size="large"
            placeholder="用户名"
            class="auth__field"
          />
          <el-input
            v-model="registerForm.email"
            size="large"
            placeholder="邮箱"
            class="auth__field"
          />
          <el-input
            v-model="registerForm.password"
            type="password"
            size="large"
            placeholder="密码"
            show-password
            class="auth__field"
            @keyup.enter="onRegister"
          />
          <el-button
            type="primary"
            size="large"
            round
            class="auth__submit"
            :loading="loading"
            @click="onRegister"
          >
            注 册
          </el-button>
          <div class="auth__switch">
            <span>已有账号？</span>
            <el-button link type="primary" @click="toggleMode">去登录</el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.auth {
  display: flex;
  height: calc(100vh - 68px);
  min-height: 560px;
}

/* ---------------- 左侧 60% ---------------- */
.auth__left {
  position: relative;
  flex: 0 0 60%;
  background-size: cover;
  background-position: center;
  overflow: hidden;

  &::after {
    // 缓慢放大，营造呼吸感
    content: '';
    position: absolute;
    inset: -4%;
    background: inherit;
    background-size: cover;
    background-position: center;
    animation: kenburns 22s ease-in-out infinite alternate;
  }
}

@keyframes kenburns {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.08);
  }
}

/* 遮罩层需盖在 Ken Burns 层（::after）之上 */
.auth__left .sy-overlay {
  z-index: 1;
}

.auth__gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, rgba(140, 31, 40, 0.58) 0%, rgba(20, 26, 34, 0.74) 100%);
}

.auth__poem {
  position: absolute;
  z-index: 2;
  left: 12%;
  bottom: 18%;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &-line {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(28px, 3.2vw, 46px);
    line-height: 1.5;
    letter-spacing: 0.16em;
    color: $color-text-light;
    text-shadow: $shadow-text;
  }

  &-from {
    margin-top: 12px;
    font-family: $font-body;
    font-size: $fs-small;
    letter-spacing: 0.16em;
    color: $color-gold;
  }
}

/* ---------------- 右侧 40% ---------------- */
.auth__right {
  display: flex;
  flex: 0 0 40%;
  align-items: center;
  justify-content: center;
  padding: clamp(28px, 4vw, 48px) clamp(20px, 3vw, 40px);
  background: $color-paper;
}

.auth__card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 360px;
}

.auth__eyebrow {
  font-family: $font-en-body;
  font-size: $fs-eyebrow;
  font-weight: 500;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: $color-gold;
}

.auth__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(26px, 2.4vw, 36px);
  letter-spacing: 0.12em;
  color: $color-primary;
}

.auth__tip {
  margin-bottom: 8px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(26, 26, 26, 0.56);
}

.auth__field {
  width: 100%;
}

.auth__submit {
  margin-top: 8px;
  letter-spacing: 8px;
}

.auth__switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.56);
}

/* ---------------- 窄屏：左侧隐藏，表单占满 ---------------- */
@media (max-width: 900px) {
  .auth__left {
    display: none;
  }

  .auth__right {
    flex: 1 1 100%;
  }
}
</style>
