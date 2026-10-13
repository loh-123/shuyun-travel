<script setup lang="ts">
/**
 * 个人中心（高端会员中心）
 * - 40vh Hero：城市拼贴底 + 金色描边头像 + 用户名 + 一句话简介 + 编辑资料
 * - 数据概览：去过的城市 / 收藏景点 / 生成行程 / AI 对话（数字滚动）
 * - 主体左右分栏：240px 菜单 + 内容区（切换淡出淡入）
 * - 五个面板：我的行程 / 我的收藏 / 浏览历史 / 我的笔记（WangEditor）/ 账号设置
 * 样式全部复用公共设计系统（variables / mixins / GoldButton / useCountUp）
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'
import gsap from 'gsap'

import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'

import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useCountUp } from '@/composables/useCountUp'
import GoldButton from '@/components/common/GoldButton.vue'
import GhostButton from '@/components/common/GhostButton.vue'
import ProfileMenuIcon from '@/components/common/ProfileMenuIcon.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import { t } from '@/i18n'

/* ------------------------------------------------------------
 * 类型
 * ---------------------------------------------------------- */
type MenuKey = 'trips' | 'collection' | 'history' | 'notes' | 'settings'

interface ScenicItem {
  id: number
  name: string
  cityName?: string
  type?: string
  score?: number
  coverImage?: string
}

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
  createTime?: string
  dayList?: TripDayItem[]
}

interface HistoryItem {
  id: number
  targetType: string
  targetId: number
  targetName?: string
  coverImage?: string
  subTitle?: string
  viewTime?: string
}

interface NoteItem {
  id: number
  title?: string
  content?: string
  targetName?: string
  tags?: string
  updateTime?: string
}

/* ------------------------------------------------------------
 * 常量
 * ---------------------------------------------------------- */
/** Hero 城市拼贴占位图（换成你自己的旅行照片即可） */
const COLLAGE = [
  'https://picsum.photos/seed/shuyun-city-1/600/400',
  'https://picsum.photos/seed/shuyun-city-19/600/400',
  'https://picsum.photos/seed/shuyun-city-10/600/400',
  'https://picsum.photos/seed/shuyun-city-20/600/400',
]

/** 菜单（图标名与 key 一致，由 ProfileMenuIcon 渲染） */
const MENUS: { key: MenuKey; label: string }[] = [
  { key: 'trips', label: 'nav.trips' },
  { key: 'collection', label: 'nav.collection' },
  { key: 'history', label: 'pf.menuHistory' },
  { key: 'notes', label: 'pf.menuNotes' },
  { key: 'settings', label: 'pf.menuSettings' },
]

/** 数据概览配置 */
const STAT_ITEMS = [
  { key: 'cities', label: 'pf.statCities' },
  { key: 'collections', label: 'pf.statCollections' },
  { key: 'trips', label: 'pf.statTrips' },
  { key: 'chats', label: 'pf.statChats' },
] as const

/* ------------------------------------------------------------
 * 状态
 * ---------------------------------------------------------- */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 菜单 key 集合，用于校验 /profile?tab=xxx 参数 */
const TAB_KEYS: MenuKey[] = ['trips', 'collection', 'history', 'notes', 'settings']

/** 顶部导航下拉可直接跳到指定面板（/profile?tab=trips 等） */
function applyQueryTab() {
  const tab = route.query.tab as string | undefined
  if (tab && TAB_KEYS.includes(tab as MenuKey)) {
    active.value = tab as MenuKey
  }
}

const active = ref<MenuKey>('trips')
/** 折叠状态持久化 key（'1' 折叠 / '0' 展开） */
const SIDEBAR_KEY = 'shuyun:profile:sidebar-collapsed'
/** 侧边栏是否折叠（展开 240px / 折叠 64px）；默认展开 */
const collapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')
/** 小屏侧边栏抽屉是否展开 */
const mobileOpen = ref(false)

/** 切换折叠并持久化 */
function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(SIDEBAR_KEY, collapsed.value ? '1' : '0')
}

/** 选择菜单（小屏时同时关上抽屉） */
function selectMenu(key: MenuKey) {
  active.value = key
  mobileOpen.value = false
}

const statsEl = ref<HTMLElement | null>(null)

/** 头像：后端没给就用 DiceBear 生成（notionists 简约商务风） */
const avatarUrl = computed(
  () =>
    userStore.user?.avatar ||
    `https://api.dicebear.com/9.x/notionists/svg?seed=${encodeURIComponent(
      userStore.user?.username || 'shuyun',
    )}`,
)

/* ---------------- 数据概览 ---------------- */
const stats = ref(
  STAT_ITEMS.map((item) => ({ key: item.key, label: item.label, num: 0, target: 0 })),
)
useCountUp(stats, { trigger: statsEl, duration: 1.6 })

async function loadStats() {
  try {
    const res = await request.get('/user/stats', { silent: true })
    stats.value.forEach((s) => {
      s.target = Number(res.data?.[s.key] ?? 0)
    })
  } catch {
    stats.value.forEach((s) => (s.target = 0))
  }
}

/* ---------------- 我的行程 ---------------- */
const trips = ref<TripItem[]>([])
const loadingTrips = ref(false)

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
    ElMessage.warning(t('pf.msgTripTitle'))
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
    ElMessage.success(t('pf.msgSaved'))
    tripDialog.value = false
    await Promise.all([loadTrips(), loadStats()])
  } finally {
    savingTrip.value = false
  }
}

