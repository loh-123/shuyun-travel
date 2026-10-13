<script setup lang="ts">
/**
 * 右下角「蜀韵管家」智能体入口（AiFab · 3D 版）
 *
 * 原生 Three.js，程序化建模，零外部资源：
 * - 建模：白团脸 + 黑眼圈 + 立耳 + 圆滚身形的熊猫，身后悬一轮旋转的鎏金藻井光环
 * - 动效：漂浮呼吸 + 周期性「活蹦乱跳」（抛物线起跳 + 落地挤压拉伸 + 手臂摆动）、
 *   随指针转头、周期性眨眼、光环旋转
 * - 交互：点击打开浮层对话窗（AiChatPopup）；可自由拖动，松手吸附到最近的左右边缘
 * - 首次气泡：加载 3s 后自动弹出欢迎语、5s 后消失，每会话一次（localStorage）
 * - 性能：像素比封顶 2、标签页隐藏暂停、prefers-reduced-motion 静态帧、卸载时释放资源
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
// 仅类型导入：编译期擦除，不会把 three 打进首屏
import type * as THREE from 'three'
import AiChatPopup from '@/components/common/AiChatPopup.vue'

type ThreeModule = typeof import('three')

const props = withDefaults(defineProps<{ size?: number }>(), { size: 160 })

/** 距视口边缘的安全间距 */
const GAP = 20
/** 判定「点击」而非「拖动」的位移阈值（px） */
const CLICK_TOLERANCE = 5
/** 一次蹦跳的时长（秒） */
const HOP_DUR = 0.62
/** 首次欢迎气泡的 localStorage key */
const WELCOME_KEY = 'shuyun_ai_welcome_shown'

const el = ref<HTMLButtonElement | null>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)

/* ---------------- 拖拽 / 吸附 / 气泡 ---------------- */
const x = ref(0)
const y = ref(0)
const ready = ref(false)
const animate = ref(false)
const dragging = ref(false)
const hovering = ref(false)
/** 浮层对话窗开关 */
const chatOpen = ref(false)
/** 引导气泡模式：'' 隐藏 / 'hover' 悬停文案 / 'welcome' 首次欢迎语 */
const bubbleMode = ref<'' | 'hover' | 'welcome'>('')
const bubbleOpen = computed(() => bubbleMode.value !== '')
/** 视口宽度（响应式，用于窄屏收缩） */
const vw = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)

/** 图标边长：默认 props.size，窄屏按视口收缩，避免遮挡内容 */
const size = computed(() => Math.min(props.size, Math.round(vw.value * 0.36)))
/** 气泡朝向：靠右停靠时气泡在左侧，靠左停靠时气泡在右侧 */
const onLeft = computed(() => x.value + size.value / 2 < vw.value / 2)

let startX = 0
let startY = 0
let originX = 0
let originY = 0
let moved = false
/** 用户是否手动拖动过 */
let userAdjusted = false

let welcomeTimer: number | undefined
let welcomeHideTimer: number | undefined

function clampY(v: number) {
  const max = Math.max(GAP, window.innerHeight - size.value - GAP)
  return Math.min(Math.max(v, GAP), max)
}

/** 默认位置：右下角 */
function resetPosition() {
  y.value = window.innerHeight - size.value - GAP
  x.value = window.innerWidth - size.value - GAP
}

/** 吸附到最近的一侧边缘（横向），纵向夹紧 */
function snapToNearestEdge() {
  const centerX = x.value + size.value / 2
  x.value = centerX < window.innerWidth / 2 ? GAP : window.innerWidth - size.value - GAP
  y.value = clampY(y.value)
}

