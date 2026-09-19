<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Navigation, Pagination } from 'swiper/modules'
import 'swiper/css'
import 'swiper/css/navigation'
import 'swiper/css/pagination'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** Swiper 模块 */
const swiperModules = [Autoplay, Navigation, Pagination]

interface ImageItem {
  id: number
  imageUrl: string
}

interface SpotVO {
  id: number
  name: string
  cityId?: number
  cityName?: string
  type?: string
  level?: string
  address?: string
  coverImage?: string
  description?: string
  openTime?: string
  ticketInfo?: string
  score?: number
  tags?: string
  images?: ImageItem[]
  recommends?: SpotVO[]
}

const detail = ref<SpotVO | null>(null)
const loading = ref(true)
const collected = ref(false)

/** 轮播图：封面 + 图片表（去重、去掉空值） */
const gallery = computed(() => {
  const d = detail.value
  if (!d) {
    return []
  }
  const urls = [d.coverImage, ...(d.images ?? []).map((i) => i.imageUrl)].filter(Boolean) as string[]
  return Array.from(new Set(urls))
})

const tagList = computed(() => (detail.value?.tags || '').split(',').filter(Boolean))

async function loadDetail() {
  loading.value = true
  try {
    const res = await request.get(`/scenic/${route.params.id}`, { silent: true })
    detail.value = res.data
    await checkCollected()
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

/** 查询当前用户是否已收藏该景点 */
async function checkCollected() {
  if (!userStore.isLogin || !detail.value) {
    return
  }
  try {
    const res = await request.get('/user/collection/check', {
      silent: true,
      params: { targetType: 'SCENIC', targetId: detail.value.id },
    })
    collected.value = res.data === true
  } catch {
    collected.value = false
  }
}

/** 收藏 / 取消收藏 */
async function onCollect() {
  const id = detail.value?.id
  if (!id) {
    return
  }
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后再收藏')
    router.push('/login')
    return
  }

  try {
    if (collected.value) {
      await request.delete('/user/collection', {
        params: { targetType: 'SCENIC', targetId: id },
      })
      collected.value = false
      ElMessage.success('已取消收藏')
    } else {
      await request.post('/user/collection', { targetType: 'SCENIC', targetId: id })
      collected.value = true
      ElMessage.success('已加入收藏')
    }
  } catch {
    // 错误提示由响应拦截器统一处理
  }
}

function goDetail(id: number) {
  router.push(`/scenic/${id}`)
}

onMounted(loadDetail)
</script>

<template>
  <section v-loading="loading" class="detail">
    <template v-if="detail">
      <!-- ============ 顶部大图轮播 ============ -->
      <div class="detail__gallery">
        <Swiper
          :modules="swiperModules"
          :autoplay="{ delay: 4200, disableOnInteraction: false }"
          :pagination="{ clickable: true }"
          :navigation="true"
          class="detail__swiper"
        >
          <SwiperSlide v-for="(url, i) in gallery" :key="i">
            <div class="detail__slide" :style="{ backgroundImage: `url(${url})` }" />
          </SwiperSlide>
        </Swiper>
      </div>

      <!-- ============ 景点信息 ============ -->
      <div class="detail__main">
        <div class="detail__head">
          <h1 class="detail__name">{{ detail.name }}</h1>
          <div class="detail__score">
            {{ detail.score }}<span>分</span>
          </div>
        </div>

        <div class="detail__meta">
          <span><b>城市</b>{{ detail.cityName || '—' }}</span>
          <span><b>类型</b>{{ detail.type || '—' }}</span>
          <span><b>等级</b>{{ detail.level || '—' }}</span>
          <span><b>开放时间</b>{{ detail.openTime || '—' }}</span>
          <span><b>门票</b>{{ detail.ticketInfo || '—' }}</span>
        </div>

        <div class="detail__tags">
          <span v-for="t in tagList" :key="t" class="detail__tag">{{ t }}</span>
        </div>

        <p class="detail__desc">{{ detail.description }}</p>

        <el-button
          :type="collected ? 'default' : 'primary'"
          round
          @click="onCollect"
        >
          {{ collected ? '★ 已收藏（点击取消）' : '☆ 收藏景点' }}
        </el-button>
      </div>

      <!-- ============ 图片墙 ============ -->
      <section class="block">
        <h2 class="block__title">图片墙</h2>
        <div v-if="detail.images?.length" class="wall">
          <div
            v-for="img in detail.images"
            :key="img.id"
            class="wall__item"
            :style="{ backgroundImage: `url(${img.imageUrl})` }"
          />
        </div>
        <el-empty v-else description="暂无更多图片" />
      </section>

      <!-- ============ 同城市推荐 ============ -->
      <section class="block">
        <h2 class="block__title">同城推荐</h2>
        <div v-if="detail.recommends?.length" class="reco">
          <article
            v-for="r in detail.recommends"
            :key="r.id"
            class="reco__card"
            @click="goDetail(r.id)"
          >
            <div class="reco__img" :style="{ backgroundImage: `url(${r.coverImage})` }" />
            <div class="reco__info">
              <h3>{{ r.name }}</h3>
              <span>{{ r.score }} 分</span>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无同城推荐" />
      </section>
    </template>

    <el-empty v-else-if="!loading" description="景点不存在，或需要登录后查看">
      <el-button type="primary" @click="router.push('/login')">去登录</el-button>
    </el-empty>
  </section>
</template>

<style scoped lang="scss">
.detail {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 24px 80px;
}

/* ---------- 轮播 ---------- */
.detail__gallery {
  border-radius: 18px;
  overflow: hidden;

  :deep(.swiper-button-next),
  :deep(.swiper-button-prev) {
    color: #fff;
    text-shadow: 0 2px 10px rgba(0, 0, 0, 0.4);
  }

  :deep(.swiper-pagination-bullet-active) {
    background: var(--color-gold);
  }
}

.detail__swiper {
  height: 420px;
}

.detail__slide {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}

/* ---------- 信息 ---------- */
.detail__main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 32px 0 8px;
}