async function removeTrip(trip: TripItem) {
  try {
    await ElMessageBox.confirm(t('pf.confirmDeleteTrip', { title: trip.title }), t('pf.confirmTitle'), {
      type: 'warning',
      confirmButtonText: t('pf.confirmDelete'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  try {
    await request.delete(`/user/trip/${trip.id}`)
    ElMessage.success(t('pf.msgDeleted'))
    await Promise.all([loadTrips(), loadStats()])
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

/** 查看行程：跳到 AI 管家继续完善（也可在此展开明细） */
function viewTrip(trip: TripItem) {
  openEditTrip(trip)
}

/* ---------------- 我的收藏 ---------------- */
const collections = ref<ScenicItem[]>([])
const loadingCollection = ref(false)

async function loadCollections() {
  if (!userStore.isLogin) {
    return
  }
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

async function onCancelCollect(id: number) {
  try {
    await request.delete('/user/collection', { params: { targetType: 'SCENIC', targetId: id } })
    collections.value = collections.value.filter((c) => c.id !== id)
    ElMessage.success(t('pf.msgUncollected'))
    loadStats()
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

/* ---------------- 浏览历史 ---------------- */
const history = ref<HistoryItem[]>([])
const loadingHistory = ref(false)

async function loadHistory() {
  if (!userStore.isLogin) {
    return
  }
  loadingHistory.value = true
  try {
    const res = await request.get('/user/history', { silent: true, params: { page: 1, size: 20 } })
    history.value = res.data?.records ?? []
  } catch {
    history.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function removeHistory(item: HistoryItem) {
  try {
    await request.delete(`/user/history/${item.id}`)
    history.value = history.value.filter((h) => h.id !== item.id)
    ElMessage.success(t('pf.msgHistoryRemoved'))
    loadStats()
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

async function clearHistory() {
  try {
    await ElMessageBox.confirm(t('pf.confirmClearHistory'), t('pf.confirmTitle'), {
      type: 'warning',
      confirmButtonText: t('pf.confirmClear'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  try {
    await request.delete('/user/history')
    history.value = []
    ElMessage.success(t('pf.msgHistoryCleared'))
    loadStats()
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

function goHistory(item: HistoryItem) {
  router.push(item.targetType === 'CULTURE' ? `/culture/${item.targetId}` : `/scenic/${item.targetId}`)
}

/** 相对时间 */
function relativeTime(time?: string) {
  if (!time) {
    return ''
  }
  const stamp = new Date(time.replace('T', ' ').replace(/-/g, '/').split('.')[0]).getTime()
  if (Number.isNaN(stamp)) {
    return ''
  }
  const m = Math.floor((Date.now() - stamp) / 60000)
  if (m < 1) {
    return t('pf.justNow')
  }
  if (m < 60) {
    return t('pf.minutesAgo', { n: m })
  }
  const h = Math.floor(m / 60)
  if (h < 24) {
    return t('pf.hoursAgo', { n: h })
  }
  const d = Math.floor(h / 24)
  return d === 1 ? t('pf.yesterday') : t('pf.daysAgo', { n: d })
}

/** 日期分组：今天 / 昨天 / 更早 */
function dayGroup(time?: string) {
  if (!time) {
    return t('pf.earlier')
  }
  const stamp = new Date(time.replace('T', ' ').replace(/-/g, '/').split('.')[0]).getTime()
  const days = Math.floor((Date.now() - stamp) / 86400000)
  if (days <= 0) {
    return t('pf.today')
  }
  if (days === 1) {
    return t('pf.yesterday')
  }
  return new Date(stamp).toLocaleDateString('zh-CN')
}

/** 按日期分组后的足迹 */
const historyGrouped = computed(() => {
  const groups: { day: string; items: HistoryItem[] }[] = []
  history.value.forEach((item) => {
    const day = dayGroup(item.viewTime)
    const found = groups.find((g) => g.day === day)
    if (found) {
      found.items.push(item)
    } else {
      groups.push({ day, items: [item] })
    }
  })
  return groups
})

/* ---------------- 我的笔记（WangEditor） ---------------- */
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

/** 可关联的景点候选：来自浏览历史 */
const spotOptions = computed(() =>
  Array.from(new Set(history.value.map((h) => h.targetName).filter(Boolean) as string[])),
)

/** WangEditor 实例 */
const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = { placeholder: t('pf.noteEditorPh') }

function onEditorCreated(editor: unknown) {
  editorRef.value = editor
}

/** 关闭弹窗时销毁编辑器，避免内存泄漏 */
watch(noteDialog, (open) => {
  if (!open && editorRef.value) {
    ;(editorRef.value as { destroy: () => void }).destroy()
    editorRef.value = null
  }
})

/** 笔记内容渲染（富文本 → 消毒后输出） */
function renderNote(html?: string) {
  return DOMPurify.sanitize(html || '')
}

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
  const plain = noteForm.content.replace(/<[^>]+>/g, '').trim()
  if (!noteForm.title.trim() && !plain) {
    ElMessage.warning(t('pf.msgNoteEmpty'))
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
    ElMessage.success(t('pf.msgNoteSaved'))
    noteDialog.value = false
    await loadNotes()
  } finally {
    savingNote.value = false
  }
}

async function removeNote(item: NoteItem) {
  try {
    await ElMessageBox.confirm(t('pf.confirmDeleteNote', { title: item.title || t('pf.untitled') }), t('pf.confirmTitle'), {
      type: 'warning',
      confirmButtonText: t('pf.confirmDelete'),
      cancelButtonText: t('common.cancel'),
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

function splitTags(tags?: string) {
  return (tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/* ---------------- 账号设置 ---------------- */
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

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

/** 编辑资料弹窗 */
const profileDialog = ref(false)
const savingProfile = ref(false)
const profileForm = reactive({ username: '', email: '', bio: '' })

function openProfileDialog() {
  profileForm.username = userStore.user?.username ?? ''
  profileForm.email = userStore.user?.email ?? ''
  profileForm.bio = userStore.user?.bio ?? ''
  profileDialog.value = true
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const res = await request.put('/user/profile', {
      username: profileForm.username,
      email: profileForm.email,
      bio: profileForm.bio,
    })
    if (res.data) {
      userStore.setUser(res.data)
    }
    ElMessage.success(t('pf.msgProfileSaved'))
    profileDialog.value = false
  } finally {
    savingProfile.value = false
  }
}

/** 选择头像文件 */
function pickAvatar() {
  if (!userStore.isLogin) {
    ElMessage.warning(t('pf.msgLoginFirst'))
    return
  }
  avatarInput.value?.click()
}

/** 上传头像 */
async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploadingAvatar.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    // 不手动指定 Content-Type：交给 axios 自动带上 multipart boundary
    const res = await request.post('/user/avatar', fd)
    const current = userStore.user
    if (res.data && current) {
      userStore.setUser({ ...current, avatar: res.data })
    }
    ElMessage.success(t('pf.msgAvatarSaved'))
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

async function onChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning(t('pf.msgPwdRequired'))
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning(t('pf.msgPwdMismatch'))
    return
  }
  savingPwd.value = true
  try {
    await request.put('/user/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success(t('pf.msgPwdSaved'))
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } finally {
    savingPwd.value = false
  }
}

function onLogout() {
  userStore.logout()
  ElMessage.success(t('pf.msgLogout'))
  router.push('/login')
}

/* ------------------------------------------------------------
 * 菜单切换：按需加载
 * ---------------------------------------------------------- */
watch(active, (key) => {
  if (!userStore.isLogin) {
    return
  }
  if (key === 'trips' && !trips.value.length) {
    loadTrips()
  }
  if (key === 'collection' && !collections.value.length) {
    loadCollections()
  }
  if (key === 'history' && !history.value.length) {
    loadHistory()
  }
  if (key === 'notes' && !notes.value.length) {
    loadHistory()
    loadNotes()
  }
})

/* ------------------------------------------------------------
 * 生命周期
 * ---------------------------------------------------------- */
onMounted(async () => {
  if (!userStore.isLogin) {
    return
  }
  applyQueryTab()
  await Promise.all([loadProfile(), loadStats(), loadTrips()])

  // Hero 文案依次淡入
  gsap.from(['.pf-hero__avatar', '.pf-hero__name', '.pf-hero__bio'], {
    y: 24,
    opacity: 0,
    duration: 0.8,
    stagger: 0.1,
    ease: 'power3.out',
  })
})

onBeforeUnmount(() => {
  if (editorRef.value) {
    ;(editorRef.value as { destroy: () => void }).destroy()
    editorRef.value = null
  }
})

// 顶部导航下拉再次点击「我的行程 / 我的收藏」时，同步切换面板
watch(
  () => route.query.tab,
  () => applyQueryTab(),
)
</script>

<template>
  <section class="pf">
    <!-- ================= 1. Hero（40vh） ================= -->
    <header class="pf-hero">
      <div class="pf-hero__collage" aria-hidden="true">
        <img v-for="(src, i) in COLLAGE" :key="i" :src="src" alt="" loading="lazy" />
      </div>

      <div class="sy-overlay">
        <div class="sy-overlay__gradient" />
        <div class="sy-overlay__brocade" />
        <div class="sy-overlay__noise" />
      </div>

      <div class="sy-container pf-hero__inner">
        <button type="button" class="pf-hero__avatar" :title="t('pf.changeAvatar')" @click="pickAvatar">
          <img :src="avatarUrl" alt="avatar" />
          <span class="pf-hero__avatar-mask">{{ uploadingAvatar ? t('pf.uploading') : t('pf.change') }}</span>
        </button>
        <input
          ref="avatarInput"
          type="file"
          accept="image/*"
          class="pf-hero__file"
          @change="onAvatarChange"
        />

        <h1 class="pf-hero__name">{{ userStore.user?.username || t('pf.notLogin') }}</h1>
        <p class="pf-hero__bio">
          {{ userStore.user?.bio || t('pf.noBio') }}
        </p>
      </div>

      <button type="button" class="pf-hero__edit" @click="openProfileDialog">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 20h4l10-10-4-4L4 16v4zM14 6l4 4" />
        </svg>
        {{ t('pf.editProfile') }}
      </button>
    </header>

    <!-- ================= 2. 数据概览 ================= -->
    <div class="sy-container">
      <ul ref="statsEl" class="pf-stats">
        <li v-for="s in stats" :key="s.key" class="pf-stats__item">
          <b class="pf-stats__num">{{ s.num }}</b>
          <span class="pf-stats__label">{{ t(s.label) }}</span>
        </li>
      </ul>
    </div>

    <!-- ================= 3. 主体 ================= -->
    <div
      class="sy-container pf-body"
      :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen }"
    >
      <!-- 小屏：呼出侧边栏 -->
      <button type="button" class="pf-side-trigger" @click="mobileOpen = true">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M4 7h16M7 12h10M10 17h4" /></svg>
        菜单
      </button>

      <!-- 左：可折叠侧边栏 -->
      <aside class="pf-side" :class="{ 'is-collapsed': collapsed }">
        <ul class="pf-menu">
          <li
            v-for="m in MENUS"
            :key="m.key"
            class="pf-menu__item"
            :class="{ 'is-active': m.key === active }"
            @click="selectMenu(m.key)"
          >
            <ProfileMenuIcon class="pf-menu__icon" :name="m.key" />
            <span class="pf-menu__label">{{ t(m.label) }}</span>
            <svg class="pf-menu__arrow" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6" /></svg>
            <span class="pf-menu__tip">{{ t(m.label) }}</span>
          </li>
        </ul>

        <!-- 折叠按钮：固定在侧边栏右边缘、垂直居中 -->
        <button
          type="button"
          class="pf-side__toggle"
          :aria-label="collapsed ? '展开菜单' : '折叠菜单'"
          @click="toggleCollapse"
        >
          <svg v-if="collapsed" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6" /></svg>
          <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 6l-6 6 6 6" /></svg>
        </button>
      </aside>

      <!-- 小屏遮罩 -->
      <div class="pf-side-mask" @click="mobileOpen = false" />

      <!-- 右：内容区（切换淡出淡入） -->
      <div class="pf-content">
        <div v-if="!userStore.isLogin" class="pf-guest">
          <el-empty :description="t('pf.guest')" />
          <GoldButton @click="router.push('/login')">{{ t('pf.goLogin') }}</GoldButton>
        </div>

        <Transition v-else name="pf-fade" mode="out-in">
          <div :key="active" class="pf-pane">
            <!-- ---------- 我的行程 ---------- -->
            <template v-if="active === 'trips'">
              <div class="pf-head">
                <h2 class="pf-head__title">{{ t('nav.trips') }}</h2>
                <GoldButton size="sm" @click="router.push('/assistant')">{{ t('pf.newTrip') }}</GoldButton>
              </div>

              <StateBlock v-if="loadingTrips" state="loading" />
              <div v-else class="trips">
                <article
                  v-for="trip in trips"
                  :key="trip.id"
                  class="trip"
                  @click="viewTrip(trip)"
                >
                  <div class="trip__cover">
                    <img :src="trip.coverImage || COLLAGE[0]" alt="" loading="lazy" />
                    <span class="trip__days">{{ t('pf.days', { n: trip.days }) }}</span>
                  </div>
                  <div class="trip__body">
                    <h3 class="trip__title">{{ trip.title }}</h3>
                    <p class="trip__meta">
                      <span v-if="trip.companion">{{ t('pf.companion', { name: trip.companion }) }}</span>
                      <span v-if="trip.budget">{{ t('pf.budget', { n: trip.budget }) }}</span>
                    </p>
                    <div class="trip__foot">
                      <span class="trip__time">{{ relativeTime(trip.createTime) }}</span>
                      <span class="trip__acts">
                        <button type="button" class="trip__btn" @click.stop="viewTrip(trip)">{{ t('pf.view') }}</button>
                        <button type="button" class="trip__btn trip__btn--danger" @click.stop="removeTrip(trip)">
                          {{ t('common.delete') }}
                        </button>
                      </span>
                    </div>
                  </div>
                </article>
              </div>

              <el-empty v-if="!loadingTrips && !trips.length" :description="t('pf.noTrips')">
                <GoldButton @click="router.push('/assistant')">{{ t('pf.goPlan') }}</GoldButton>
              </el-empty>
            </template>

            <!-- ---------- 我的收藏 ---------- -->
            <template v-else-if="active === 'collection'">
              <div class="pf-head">
                <h2 class="pf-head__title">{{ t('nav.collection') }}</h2>
                <span class="pf-head__count">{{ t('pf.collectionCount', { n: collections.length }) }}</span>
              </div>

              <StateBlock v-if="loadingCollection" state="loading" />
              <div v-else class="cards">
                <article
                  v-for="item in collections"
                  :key="item.id"
                  class="ccard"
                  @click="router.push(`/scenic/${item.id}`)"
                >
                  <div class="ccard__img" :style="{ backgroundImage: `url(${item.coverImage})` }">
                    <button
                      type="button"
                      class="ccard__heart"
                      :title="t('pf.unCollect')"
                      @click.stop="onCancelCollect(item.id)"
                    >
                      ❤
                    </button>
                  </div>
                  <div class="ccard__body">
                    <h3 class="ccard__name">{{ item.name }}</h3>
                    <p class="ccard__meta">{{ item.cityName }} · {{ item.type }}</p>
                    <span class="ccard__score">★ {{ item.score }}</span>
                  </div>
                </article>
              </div>

              <el-empty v-if="!loadingCollection && !collections.length" :description="t('pf.noCollections')">
                <GoldButton @click="router.push('/scenic')">{{ t('pf.goScenic') }}</GoldButton>
              </el-empty>
            </template>

            <!-- ---------- 浏览历史 ---------- -->
            <template v-else-if="active === 'history'">
              <div class="pf-head">
                <h2 class="pf-head__title">{{ t('pf.menuHistory') }}</h2>
                <button v-if="history.length" type="button" class="pf-head__clear" @click="clearHistory">
                  {{ t('pf.clearHistory') }}
                </button>
              </div>

              <StateBlock v-if="loadingHistory" state="loading" />
              <div v-else class="foot-wrap">
                <section v-for="group in historyGrouped" :key="group.day" class="foot-group">
                  <p class="foot-group__day">{{ group.day }}</p>
                  <ol class="foot">
                    <li v-for="item in group.items" :key="item.id" class="foot__item">
                      <span class="foot__node" aria-hidden="true" />
                      <button type="button" class="foot__card" @click="goHistory(item)">
                        <img class="foot__cover" :src="item.coverImage" alt="" loading="lazy" />
                        <span class="foot__info">
                          <b>{{ item.targetName }}</b>
                          <em>{{ item.subTitle }} · {{ item.targetType === 'CULTURE' ? t('pf.tagCulture') : t('pf.tagScenic') }}</em>
                          <i>{{ relativeTime(item.viewTime) }}</i>
                        </span>
                        <span class="foot__arrow">→</span>
                      </button>
                      <button type="button" class="foot__del" @click.stop="removeHistory(item)">{{ t('pf.remove') }}</button>
                    </li>
                  </ol>
                </section>
              </div>

              <el-empty v-if="!loadingHistory && !history.length" :description="t('pf.noHistory')">
                <GoldButton @click="router.push('/scenic')">{{ t('pf.goScenic2') }}</GoldButton>
              </el-empty>
            </template>

            <!-- ---------- 我的笔记 ---------- -->
            <template v-else-if="active === 'notes'">
              <div class="pf-head">
                <h2 class="pf-head__title">{{ t('pf.menuNotes') }}</h2>
                <GoldButton size="sm" @click="openNote()">{{ t('pf.writeNote') }}</GoldButton>
              </div>

              <div class="note-bar">
                <p class="note-bar__stat">
                  {{ t('pf.noteStat', { count: noteStats.count, chars: noteStats.chars }) }}
                </p>
                <label class="note-bar__search">
                  <input
                    v-model="noteKeyword"
                    type="text"
                    :placeholder="t('pf.searchNote')"
                    @keyup.enter="loadNotes"
                  />
                  <span class="note-bar__line" />
                </label>
              </div>

              <StateBlock v-if="loadingNotes" state="loading" />
              <div v-else class="notes">
                <article
                  v-for="(item, i) in notes"
                  :key="item.id"
                  class="note"
                  :class="`note--r${i % 3}`"
                  @click="openNote(item)"
                >
                  <span class="note__pin" aria-hidden="true" />
                  <h3 class="note__title">{{ item.title || t('pf.untitled') }}</h3>
                  <div class="note__content" v-html="renderNote(item.content)" />
                  <div v-if="splitTags(item.tags).length" class="note__tags">
                    <span v-for="t in splitTags(item.tags)" :key="t" class="note__tag">{{ t }}</span>
                  </div>
                  <footer class="note__foot">
                    <span v-if="item.targetName" class="note__linked">{{ t('pf.linked', { name: item.targetName }) }}</span>
                    <span class="note__date">{{ relativeTime(item.updateTime) }}</span>
                  </footer>
                  <button type="button" class="note__del" @click.stop="removeNote(item)">{{ t('common.delete') }}</button>
                </article>
              </div>

              <el-empty v-if="!loadingNotes && !notes.length" :description="t('pf.noNotes')">
                <GoldButton @click="openNote()">{{ t('pf.writeNote') }}</GoldButton>
              </el-empty>
            </template>

            <!-- ---------- 账号设置 ---------- -->
            <template v-else>
              <div class="pf-head">
                <h2 class="pf-head__title">{{ t('pf.menuSettings') }}</h2>
              </div>

              <!-- 资料 -->
              <div class="set-block">
                <h3 class="set-block__title">{{ t('pf.basic') }}</h3>
                <div class="set-row">
                  <span class="set-row__label">{{ t('pf.username') }}</span>
                  <span class="set-row__value">{{ userStore.user?.username || '—' }}</span>
                </div>
                <div class="set-row">
                  <span class="set-row__label">{{ t('pf.emailLabel') }}</span>
                  <span class="set-row__value">{{ userStore.user?.email || '—' }}</span>
                </div>
                <div class="set-row">
                  <span class="set-row__label">{{ t('pf.bioLabel') }}</span>
                  <span class="set-row__value">{{ userStore.user?.bio || '—' }}</span>
                </div>
                <div class="set-row">
                  <span class="set-row__label">{{ t('pf.role') }}</span>
                  <span class="set-row__value">{{ userStore.user?.role || '—' }}</span>
                </div>
                <div class="set-row">
                  <span class="set-row__label">{{ t('pf.avatarLabel') }}</span>
                  <span class="set-row__value">
                    <GhostButton @click="pickAvatar">{{ t('pf.uploadAvatar') }}</GhostButton>
                  </span>
                </div>
              </div>

              <!-- 密码 -->
              <div class="set-block">
                <h3 class="set-block__title">{{ t('pf.changePwd') }}</h3>
                <div class="set-form">
                  <el-input v-model="pwdForm.oldPassword" type="password" size="large" show-password :placeholder="t('pf.oldPwd')" />
                  <el-input v-model="pwdForm.newPassword" type="password" size="large" show-password :placeholder="t('pf.newPwd')" />
                  <el-input
                    v-model="pwdForm.confirmPassword"
                    type="password"
                    size="large"
                    show-password
                    :placeholder="t('pf.confirmPwd')"
                    @keyup.enter="onChangePassword"
                  />
                  <GoldButton :disabled="savingPwd" @click="onChangePassword">{{ t('pf.savePwd') }}</GoldButton>
                </div>
              </div>

              <!-- 退出 -->
              <div class="set-block">
                <h3 class="set-block__title">{{ t('pf.logoutBlock') }}</h3>
                <p class="set-block__tip">{{ t('pf.logoutTip') }}</p>
                <button type="button" class="set-logout" @click="onLogout">{{ t('pf.logoutBlock') }}</button>
              </div>
            </template>
          </div>
        </Transition>
      </div>
    </div>

    <!-- ================= 行程编辑弹窗 ================= -->
    <el-dialog v-model="tripDialog" :title="tripForm.id ? t('pf.tripEdit') : t('pf.tripNew')" width="760px" top="6vh">
      <div class="trip-form">
        <el-input v-model="tripForm.title" size="large" :placeholder="t('pf.tripTitlePh')" />
        <div class="trip-form__row">
          <el-input-number v-model="tripForm.days" :min="1" :max="30" />
          <el-input v-model="tripForm.companion" :placeholder="t('pf.companionPh')" />
          <el-input v-model="tripForm.budget" :placeholder="t('pf.budgetPh')" />
        </div>
        <el-input v-model="tripForm.interests" :placeholder="t('pf.interestsPh')" />

        <div class="trip-form__days-head">
          <span>{{ t('pf.dayList') }}</span>
          <el-button link type="primary" @click="addDay">{{ t('pf.addDay') }}</el-button>
        </div>

        <div v-for="(day, index) in tripForm.dayList" :key="index" class="trip-form__day">
          <span class="trip-form__day-no">D{{ day.dayIndex }}</span>
          <el-input v-model="day.date" :placeholder="t('pf.datePh')" />
          <el-input v-model="day.scenicName" :placeholder="t('pf.scenicPh')" />
          <el-input v-model="day.stayDuration" :placeholder="t('pf.durationPh')" />
          <el-input v-model="day.transport" :placeholder="t('pf.transportPh')" />
          <el-input v-model="day.food" :placeholder="t('pf.foodPh')" />
          <el-button link type="danger" @click="removeDay(index)">{{ t('pf.remove') }}</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="tripDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingTrip" @click="saveTrip">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- ================= 笔记编辑弹窗（WangEditor 富文本） ================= -->
    <el-dialog v-model="noteDialog" :title="noteForm.id ? t('pf.noteEdit') : t('pf.noteNew')" width="760px" top="6vh">
      <div class="note-form">
        <el-input
          v-model="noteForm.title"
          size="large"
          maxlength="60"
          show-word-limit
          :placeholder="t('pf.noteTitlePh')"
        />

        <div class="note-editor">
          <Toolbar
            class="note-editor__bar"
            :editor="editorRef"
            :default-config="toolbarConfig"
            mode="default"
          />
          <Editor
            v-model="noteForm.content"
            class="note-editor__body"
            :default-config="editorConfig"
            mode="default"
            @on-created="onEditorCreated"
          />
        </div>

        <div class="note-form__row">
          <el-select
            v-model="noteForm.targetName"
            class="note-form__select"
            :placeholder="t('pf.linkSpotPh')"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="s in spotOptions" :key="s" :label="s" :value="s" />
          </el-select>
          <el-input v-model="noteForm.tags" :placeholder="t('pf.tagsPh')" />
        </div>
      </div>

      <template #footer>
        <el-button @click="noteDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingNote" @click="saveNote">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- ================= 编辑资料弹窗 ================= -->
    <el-dialog v-model="profileDialog" :title="t('pf.editProfile')" width="520px" top="10vh">
      <div class="pf-form">
        <el-input v-model="profileForm.username" size="large" :placeholder="t('pf.username')" maxlength="50" />
        <el-input v-model="profileForm.email" size="large" :placeholder="t('pf.emailLabel')" maxlength="100" />
        <el-input
          v-model="profileForm.bio"
          type="textarea"
          :rows="3"
          resize="none"
          maxlength="120"
          show-word-limit
          :placeholder="t('pf.bioPh')"
        />
      </div>

      <template #footer>
        <el-button @click="profileDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingProfile" @click="saveProfile">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;
/* ============================================================
 * 1. Hero（40vh）
 * ========================================================== */
.pf {
  min-height: 100vh;
  background: $color-paper;
}

.pf-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: 40vh;
  overflow: hidden;
  isolation: isolate;
}

/* 城市拼贴底图 */
.pf-hero__collage {
  position: absolute;
  inset: 0;
  z-index: $z-bg;
  display: grid;
  grid-template-columns: repeat(4, 1fr);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    filter: saturate(0.9);
  }
}

.pf-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(48px, 9vh, 110px) clamp(28px, 5vh, 56px);
  text-align: center;
}

/* 头像：金色描边 */
.pf-hero__avatar {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: clamp(84px, 9vw, 118px);
  height: clamp(84px, 9vw, 118px);
  overflow: hidden;
  border: 2px solid $color-gold;
  border-radius: 50%;
  background: $color-paper;
  box-shadow:
    0 0 0 6px rgba(212, 179, 116, 0.16),
    $shadow-lg;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &:hover .pf-hero__avatar-mask {
    opacity: 1;
  }
}

.pf-hero__avatar-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(14, 42, 71, 0.6);
  font-size: 18px;
  letter-spacing: 0.16em;
  color: $color-gold;
  opacity: 0;
  transition: opacity $duration-normal $ease-out-expo;
}

.pf-hero__file {
  display: none;
}

.pf-hero__name {
  margin-top: clamp(14px, 2vh, 22px);
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(36px, 4.2vw, 63px);
  letter-spacing: 0.1em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.pf-hero__bio {
  margin-top: 10px;
  font-family: $font-body;
  font-size: clamp(19.5px, 1.65vw, 24px);
  letter-spacing: 0.14em;
  color: rgba(245, 241, 232, 0.82);
  text-shadow: $shadow-text-soft;
}

.pf-hero__edit {
  position: absolute;
  right: clamp(16px, 3vw, 56px);
  bottom: clamp(16px, 3vw, 40px);
  z-index: $z-content;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: $radius-pill;
  background: rgba(14, 42, 71, 0.35);
  backdrop-filter: blur(8px);
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.1em;
  color: $color-gold;
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    background: $color-gold;
    color: $color-ink;
    transform: translateY(-2px);
  }
}

/* ============================================================
 * 2. 数据概览
 * ========================================================== */
.pf-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: clamp(-40px, -3vw, -18px);
  overflow: hidden;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.92);
  box-shadow: $shadow-lg;
  position: relative;
  z-index: $z-content;
}

.pf-stats__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: clamp(18px, 2.2vw, 30px) 12px;

  & + & {
    border-left: 1px solid rgba(212, 179, 116, 0.28);
  }
}

.pf-stats__num {
  font-family: $font-en;
  font-size: clamp(42px, 4.8vw, 69px);
  line-height: 1;
  color: $color-gold;
}

.pf-stats__label {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.14em;
  color: rgba(var(--sy-text-rgb), 0.55);
}

/* ============================================================
 * 3. 主体：菜单 + 内容
 * ========================================================== */
.pf-body {
  display: flex;
  align-items: flex-start;
  gap: clamp(20px, 3vw, 46px);
  padding-block: clamp(28px, 4vw, 56px) clamp(48px, 7vw, 110px);
}

.pf-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* 通栏全高：随屏幕高度自适应 */
  height: calc(100vh - #{$nav-height} - 48px);
  min-height: 360px;
  padding: 10px;
  border: 1px solid $color-border-gold;
  /* 直角 */
  border-radius: 0;
  background: rgba(var(--sy-surface-rgb), 0.62);
  overflow-y: auto;
}

.pf-menu__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  /* 直角 */
  border-radius: 0;
  font-family: $font-body;
  font-size: calc(#{$fs-body} * 1.5);
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.76);
  cursor: pointer;
  transition:
    background-color $duration-normal $ease-out-expo,
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.14);
    color: $color-gold;
    transform: translateX(3px);
  }

  &.is-active {
    background: linear-gradient(120deg, $color-primary, #6e141c);
    color: $color-text-light;
    box-shadow: 0 8px 20px rgba(122, 26, 34, 0.22);

    // 选中：金色左侧 3px 竖条
    &::before {
      content: '';
      position: absolute;
      top: 8px;
      bottom: 8px;
      left: 0;
      width: 3px;
      border-radius: 0;
      background: $color-gold;
    }

    .pf-menu__arrow {
      color: $color-gold;
    }
  }
}

.pf-menu__icon {
  flex-shrink: 0;
  color: rgba(var(--sy-text-rgb), 0.5);
  transition: color $duration-normal $ease-out-expo;
}

.pf-menu__item:hover .pf-menu__icon,
.pf-menu__item.is-active .pf-menu__icon {
  color: $color-gold;
}

.pf-menu__label {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
}

.pf-menu__arrow {
  flex-shrink: 0;
  color: rgba(var(--sy-text-rgb), 0.3);
  transition: transform $duration-normal $ease-out-expo;
}

.pf-menu__tip {
  position: absolute;
  top: 50%;
  left: calc(100% + 14px);
  z-index: 30;
  padding: 6px 12px;
  border-radius: $radius-md;
  background: rgba(14, 42, 71, 0.94);
  color: $color-text-light;
  font-size: calc(#{$fs-tiny} * 1.5);
  letter-spacing: 0.06em;
  white-space: nowrap;
  opacity: 0;
  transform: translateY(-50%) scale(0.96);
  pointer-events: none;
  transition:
    opacity $duration-fast $ease-out-expo,
    transform $duration-fast $ease-out-expo;
}

/* ---------------- 可折叠侧边栏 ---------------- */
.pf-side {
  position: sticky;
  top: calc(#{$nav-height} + 24px);
  flex-shrink: 0;
  width: 240px;
  transition: width $duration-fast $ease-out-expo;

  // 右边缘 1px 金色渐变分隔线
  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: -1px;
    bottom: 0;
    width: 1px;
    background: linear-gradient(
      180deg,
      rgba(212, 179, 116, 0) 0%,
      $color-gold 50%,
      rgba(212, 179, 116, 0) 100%
    );
  }
}

.pf-side.is-collapsed {
  width: 64px;

  .pf-menu__label,
  .pf-menu__arrow {
    display: none;
  }

  .pf-menu__item {
    justify-content: center;
    padding: 12px 0;

    &:hover {
      transform: none;
    }
  }

  .pf-menu__item:hover .pf-menu__tip {
    opacity: 1;
    transform: translateY(-50%) scale(1);
  }
}

/* 折叠按钮：侧边栏右边缘、垂直居中 */
.pf-side__toggle {
  position: absolute;
  top: 50%;
  right: -16px;
  z-index: 12;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  transform: translateY(-50%);
  border: 1px solid $color-gold;
  /* 直角 */
  border-radius: 0;
  background: $color-paper;
  color: var(--sy-accent);
  box-shadow: $shadow-sm;
  transition:
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    background: $color-gold;
    color: $color-ink;
  }
}

/* 小屏「菜单」按钮（默认隐藏） */
.pf-side-trigger {
  display: none;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: 1px solid $color-gold;
  border-radius: $radius-pill;
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.1em;
  color: var(--sy-accent);
}

.pf-side-mask {
  display: none;
}

.pf-content {
  flex: 1;
  min-width: 0;
}

/* 面板切换：淡出淡入 */
.pf-fade-enter-active,
.pf-fade-leave-active {
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}

.pf-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.pf-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 面板头部 */
.pf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: clamp(16px, 2vw, 26px);
}

.pf-head__title {
  position: relative;
  padding-left: 16px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(27px, 2.7vw, 39px);
  letter-spacing: 0.1em;
  color: $color-ink;

  &::before {
    content: '';
    position: absolute;
    top: 0.16em;
    left: 0;
    width: 3px;
    height: 0.84em;
    background: $color-gold;
  }
}

.pf-head__count {
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.5);
}

.pf-head__clear {
  padding: 7px 16px;
  border: 1px solid rgba(122, 26, 34, 0.3);
  border-radius: $radius-pill;
  font-size: calc(#{$fs-small} * 1.5);
  color: var(--sy-accent);
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.08);
  }
}

.pf-guest {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding-block: clamp(40px, 8vw, 100px);
}

/* ============================================================
 * 我的行程
 * ========================================================== */
.trips {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 22px);
}

.trip {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.7);
  box-shadow: $shadow-md;
  cursor: pointer;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    transform: translateY(-4px);
    box-shadow: $shadow-lg;

    .trip__cover img {
      transform: scale(1.06);
    }

    // hover 才浮出操作按钮
    .trip__btn {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

.trip__cover {
  position: relative;
  min-height: 170px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 1s $ease-out-expo;
  }
}

.trip__days {
  position: absolute;
  left: 12px;
  bottom: 10px;
  padding: 3px 12px;
  border-radius: $radius-pill;
  background: rgba(14, 42, 71, 0.6);
  backdrop-filter: blur(6px);
  font-size: 18px;
  letter-spacing: 0.1em;
  color: $color-gold;
}

.trip__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: clamp(14px, 1.6vw, 22px);
}

.trip__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(24px, 2.1vw, 31.5px);
  letter-spacing: 0.06em;
  color: $color-ink;
  @include text-ellipsis(1);
}

.trip__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.6);
}

.trip__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed rgba(122, 26, 34, 0.15);
}

.trip__time {
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.45);
}

.trip__acts {
  display: flex;
  gap: 8px;
}

.trip__btn {
  padding: 6px 14px;
  border: 1px solid rgba(212, 179, 116, 0.6);
  border-radius: $radius-pill;
  font-size: 18px;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
  opacity: 0;
  transform: translateY(4px);
  transition:
    opacity $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    background: $color-gold;
    color: $color-ink;
  }

  &--danger {
    border-color: rgba(122, 26, 34, 0.35);

    &:hover {
      border-color: $color-primary;
      background: $color-primary;
      color: $color-text-light;
    }
  }
}

/* ============================================================
 * 我的收藏
 * ========================================================== */
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: clamp(16px, 2vw, 28px);
}

