<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

type MenuKey = 'trips' | 'collection' | 'history' | 'notes' | 'settings'

const menus: { key: MenuKey; label: string }[] = [
  { key: 'trips', label: '我的行程' },
  { key: 'collection', label: '我的收藏' },
  { key: 'history', label: '浏览历史' },
  { key: 'notes', label: '我的笔记' },
  { key: 'settings', label: '账号设置' },
]

const active = ref<MenuKey>('collection')

interface ScenicItem {
  id: number
  name: string
  cityName?: string
  type?: string
  score?: number
  coverImage?: string
}

/** 头像：后端没给就用 DiceBear 生成 */
const avatarUrl = computed(
  () =>
    userStore.user?.avatar ||
    `https://api.dicebear.com/9.x/avataaars/svg?seed=${encodeURIComponent(
      userStore.user?.username || 'shuyun',
    )}`,
)

// ---------------- 我的收藏 ----------------
const collections = ref<ScenicItem[]>([])
const loadingCollection = ref(false)

async function loadCollections() {
  loadingCollection.value = true
  try {
    const res = await request.get('/user/collection', { silent: true })
    collections.value = Array.isArray(res.data) ? res.data : []
  } catch {
    collections.value = []
  } finally {
    loadingCollection.value = false
  }
}

/** 取消收藏 */
async function onCancelCollect(id: number) {
  try {
    await request.delete('/user/collection', {
      params: { targetType: 'SCENIC', targetId: id },
    })
    collections.value = collections.value.filter((c) => c.id !== id)
    ElMessage.success('已取消收藏')
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

// ---------------- 我的行程 ----------------
interface TripDayItem {
  id?: number
  dayIndex?: number | null
  date?: string | null
  scenicName?: string | null
  stayDuration?: string | null
  transport?: string | null
  food?: string | null
  tips?: string | null
}

interface TripItem {
  id: number
  title?: string
  days?: number
  companion?: string
  interests?: string
  budget?: number
  coverImage?: string
  dayList?: TripDayItem[]
}

function emptyDay(index: number): TripDayItem {
  return {
    dayIndex: index,
    date: '',
    scenicName: '',
    stayDuration: '',
    transport: '',
    food: '',
    tips: '',
  }
}

function emptyTripForm() {
  return {
    id: null as number | null,
    title: '',
    days: 1,
    companion: '',
    interests: '',
    budget: '',
    dayList: [emptyDay(1)] as TripDayItem[],
  }
}

const trips = ref<TripItem[]>([])
const loadingTrips = ref(false)
const tripDialog = ref(false)
const savingTrip = ref(false)
const tripForm = reactive(emptyTripForm())

async function loadTrips() {
  if (!userStore.isLogin) {
    return
  }
  loadingTrips.value = true
  try {
    const res = await request.get('/user/trip', { silent: true })
    trips.value = Array.isArray(res.data) ? res.data : []
  } catch {
    trips.value = []
  } finally {
    loadingTrips.value = false
  }
}

function openCreateTrip() {
  Object.assign(tripForm, emptyTripForm())
  tripDialog.value = true
}

function openEditTrip(trip: TripItem) {
  Object.assign(tripForm, {
    id: trip.id,
    title: trip.title ?? '',
    days: trip.days ?? 1,
    companion: trip.companion ?? '',
    interests: trip.interests ?? '',
    budget: trip.budget != null ? String(trip.budget) : '',
    dayList: (trip.dayList ?? []).map((d, i) => ({
      dayIndex: d.dayIndex ?? i + 1,
      date: d.date ?? '',
      scenicName: d.scenicName ?? '',
      stayDuration: d.stayDuration ?? '',
      transport: d.transport ?? '',
      food: d.food ?? '',
      tips: d.tips ?? '',
    })),
  })
  if (!tripForm.dayList.length) {
    tripForm.dayList = [emptyDay(1)]
  }
  tripDialog.value = true
}

function addDay() {
  tripForm.dayList.push(emptyDay(tripForm.dayList.length + 1))
}

function removeDay(index: number) {
  tripForm.dayList.splice(index, 1)
  tripForm.dayList.forEach((d, i) => {
    d.dayIndex = i + 1
  })
}

async function saveTrip() {
  if (!tripForm.title) {
    ElMessage.warning('请填写行程标题')
    return
  }
  savingTrip.value = true
  try {
    const payload = {
      title: tripForm.title,
      days: tripForm.days,
      companion: tripForm.companion,
      interests: tripForm.interests,
      budget: tripForm.budget || null,
      status: 1,
      // 空字符串会令后端 LocalDate 解析失败，统一转成 null
      dayList: tripForm.dayList.map((d, i) => ({
        dayIndex: d.dayIndex ?? i + 1,
        date: d.date || null,
        scenicName: d.scenicName || null,
        stayDuration: d.stayDuration || null,
        transport: d.transport || null,
        food: d.food || null,
        tips: d.tips || null,
      })),
    }

    if (tripForm.id) {
      await request.put(`/user/trip/${tripForm.id}`, payload)
    } else {
      await request.post('/user/trip', payload)
    }
    ElMessage.success('保存成功')
    tripDialog.value = false
    await loadTrips()
  } finally {
    savingTrip.value = false
  }
}

async function removeTrip(trip: TripItem) {
  try {
    await ElMessageBox.confirm(`确定删除行程「${trip.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await request.delete(`/user/trip/${trip.id}`)
    ElMessage.success('已删除')
    await loadTrips()
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

// ---------------- 账号信息 ----------------
async function loadProfile() {
  try {
    const res = await request.get('/user/profile', { silent: true })
    if (res.data) {
      userStore.setUser(res.data)
    }
  } catch {
    // 忽略：本地缓存仍可展示
  }
}

// ---------------- 修改密码 ----------------
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const savingPwd = ref(false)

async function onChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  savingPwd.value = true
  try {
    await request.put('/user/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } finally {
    savingPwd.value = false
  }
}

function onLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function goScenic(id: number) {
  router.push(`/scenic/${id}`)
}

onMounted(() => {
  if (userStore.isLogin) {
    loadProfile()
    loadCollections()
    loadTrips()
  }
})
</script>

<template>
  <section class="profile">
    <!-- ============ 顶部：头像 + 用户名 ============ -->
    <header class="profile__head">
      <img class="profile__avatar" :src="avatarUrl" alt="avatar" />
      <div class="profile__user">
        <h1 class="profile__name">{{ userStore.user?.username || '未登录' }}</h1>
        <p class="profile__email">{{ userStore.user?.email || '登录后可管理你的行程与收藏' }}</p>
      </div>
      <el-button v-if="userStore.isLogin" plain round @click="onLogout">退出登录</el-button>
      <el-button v-else type="primary" round @click="router.push('/login')">去登录</el-button>
    </header>

    <div class="profile__body">
      <!-- ============ 左侧菜单 ============ -->
      <ul class="menu">
        <li
          v-for="m in menus"
          :key="m.key"
          class="menu__item"
          :class="{ 'is-active': m.key === active }"
          @click="active = m.key"
        >
          {{ m.label }}
        </li>
      </ul>

      <!-- ============ 右侧内容区 ============ -->
      <div class="content">
        <!-- 我的行程 -->
        <template v-if="active === 'trips'">
          <div class="content__head">
            <h2 class="content__title">我的行程</h2>
            <el-button v-if="userStore.isLogin" type="primary" round @click="openCreateTrip">
              + 新建行程
            </el-button>
          </div>

          <div v-if="!userStore.isLogin" class="content__guest">
            <el-empty description="登录后可管理你的行程" />
            <el-button type="primary" round @click="router.push('/login')">去登录</el-button>
          </div>

          <template v-else>
            <div v-loading="loadingTrips" class="trips">
              <article v-for="t in trips" :key="t.id" class="trip">
                <header class="trip__head">
                  <h3 class="trip__title">{{ t.title }}</h3>
                  <div>
                    <el-button link type="primary" @click="openEditTrip(t)">编辑</el-button>
                    <el-button link type="danger" @click="removeTrip(t)">删除</el-button>
                  </div>
                </header>

                <div class="trip__meta">
                  <span>{{ t.days }} 天</span>
                  <span v-if="t.companion">同行：{{ t.companion }}</span>
                  <span v-if="t.interests">偏好：{{ t.interests }}</span>
                  <span v-if="t.budget">预算：￥{{ t.budget }}</span>
                </div>

                <ul v-if="t.dayList?.length" class="trip__days">
                  <li v-for="d in t.dayList" :key="d.id">
                    <b>D{{ d.dayIndex }}</b>
                    <span class="trip__date">{{ d.date }}</span>
                    <span>{{ d.scenicName }}</span>
                    <span v-if="d.stayDuration">停留 {{ d.stayDuration }}</span>
                    <span v-if="d.transport">交通 {{ d.transport }}</span>
                    <span v-if="d.food">美食 {{ d.food }}</span>
                  </li>
                </ul>
                <p v-else class="trip__empty">暂无每日明细</p>
              </article>
            </div>

            <el-empty
              v-if="!loadingTrips && !trips.length"
              description="还没有行程，点右上角新建一个吧"
            >
              <el-button type="primary" round @click="openCreateTrip">新建行程</el-button>
            </el-empty>
          </template>
        </template>

        <!-- 我的收藏 -->
        <template v-else-if="active === 'collection'">
          <h2 class="content__title">我的收藏</h2>

          <div v-if="!userStore.isLogin" class="content__guest">
            <el-empty description="登录后可查看你的收藏" />
            <el-button type="primary" round @click="router.push('/login')">去登录</el-button>
          </div>

          <template v-else>
            <div v-loading="loadingCollection" class="grid">
              <article
                v-for="item in collections"
                :key="item.id"
                class="card"
                @click="goScenic(item.id)"
              >
                <div class="card__img" :style="{ backgroundImage: `url(${item.coverImage})` }">
                  <span class="card__cancel" @click.stop="onCancelCollect(item.id)">取消收藏</span>
                </div>
                <div class="card__body">
                  <h3 class="card__name">{{ item.name }}</h3>
                  <div class="card__meta">
                    <span>{{ item.cityName }} · {{ item.type }}</span>
                    <span class="card__score">{{ item.score }}</span>
                  </div>
                </div>
              </article>
            </div>

            <el-empty
              v-if="!loadingCollection && !collections.length"
              description="还没有收藏任何景点"
            >
              <el-button type="primary" round @click="router.push('/scenic')">去逛逛景点</el-button>
            </el-empty>
          </template>
        </template>

        <!-- 浏览历史 -->
        <template v-else-if="active === 'history'">
          <h2 class="content__title">浏览历史</h2>
          <el-empty description="暂无浏览记录" />
        </template>

        <!-- 我的笔记 -->
        <template v-else-if="active === 'notes'">
          <h2 class="content__title">我的笔记</h2>
          <el-empty description="笔记功能开发中，敬请期待" />
        </template>

        <!-- 账号设置 -->
        <template v-else>
          <h2 class="content__title">账号设置</h2>

          <div class="settings">
            <div class="settings__row">
              <span class="settings__label">用户名</span>
              <span class="settings__value">{{ userStore.user?.username || '—' }}</span>
            </div>
            <div class="settings__row">
              <span class="settings__label">邮箱</span>
              <span class="settings__value">{{ userStore.user?.email || '—' }}</span>
            </div>
            <div class="settings__row">
              <span class="settings__label">角色</span>
              <span class="settings__value">{{ userStore.user?.role || '—' }}</span>
            </div>
          </div>

          <h3 class="settings__subtitle">修改密码</h3>
          <div class="settings__form">
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              size="large"
              show-password
              placeholder="原密码"
            />
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              size="large"
              show-password
              placeholder="新密码"
            />
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              size="large"
              show-password
              placeholder="确认新密码"
              @keyup.enter="onChangePassword"
            />
            <el-button
              type="primary"
              round
              :loading="savingPwd"
              @click="onChangePassword"
            >
              保存修改
            </el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- ============ 行程编辑弹窗 ============ -->
    <el-dialog
      v-model="tripDialog"
      :title="tripForm.id ? '编辑行程' : '新建行程'"
      width="760px"
      top="6vh"
    >
      <div class="trip-form">
        <el-input v-model="tripForm.title" size="large" placeholder="行程标题（如：成都 3 日游）" />

        <div class="trip-form__row">
          <el-input-number v-model="tripForm.days" :min="1" :max="30" />
          <el-input v-model="tripForm.companion" placeholder="同行人（如：家人）" />
          <el-input v-model="tripForm.budget" placeholder="预算（如：3000）" />
        </div>

        <el-input v-model="tripForm.interests" placeholder="兴趣偏好（如：自然风光,美食）" />

        <div class="trip-form__days-head">
          <span>每日明细</span>
          <el-button link type="primary" @click="addDay">+ 添加一天</el-button>
        </div>

        <div v-for="(day, index) in tripForm.dayList" :key="index" class="trip-form__day">
          <span class="trip-form__day-no">D{{ day.dayIndex }}</span>
          <el-input v-model="day.date" placeholder="日期 2026-05-01" />
          <el-input v-model="day.scenicName" placeholder="景点名称" />
          <el-input v-model="day.stayDuration" placeholder="停留时长" />
          <el-input v-model="day.transport" placeholder="交通方式" />
          <el-input v-model="day.food" placeholder="美食推荐" />
          <el-button link type="danger" @click="removeDay(index)">移除</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="tripDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingTrip" @click="saveTrip">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.profile {
  width: 100%;
  padding: clamp(24px, 3vw, 48px) $gutter clamp(64px, 8vw, 120px);
  background: $color-paper;
}

/* ================= 顶部：头像 + 用户名 ================= */
.profile__head {
  position: relative;
  display: flex;
  align-items: center;
  gap: clamp(16px, 2vw, 26px);
  padding: clamp(20px, 2.6vw, 36px) clamp(20px, 2.8vw, 44px);
  border-radius: $radius-lg;
  overflow: hidden;
  // 墨蓝 → 金调，与首页/AI 屏同源
  background:
    radial-gradient(120% 160% at 88% 0%, rgba(201, 168, 106, 0.28) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(120deg, $color-ink 0%, #16324f 55%, #1d2436 100%);
  box-shadow: $shadow-md;

  // 蜀锦纹样
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image:
      repeating-linear-gradient(45deg, #{$color-gold} 0 1px, transparent 1px 16px),
      repeating-linear-gradient(-45deg, #{$color-gold} 0 1px, transparent 1px 16px);
    opacity: 0.07;
    mix-blend-mode: overlay;
    pointer-events: none;
  }
}

.profile__avatar {
  position: relative;
  z-index: 1;
  width: clamp(60px, 6vw, 88px);
  height: clamp(60px, 6vw, 88px);
  border-radius: 50%;
  background: $color-paper;
  border: 2px solid $color-gold;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.35);
}

.profile__user {
  position: relative;
  z-index: 1;
  flex: 1;
}

.profile__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(20px, 2.2vw, 32px);
  letter-spacing: 0.12em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.profile__email {
  margin-top: 6px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.08em;
  color: rgba(201, 168, 106, 0.95);
}

/* ================= 主体 ================= */
.profile__body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: clamp(16px, 2vw, 30px);
  margin-top: clamp(18px, 2.2vw, 32px);
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid rgba(201, 168, 106, 0.42);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  align-self: start;

  &__item {
    padding: 11px 14px;
    border-radius: 10px;
    font-family: $font-body;
    font-size: $fs-small;
    letter-spacing: 0.1em;
    color: rgba(26, 26, 26, 0.78);
    cursor: pointer;
    transition:
      background-color $duration-fast $ease-out-expo,
      color $duration-fast $ease-out-expo,
      transform $duration-fast $ease-out-expo;

    &:hover {
      background: rgba(140, 31, 40, 0.07);
      transform: translateX(3px);
    }

    &.is-active {
      color: $color-text-light;
      background: linear-gradient(120deg, $color-primary, #6e141c);
      transform: none;
    }
  }
}

.content {
  min-height: 360px;

  &__title {
    position: relative;
    margin-bottom: clamp(16px, 2vw, 26px);
    padding-left: 16px;
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(19px, 1.8vw, 28px);
    letter-spacing: 0.14em;
    color: $color-primary;

    // 左侧金色竖线
    &::before {
      content: '';
      position: absolute;
      top: 0.14em;
      left: 0;
      width: 2px;
      height: 0.86em;
      background: $color-gold;
    }
  }

  &__guest {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
}

/* ================= 收藏网格 ================= */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: clamp(14px, 1.8vw, 24px);
  min-height: 160px;
}

.card {
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  cursor: pointer;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo;
  will-change: transform;

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: $shadow-lg;
    border-color: $color-gold;

    .card__img {
      transform: scale(1.06);
    }
  }

  &__img {
    position: relative;
    aspect-ratio: 16 / 10;
    background-size: cover;
    background-position: center;
    transition: transform 1s $ease-out-expo;

    // 顶部压暗，保证「取消收藏」可读
    &::after {
      content: '';
      position: absolute;
      inset: 0 0 40% 0;
      background: linear-gradient(180deg, rgba(20, 26, 34, 0.42), rgba(20, 26, 34, 0));
      pointer-events: none;
    }
  }

  &__cancel {
    position: absolute;
    z-index: 1;
    top: 10px;
    right: 10px;
    padding: 4px 12px;
    border-radius: $radius-pill;
    font-size: 12px;
    letter-spacing: 0.08em;
    color: $color-text-light;
    background: rgba(20, 26, 34, 0.62);
    backdrop-filter: blur(6px);
    opacity: 0;
    transition:
      opacity $duration-fast $ease-out-expo,
      background-color $duration-fast $ease-out-expo;

    &:hover {
      background: $color-primary;
    }
  }

  &:hover &__cancel {
    opacity: 1;
  }

  &__body {
    padding: clamp(12px, 1.4vw, 18px);
  }

  &__name {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(15px, 1.3vw, 19px);
    letter-spacing: 0.08em;
    color: $color-text-dark;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 8px;
    font-family: $font-body;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.55);
  }

  &__score {
    font-family: $font-en;
    font-size: 1.15em;
    color: $color-primary;
  }
}

/* ================= 账号设置 ================= */
.settings {
  padding: 6px 0 10px;

  &__row {
    display: flex;
    align-items: center;
    padding: 13px 0;
    font-family: $font-body;
    font-size: $fs-small;
    border-bottom: 1px dashed rgba(140, 31, 40, 0.15);
  }

  &__label {
    width: 120px;
    letter-spacing: 0.12em;
    color: $color-gold;
  }

  &__value {
    color: rgba(26, 26, 26, 0.78);
  }

  &__subtitle {
    position: relative;
    margin: clamp(22px, 2.6vw, 34px) 0 14px;
    padding-left: 14px;
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(15px, 1.3vw, 19px);
    letter-spacing: 0.12em;
    color: $color-ink;

    &::before {
      content: '';
      position: absolute;
      top: 0.14em;
      left: 0;
      width: 2px;
      height: 0.86em;
      background: $color-gold;
    }
  }

  &__form {
    display: flex;
    flex-direction: column;
    gap: 14px;
    max-width: 360px;
  }
}

/* ================= 我的行程 ================= */
.content__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: clamp(16px, 2vw, 26px);

  .content__title {
    margin-bottom: 0;
  }
}

.trips {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 22px);
  min-height: 120px;
}

.trip {
  padding: clamp(16px, 1.8vw, 26px);
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: $shadow-md;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
    border-color: $color-gold;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  &__title {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(16px, 1.5vw, 22px);
    letter-spacing: 0.1em;
    color: $color-primary;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
    margin-top: 10px;
    font-family: $font-body;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.6);
  }

  &__days {
    display: flex;
    flex-direction: column;
    gap: 7px;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px dashed rgba(140, 31, 40, 0.15);

    li {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 16px;
      font-family: $font-body;
      font-size: $fs-small;
      color: rgba(26, 26, 26, 0.78);

      b {
        font-family: $font-en;
        font-size: 1.05em;
        color: $color-gold;
      }
    }
  }

  &__date {
    color: rgba(26, 26, 26, 0.45);
  }

  &__empty {
    margin-top: 10px;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.45);
  }
}

/* ---------- 行程表单（弹窗内） ---------- */
.trip-form {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;

    :deep(.el-input) {
      flex: 1;
      min-width: 160px;
    }
  }

  &__days-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(14px, 1.2vw, 17px);
    letter-spacing: 0.1em;
    color: $color-primary;
  }

  &__day {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px dashed rgba(140, 31, 40, 0.12);

    :deep(.el-input) {
      width: 128px;
    }
  }

  &__day-no {
    width: 34px;
    font-family: $font-en;
    font-size: $fs-small;
    color: $color-gold;
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1000px) {
  .profile__body {
    grid-template-columns: 180px minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .profile__body {
    grid-template-columns: 1fr;
  }

  .menu {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .profile__head {
    flex-wrap: wrap;
  }
}
</style>
