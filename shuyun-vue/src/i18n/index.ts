/**
 * 轻量国际化（自实现，不引入第三方依赖）
 *
 * 为什么自己做：
 * - 项目只需「中 / 英」两种，vue-i18n 会再增加一份体积，自实现约 150 行足够
 * - locale 用 ref 保存，模板里调用 t() 时会自动追踪依赖，切换语言即整体刷新
 * - 词表按「通用 / 导航 / 搜索 / 页脚 / 登录」等前缀分组，查不到时回退到 key 本身，
 *   方便一眼发现漏翻
 *
 * 覆盖边界（重要）：
 * - 已英文化：全局框架层（导航栏、页脚、搜索浮层、回到顶部）+ 登录页
 * - 保留中文原文：景点 / 文化的名称与正文 —— 这类内容的正式英文名需人工校订，
 *   硬翻反而会出错，因此按「原样呈现」处理，并在切换语言时明确告知用户
 */
import { ref, watch } from 'vue'

export type Locale = 'zh' | 'en'

const STORAGE_KEY = 'shuyun_lang'

/** 中文词表（同时作为兜底词表） */
const ZH: Record<string, string> = {
  /* ---------------- 通用 ---------------- */
  'common.online': '在线',
  'common.backTop': '回到顶部',
  'common.more': '更多',

  /* ---------------- 导航栏 ---------------- */
  'nav.home': '首页',
  'nav.scenic': '景点',
  'nav.culture': '文化',
  'nav.assistant': 'AI管家',
  'nav.profile': '个人中心',
  'nav.trips': '我的行程',
  'nav.collection': '我的收藏',
  'nav.logout': '退出登录',
  'nav.login': '登录',
  'nav.register': '登录 / 注册',
  'nav.searchTitle': '搜索（景点 / 市州 / 文化）',
  'nav.menuTitle': '菜单',
  'nav.accountTitle': '账号',
  'nav.langZh': '中文',
  'nav.langEn': 'English',
  'nav.langCurrentZh': '当前：中文',
  'nav.langCurrentEn': 'Current: English',

  /* ---------------- 搜索浮层 ---------------- */
  'search.placeholder': '搜索景点、市州、文化（如 九寨沟 / 成都 / 川剧）',
  'search.city': '市州',
  'search.scenic': '景点',
  'search.culture': '文化',
  'search.cityCount': '共 {count} 个景点',
  'search.viewAll': '查看全部 →',
  'search.recent': '最近搜索',
  'search.hot': '热门搜索',
  'search.clear': '清空',
  'search.searching': '正在搜索…',
  'search.empty': '没有找到「{keyword}」相关的内容',
  'search.hint': '↑↓ 选择 · Enter 打开 · Esc 关闭',

  /* ---------------- 页脚 ---------------- */
  'footer.slogan': 'AI 懂你的巴蜀旅行管家',
  'footer.quick': '快速导航',
  'footer.resource': '资源中心',
  'footer.contact': '联系我们',
  'footer.res.scenic': '景点大全',
  'footer.res.culture': '文化图谱',
  'footer.res.food': '川味地图',
  'footer.res.heritage': '非遗名录',
  'footer.res.help': '帮助中心',
  'footer.email': '邮箱',
  'footer.phone': '电话',
  'footer.address': '地址',
  'footer.icp': '备案',
  'footer.addressValue': '四川省成都市武侯区',
  'footer.copyright': '© 2026 蜀韵·智游 shuyun-travel · 保留所有权利',
  'footer.icpValue': '蜀ICP备2026000000号 · 川公网安备 51010000000000号',
  'footer.friends': '友情链接：',
  'footer.friend1': '四川省文化和旅游厅',
  'footer.friend2': '成都文旅',
  'footer.friend3': '四川非遗',

  /* ---------------- 语言切换提示 ---------------- */
  'lang.toZh': '已切换为中文',
  'lang.toEn': '已切换为 English：界面文案为英文，景点与文化的名称与正文保留中文原文',
  'lang.tip': '「{name}」正在建设中',

  /* ---------------- 登录页 ---------------- */
  'login.eyebrow': 'Shuyun · Zhiyou',
  'login.welcome': '欢迎回来',
  'login.create': '创建账号',
  'login.tipLogin': '登录蜀韵·智游，开启智能旅程',
  'login.tipRegister': '注册后即可收藏、规划与对话',
  'login.account': '用户名 / 邮箱',
  'login.username': '用户名',
  'login.email': '邮箱',
  'login.password': '密码',
  'login.submit': '登 录',
  'login.register': '注 册',
  'login.noAccount': '没有账号？',
  'login.hasAccount': '已有账号？',
  'login.goRegister': '去注册',
  'login.goLogin': '去登录',
  'login.needAccount': '请输入账号和密码',
  'login.needAll': '请填写完整的注册信息',
  'login.success': '登录成功',
  'login.registerSuccess': '注册成功，请登录',
  'login.poem1': '九天开出一成都',
  'login.poem2': '万户千门入画图',
  'login.poemFrom': '—— 李白《上皇西巡南京歌》',

  /* ---------------- 路由标题 ---------------- */
  'route.home': '首页',
  'route.login': '登录',
  'route.scenic': '景点',
  'route.scenicDetail': '景点详情',
  'route.culture': '文化',
  'route.cultureDetail': '文化详情',
  'route.assistant': 'AI管家',
  'route.profile': '个人中心',
  'app.name': '蜀韵·智游',
  'app.tagline': 'AI 懂你的巴蜀旅行管家',
  'social.wechat': '微信',
  'social.weibo': '微博',
  'social.bilibili': 'B 站',
  'social.red': '小红书',

  /* ---------------- 通用补充 ---------------- */
  'common.close': '关闭',
  'common.done': '完成',
  'common.all': '全部',
  'common.cancel': '取消',
  'common.save': '保存',
  'common.delete': '删除',

  /* ---------------- 景点列表 ---------------- */
  'scenic.typeAll': '全部',
  'scenic.typeNature': '自然',
  'scenic.typeHuman': '人文',
  'scenic.typeMuseum': '博物馆',
  'scenic.typeRed': '红色',
  'scenic.typeFamily': '亲子',
  'scenic.typeTown': '古镇',
  'scenic.sortDefault': '综合',
  'scenic.sortScore': '评分',
  'scenic.sortHot': '人气',
  'scenic.allCities': '全部市州',
  'scenic.heroAlt': '蜀地山水',
  'scenic.heroTitle1': '蜀地',
  'scenic.heroTitle2': '秘境',
  'scenic.heroSub': '21 市州 · 千处风光 · 一步一景',
  'scenic.filter': '筛选',
  'scenic.pageTitle': '蜀地秘境',
  'scenic.pageDesc': '从都江堰的水利智慧到九寨沟的翠海叠瀑 —— 共 {total} 处景点，按你的节奏慢慢挑。',
  'scenic.viewDetail': '查看详情',
  'scenic.empty': '未找到符合条件的景点',
  'scenic.reset': '重置筛选',
  'scenic.pagerAria': '分页',
  'scenic.prevPage': '上一页',
  'scenic.nextPage': '下一页',
  'scenic.cityLabel': '市州',
  'scenic.typeLabel': '类型',
  'scenic.sortLabel': '排序',
  'scenic.searchPlaceholder': '搜索景点 / 标签',

  /* ---------------- 景点详情 ---------------- */
  'detail.score': '评分',
  'detail.level': '等级',
  'detail.openTime': '开放时间',
  'detail.ticket': '门票',
  'detail.collected': '已收藏',
  'detail.collect': '收藏',
  'detail.share': '分享',
  'detail.scrollText': '向下探索',
  'detail.galleryTitle': '影像',
  'detail.galleryDesc': '在抵达之前，先看一遍它的样子。',
  'detail.about': '关于 {name}',
  'detail.weather': '实时天气',
  'detail.weatherMock': '示例数据（后端 /api/weather 待接入）',
  'detail.location': '位置',
  'detail.addressPending': '地址待补充',
  'detail.openInMap': '在地图 App 中打开',
  'detail.sameCity': '同城推荐',
  'detail.planNow': '立即规划行程',
  'detail.sameCityGallery': '同城影像',
  'detail.sameCityGalleryDesc': '同一座城的另外三处风景，顺路就去看看。',
  'detail.ctaTitle': '去蜀地，见山水',
  'detail.ctaButton': '让 AI 帮你规划',
  'detail.notFound': '景点不存在，或需要登录后查看',
  'detail.backList': '返回景点列表',
  'detail.loginFirst': '请先登录后再收藏',
  'detail.unCollected': '已取消收藏',
  'detail.collectedOk': '已加入收藏',
  'detail.linkCopied': '链接已复制，去分享吧',
  'detail.copyHint': '可复制浏览器地址栏链接分享',
  'detail.viewImage': '查看第 {index} 张图片',
  'detail.prevImage': '上一张',
  'detail.nextImage': '下一张',

  /* ---------------- 文化列表 ---------------- */
  'culture.heroAlt': '巴蜀文化',
  'culture.heroTitle1': '巴蜀',
  'culture.heroTitle2': '文脉',
  'culture.heroSub': '千年蜀地，八脉相承',
  'culture.navAria': '文化导航',
  'culture.more': '深入了解',
  'culture.timelineTitle': '八脉相承 · 一线千年',
}

