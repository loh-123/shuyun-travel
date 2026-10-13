<script setup lang="ts">
/**
 * 微光粒子背景（ParticleField）
 *
 * 原生 Canvas 粒子系统：30 个金色小点缓慢漂浮 + 闪烁。
 * 固定铺满全屏，pointer-events: none，不干扰内容交互。
 */
import { onMounted, onUnmounted, ref } from 'vue'

/** 粒子数量 */
const COUNT = 30

const canvasEl = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let raf = 0
let width = 0
let height = 0

/** 单个粒子 */
interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  r: number
  phase: number
}

const particles: Particle[] = []

/** 初始化粒子 */
function initParticles() {
  particles.length = 0
  for (let i = 0; i < COUNT; i++) {
    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25,
      r: 1 + Math.random() * 2,
      phase: Math.random() * Math.PI * 2,
    })
  }
}

/** 尺寸适配（含高 DPI） */
function resize() {
  const canvas = canvasEl.value
  if (!canvas) return
  width = canvas.clientWidth
  height = canvas.clientHeight
  const dpr = window.devicePixelRatio || 1
  canvas.width = width * dpr
  canvas.height = height * dpr
  ctx = canvas.getContext('2d')
  ctx?.scale(dpr, dpr)
}

/** 逐帧绘制 */
function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  for (const p of particles) {
    p.x += p.vx
    p.y += p.vy
    p.phase += 0.02
    if (p.x < -10) p.x = width + 10
    if (p.x > width + 10) p.x = -10
    if (p.y < -10) p.y = height + 10
    if (p.y > height + 10) p.y = -10

    const alpha = 0.25 + Math.abs(Math.sin(p.phase)) * 0.6
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(212, 179, 116, ${alpha})`
    ctx.shadowBlur = 8
    ctx.shadowColor = 'rgba(212, 179, 116, 0.7)'
    ctx.fill()
  }
  raf = requestAnimationFrame(draw)
}

onMounted(() => {
  resize()
  initParticles()
  raf = requestAnimationFrame(draw)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', resize)
})
</script>

<template>
  <canvas ref="canvasEl" class="pf" />
</template>

<style scoped>
.pf {
  position: fixed;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