.ccard {
  @include hover-lift;
  overflow: hidden;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.66);
  box-shadow: $shadow-md;
  cursor: pointer;

  &:hover {
    border-color: $color-gold;

    .ccard__img {
      transform: scale(1.06);
    }

    .ccard__heart {
      opacity: 1;
    }
  }
}

.ccard__img {
  position: relative;
  aspect-ratio: 4 / 3;
  background-position: center;
  background-size: cover;
  transition: transform 1s $ease-out-expo;
}

.ccard__heart {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(14, 42, 71, 0.5);
  backdrop-filter: blur(6px);
  font-size: 22.5px;
  color: #e2666f;
  opacity: 0;
  transition:
    opacity $duration-normal $ease-out-expo,
    background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.92);
    color: #fff;
  }
}

.ccard__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: clamp(12px, 1.4vw, 18px);
}

.ccard__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(22.5px, 1.95vw, 27px);
  letter-spacing: 0.06em;
  color: $color-text-dark;
  @include text-ellipsis(1);
}

.ccard__meta {
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.ccard__score {
  font-family: $font-en;
  font-size: 19.5px;
  color: $color-gold;
}

/* ============================================================
 * 浏览历史（时间轴）
 * ========================================================== */
.foot-wrap {
  display: flex;
  flex-direction: column;
  gap: clamp(18px, 2.2vw, 30px);
}

.foot-group__day {
  margin-bottom: 12px;
  font-family: $font-title;
  font-weight: 900;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.14em;
  color: $color-gold;
}

.foot {
  position: relative;
  padding-left: 34px;
  list-style: none;

  &::before {
    content: '';
    position: absolute;
    top: 6px;
    bottom: 6px;
    left: 13px;
    width: 1px;
    background: linear-gradient(180deg, rgba(212, 179, 116, 0.6), rgba(212, 179, 116, 0.1));
  }
}

.foot__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;

  & + .foot__item {
    margin-top: 14px;
  }
}

