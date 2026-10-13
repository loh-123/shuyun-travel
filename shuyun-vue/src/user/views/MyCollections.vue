<script setup lang="ts">
/**
 * 用户后台 · 我的收藏
 *
 * 展示当前用户收藏的景点，可取消收藏或进入详情。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import request from '@/api/request'

interface ScenicItem {
  id: number
  name: string
  cityName?: string
  type?: string
  coverImage?: string
  score?: number
  tags?: string
}

const router = useRouter()
const list = ref<ScenicItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/collection', { silent: true })
    list.value = res.data ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 取消收藏 */
async function remove(item: ScenicItem) {
  try {
    await ElMessageBox.confirm(`确定取消收藏「${item.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '取消收藏',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  try {
    await request.delete('/user/collection', { params: { targetType: 'SCENIC', targetId: item.id } })
    ElMessage.success('已取消收藏')
    await load()
  } catch {
    // 提示由拦截器处理
  }
}

onMounted(load)
</script>

<template>
  <div class="col">
    <p v-if="loading" class="col__state">加载中…</p>
    <p v-else-if="!list.length" class="col__state">还没有收藏任何景点，去景点页逛逛吧</p>

    <div v-else class="col__grid">
      <article v-for="s in list" :key="s.id" class="col__card">
        <img class="col__img" :src="s.coverImage" alt="" loading="lazy" @click="router.push(`/scenic/${s.id}`)" />
        <div class="col__body">
          <h3 class="col__name" @click="router.push(`/scenic/${s.id}`)">{{ s.name }}</h3>
          <p class="col__meta">{{ s.cityName }} · {{ s.type }}</p>
          <div class="col__foot">
            <span class="col__score">★ {{ s.score ?? '—' }}</span>
            <button type="button" class="col__btn" @click="remove(s)">取消收藏</button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.col__state {
  padding: 60px 0;
  font-size: 14px;
  color: rgba(var(--sy-text-rgb), 0.5);
  text-align: center;
}

.col__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: clamp(14px, 1.8vw, 22px);
}

.col__card {
  border: 1px solid $color-border-gold;
  border-radius: $radius-lg;
  background: rgba(var(--sy-surface-rgb), 0.78);
  overflow: hidden;
  @include hover-lift(-6px, 1.01);
}

.col__img {
  width: 100%;
  height: 150px;
  object-fit: cover;
  cursor: pointer;
}

.col__body {
  padding: 14px 16px 16px;
}

.col__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: 16px;
  color: var(--sy-accent);
  cursor: pointer;
  @include text-ellipsis(1);
}

.col__meta {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.col__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.col__score {
  font-size: 13px;
  color: $color-gold;
}

.col__btn {
  padding: 6px 14px;
  border: 1px solid rgba(122, 26, 34, 0.4);
  border-radius: $radius-pill;
  background: transparent;
  font-size: 12px;
  color: var(--sy-accent);
  cursor: pointer;
  transition: background-color $duration-fast $ease-out-expo;

  &:hover {
    background: rgba(122, 26, 34, 0.1);
  }
}
</style>
