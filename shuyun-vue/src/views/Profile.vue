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
.profile {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 80px;
}

/* ================= 顶部 ================= */
.profile__head {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 26px 28px;
  border-radius: 18px;
  background: linear-gradient(120deg, rgba(140, 31, 40, 0.92), rgba(110, 20, 28, 0.92));
  color: #fff;
}

.profile__avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: #fff;
  border: 3px solid var(--color-gold);
}

.profile__user {
  flex: 1;
}

.profile__name {
  font-size: 26px;
  letter-spacing: 3px;
}

.profile__email {
  margin-top: 4px;
  font-size: 13px;
  color: rgba(201, 168, 106, 0.95);
}

/* ================= 主体 ================= */
.profile__body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 24px;
  margin-top: 24px;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(201, 168, 106, 0.35);
  align-self: start;

  &__item {
    padding: 11px 14px;
    border-radius: 10px;
    font-size: 14px;
    letter-spacing: 1px;
    color: #4a3a32;
    cursor: pointer;
    transition: background 0.25s ease, color 0.25s ease;

    &:hover {
      background: rgba(140, 31, 40, 0.06);
    }

    &.is-active {
      color: #fff;
      background: var(--color-primary);
    }
  }
}

.content {
  min-height: 360px;

  &__title {
    margin-bottom: 20px;
    font-size: 24px;
    letter-spacing: 4px;
    color: var(--color-primary);
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
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  min-height: 160px;
}

.card {
  border-radius: 14px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(201, 168, 106, 0.3);
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 16px 34px rgba(140, 31, 40, 0.15);
  }

  &__img {
    position: relative;
    height: 140px;
    background-size: cover;
    background-position: center;
  }

  &__cancel {
    position: absolute;
    top: 8px;
    right: 8px;
    padding: 2px 10px;
    border-radius: 10px;
    font-size: 12px;
    color: #fff;
    background: rgba(36, 26, 27, 0.6);
    opacity: 0;
    transition: opacity 0.25s ease, background 0.25s ease;

    &:hover {
      background: var(--color-primary);
    }
  }

  &:hover &__cancel {
    opacity: 1;
  }

  &__body {
    padding: 12px 14px 16px;
  }

  &__name {
    font-size: 16px;
    letter-spacing: 2px;
    color: #2b2b2b;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    font-size: 12px;
    color: #8a7a70;
  }

  &__score {
    font-weight: 600;
    color: var(--color-primary);
  }
}

/* ================= 账号设置 ================= */
.settings {
  padding: 6px 0 10px;

  &__row {
    display: flex;
    align-items: center;
    padding: 12px 0;
    font-size: 15px;
    border-bottom: 1px dashed rgba(140, 31, 40, 0.15);
  }

  &__label {
    width: 120px;
    color: var(--color-gold);
  }

  &__value {
    color: #4a3a32;
  }

  &__subtitle {
    margin: 26px 0 14px;
    font-size: 17px;
    letter-spacing: 2px;
    color: var(--color-primary);
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
  margin-bottom: 20px;

  .content__title {
    margin-bottom: 0;
  }
}

.trips {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 120px;
}

.trip {
  padding: 18px 20px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(201, 168, 106, 0.35);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  &__title {
    font-size: 19px;
    letter-spacing: 2px;
    color: var(--color-primary);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
    margin-top: 8px;
    font-size: 13px;
    color: #7a6a60;
  }

  &__days {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px dashed rgba(140, 31, 40, 0.15);

    li {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 16px;
      font-size: 13px;
      color: #4a3a32;

      b {
        color: var(--color-gold);
      }
    }
  }

  &__date {
    color: #9a8b82;
  }

  &__empty {
    margin-top: 10px;
    font-size: 13px;
    color: #9a8b82;
  }
}

/* ---------- 行程表单 ---------- */
.trip-form {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__row {
    display: flex;
    gap: 12px;

    :deep(.el-input) {
      flex: 1;
    }
  }

  &__days-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    font-size: 15px;
    letter-spacing: 2px;
    color: var(--color-primary);
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
    width: 32px;
    font-size: 13px;
    color: var(--color-gold);
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1000px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
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

  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
