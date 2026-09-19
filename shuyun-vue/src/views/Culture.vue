<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

// 仅用于演示路由跳转，正式列表页后续接入 /api/culture/list
const demo = [
  { id: 1, name: '川剧变脸' },
  { id: 2, name: '蜀绣' },
  { id: 3, name: '川菜' },
]

function goDetail(id: number) {
  router.push(`/culture/${id}`)
}
</script>

<template>
  <section class="page">
    <h1 class="page__title">文化寻踪</h1>
    <p class="page__desc">
      页面待开发：这里将展示 8 大文化条目，支持按分类筛选，数据来自 /api/culture/list。
    </p>
    <div class="page__actions">
      <el-button v-for="d in demo" :key="d.id" round @click="goDetail(d.id)">
        查看示例：{{ d.name }}
      </el-button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 64px 24px;
  text-align: center;

  &__title {
    margin-bottom: 14px;
    font-size: 40px;
    letter-spacing: 8px;
    color: var(--color-primary);
  }

  &__desc {
    color: #6b5b52;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 12px;
    margin-top: 28px;
  }
}
</style>