function onPointerDown(e: PointerEvent) {
  if (e.pointerType === 'mouse' && e.button !== 0) {
    return
  }
  dragging.value = true
  moved = false
  startX = e.clientX
  startY = e.clientY
  originX = x.value
  originY = y.value
  el.value?.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) {
    return
  }
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  if (!moved && Math.hypot(dx, dy) > CLICK_TOLERANCE) {
    moved = true
  }
  x.value = Math.min(Math.max(originX + dx, GAP), window.innerWidth - size.value - GAP)
  y.value = clampY(originY + dy)
}

function onPointerUp(e: PointerEvent) {
  if (!dragging.value) {
    return
  }
  dragging.value = false
  if (el.value?.hasPointerCapture(e.pointerId)) {
    el.value.releasePointerCapture(e.pointerId)
  }
  // 拖动过才吸附；单纯点击保持原位
  if (moved) {
    userAdjusted = true
    snapToNearestEdge()
  }
}

/** 点击：打开 / 关闭浮层对话窗（拖动不触发） */
function onClick() {
  if (moved) {
    return
  }
  chatOpen.value = !chatOpen.value
  bubbleMode.value = ''
}

/** 鼠标移入：显示悬停气泡 */
function onEnter() {
  hovering.value = true
  if (!chatOpen.value) {
    bubbleMode.value = 'hover'
  }
}

/** 鼠标移出：收起悬停气泡 */
function onLeave() {
  hovering.value = false
  if (bubbleMode.value === 'hover') {
    bubbleMode.value = ''
  }
}

function onResize() {
  vw.value = window.innerWidth
  if (!userAdjusted) {
    resetPosition()
  } else {
    snapToNearestEdge()
  }
}

/** 首次欢迎气泡：3s 后弹出，5s 后消失，每会话一次（localStorage） */
function scheduleWelcome() {
  if (localStorage.getItem(WELCOME_KEY)) {
    return
  }
  welcomeTimer = window.setTimeout(() => {
    if (chatOpen.value || hovering.value) {
      return
    }
    bubbleMode.value = 'welcome'
    localStorage.setItem(WELCOME_KEY, '1')
    welcomeHideTimer = window.setTimeout(() => {
      if (bubbleMode.value === 'welcome') {
        bubbleMode.value = ''
      }
    }, 5000)
  }, 3000)
}

/* ---------------- Three.js 场景 ---------------- */
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let panda: THREE.Group | null = null
let head: THREE.Group | null = null
let halo: THREE.Group | null = null
let glowMat: THREE.MeshBasicMaterial | null = null
let glowTex: THREE.CanvasTexture | null = null
const eyes: THREE.Mesh[] = []
const arms: THREE.Group[] = []

let rafId = 0
let disposed = false
let paused = false
let lastTime = 0
let reduceMotion = false

/** 随指针转头的目标角度（运行时按阻尼逼近） */
let targetRotY = 0
let targetRotX = 0

/** 眨眼状态机 */
let nextBlinkAt = 2.6
let blinkStart = -1

/** 蹦跳状态机 */
let hopT = -1
let nextHopAt = 1.2

/** 程序化柔光贴图（canvas 生成，零外部资源） */
function makeGlowTexture(T: ThreeModule) {
  const s = 256
  const c = document.createElement('canvas')
  c.width = s
  c.height = s
  const ctx = c.getContext('2d')!
  const grad = ctx.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
  grad.addColorStop(0, 'rgba(255,224,150,0.95)')
  grad.addColorStop(0.42, 'rgba(255,196,96,0.34)')
  grad.addColorStop(1, 'rgba(255,180,80,0)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, s, s)
  const tex = new T.CanvasTexture(c)
  tex.colorSpace = T.SRGBColorSpace
  return tex
}

/** 鎏金材质（藻井光环用） */
function goldMat(T: ThreeModule) {
  return new T.MeshStandardMaterial({
    color: 0xd4b374,
    metalness: 1,
    roughness: 0.26,
    emissive: 0x6b4a12,
    emissiveIntensity: 0.55,
  })
}

/** 鎏金藻井光环：双环 + 12 道辐条 + 12 颗宝珠 */
function buildHalo(T: ThreeModule) {
  const g = new T.Group()
  const mat = goldMat(T)

  g.add(new T.Mesh(new T.TorusGeometry(1.86, 0.055, 10, 72), mat))
  g.add(new T.Mesh(new T.TorusGeometry(1.2, 0.04, 10, 56), mat))

  const spokeGeo = new T.BoxGeometry(0.055, 0.62, 0.055)
  const beadGeo = new T.SphereGeometry(0.075, 12, 12)
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2

    const spoke = new T.Mesh(spokeGeo, mat)
    spoke.position.set(Math.cos(a) * 1.52, Math.sin(a) * 1.52, 0)
    spoke.rotation.z = a - Math.PI / 2
    g.add(spoke)

    const bead = new T.Mesh(beadGeo, mat)
    bead.position.set(Math.cos(a) * 2.02, Math.sin(a) * 2.02, 0)
    g.add(bead)
  }
  return g
}