.foot__node {
  position: absolute;
  left: -26px;
  width: 10px;
  height: 10px;
  border: 1px solid $color-gold;
  border-radius: 50%;
  background: $color-paper;
}

.foot__item:first-child .foot__node {
  background: $color-gold;
  box-shadow: 0 0 0 4px rgba(212, 179, 116, 0.2);
}

.foot__card {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border: 1px solid rgba(212, 179, 116, 0.3);
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.66);
  text-align: left;
  transition:
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    border-color: $color-gold;
    transform: translateX(4px);
    box-shadow: $shadow-sm;
  }
}

.foot__cover {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid rgba(212, 179, 116, 0.4);
  border-radius: $radius-md;
}

.foot__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  b {
    font-family: $font-title;
    font-weight: 900;
    font-size: calc(#{$fs-small} * 1.5);
    color: $color-text-dark;
    @include text-ellipsis(1);
  }

  em {
    font-style: normal;
    font-size: 18px;
    color: rgba(var(--sy-text-rgb), 0.5);
  }

  i {
    font-style: normal;
    font-size: 16.5px;
    color: $color-gold;
  }
}

.foot__arrow {
  margin-left: auto;
  color: $color-gold;
}

.foot__del {
  flex-shrink: 0;
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.45);

  &:hover {
    color: var(--sy-accent);
  }
}