.detail__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.detail__name {
  font-size: 38px;
  letter-spacing: 6px;
  color: var(--color-primary);
}

.detail__score {
  font-family: var(--font-title);
  font-size: 34px;
  font-weight: 600;
  color: var(--color-gold);

  span {
    margin-left: 2px;
    font-size: 14px;
  }
}

.detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  font-size: 14px;
  color: #4a3a32;

  b {
    margin-right: 6px;
    font-weight: 400;
    color: var(--color-gold);
  }
}

.detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail__tag {
  padding: 3px 12px;
  border-radius: 10px;
  font-size: 12px;
  color: #fff;
  background: var(--color-primary);
}

.detail__desc {
  font-size: 15px;
  line-height: 2;
  color: #4a3a32;
}

/* ---------- 区块 ---------- */
.block {
  margin-top: 40px;
}

.block__title {
  margin-bottom: 18px;
  font-size: 22px;
  letter-spacing: 4px;
  color: var(--color-primary);
}

.wall {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;

  &__item {
    height: 180px;
    border-radius: 12px;
    background-size: cover;
    background-position: center;
    transition: transform 0.4s ease;

    &:hover {
      transform: scale(1.03);
    }
  }
}

.reco {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;

  &__card {
    border-radius: 14px;
    overflow: hidden;
    background: #fff;
    border: 1px solid rgba(201, 168, 106, 0.3);
    cursor: pointer;
    transition: transform 0.3s ease, box-shadow 0.3s ease;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 16px 34px rgba(140, 31, 40, 0.15);
    }
  }

  &__img {
    height: 140px;
    background-size: cover;
    background-position: center;
  }

  &__info {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    font-size: 15px;

    h3 {
      font-size: 16px;
      letter-spacing: 2px;
      color: #2b2b2b;
    }

    span {
      font-size: 13px;
      color: var(--color-primary);
    }
  }
}

@media (max-width: 820px) {
  .detail__swiper {
    height: 260px;
  }

  .wall,
  .reco {
    grid-template-columns: 1fr;
  }
}
</style>