/** 英文词表 */
const EN: Record<string, string> = {
  /* ---------------- Common ---------------- */
  'common.online': 'Online',
  'common.backTop': 'Back to top',
  'common.more': 'More',

  /* ---------------- Navbar ---------------- */
  'nav.home': 'Home',
  'nav.scenic': 'Attractions',
  'nav.culture': 'Culture',
  'nav.assistant': 'AI Butler',
  'nav.profile': 'Profile',
  'nav.trips': 'My Trips',
  'nav.collection': 'Favorites',
  'nav.logout': 'Sign out',
  'nav.login': 'Sign in',
  'nav.register': 'Sign in / Sign up',
  'nav.searchTitle': 'Search (attractions / cities / culture)',
  'nav.menuTitle': 'Menu',
  'nav.accountTitle': 'Account',
  'nav.langZh': '中文',
  'nav.langEn': 'English',
  'nav.langCurrentZh': '当前：中文',
  'nav.langCurrentEn': 'Current: English',

  /* ---------------- Search ---------------- */
  'search.placeholder': 'Search attractions, cities, culture (e.g. Jiuzhaigou / Chengdu)',
  'search.city': 'Cities',
  'search.scenic': 'Attractions',
  'search.culture': 'Culture',
  'search.cityCount': '{count} attractions',
  'search.viewAll': 'View all →',
  'search.recent': 'Recent',
  'search.hot': 'Trending',
  'search.clear': 'Clear',
  'search.searching': 'Searching…',
  'search.empty': 'Nothing found for “{keyword}”',
  'search.hint': '↑↓ to select · Enter to open · Esc to close',

  /* ---------------- Footer ---------------- */
  'footer.slogan': 'Your AI travel butler for Sichuan',
  'footer.quick': 'Navigate',
  'footer.resource': 'Resources',
  'footer.contact': 'Contact',
  'footer.res.scenic': 'All Attractions',
  'footer.res.culture': 'Culture Atlas',
  'footer.res.food': 'Sichuan Food Map',
  'footer.res.heritage': 'Intangible Heritage',
  'footer.res.help': 'Help Center',
  'footer.email': 'Email',
  'footer.phone': 'Phone',
  'footer.address': 'Address',
  'footer.icp': 'ICP',
  'footer.addressValue': 'Wuhou District, Chengdu, Sichuan',
  'footer.copyright': '© 2026 Shuyun Zhiyou · All rights reserved',
  'footer.icpValue': 'Sichuan ICP No. 2026000000',
  'footer.friends': 'Links: ',
  'footer.friend1': 'Sichuan Culture & Tourism Dept.',
  'footer.friend2': 'Chengdu Culture & Tourism',
  'footer.friend3': 'Sichuan Intangible Heritage',

  /* ---------------- Language switch ---------------- */
  'lang.toZh': '已切换为中文',
  'lang.toEn': 'Switched to English. UI copy is in English; attraction & culture content stays in Chinese.',
  'lang.tip': '“{name}” is coming soon',

  /* ---------------- Login ---------------- */
  'login.eyebrow': 'Shuyun · Zhiyou',
  'login.welcome': 'Welcome back',
  'login.create': 'Create account',
  'login.tipLogin': 'Sign in to start your intelligent journey',
  'login.tipRegister': 'Sign up to save, plan and chat',
  'login.account': 'Username / Email',
  'login.username': 'Username',
  'login.email': 'Email',
  'login.password': 'Password',
  'login.submit': 'SIGN IN',
  'login.register': 'SIGN UP',
  'login.noAccount': 'No account?',
  'login.hasAccount': 'Already registered?',
  'login.goRegister': 'Sign up',
  'login.goLogin': 'Sign in',
  'login.needAccount': 'Please enter your account and password',
  'login.needAll': 'Please fill in all registration fields',
  'login.success': 'Signed in',
  'login.registerSuccess': 'Registered, please sign in',
  'login.poem1': 'Nine heavens opened, and Chengdu came to be',
  'login.poem2': 'A thousand gates, a thousand doors, all like a painting',
  'login.poemFrom': '— Li Bai, Song of the Western Journey',

  /* ---------------- Route titles ---------------- */
  'route.home': 'Home',
  'route.login': 'Sign in',
  'route.scenic': 'Attractions',
  'route.scenicDetail': 'Attraction',
  'route.culture': 'Culture',
  'route.cultureDetail': 'Culture',
  'route.assistant': 'AI Butler',
  'route.profile': 'Profile',
  'app.name': 'Shuyun Zhiyou',
  'app.tagline': 'Your AI travel butler for Sichuan',
  'social.wechat': 'WeChat',
  'social.weibo': 'Weibo',
  'social.bilibili': 'Bilibili',
  'social.red': 'RED',

  /* ---------------- Common ---------------- */
  'common.close': 'Close',
  'common.done': 'Done',
  'common.all': 'All',
  'common.cancel': 'Cancel',
  'common.save': 'Save',
  'common.delete': 'Delete',

  /* ---------------- Attraction list ---------------- */
  'scenic.typeAll': 'All',
  'scenic.typeNature': 'Nature',
  'scenic.typeHuman': 'Heritage',
  'scenic.typeMuseum': 'Museums',
  'scenic.typeRed': 'Red Tourism',
  'scenic.typeFamily': 'Family',
  'scenic.typeTown': 'Ancient Towns',
  'scenic.sortDefault': 'Recommended',
  'scenic.sortScore': 'Top rated',
  'scenic.sortHot': 'Popular',
  'scenic.allCities': 'All Cities',
  'scenic.heroAlt': 'Landscape of Sichuan',
  'scenic.heroTitle1': "Sichuan's",
  'scenic.heroTitle2': 'Hidden Wonders',
  'scenic.heroSub': '21 prefectures · countless views · one step, one scene',
  'scenic.filter': 'Filter',
  'scenic.pageTitle': 'Hidden Wonders of Sichuan',
  'scenic.pageDesc': 'From the ancient waterworks of Dujiangyan to the emerald lakes of Jiuzhaigou — {total} attractions, take them at your own pace.',
  'scenic.viewDetail': 'View details',
  'scenic.empty': 'No attractions match your filters',
  'scenic.reset': 'Reset filters',
  'scenic.pagerAria': 'Pagination',
  'scenic.prevPage': 'Previous page',
  'scenic.nextPage': 'Next page',
  'scenic.cityLabel': 'City',
  'scenic.typeLabel': 'Type',
  'scenic.sortLabel': 'Sort',
  'scenic.searchPlaceholder': 'Search attractions / tags',

  /* ---------------- Attraction detail ---------------- */
  'detail.score': 'Rating',
  'detail.level': 'Level',
  'detail.openTime': 'Hours',
  'detail.ticket': 'Tickets',
  'detail.collected': 'Saved',
  'detail.collect': 'Save',
  'detail.share': 'Share',
  'detail.scrollText': 'Scroll to explore',
  'detail.galleryTitle': 'Gallery',
  'detail.galleryDesc': 'A glimpse before you arrive.',
  'detail.about': 'About {name}',
  'detail.weather': 'Live weather',
  'detail.weatherMock': 'Sample data',
  'detail.location': 'Location',
  'detail.addressPending': 'Address pending',
  'detail.openInMap': 'Open in Maps',
  'detail.sameCity': 'Nearby',
  'detail.planNow': 'Plan a trip',
  'detail.sameCityGallery': 'Nearby sights',
  'detail.sameCityGalleryDesc': 'Three more sights in the same city, on your way.',
  'detail.ctaTitle': 'Go and see the mountains',
  'detail.ctaButton': 'Let AI plan it',
  'detail.notFound': 'Attraction not found, or sign in to view',
  'detail.backList': 'Back to attractions',
  'detail.loginFirst': 'Please sign in to save',
  'detail.unCollected': 'Removed from favorites',
  'detail.collectedOk': 'Added to favorites',
  'detail.linkCopied': 'Link copied, share it!',
  'detail.copyHint': 'Copy the address bar link to share',
  'detail.viewImage': 'View image {index}',
  'detail.prevImage': 'Previous image',
  'detail.nextImage': 'Next image',

  /* ---------------- Culture list ---------------- */
  'culture.heroAlt': 'Bashu culture',
  'culture.heroTitle1': 'Bashu',
  'culture.heroTitle2': 'Heritage',
  'culture.heroSub': 'A thousand years of Shu, eight living threads',
  'culture.navAria': 'Culture navigation',
  'culture.more': 'Learn more',
  'culture.timelineTitle': 'Eight threads, one line through time',
}

