<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import GoldButton from '@/components/common/GoldButton.vue'

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

/* ------------------------------------------------------------
 * 浏览足迹
 * ---------------------------------------------------------- */
interface HistoryItem {
  id: number
  targetType: string
  targetId: number
  targetName?: string
  coverImage?: string
  subTitle?: string
  viewTime?: string
}

const history = ref<HistoryItem[]>([])
const loadingHistory = ref(false)
const historyTotal = ref(0)

async function loadHistory() {
  if (!userStore.isLogin) {
    return
  }
  loadingHistory.value = true
  try {
    const res = await request.get('/user/history', { silent: true, params: { page: 1, size: 12 } })
    history.value = res.data?.records ?? []
    historyTotal.value = res.data?.total ?? 0
  } catch {
    history.value = []
    historyTotal.value = 0
  } finally {
    loadingHistory.value = false
  }
}

async function removeHistory(item: HistoryItem) {
  try {
    await request.delete(`/user/history/${item.id}`)
    history.value = history.value.filter((h) => h.id !== item.id)
    historyTotal.value = Math.max(0, historyTotal.value - 1)
    ElMessage.success('已移除该足迹')
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

async function clearHistory() {
  try {
    await ElMessageBox.confirm('确定清空全部浏览足迹吗？清空后不可恢复。', '提示', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await request.delete('/user/history')
    history.value = []
    historyTotal.value = 0
    ElMessage.success('足迹已清空')
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

function goHistory(item: HistoryItem) {
  router.push(item.targetType === 'CULTURE' ? `/culture/${item.targetId}` : `/scenic/${item.targetId}`)
}

/** 解析后端 LocalDateTime 字符串（兼容各浏览器） */
function parseTime(time?: string) {
  if (!time) {
    return 0
  }
  const normalized = time.replace('T', ' ').replace(/-/g, '/').split('.')[0]
  const stamp = new Date(normalized).getTime()
  return Number.isNaN(stamp) ? 0 : stamp
}

/** 相对时间：刚刚 / n 分钟前 / n 小时前 / 昨天 / n 天前 / 月日 */
function relativeTime(time?: string) {
  const stamp = parseTime(time)
  if (!stamp) {
    return ''
  }
  const minutes = Math.floor((Date.now() - stamp) / 60000)
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  const days = Math.floor(hours / 24)
  if (days === 1) {
    return '昨天'
  }
  if (days < 30) {
    return `${days} 天前`
  }
  const d = new Date(stamp)
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日`
}

/** 足迹的「最近浏览」文案 */
const lastViewText = computed(() => relativeTime(history.value[0]?.viewTime) || '—')

/* ------------------------------------------------------------
 * 旅行笔记
 * ---------------------------------------------------------- */
interface NoteItem {
  id: number
  title?: string
  content?: string
  targetName?: string
  tags?: string
  updateTime?: string
}

const notes = ref<NoteItem[]>([])
const loadingNotes = ref(false)
const noteKeyword = ref('')
const noteStats = reactive({ count: 0, chars: 0 })

const noteDialog = ref(false)
const savingNote = ref(false)
const noteForm = reactive({
  id: null as number | null,
  title: '',
  content: '',
  targetName: '',
  tags: '',
})

/** 可关联的景点候选：来自浏览足迹里的景点 */
const spotOptions = computed(() =>
  Array.from(
    new Set(
      history.value
        .filter((h) => h.targetType === 'SCENIC')
        .map((h) => h.targetName)
        .filter(Boolean) as string[],
    ),
  ),
)

async function loadNotes() {
  if (!userStore.isLogin) {
    return
  }
  loadingNotes.value = true
  try {
    const res = await request.get('/user/note', {
      silent: true,
      params: { keyword: noteKeyword.value || undefined, page: 1, size: 12 },
    })
    notes.value = res.data?.records ?? []
  } catch {
    notes.value = []
  } finally {
    loadingNotes.value = false
  }
  loadNoteSummary()
}

async function loadNoteSummary() {
  try {
    const res = await request.get('/user/note/summary', { silent: true })
    noteStats.count = res.data?.count ?? 0
    noteStats.chars = res.data?.chars ?? 0
  } catch {
    noteStats.count = 0
    noteStats.chars = 0
  }
}

function openNote(item?: NoteItem) {
  noteForm.id = item?.id ?? null
  noteForm.title = item?.title ?? ''
  noteForm.content = item?.content ?? ''
  noteForm.targetName = item?.targetName ?? ''
  noteForm.tags = item?.tags ?? ''
  noteDialog.value = true
}

async function saveNote() {
  if (!noteForm.title.trim() && !noteForm.content.trim()) {
    ElMessage.warning('标题和正文不能同时为空')
    return
  }
  savingNote.value = true
  try {
    const payload = {
      title: noteForm.title || null,
      content: noteForm.content || null,
      targetType: null,
      targetId: null,
      targetName: noteForm.targetName || null,
      tags: noteForm.tags || null,
    }
    if (noteForm.id) {
      await request.put(`/user/note/${noteForm.id}`, payload)
    } else {
      await request.post('/user/note', payload)
    }
    ElMessage.success('已保存')
    noteDialog.value = false
    await loadNotes()
  } finally {
    savingNote.value = false
  }
}

async function removeNote(item: NoteItem) {
  try {
    await ElMessageBox.confirm(`确定删除笔记「${item.title || '未命名笔记'}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await request.delete(`/user/note/${item.id}`)
    ElMessage.success('已删除')
    await loadNotes()
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

/** 标签拆分 */
function splitNoteTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/** 切换菜单时按需加载 */
watch(active, (key) => {
  if (!userStore.isLogin) {
    return
  }
  if (key === 'history' && !history.value.length) {
    loadHistory()
  }
  if (key === 'notes' && !notes.value.length) {
    loadHistory()
    loadNotes()
  }
})

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

        <!-- 浏览足迹：时间轴 -->
        <template v-else-if="active === 'history'">
          <div class="content__head">
            <h2 class="content__title">浏览足迹</h2>
            <el-button v-if="history.length" link type="danger" @click="clearHistory">
              清空足迹
            </el-button>
          </div>

          <div v-if="!userStore.isLogin" class="content__guest">
            <el-empty description="登录后可查看你的浏览足迹" />
            <GoldButton @click="router.push('/login')">去登录</GoldButton>
          </div>

          <template v-else>
            <!-- 统计条 -->
            <div v-if="historyTotal" class="foot-stat">
              <div class="foot-stat__cell">
                <b>{{ historyTotal }}</b>
                <span>处足迹</span>
              </div>
              <i class="foot-stat__line" />
              <div class="foot-stat__cell">
                <b class="foot-stat__small">{{ lastViewText }}</b>
                <span>最近浏览</span>
              </div>
            </div>

            <!-- 时间轴 -->
            <ol v-loading="loadingHistory" class="foot">
              <li v-for="item in history" :key="item.id" class="foot__item">
                <span class="foot__node" aria-hidden="true" />

                <button type="button" class="foot__card" @click="goHistory(item)">
                  <img class="foot__cover" :src="item.coverImage" alt="" loading="lazy" />
                  <div class="foot__info">
                    <h3 class="foot__name">{{ item.targetName }}</h3>
                    <p class="foot__sub">
                      {{ item.subTitle }}
                      <em>{{ item.targetType === 'CULTURE' ? '· 文化' : '· 景点' }}</em>
                    </p>
                    <span class="foot__time">{{ relativeTime(item.viewTime) }}</span>
                  </div>
                  <span class="foot__arrow">→</span>
                </button>

                <button type="button" class="foot__del" @click.stop="removeHistory(item)">
                  移除
                </button>
              </li>
            </ol>

            <el-empty
              v-if="!loadingHistory && !history.length"
              description="还没有浏览记录，去景点逛逛吧"
            >
              <GoldButton @click="router.push('/scenic')">去逛景点</GoldButton>
            </el-empty>
          </template>
        </template>

        <!-- 我的笔记：便签墙 -->
        <template v-else-if="active === 'notes'">
          <div class="content__head">
            <h2 class="content__title">我的笔记</h2>
            <GoldButton v-if="userStore.isLogin" size="sm" @click="openNote()">写笔记</GoldButton>
          </div>

          <div v-if="!userStore.isLogin" class="content__guest">
            <el-empty description="登录后可记录你的旅行笔记" />
            <GoldButton @click="router.push('/login')">去登录</GoldButton>
          </div>

          <template v-else>
            <!-- 统计 + 搜索 -->
            <div class="note-bar">
              <p class="note-bar__stat">
                共 <b>{{ noteStats.count }}</b> 篇 · 累计 <b>{{ noteStats.chars }}</b> 字
              </p>

              <label class="note-bar__search">
                <input
                  v-model="noteKeyword"
                  type="text"
                  placeholder="搜索标题 / 正文 / 标签"
                  @keyup.enter="loadNotes"
                />
                <span class="note-bar__line" />
              </label>
            </div>

            <!-- 便签墙 -->
            <div v-loading="loadingNotes" class="notes">
              <article
                v-for="(item, i) in notes"
                :key="item.id"
                class="note"
                :class="`note--r${i % 3}`"
                @click="openNote(item)"
              >
                <span class="note__pin" aria-hidden="true" />
                <h3 class="note__title">{{ item.title || '未命名笔记' }}</h3>
                <p class="note__content">{{ item.content }}</p>

                <div v-if="splitNoteTags(item.tags).length" class="note__tags">
                  <span v-for="t in splitNoteTags(item.tags)" :key="t" class="note__tag">{{ t }}</span>
                </div>

                <footer class="note__foot">
                  <span v-if="item.targetName" class="note__linked">关联 · {{ item.targetName }}</span>
                  <span class="note__date">{{ relativeTime(item.updateTime) }}</span>
                </footer>

                <button type="button" class="note__del" @click.stop="removeNote(item)">删除</button>
              </article>
            </div>

            <el-empty
              v-if="!loadingNotes && !notes.length"
              description="还没有笔记，写下第一段旅程吧"
            >
              <GoldButton @click="openNote()">写笔记</GoldButton>
            </el-empty>
          </template>
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

    <!-- ============ 笔记编辑弹窗 ============ -->
    <el-dialog
      v-model="noteDialog"
      :title="noteForm.id ? '编辑笔记' : '写笔记'"
      width="640px"
      top="8vh"
    >
      <div class="note-form">
        <el-input
          v-model="noteForm.title"
          size="large"
          maxlength="60"
          show-word-limit
          placeholder="标题（如：九寨沟的第一天）"
        />

        <el-input
          v-model="noteForm.content"
          type="textarea"
          :rows="8"
          resize="none"
          placeholder="写下此刻的心情、路线、花费，或一句诗……"
        />

        <div class="note-form__row">
          <el-select
            v-model="noteForm.targetName"
            class="note-form__select"
            placeholder="关联景点（可选）"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="s in spotOptions" :key="s" :label="s" :value="s" />
          </el-select>

          <el-input v-model="noteForm.tags" placeholder="标签，用逗号分隔（可选）" />
        </div>
      </div>

      <template #footer>
        <el-button @click="noteDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingNote" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

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

/* ================= 浏览足迹（时间轴） ================= */
.foot-stat {
  display: flex;
  align-items: center;
  gap: clamp(18px, 3vw, 46px);
  padding: clamp(14px, 1.6vw, 22px) clamp(16px, 2vw, 26px);
  margin-bottom: clamp(20px, 2.4vw, 32px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
}

.foot-stat__cell {
  display: flex;
  flex-direction: column;
  gap: 4px;

  b {
    font-family: $font-en;
    font-size: clamp(22px, 2.2vw, 34px);
    line-height: 1;
    color: $color-gold;
  }

  span {
    font-size: $fs-small;
    letter-spacing: 0.16em;
    color: rgba(26, 26, 26, 0.5);
  }
}

/* 「最近浏览」用宋体小字，与数字区分 */
.foot-stat__cell b.foot-stat__small {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 20px);
  color: $color-primary;
}

.foot-stat__line {
  width: 1px;
  height: 34px;
  background: linear-gradient(
    180deg,
    rgba(201, 168, 106, 0) 0%,
    $color-gold 50%,
    rgba(201, 168, 106, 0) 100%
  );
}

/* 时间轴本体 */
.foot {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 22px);
  min-height: 120px;
  padding-left: 34px;

  // 金色轴线
  &::before {
    content: '';
    position: absolute;
    top: 12px;
    bottom: 12px;
    left: 13px;
    width: 1px;
    background: linear-gradient(180deg, $color-gold 0%, rgba(201, 168, 106, 0.12) 100%);
  }
}

.foot__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 轴上的圆点：与 13px 处的轴线对齐 */
.foot__node {
  position: absolute;
  top: 50%;
  left: -26px;
  width: 10px;
  height: 10px;
  transform: translateY(-50%);
  border: 1.5px solid $color-gold;
  border-radius: 50%;
  background: $color-paper;
}

/* 最近一条：实心 + 光晕 */
.foot__item:first-child .foot__node {
  background: $color-gold;
  box-shadow: 0 0 0 4px rgba(201, 168, 106, 0.18);
}

.foot__card {
  display: flex;
  flex: 1;
  align-items: center;
  gap: clamp(12px, 1.4vw, 20px);
  padding: clamp(10px, 1.2vw, 16px);
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-md;
  text-align: left;
  @include hover-lift(-4px, 1, $shadow-lg);

  &:hover {
    border-color: $color-gold;

    .foot__arrow {
      opacity: 1;
      transform: translateX(3px);
    }
  }
}

.foot__cover {
  width: clamp(72px, 7vw, 104px);
  aspect-ratio: 4 / 3;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: $radius-md;
  border: 1px solid rgba(201, 168, 106, 0.35);
}

.foot__info {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.foot__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 19px);
  letter-spacing: 0.08em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}

.foot__sub {
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.55);

  em {
    font-style: normal;
    color: $color-gold;
  }
}

.foot__time {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-primary;
}

.foot__arrow {
  margin-left: auto;
  color: $color-gold;
  opacity: 0.5;
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.foot__del {
  flex-shrink: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: rgba(26, 26, 26, 0.4);
  opacity: 0.45;
  transition:
    opacity $duration-normal $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
    opacity: 1;
  }
}

.foot__item:hover .foot__del {
  opacity: 1;
}

/* ================= 旅行笔记（便签墙） ================= */
.note-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: clamp(18px, 2.2vw, 30px);
}

.note-bar__stat {
  font-size: $fs-small;
  letter-spacing: 0.1em;
  color: rgba(26, 26, 26, 0.6);

  b {
    margin: 0 2px;
    font-family: $font-en;
    font-size: 1.4em;
    color: $color-primary;
  }
}

.note-bar__search {
  position: relative;
  min-width: 220px;
  padding: 6px 2px;

  input {
    width: 100%;
    padding: 5px 0;
    border: none;
    background: transparent;
    font-family: $font-body;
    font-size: $fs-small;
    color: $color-text-dark;

    &::placeholder {
      color: rgba(26, 26, 26, 0.35);
    }
  }
}

.note-bar__line {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 1px;
  background: rgba(140, 31, 40, 0.25);
  transition: background-color $duration-normal $ease-out-expo;
}

.note-bar__search:focus-within .note-bar__line {
  background: $color-gold;
  box-shadow: 0 1px 8px rgba(201, 168, 106, 0.6);
}

.notes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: clamp(16px, 2vw, 26px);
  min-height: 120px;
}

/* 便签本体：宣纸底 + 轻微错落（hover 时摆正） */
.note {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: clamp(18px, 2vw, 26px) clamp(16px, 1.8vw, 24px);
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-md;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(232, 227, 216, 0.86));
  box-shadow: $shadow-md;
  cursor: pointer;
  transform: rotate(-0.35deg);
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo;

  &--r1 {
    transform: rotate(0.45deg);
  }

  &--r2 {
    transform: rotate(-0.6deg);
  }

  &:hover {
    transform: rotate(0deg) translateY(-6px);
    box-shadow: $shadow-lg;
    border-color: $color-gold;

    .note__del {
      opacity: 1;
    }
  }
}