/** 拟人化熊猫：白团脸 + 黑眼圈 + 立耳 + 圆滚身形；手臂挂在肩关节下便于摆动 */
function buildPanda(T: ThreeModule) {
  const root = new T.Group()
  const white = new T.MeshStandardMaterial({ color: 0xf8f5ec, roughness: 0.66, metalness: 0.04 })
  const black = new T.MeshStandardMaterial({ color: 0x232a33, roughness: 0.5, metalness: 0.08 })
  const ink = new T.MeshStandardMaterial({ color: 0x11161d, roughness: 0.42 })

  // ---- 身体（圆滚）----
  const body = new T.Mesh(new T.SphereGeometry(1.0, 32, 24), white)
  body.position.y = -1.08
  body.scale.set(1, 0.94, 0.9)
  root.add(body)

  // ---- 手臂：肩关节 Group + 偏移手掌，rotation 才有摆动效果 ----
  const armGeo = new T.SphereGeometry(0.3, 20, 16)
  for (const sx of [-1, 1]) {
    const shoulder = new T.Group()
    shoulder.position.set(0.8 * sx, -0.66, 0.3)
    const hand = new T.Mesh(armGeo, black)
    hand.position.set(0.1 * sx, -0.34, 0)
    shoulder.add(hand)
    root.add(shoulder)
    arms.push(shoulder)
  }

  // ---- 脚 ----
  const footGeo = new T.SphereGeometry(0.32, 20, 16)
  for (const sx of [-1, 1]) {
    const foot = new T.Mesh(footGeo, black)
    foot.position.set(0.42 * sx, -1.86, 0.28)
    foot.scale.set(1, 0.78, 1.1)
    root.add(foot)
  }

  // ---- 头部（单独成组，便于随指针转头）----
  const h = new T.Group()
  h.position.y = 0.32
  root.add(h)

  const skull = new T.Mesh(new T.SphereGeometry(1.16, 36, 28), white)
  skull.scale.set(1, 0.96, 1)
  h.add(skull)

  // 立耳
  const earGeo = new T.SphereGeometry(0.36, 20, 16)
  for (const sx of [-1, 1]) {
    const ear = new T.Mesh(earGeo, black)
    ear.position.set(0.78 * sx, 1.02, -0.1)
    ear.scale.set(1, 1, 0.55)
    h.add(ear)
  }

  // 黑眼圈（斜向椭圆，贴脸）
  const patchGeo = new T.SphereGeometry(0.36, 20, 16)
  for (const sx of [-1, 1]) {
    const patch = new T.Mesh(patchGeo, black)
    patch.position.set(0.45 * sx, 0.2, 0.94)
    patch.scale.set(1, 1.16, 0.34)
    patch.rotation.set(-0.22, 0, 0.34 * sx)
    h.add(patch)
  }

  // 眼白（眨眼时纵向压扁）+ 瞳孔
  const eyeGeo = new T.SphereGeometry(0.115, 16, 16)
  const eyeMat = new T.MeshStandardMaterial({ color: 0xffffff, roughness: 0.3 })
  const pupilGeo = new T.SphereGeometry(0.062, 12, 12)
  for (const sx of [-1, 1]) {
    const eye = new T.Mesh(eyeGeo, eyeMat)
    eye.position.set(0.45 * sx, 0.24, 1.06)
    eye.scale.set(1, 1, 0.7)
    h.add(eye)
    eyes.push(eye)

    const pupil = new T.Mesh(pupilGeo, ink)
    pupil.position.set(0.45 * sx, 0.24, 1.14)
    h.add(pupil)
  }

  // 鼻子
  const nose = new T.Mesh(new T.SphereGeometry(0.14, 16, 16), ink)
  nose.position.set(0, -0.18, 1.12)
  nose.scale.set(1.25, 0.85, 0.7)
  h.add(nose)

  // 嘴（细扁的一条，近似微笑）
  const mouth = new T.Mesh(new T.BoxGeometry(0.02, 0.13, 0.02), ink)
  mouth.position.set(0, -0.42, 1.06)
  h.add(mouth)

  head = h
  return root
}