/** 当前语言（持久化到 localStorage） */
export const locale = ref<Locale>((localStorage.getItem(STORAGE_KEY) as Locale) || 'zh')

/**
 * 取词
 *
 * @param key    词条 key
 * @param params 占位符，如 t('search.cityCount', { count: 9 })
 * @returns 翻译后的文案；查不到时回退中文，再回退 key 本身
 */
export function t(key: string, params?: Record<string, string | number>): string {
  const dict = locale.value === 'en' ? EN : ZH
  let text = dict[key] ?? ZH[key] ?? key
  if (params) {
    Object.keys(params).forEach((name) => {
      text = text.replace(new RegExp(`\\{${name}\\}`, 'g'), String(params[name]))
    })
  }
  return text
}

/**
 * 设置语言（同时持久化）
 */
export function setLocale(next: Locale) {
  locale.value = next
  localStorage.setItem(STORAGE_KEY, next)
}

/**
 * 组件内使用：返回响应式的 locale / t / setLocale
 */
export function useI18n() {
  return { locale, t, setLocale }
}

/* ------------------------------------------------------------
 * 文档标题（随语言变化）
 * ---------------------------------------------------------- */

/** 路由 name → 标题词条 */
const ROUTE_TITLE_KEYS: Record<string, string> = {
  home: 'route.home',
  login: 'route.login',
  scenic: 'route.scenic',
  'scenic-detail': 'route.scenicDetail',
  culture: 'route.culture',
  'culture-detail': 'route.cultureDetail',
  assistant: 'route.assistant',
  profile: 'route.profile',
}

/** 记住最近一次的路由名，便于切换语言时重写标题 */
let lastRouteName = ''

/**
 * 应用文档标题（路由跳转与语言切换时都调用）
 */
export function applyTitle(routeName: string) {
  if (routeName) {
    lastRouteName = routeName
  }
  const key = ROUTE_TITLE_KEYS[lastRouteName]
  const page = key ? t(key) : ''
  document.title = page ? `${page} · ${t('app.name')}` : `${t('app.name')} · ${t('app.tagline')}`
}

// 切换语言时自动重写标题
watch(locale, () => applyTitle(lastRouteName))
