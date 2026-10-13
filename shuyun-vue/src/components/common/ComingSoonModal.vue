<script setup lang="ts">
/**
 * 通用「即将上线」提示弹窗（ComingSoonModal）
 *
 * 用于第三方登录等尚未开放能力的占位提示：
 * - 遮罩：半透明墨色 + 磨砂玻璃（backdrop-filter blur）
 * - 卡片：白色底 + 1px 蜀锦红边框 + 直角（无圆角、无阴影）
 * - 底部「关闭」按钮沿用深红直角风格
 *
 * 用法：
 * <ComingSoonModal v-model="visible" title="微信扫码登录" content="……" />
 */
import { onBeforeUnmount, watch } from 'vue'
import { t } from '@/i18n'

const props = defineProps<{
  /** 是否显示（配合 v-model 使用） */
  modelValue: boolean
  /** 弹窗标题 */
  title?: string
  /** 弹窗正文 */
  content?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

/** 关闭弹窗 */
function close() {
  emit('update:modelValue', false)
}

/** 锁定 / 释放 body 滚动，避免弹窗打开时背景可滚动 */
function lockScroll(lock: boolean) {
  document.body.style.overflow = lock ? 'hidden' : ''
}

/** 支持 Esc 关闭 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue) close()
}

/** 显示状态变化：接管滚动锁与键盘监听 */
watch(
  () => props.modelValue,
  (visible) => {
    lockScroll(visible)
    if (visible) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
)

onBeforeUnmount(() => {
  lockScroll(false)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="csm">
      <div v-if="modelValue" class="csm" @click.self="close">
        <div class="csm__card" role="dialog" aria-modal="true" :aria-label="title">
          <h3 class="csm__title">{{ title }}</h3>
          <p class="csm__content">{{ content }}</p>
          <button type="button" class="csm__close" @click="close">{{ t('common.close') }}</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

/* ---------------- 极简配色 ---------------- */
$_red: #8c1f28;
$_red-dark: #5a1018;

.csm {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  /* 磨砂玻璃遮罩 */
  background: rgba(26, 26, 26, 0.38);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.csm__card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px 28px;
  border: 1px solid $_red;
  border-radius: 0;
  background: #ffffff;
  text-align: center;
}

.csm__title {
  font-family: $font-title;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: $_red;
}

.csm__content {
  margin-top: 18px;
  font-family: $font-body;
  font-size: 15px;
  line-height: 1.8;
  color: #4b5563;
}

.csm__close {
  width: 100%;
  height: 48px;
  margin-top: 28px;
  border: none;
  border-radius: 0;
  background: $_red;
  font-family: $font-body;
  font-size: 15px;
  letter-spacing: 0.2em;
  color: #ffffff;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover {
    background: $_red-dark;
  }
}

/* ---------------- 过渡动画 ---------------- */
.csm-enter-active,
.csm-leave-active {
  transition: opacity 0.2s ease;
}

.csm-enter-from,
.csm-leave-to {
  opacity: 0;
}

.csm-enter-active .csm__card,
.csm-leave-active .csm__card {
  transition: transform 0.24s ease;
}

.csm-enter-from .csm__card,
.csm-leave-to .csm__card {
  transform: translateY(12px);
}
</style>