/* ---------------- 渲染循环 ---------------- */
function loop() {
  if (disposed) {
    return
  }
  rafId = requestAnimationFrame(loop)

  const r = renderer
  const sc = scene
  const cam = camera
  if (paused || !r || !sc || !cam) {
    return
  }

  const t = performance.now() / 1000
  const dt = Math.min(t - lastTime, 0.06)
  lastTime = t

  const boost = hovering.value || dragging.value || chatOpen.value
  const pd = panda
  const hd = head
  const hl = halo
  const gm = glowMat

  /* ---- 活蹦乱跳：周期性抛物线起跳 + 落地挤压拉伸 ---- */
  let air = 0
  let hopY = 0
  if (hopT < 0 && t >= nextHopAt) {
    hopT = 0
  }
  if (hopT >= 0) {
    hopT += dt
    const p = hopT / HOP_DUR
    if (p >= 1) {
      hopT = -1
      nextHopAt = t + 0.85 + Math.random() * 1.25
    } else {
      air = Math.sin(Math.PI * p)
      hopY = air * 0.5
    }
  }

  const ground = 1 - air
  // 空中拉长、落地压扁（squash & stretch）
  const scaleY = 1 + air * 0.17 - ground * 0.06
  const scaleXZ = 1 - air * 0.12 + ground * 0.05

  if (pd) {
    pd.position.y = Math.sin(t * 1.6) * 0.035 + hopY
    pd.scale.set(scaleXZ, scaleY, scaleXZ)
    pd.rotation.z = Math.sin(t * 1.1) * 0.03
  }

  // 手臂：待机轻摆 + 起跳时上举
  arms.forEach((a, i) => {
    const dir = i === 0 ? 1 : -1
    a.rotation.z = dir * (Math.sin(t * 2.2) * 0.14 + air * 0.95)
    a.rotation.x = air * 0.3
  })

  // 随指针转头（阻尼逼近目标角），跳跃时带一点点头
  if (hd) {
    const k = Math.min(1, dt * 7)
    hd.rotation.y += (targetRotY - hd.rotation.y) * k
    hd.rotation.x += (targetRotX + air * 0.16 - hd.rotation.x) * k
    hd.rotation.z = Math.sin(t * 1.3) * 0.02
  }

  // 周期性眨眼
  if (eyes.length) {
    if (blinkStart < 0 && t >= nextBlinkAt) {
      blinkStart = t
    }
    let k = 0
    if (blinkStart >= 0) {
      const e = t - blinkStart
      if (e >= 0.24) {
        blinkStart = -1
        nextBlinkAt = t + 2.2 + Math.random() * 2.4
      } else {
        k = e < 0.12 ? e / 0.12 : 1 - (e - 0.12) / 0.12
      }
    }
    const sy = 1 - k * 0.92
    eyes.forEach((m) => m.scale.set(1, sy, 0.7))
  }

  // 藻井光环旋转（hover / 拖动 / 对话窗打开时提速）
  if (hl) {
    hl.rotation.z += dt * (boost ? 1.7 : 0.42)
  }
  // 辉光增强
  if (gm) {
    gm.opacity += ((boost ? 0.95 : 0.62) - gm.opacity) * Math.min(1, dt * 5)
  }

  r.render(sc, cam)
}

