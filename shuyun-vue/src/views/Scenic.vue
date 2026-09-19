<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

interface ScenicItem {
  id: number
  name: string
  cityName?: string
  type?: string
  level?: string
  coverImage?: string
  description?: string
  score?: number
  tags?: string
}

interface CityItem {
  id: number
  name: string
}

/** 类型下拉（与数据库 scenic_spot.type 取值保持一致） */
const TYPES = ['自然风光', '人文古迹', '博物馆']

const cities = ref<CityItem[]>([])

const query = reactive({
  page: 1,
  size: 8,
  cityId: undefined as number | undefined,
  type: '' as string,
  keyword: '',
})

const list = ref<ScenicItem[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)

const emptyText = computed(() =>
  failed.value && !userStore.isLogin ? '登录后可查看景点列表' : '暂无符合条件的景点',
)

async function loadCities() {
  try {
    const res = await request.get('/city/list', { silent: true })
    if (Array.isArray(res.data)) {
      cities.value = res.data
    }
  } catch {
    // 忽略：城市下拉为空不影响其它筛选
  }
}

async function loadList() {
  loading.value = true
  failed.value = false
  try {
    const res = await request.get('/scenic/list', {
      silent: true,
      params: {
        page: query.page,
        size: query.size,
        cityId: query.cityId || undefined,
        type: query.type || undefined,
        keyword: query.keyword || undefined,
      },
    })
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
    failed.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadList()
}

function onReset() {
  query.cityId = undefined
  query.type = ''
  query.keyword = ''
  query.page = 1
  loadList()
}

function onPageChange(page: number) {
  query.page = page
  loadList()
}

function goDetail(id: number) {
  router.push(`/scenic/${id}`)
}

function splitTags(tags?: string) {
  return (tags || '').split(',').filter(Boolean)
}

onMounted(() => {
  loadCities()
  loadList()
})
</script>

<template>
  <section class="scenic">
    <header class="scenic__head">
      <h1 class="scenic__title">景点导览</h1>
      <p class="scenic__desc">走遍巴蜀山水 · 共 {{ total }} 处景点</p>
    </header>

    <!-- ============ 筛选栏 ============ -->
    <div class="filter">
      <el-select
        v-model="query.cityId"
        placeholder="全部城市"
        clearable
        class="filter__item"
        @change="onSearch"
      >
        <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>

      <el-select
        v-model="query.type"
        placeholder="全部类型"
        clearable
        class="filter__item"
        @change="onSearch"
      >
        <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
      </el-select>

      <el-input
        v-model="query.keyword"
        placeholder="搜索景点名称 / 标签"
        clearable
        class="filter__search"
        @keyup.enter="onSearch"
      />

      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <!-- ============ 卡片网格（4 列） ============ -->
    <div v-loading="loading" class="grid">
      <article
        v-for="item in list"
        :key="item.id"
        class="card"
        @click="goDetail(item.id)"
      >
        <div class="card__img" :style="{ backgroundImage: `url(${item.coverImage})` }">
          <span v-if="item.level" class="card__level">{{ item.level }}</span>
        </div>

        <div class="card__body">
          <h3 class="card__name">{{ item.name }}</h3>

          <div class="card__meta">
            <span>{{ item.cityName }} · {{ item.type }}</span>
            <span class="card__score">{{ item.score }}</span>
          </div>

          <p class="card__desc">{{ item.description }}</p>

          <div class="card__tags">
            <span v-for="t in splitTags(item.tags)" :key="t" class="card__tag">{{ t }}</span>
          </div>
        </div>
      </article>
    </div>

    <el-empty v-if="!loading && !list.length" :description="emptyText">
      <el-button v-if="!userStore.isLogin" type="primary" @click="router.push('/login')">
        去登录
      </el-button>
    </el-empty>

    <!-- ============ 分页 ============ -->
    <div v-if="total > query.size" class="scenic__pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="onPageChange"
      />
    </div>
  </section>
</template>

<style scoped lang="scss">
.scenic {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px 80px;
}

.scenic__head {
  margin-bottom: 24px;
}

.scenic__title {
  font-size: 34px;
  letter-spacing: 6px;
  color: var(--color-primary);
}

.scenic__desc {
  margin-top: 6px;
  font-size: 14px;
  letter-spacing: 1px;
  color: var(--color-gold);
}

/* ---------- 筛选栏 ---------- */
.filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  margin-bottom: 28px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(201, 168, 106, 0.35);

  &__item {
    width: 160px;
  }

  &__search {
    width: 240px;
  }
}

/* ---------- 网格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  min-height: 240px;
}

.card {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(201, 168, 106, 0.3);
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-6px);
    box-shadow: 0 18px 40px rgba(140, 31, 40, 0.16);
  }

  &__img {
    position: relative;
    height: 170px;
    background-size: cover;
    background-position: center;
  }

  &__level {
    position: absolute;
    top: 10px;
    left: 10px;
    padding: 2px 10px;
    border-radius: 10px;
    font-size: 12px;
    color: #fff;
    background: rgba(140, 31, 40, 0.85);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 16px 18px;
  }

  &__name {
    font-size: 18px;
    letter-spacing: 2px;
    color: #2b2b2b;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    color: #8a7a70;
  }

  &__score {
    font-weight: 600;
    color: var(--color-primary);
  }

  &__desc {
    display: -webkit-box;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.7;
    color: #6b5b52;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__tag {
    padding: 1px 8px;
    border-radius: 8px;
    font-size: 11px;
    color: var(--color-primary);
    background: rgba(140, 31, 40, 0.08);
  }
}

.scenic__pager {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1100px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 820px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 540px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
