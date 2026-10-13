<script setup lang="ts">
/**
 * 传承人列表（Persons）
 *
 * 展示全部非遗传承人（数据来自 /api/person/all），点击卡片进入传承人详情。
 * 用 PersonCards 统一渲染，自带「点击卡片可查看详情」提醒语。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import PersonCards from '@/components/common/PersonCards.vue'
import StateBlock from '@/components/common/StateBlock.vue'
import { listAllPerson } from '@/api/person'

/** 传承人条目（与 PersonCards 结构兼容） */
interface Person {
  id?: number | null
  name: string
  avatar?: string | null
  brief?: string
  cultureName?: string
}

const router = useRouter()

const loading = ref(true)
const failed = ref(false)
const persons = ref<Person[]>([])

/** 拉取全部传承人（上限放宽，一次拿全） */
async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listAllPerson(200)
    persons.value = (Array.isArray(data) ? data : []).map((p) => ({
      id: p.id,
      name: p.name,
      avatar: p.avatar,
      brief: p.brief || p.cultureName || '非遗传承人',
    }))
  } catch {
    failed.value = true
    persons.value = []
  } finally {
    loading.value = false
  }
}

function goHome() {
  router.push('/')
}

onMounted(load)
</script>

<template>
  <section class="ps-page">
    <!-- Hero -->
    <header class="ps-hero">
      <div class="sy-container ps-hero__inner">
        <nav class="ps-hero__crumb">
          <a href="javascript:;" @click="goHome">首页</a>
          <span class="ps-hero__sep">/</span>
          <span>传承人</span>
        </nav>
        <h1 class="ps-hero__title">川蜀传承人</h1>
        <p class="ps-hero__sub">匠心不灭 · 一门技艺，一生坚守</p>
      </div>
    </header>

    <!-- 列表 -->
    <div class="sy-container ps-body">
      <div class="ps-body__head">
        <p class="sy-eyebrow">Inheritors</p>
        <h2 class="sy-page-title">川蜀传承人</h2>
        <p class="sy-page-desc">
          共 {{ persons.length }} 位传承人，守护着巴蜀的技艺与记忆。点击卡片可查看其详情。
        </p>
      </div>

      <StateBlock v-if="loading" state="loading" />
      <StateBlock v-else-if="failed" state="error" :retry="load" />

      <template v-else>
        <PersonCards :persons="persons" hint="点击卡片可查看传承人详情" />
        <StateBlock v-if="!persons.length" state="empty" empty-text="暂无传承人数据" />
      </template>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.ps-page {
  background: $color-paper;
}

/* ---------------- Hero ---------------- */
.ps-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: $hero-list-height;
  overflow: hidden;
  isolation: isolate;
  background:
    radial-gradient(120% 130% at 80% 0%, rgba(212, 179, 116, 0.18) 0%, rgba(14, 42, 71, 0) 60%),
    linear-gradient(120deg, $color-ink 0%, #16324f 55%, #1d2436 100%);
}

.ps-hero__inner {
  position: relative;
  z-index: $z-content;
  width: 100%;
  padding-block: clamp(72px, 12vh, 150px) clamp(36px, 6vh, 72px);
}

.ps-hero__crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-body;
  font-size: $fs-small;
  letter-spacing: 0.16em;
  color: rgba(245, 241, 232, 0.78);

  a:hover {
    color: $color-gold;
  }
}

.ps-hero__sep {
  color: $color-gold;
}

.ps-hero__title {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-title;
  font-weight: 900;
  font-size: $fs-hero-xl;
  line-height: 1;
  letter-spacing: -0.02em;
  color: $color-text-light;
  text-shadow: $shadow-text;
}

.ps-hero__sub {
  margin-top: clamp(14px, 2vh, 26px);
  font-family: $font-body;
  font-size: clamp(15px, 1.4vw, 22px);
  letter-spacing: 0.36em;
  color: rgba(245, 241, 232, 0.88);
  text-shadow: $shadow-text-soft;
}

/* ---------------- 列表 ---------------- */
.ps-body {
  padding-block: clamp(44px, 6vw, 96px) clamp(64px, 8vw, 120px);
}

.ps-body__head {
  margin-bottom: clamp(24px, 3vw, 44px);
}

.ps-body__head .sy-page-title {
  margin-top: 6px;
}

/* ============================================================
 * 字号 ×1.5 + 卡片 ×1.5（仅本页；不影响其它页面的传承人卡片）
 * ========================================================== */
.ps-hero__title {
  font-size: clamp(94px, 15.6vw, 273px);
}

.ps-hero__sub {
  font-size: clamp(29px, 2.73vw, 43px);
}

.ps-body__head .sy-eyebrow {
  font-size: clamp(23px, 1.95vw, 31px);
}

.ps-body__head .sy-page-title {
  font-size: clamp(55px, 5.85vw, 86px);
}

.ps-body__head .sy-page-desc {
  font-size: clamp(27px, 1.95vw, 35px);
  line-height: 1.8;
}

/* 卡片：更大的头像 / 字号 / 间距，网格自适应更少列 */
.ps-body :deep(.person-cards) {
  gap: 21px;
}

.ps-body :deep(.person-cards__hint) {
  font-size: clamp(21px, 1.43vw, 26px);
}

.ps-body :deep(.person-cards__hint-dot) {
  width: 12px;
  height: 12px;
}

.ps-body :deep(.person-cards__grid) {
  grid-template-columns: repeat(auto-fill, minmax(442px, 1fr));
  gap: 23px;
}

.ps-body :deep(.person-cards__item) {
  gap: 23px;
  padding: 23px 26px;
}

.ps-body :deep(.person-cards__avatar) {
  width: 101px;
  height: 101px;
}

.ps-body :deep(.person-cards__avatar--char) {
  font-size: 43px;
}

.ps-body :deep(.person-cards__name) {
  font-size: clamp(26px, 2.21vw, 34px);
}

.ps-body :deep(.person-cards__brief) {
  font-size: clamp(20px, 1.43vw, 23px);
  line-height: 1.6;
}

.ps-body :deep(.person-cards__go) {
  font-size: 39px;
}
</style>