async function initThree() {
  const canvas = canvasEl.value
  if (!canvas) {
    return
  }
  // three 走动态 import：不进首屏主包；加载完成前先显示 CSS 底盘
  const T = await import('three')
  if (disposed) {
    return
  }

  const s = size.value
  const r = new T.WebGLRenderer({ canvas, alpha: true, antialias: true })
  // 性能：像素比封顶 2
  r.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  r.setSize(s, s, false)

  const sc = new T.Scene()
  const cam = new T.PerspectiveCamera(30, 1, 0.1, 100)
  cam.position.set(0, -0.1, 9.6)
  cam.lookAt(0, -0.25, 0)

  // 灯光：环境 + 主光 + 暖色轮廓光（衬托鎏金）
  sc.add(new T.AmbientLight(0xffffff, 1.1))
  const key = new T.DirectionalLight(0xfff3dc, 1.55)
  key.position.set(3.2, 4.2, 6)
  sc.add(key)
  const rim = new T.PointLight(0xffcf82, 14, 26)
  rim.position.set(-3.4, -1.6, 4.2)
  sc.add(rim)

  // 柔光（程序化贴图 + 叠加混合）
  const gt = makeGlowTexture(T)
  const gm = new T.MeshBasicMaterial({
    map: gt,
    transparent: true,
    opacity: 0.62,
    blending: T.AdditiveBlending,
    depthWrite: false,
  })
  const glow = new T.Mesh(new T.PlaneGeometry(6.4, 6.4), gm)
  glow.position.z = -2.6
  sc.add(glow)

  // 鎏金藻井光环
  const hl = buildHalo(T)
  hl.position.z = -1.7
  sc.add(hl)

  // 熊猫
  const pd = buildPanda(T)
  sc.add(pd)

  renderer = r
  scene = sc
  camera = cam
  glowTex = gt
  glowMat = gm
  halo = hl
  panda = pd

  // prefers-reduced-motion：只渲染一帧静态画面
  reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  r.render(sc, cam)
  if (!reduceMotion) {
    lastTime = performance.now() / 1000
    loop()
  }
}

/** 指针移动：记录目标转头角度 */
function onWindowPointerMove(e: PointerEvent) {
  const nx = e.clientX / window.innerWidth - 0.5
  const ny = e.clientY / window.innerHeight - 0.5
  targetRotY = nx * 0.7
  targetRotX = ny * 0.4
}

/** 标签页隐藏时暂停渲染 */
function onVisibility() {
  paused = document.hidden
  if (!paused) {
    lastTime = performance.now() / 1000
  }
}

/** 释放几何体 / 材质 / 贴图（避免切页后显存泄漏） */
function disposeScene() {
  const sc = scene
  if (sc) {
    sc.traverse((o) => {
      const mesh = o as THREE.Mesh
      mesh.geometry?.dispose()
      const mat = mesh.material as THREE.Material | THREE.Material[] | undefined
      if (Array.isArray(mat)) {
        mat.forEach((m) => m.dispose())
      } else {
        mat?.dispose()
      }
    })
    sc.clear()
  }
  scene = null
  glowTex?.dispose()
  glowTex = null
  glowMat = null
  renderer?.dispose()
  renderer = null
  camera = null
  panda = null
  head = null
  halo = null
  eyes.length = 0
  arms.length = 0
}

