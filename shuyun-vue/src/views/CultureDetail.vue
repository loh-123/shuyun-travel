<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const cultureId = computed(() => route.params.id as string)
</script>

<template>
  <section class="page">
    <h1 class="page__title">文化详情</h1>
    <p class="page__meta">当前文化 ID：{{ cultureId }}</p>
    <p class="page__desc">
      页面待开发：将展示文化条目的名称、分类、封面、摘要、正文与标签，数据来自 /api/culture/{id}。
    </p>
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

  &__meta {
    margin-bottom: 10px;
    font-size: 15px;
    letter-spacing: 2px;
    color: var(--color-gold);
  }

  &__desc {
    color: #6b5b52;
  }
}
</style>