/* ============================================================
 * 我的笔记（便签墙）
 * ========================================================== */
.note-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: clamp(16px, 2vw, 26px);
  padding: 12px 16px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.6);
}

.note-bar__stat {
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.6);

  b {
    font-family: $font-en;
    font-size: 25.5px;
    color: $color-gold;
  }
}

.note-bar__search {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 230px;

  input {
    width: 100%;
    padding: 6px 2px;
    border: none;
    background: transparent;
    font-family: $font-body;
    font-size: calc(#{$fs-small} * 1.5);
    color: $color-text-dark;
    caret-color: $color-gold;

    &::placeholder {
      color: rgba(var(--sy-text-rgb), 0.35);
    }
  }

  .note-bar__line {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 1px;
    background: rgba(122, 26, 34, 0.25);
    transition: background-color $duration-normal $ease-out-expo;
  }

  &:focus-within .note-bar__line {
    background: $color-gold;
  }
}

.notes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: clamp(16px, 2vw, 26px);
}

.note {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 22px 18px 16px;
  border: 1px solid rgba(212, 179, 116, 0.42);
  border-radius: $radius-md;
  background: linear-gradient(180deg, rgba(var(--sy-surface-rgb), 1), rgba(var(--sy-paper-rgb), 1));
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo,
    border-color $duration-normal $ease-out-expo;

  // 轻微错落，hover 摆正
  &--r0 {
    transform: rotate(-0.5deg);
  }

  &--r1 {
    transform: rotate(0.35deg);
  }

  &--r2 {
    transform: rotate(-0.2deg);
  }

  &:hover {
    border-color: $color-gold;
    transform: rotate(0deg) translateY(-6px);
    box-shadow: $shadow-lg;

    .note__del {
      opacity: 1;
    }
  }
}