onMounted(() => {
  resetPosition()
  window.addEventListener('resize', onResize)
  window.addEventListener('pointermove', onWindowPointerMove, { passive: true })
  document.addEventListener('visibilitychange', onVisibility)

  // 首帧显示；再下一帧才开位移过渡，避免从 (0,0) 飞入
  requestAnimationFrame(() => {
    ready.value = true
    requestAnimationFrame(() => {
      animate.value = true
    })
  })

  void initThree()
  scheduleWelcome()
})

onBeforeUnmount(() => {
  disposed = true
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
  window.removeEventListener('resize', onResize)
  window.removeEventListener('pointermove', onWindowPointerMove)
  document.removeEventListener('visibilitychange', onVisibility)
  window.clearTimeout(welcomeTimer)
  window.clearTimeout(welcomeHideTimer)
  disposeScene()
})

const style = computed(() => ({
  width: `${size.value}px`,
  height: `${size.value}px`,
  transform: `translate3d(${x.value}px, ${y.value}px, 0)`,
}))
</script>

<template>
  <button
    ref="el"
    type="button"
    class="ai-fab"
    :class="{ 'is-ready': ready, 'is-animate': animate, 'is-dragging': dragging, 'is-open': chatOpen }"
    :style="style"
    title="蜀韵管家 · 智能问答"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @click="onClick"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
  >
    <span class="ai-fab__tilt">
      <!-- 金色底盘：three 加载完成前先有视觉，加载后作为衬托 -->
      <span class="ai-fab__plate" aria-hidden="true" />
      <!-- 3D 舞台：熊猫 + 鎏金藻井光环 -->
      <canvas ref="canvasEl" class="ai-fab__canvas" aria-hidden="true" />

      <!-- 引导气泡 -->
      <span
        v-if="bubbleOpen"
        class="ai-fab__bubble"
        :class="{ 'is-left': onLeft, 'is-welcome': bubbleMode === 'welcome' }"
      >
        <template v-if="bubbleMode === 'welcome'">
          <em>你好，我是蜀韵管家，可以问我关于四川的任何问题～</em>
        </template>
        <template v-else>
          <b>你好呀，我是蜀韵管家</b>
          <em>一个懂巴蜀文化的 AI 旅行智能体</em>
          <i>点我开始对话 →</i>
          <span class="ai-fab__tags">
            <span class="ai-fab__tag">智能导游</span>
            <span class="ai-fab__tag">文化推广</span>
            <span class="ai-fab__tag">旅游规划</span>
            <span class="ai-fab__tag">非遗讲解</span>
          </span>
        </template>
      </span>
    </span>
  </button>

  <!-- 浮层对话窗（右下角，复用 SSE 流式逻辑） -->
  <AiChatPopup :open="chatOpen" @close="chatOpen = false" />
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.ai-fab {
  position: fixed;
  top: 0;
  left: 0;
  z-index: $z-header + 30;
  padding: 0;
  border: none;
  background: none;
  opacity: 0;
  cursor: grab;
  // 拖拽时不被页面滚动劫持
  touch-action: none;
  user-select: none;
  will-change: transform;
  transition: opacity 0.45s $ease-out-expo;

  &.is-ready {
    opacity: 1;
  }

  // 首帧之后再开启位移过渡（吸附动画）
  &.is-animate {
    transition:
      transform 0.38s $ease-out-expo,
      opacity 0.45s $ease-out-expo;
  }

  // 拖动中关掉过渡，保证跟手
  &.is-dragging {
    transition: none;
    cursor: grabbing;
  }
}

.ai-fab__tilt {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform 0.4s $ease-out-expo;
}

/* hover：立体倾斜放大（对话窗打开时不倾斜） */
.ai-fab:hover:not(.is-open) .ai-fab__tilt {
  transform: perspective(640px) rotateX(6deg) rotateY(-6deg) scale(1.05);
}

