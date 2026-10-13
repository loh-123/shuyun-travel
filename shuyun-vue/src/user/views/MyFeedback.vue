<script setup lang="ts">
/**
 * 用户后台 · 问题反馈
 *
 * 提交反馈并查看自己的历史反馈与处理状态。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import request from '@/api/request'
import { formatDateTime } from '@/user/utils/format'

interface FeedbackItem {
  id: number
  type?: string
  content?: string
  contact?: string
  status: number
  reply?: string
  createTime?: string
}

/** 反馈类型 */
const TYPES = ['功能异常', '体验建议', '内容纠错', '其他']

const type = ref(TYPES[0])
const content = ref('')
const contact = ref('')
const submitting = ref(false)

const list = ref<FeedbackItem[]>([])
const loading = ref(false)

const canSubmit = computed(() => content.value.trim().length > 0)

/** 加载我的反馈 */
async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/feedback', { silent: true, params: { page: 1, size: 10 } })
    list.value = res.data?.records ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 提交反馈 */
async function submitFeedback() {
  if (!canSubmit.value) {
    ElMessage.warning('请先填写反馈内容')
    return
  }
  submitting.value = true
  try {
    await request.post('/user/feedback', {
      type: type.value,
      content: content.value,
      contact: contact.value,
    })
    ElMessage.success('感谢反馈，我们已收到你的问题')
    content.value = ''
    contact.value = ''
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="fb">
    <!-- 提交表单 -->
    <div class="fb__card">
      <h3 class="fb__title">反馈一个问题</h3>
      <p class="fb__desc">你的反馈会帮助我们做得更好，我们通常在 1–2 个工作日内处理。</p>

      <label class="fb__label">问题类型</label>
      <div class="fb__types">
        <button
          v-for="t in TYPES"
          :key="t"
          type="button"
          class="fb__type"
          :class="{ 'is-on': type === t }"
          @click="type = t"
        >
          {{ t }}
        </button>
      </div>

      <label class="fb__label">具体描述</label>
      <textarea
        v-model="content"
        class="fb__text"
        rows="5"
        placeholder="请描述你遇到的问题或建议，越具体越好…"
      />

      <label class="fb__label">联系方式（可选）</label>
      <input v-model="contact" class="fb__input" type="text" placeholder="邮箱 / 手机号，便于我们回复你" />

      <button type="button" class="fb__submit" :disabled="!canSubmit || submitting" @click="submitFeedback">
        {{ submitting ? '提交中…' : '提交反馈' }}
      </button>
    </div>

    <!-- 我的反馈 -->
    <div class="fb__card">
      <h3 class="fb__title">我的反馈</h3>
      <p v-if="loading" class="fb__empty">加载中…</p>
      <p v-else-if="!list.length" class="fb__empty">还没有提交过反馈</p>

      <ul v-else class="fb__list">
        <li v-for="f in list" :key="f.id" class="fb__item">
          <div class="fb__item-head">
            <span class="fb__item-type">{{ f.type || '其他' }}</span>
            <span class="fb__item-status" :class="f.status === 1 ? 'is-done' : 'is-pending'">
              {{ f.status === 1 ? '已处理' : '待处理' }}
            </span>
            <span class="fb__item-time">{{ formatDateTime(f.createTime) }}</span>
          </div>
          <p class="fb__item-content">{{ f.content }}</p>
          <p v-if="f.reply" class="fb__item-reply">官方回复：{{ f.reply }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.fb {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(16px, 2vw, 26px);
  align-items: start;
}

.fb__card {
  padding: clamp(22px, 2.6vw, 34px);
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  box-shadow: $shadow-md;
}

.fb__title {
  font-family: $font-title;
  font-weight: 900;
  font-size: 20px;
  letter-spacing: 0.08em;
  color: var(--sy-accent);
}

.fb__desc {
  margin-top: 8px;
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.fb__label {
  display: block;
  margin: 20px 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(var(--sy-text-rgb), 0.7);
}

.fb__types {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.fb__type {
  padding: 8px 18px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-pill;
  background: transparent;
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.7);
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out-expo,
    color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(212, 179, 116, 0.16);
  }

  &.is-on {
    border-color: $color-gold;
    background: $color-primary;
    color: $color-gold;
  }
}

.fb__text,
.fb__input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.85);
  font-family: $font-body;
  font-size: 14px;
  color: $color-text-dark;
  outline: none;

  &:focus {
    border-color: $color-gold;
  }
}

.fb__text {
  resize: vertical;
}

.fb__submit {
  width: 100%;
  height: 48px;
  margin-top: 24px;
  border: none;
  border-radius: $radius-pill;
  background: linear-gradient(120deg, #b98f45, $color-gold 55%, #e3cd97);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.24em;
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
    opacity: 0.55;
    cursor: not-allowed;
  }
}

/* 我的反馈列表 */
.fb__empty {
  padding: 40px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.fb__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 0;
  margin: 16px 0 0;
  list-style: none;
}

.fb__item {
  padding: 14px 16px;
  border: 1px solid $color-border-gold-light;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.7);
}

.fb__item-head {
  display: flex;
  gap: 10px;
  align-items: center;
}

.fb__item-type {
  font-size: 13px;
  font-weight: 600;
  color: var(--sy-accent);
}

.fb__item-status {
  padding: 2px 10px;
  border-radius: $radius-pill;
  font-size: 12px;

  &.is-pending {
    background: rgba(122, 26, 34, 0.12);
    color: var(--sy-accent);
  }

  &.is-done {
    background: rgba(58, 90, 64, 0.16);
    color: $color-bamboo;
  }
}

.fb__item-time {
  margin-left: auto;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.4);
}

.fb__item-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(var(--sy-text-rgb), 0.72);
}

.fb__item-reply {
  margin-top: 8px;
  padding: 8px 12px;
  border-left: 3px solid $color-gold;
  border-radius: 0 $radius-sm $radius-sm 0;
  background: rgba(212, 179, 116, 0.12);
  font-size: 13px;
  color: rgba(var(--sy-text-rgb), 0.72);
}

@media (max-width: 1000px) {
  .fb {
    grid-template-columns: 1fr;
  }
}
</style>