/* 金色图钉 */
.note__pin {
  position: absolute;
  top: -7px;
  left: 50%;
  width: 14px;
  height: 14px;
  margin-left: -7px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #e8c98b, $color-gold 60%, #a8873f);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.25);
}

.note__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(22.5px, 1.95vw, 27px);
  letter-spacing: 0.06em;
  color: $color-ink;
  @include text-ellipsis(1);
}

.note__content {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  line-height: 1.9;
  color: rgba(var(--sy-text-rgb), 0.72);
  @include text-ellipsis(5);

  :deep(p) {
    margin: 0 0 6px;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: $radius-sm;
  }
}

.note__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note__tag {
  padding: 2px 10px;
  border: 1px solid rgba(212, 179, 116, 0.5);
  border-radius: $radius-pill;
  font-size: 16.5px;
  color: $color-bamboo;
}

.note__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(122, 26, 34, 0.15);
  font-size: 16.5px;
  color: rgba(var(--sy-text-rgb), 0.45);
}

.note__linked {
  color: $color-gold;
}

.note__del {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 18px;
  color: rgba(var(--sy-text-rgb), 0.45);
  opacity: 0;
  transition: opacity $duration-normal $ease-out-expo;

  &:hover {
    color: var(--sy-accent);
  }
}

/* ============================================================
 * 账号设置
 * ========================================================== */
