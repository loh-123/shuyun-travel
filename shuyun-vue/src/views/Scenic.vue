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
  <section class="sy-page scenic">
    <header class="sy-page-head">
      <p class="sy-eyebrow">Scenic Spots</p>
      <h1 class="sy-page-title">景点导览</h1>
      <p class="sy-page-desc">
        走遍巴蜀山水 —— 从都江堰的水利智慧到九寨沟的翠海叠瀑，共 {{ total }} 处景点等你探访。
      </p>
    </header>

    <!-- ============ 筛选栏 ============ -->
    <div class="filter sy-panel">
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
@use '@/styles/variables' as *;

/* 页面外壳与页头统一由全局 .sy-page / .sy-page-head 提供 */

/* ---------- 筛选栏 ---------- */
.filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: clamp(14px, 1.6vw, 22px) clamp(16px, 1.8vw, 26px);
  margin-bottom: clamp(22px, 3vw, 40px);

  &__item {
    width: clamp(140px, 14vw, 180px);
  }

  &__search {
    width: clamp(180px, 20vw, 280px);
  }
}

/* ---------- 网格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: clamp(18px, 2vw, 30px);
  min-height: 240px;
}

.card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-card;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: $shadow-card;
  cursor: pointer;
  transition:
    transform $dur-base $ease-out-expo,
    box-shadow $dur-base $ease-out-expo,
    border-color $dur-base $ease-out-expo;
  will-change: transform;

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: $shadow-card-hover;
    border-color: $color-gold;

    .card__img {
      transform: scale(1.06);
    }

    .card__name {
      color: $color-primary;
    }
  }

  &__img {
    position: relative;
    aspect-ratio: 16 / 10;
    background-size: cover;
    background-position: center;
    transition: transform 1s $ease-out-expo;
  }

  &__level {
    position: absolute;
    top: 12px;
    left: 12px;
    padding: 4px 13px;
    border-radius: $radius-pill;
    font-size: 12px;
    letter-spacing: 0.08em;
    color: $color-gold;
    background: rgba(14, 42, 71, 0.72);
    backdrop-filter: blur(6px);
  }

  &__body {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 9px;
    padding: clamp(16px, 1.8vw, 24px);
  }

  &__name {
    font-family: $font-title;
    font-weight: 900;
    font-size: clamp(17px, 1.5vw, 22px);
    letter-spacing: 0.08em;
    color: $color-text-dark;
    transition: color $dur-base $ease-out-expo;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.55);
  }

  &__score {
    font-family: $font-en;
    font-size: 1.15em;
    color: $color-primary;
  }

  &__desc {
    display: -webkit-box;
    overflow: hidden;
    font-family: $font-body;
    font-size: $fs-small;
    line-height: 1.85;
    color: rgba(26, 26, 26, 0.66);
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: auto;
  }

  &__tag {
    padding: 3px 10px;
    border: 1px solid rgba(201, 168, 106, 0.5);
    border-radius: $radius-sm;
    font-size: 12px;
    letter-spacing: 0.08em;
    color: $color-bamboo;
  }
}

.scenic__pager {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}

/* ---------- 响应式：auto-fit 已自适应，仅小屏收紧 ---------- */
@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .filter {
    &__item,
    &__search {
      width: 100%;
    }
  }
}
</style>
