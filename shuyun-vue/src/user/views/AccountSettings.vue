<script setup lang="ts">
/**
 * 用户后台 · 账号设置
 *
 * 修改基本资料（用户名 / 邮箱 / 简介）与登录密码。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/stores/user'

const userStore = useUserStore()

/** 基本资料 */
const profile = reactive({ username: '', email: '', bio: '' })
/** 修改密码 */
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const savingProfile = ref(false)
const savingPwd = ref(false)

/** 加载当前资料 */
async function loadProfile() {
  try {
    const res = await request.get('/user/profile', { silent: true })
    profile.username = res.data?.username ?? ''
    profile.email = res.data?.email ?? ''
    profile.bio = res.data?.bio ?? ''
  } catch {
    // 静默
  }
}

/** 保存资料 */
async function saveProfile() {
  if (!profile.username || !profile.email) {
    ElMessage.warning('用户名和邮箱不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await request.put('/user/profile', { ...profile })
    userStore.setUser(res.data as UserInfo)
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

/** 修改密码 */
async function savePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    await request.put('/user/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码修改成功')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirm = ''
  } finally {
    savingPwd.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="set">
    <!-- 基本资料 -->
    <section class="set__card">
      <h3 class="set__title">基本资料</h3>

      <label class="set__label">用户名</label>
      <input v-model="profile.username" class="set__input" type="text" placeholder="用户名" />

      <label class="set__label">邮箱</label>
      <input v-model="profile.email" class="set__input" type="text" placeholder="邮箱" />

      <label class="set__label">一句话简介</label>
      <input v-model="profile.bio" class="set__input" type="text" placeholder="写一句关于你的话" />

      <button type="button" class="set__submit" :disabled="savingProfile" @click="saveProfile">
        {{ savingProfile ? '保存中…' : '保存资料' }}
      </button>
    </section>

    <!-- 修改密码 -->
    <section class="set__card">
      <h3 class="set__title">修改密码</h3>

      <label class="set__label">原密码</label>
      <input v-model="pwd.oldPassword" class="set__input" type="password" placeholder="请输入原密码" />

      <label class="set__label">新密码</label>
      <input v-model="pwd.newPassword" class="set__input" type="password" placeholder="请输入新密码" />

      <label class="set__label">确认新密码</label>
      <input v-model="pwd.confirm" class="set__input" type="password" placeholder="请再次输入新密码" />

      <button type="button" class="set__submit" :disabled="savingPwd" @click="savePassword">
        {{ savingPwd ? '提交中…' : '保存修改' }}
      </button>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.set {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(24px, 3vw, 39px);
}

.set__card {
  padding: clamp(30px, 3.6vw, 45px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  box-shadow: $shadow-md;
}

.set__title {
  margin-bottom: 8px;
  font-family: $font-title;
  font-weight: 900;
  font-size: 40.5px;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
}

.set__label {
  display: block;
  margin: 24px 0 12px;
  font-size: 29.25px;
  font-weight: 600;
  color: rgba(var(--sy-text-rgb), 0.7);
}

.set__input {
  width: 100%;
  height: 66px;
  padding: 0 24px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.85);
  font-size: 31.5px;
  color: $color-text-dark;
  outline: none;

  &:focus {
    border-color: $color-gold;
  }
}

.set__submit {
  width: 100%;
  height: 69px;
  margin-top: 36px;
  border: none;
  border-radius: $radius-pill;
  background: linear-gradient(120deg, #b98f45, $color-gold 55%, #e3cd97);
  font-size: 33.75px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: #241a08;
  cursor: pointer;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 12px 26px rgba(212, 179, 116, 0.35);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

@media (max-width: 1000px) {
  .set {
    grid-template-columns: 1fr;
  }
}
</style>