/* 金色底盘（深色玻璃 + 金环，衬托熊猫与鎏金光环） */
.ai-fab__plate {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 38% 28%, rgba(32, 66, 102, 0.94), rgba(8, 22, 36, 0.97));
  border: 1.5px solid rgba(212, 179, 116, 0.85);
  box-shadow:
    inset 0 0 18px rgba(255, 214, 122, 0.2),
    0 10px 26px rgba(0, 0, 0, 0.42),
    0 0 28px rgba(255, 214, 122, 0.3);
}

.ai-fab__canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

/* ---------------- 引导气泡 ---------------- */
.ai-fab__bubble {
  position: absolute;
  top: 50%;
  right: calc(100% + 26px);
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: max-content;
  max-width: 400px;
  padding: 22px 28px;
  border: 1.5px solid rgba(212, 179, 116, 0.8);
  border-radius: 26px;
  background: rgba(var(--sy-surface-rgb), 0.98);
  box-shadow:
    0 22px 50px rgba(14, 42, 71, 0.24),
    0 0 34px rgba(212, 179, 116, 0.32);
  text-align: left;
  transform: translate(0, -50%);
  transform-origin: right center;
  pointer-events: none;
  animation: aiFabBubbleIn 0.4s $ease-out-expo;

  b {
    font-family: $font-title;
    font-size: 20px;
    font-weight: 900;
    letter-spacing: 0.16em;
    color: var(--sy-accent);
  }

  em {
    font-style: normal;
    font-family: $font-body;
    font-size: 22px;
    line-height: 1.5;
    color: rgba(var(--sy-text-rgb), 0.82);
  }

  i {
    margin-top: 3px;
    font-style: normal;
    font-family: $font-body;
    font-size: 20px;
    letter-spacing: 0.08em;
    color: $color-gold;
  }

  /* 能力词条胶囊 */
  .ai-fab__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  .ai-fab__tag {
    padding: 4px 12px;
    border: 1px solid rgba(212, 179, 116, 0.6);
    border-radius: 999px;
    background: rgba(212, 179, 116, 0.1);
    font-family: $font-body;
    font-size: 16px;
    letter-spacing: 0.06em;
    color: var(--sy-accent);
  }

  /* 指向熊猫的圆润尾巴 */
  &::after {
    content: '';
    position: absolute;
    top: 50%;
    right: -9px;
    width: 18px;
    height: 18px;
    margin-top: -9px;
    background: rgba(var(--sy-surface-rgb), 0.98);
    border-top: 1.5px solid rgba(212, 179, 116, 0.8);
    border-right: 1.5px solid rgba(212, 179, 116, 0.8);
    border-top-right-radius: 6px;
    transform: rotate(45deg);
  }
}

/* 首次欢迎气泡：更紧凑的宽度 */
.ai-fab__bubble.is-welcome {
  max-width: 320px;
  padding: 18px 22px;

  em {
    font-size: 20px;
  }
}

/* 熊猫停靠在左侧时，气泡翻到右边 */
.ai-fab__bubble.is-left {
  right: auto;
  left: calc(100% + 26px);
  transform: translate(0, -50%);
  transform-origin: left center;

  &::after {
    right: auto;
    left: -9px;
    border-top: none;
    border-right: none;
    border-bottom: 1.5px solid rgba(212, 179, 116, 0.8);
    border-left: 1.5px solid rgba(212, 179, 116, 0.8);
    border-top-right-radius: 0;
    border-bottom-left-radius: 6px;
  }
}

@keyframes aiFabBubbleIn {
  from {
    opacity: 0;
    transform: translate(16px, -50%) scale(0.94);
  }
  to {
    opacity: 1;
    transform: translate(0, -50%) scale(1);
  }
}

/* 窄屏：气泡会溢出，直接不显示（点击熊猫即可对话） */
@media (max-width: 640px) {
  .ai-fab__bubble {
    display: none;
  }
}
</style>