.set-block {
  padding: clamp(16px, 1.8vw, 24px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.66);
  box-shadow: $shadow-md;

  & + & {
    margin-top: clamp(16px, 2vw, 24px);
  }
}

.set-block__title {
  position: relative;
  margin-bottom: 14px;
  padding-left: 14px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(21px, 1.8vw, 25.5px);
  letter-spacing: 0.12em;
  color: var(--sy-accent);

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

.set-block__tip {
  margin-bottom: 14px;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.55);
}

.set-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;

  & + & {
    border-top: 1px dashed rgba(122, 26, 34, 0.12);
  }
}

.set-row__label {
  width: 72px;
  flex-shrink: 0;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.1em;
  color: $color-gold;
}

.set-row__value {
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  color: rgba(var(--sy-text-rgb), 0.8);
}

.set-form {
  display: grid;
  gap: 12px;
  max-width: 420px;
}

/* 退出登录：红色按钮 */
.set-logout {
  padding: 11px 28px;
  border: 1px solid rgba(200, 40, 50, 0.5);
  border-radius: $radius-pill;
  background: linear-gradient(120deg, #c0392b, #8e1f16);
  font-family: $font-body;
  font-size: calc(#{$fs-small} * 1.5);
  letter-spacing: 0.12em;
  color: #fff;
  transition:
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(192, 57, 43, 0.32);
  }
}

/* ============================================================
 * 弹窗内部
 * ========================================================== */
.trip-form,
.note-form,
.pf-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trip-form__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.trip-form__days-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  font-size: calc(#{$fs-small} * 1.5);
  color: var(--sy-accent);
}

.trip-form__day {
  display: grid;
  grid-template-columns: 40px repeat(3, minmax(0, 1fr)) auto;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border: 1px dashed rgba(212, 179, 116, 0.5);
  border-radius: $radius-md;
}

.trip-form__day-no {
  font-family: $font-en;
  font-size: 19.5px;
  color: $color-gold;
}

/* WangEditor 富文本 */
.note-editor {
  z-index: 1;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 1);

  :deep(.w-e-toolbar) {
    border-bottom: 1px solid rgba(212, 179, 116, 0.3);
  }

  :deep(.w-e-text-container) {
    height: 280px !important;
  }
}