/* 金色图钉 */
.note__pin {
  position: absolute;
  top: 10px;
  left: 50%;
  width: 10px;
  height: 10px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #e4cf9e, $color-gold);
  box-shadow: 0 2px 6px rgba(140, 31, 40, 0.35);
}

.note__title {
  margin-top: 6px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(15px, 1.3vw, 19px);
  letter-spacing: 0.08em;
  color: $color-ink;
  @include text-ellipsis(1);
}

.note__content {
  flex: 1;
  font-family: $font-body;
  font-size: $fs-small;
  line-height: 1.9;
  color: rgba(26, 26, 26, 0.72);
  white-space: pre-wrap;
  @include text-ellipsis(5);
}

.note__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note__tag {
  padding: 2px 9px;
  border: 1px solid rgba(201, 168, 106, 0.5);
  border-radius: $radius-sm;
  font-size: 12px;
  color: $color-bamboo;
}

.note__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed rgba(140, 31, 40, 0.15);
  font-size: 12px;
}

.note__linked {
  color: $color-primary;
  @include text-ellipsis(1);
}

.note__date {
  flex-shrink: 0;
  color: rgba(26, 26, 26, 0.45);
}

.note__del {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 12px;
  color: rgba(26, 26, 26, 0.45);
  opacity: 0;
  transition:
    opacity $duration-normal $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    color: $color-primary;
  }
}

/* 笔记表单 */
.note-form {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;

    :deep(.el-select) {
      flex: 1 1 55%;
    }

    :deep(.el-input) {
      flex: 1 1 30%;
    }
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