.note-form__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

/* ============================================================
 * 响应式
 * ========================================================== */
@include responsive(lg) {
  .pf-body {
    grid-template-columns: minmax(0, 1fr);
  }

  // 菜单横向滚动
  .pf-menu {
    position: static;
    flex-direction: row;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .pf-menu__item {
    flex-shrink: 0;
  }

  .trip {
    grid-template-columns: minmax(0, 1fr);
  }

  .trip__cover {
    aspect-ratio: 16 / 9;
    min-height: 0;
  }

  // 触屏上直接显示操作按钮
  .trip__btn,
  .note__del {
    opacity: 1;
    transform: none;
  }
}

@include responsive(md) {
  .pf-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pf-stats__item:nth-child(3) {
    border-left: none;
  }

  .pf-stats__item:nth-child(n + 3) {
    border-top: 1px solid rgba(212, 179, 116, 0.28);
  }

  .pf-hero__collage {
    grid-template-columns: repeat(2, 1fr);
  }

  .trip-form__row,
  .trip-form__day,
  .note-form__row {
    grid-template-columns: minmax(0, 1fr);
  }

  .set-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  /* 小屏：主体纵向排列，侧边栏改为抽屉 */
  .pf-body {
    flex-direction: column;
    gap: 18px;
  }

  .pf-side-trigger {
    display: inline-flex;
  }

  .pf-side {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: $z-header + 2;
    width: 260px;
    padding: 20px 12px;
    transform: translateX(-100%);
    transition: transform $duration-fast $ease-out-expo;
    background: rgba(14, 42, 71, 0.92);
    backdrop-filter: blur(16px);

    &::after {
      display: none;
    }

    .pf-menu {
      border-color: rgba(212, 179, 116, 0.3);
      background: transparent;
      box-shadow: none;
    }

    .pf-menu__item {
      color: rgba(245, 241, 232, 0.9);

      &:hover {
        background: rgba(212, 179, 116, 0.16);
        color: $color-gold;
      }
    }

    .pf-menu__icon {
      color: rgba(245, 241, 232, 0.72);
    }
  }

  /* 小屏下即使折匉也强制展开显示（抽屉需完整） */
  .pf-side.is-collapsed {
    width: 260px;

    .pf-menu__label,
    .pf-menu__arrow {
      display: flex;
    }

    .pf-menu__item {
      justify-content: flex-start;
      padding: 12px 14px;
    }
  }

  .pf-side__toggle {
    display: none;
  }

  .pf-body.is-mobile-open .pf-side {
    transform: translateX(0);
  }

  .pf-body.is-mobile-open .pf-side-mask {
    position: fixed;
    inset: 0;
    z-index: $z-header + 1;
    display: block;
    background: rgba(8, 12, 18, 0.5);
    backdrop-filter: blur(2px);
  }
}
</style>
