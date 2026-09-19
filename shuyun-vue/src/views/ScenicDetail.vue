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
  <section v-loading="loading" class="sy-page detail">
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
@use '@/styles/variables' as *;

/* 页面外壳由全局 .sy-page 提供，这里只收紧顶部间距 */
.detail {
  padding-top: clamp(16px, 2vw, 32px);
}

/* ---------- 轮播 ---------- */
.detail__gallery {
  border-radius: $radius-card;
  overflow: hidden;
  box-shadow: $shadow-card;

  :deep(.swiper-button-next),
  :deep(.swiper-button-prev) {
    color: $color-gold;
    text-shadow: 0 2px 12px rgba(0, 0, 0, 0.55);
  }

  :deep(.swiper-pagination-bullet) {
    background: $color-text-light;
    opacity: 0.5;
  }

  :deep(.swiper-pagination-bullet-active) {
    background: $color-gold;
    opacity: 1;
  }
}

.detail__swiper {
  height: clamp(300px, 46vh, 560px);
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
  gap: clamp(14px, 1.8vw, 24px);
  padding: clamp(24px, 3vw, 44px) 0 8px;
}

.detail__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.detail__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(28px, 3.6vw, 52px);
  letter-spacing: 0.14em;
  color: $color-primary;
}

.detail__score {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-family: $font-en;
  font-size: clamp(26px, 3vw, 42px);
  line-height: 1;
  color: $color-gold;

  span {
    font-family: $font-body;
    font-size: $fs-small;
    color: rgba(26, 26, 26, 0.5);
  }
}

.detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  padding: clamp(14px, 1.8vw, 22px) clamp(16px, 2vw, 26px);
  border: 1px solid rgba(201, 168, 106, 0.4);
  border-radius: $radius-card;
  background: rgba(255, 255, 255, 0.6);
  font-family: $font-body;
  font-size: $fs-small;
  color: rgba(26, 26, 26, 0.78);

  b {
    margin-right: 8px;
    font-weight: 400;
    letter-spacing: 0.12em;
    color: $color-gold;
  }
}

.detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail__tag {
  padding: 4px 13px;
  border: 1px solid rgba(201, 168, 106, 0.55);
  border-radius: $radius-pill;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: $color-primary;
}

.detail__desc {
  max-width: 78ch;
  font-family: $font-body;
  font-size: $fs-body;
  line-height: 2.1;
  color: rgba(26, 26, 26, 0.8);
  text-align: justify;
}

/* ---------- 区块 ---------- */
.block {
  margin-top: clamp(36px, 5vw, 72px);
}

.block__title {
  position: relative;
  margin-bottom: clamp(16px, 2vw, 26px);
  padding-left: 16px;
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(18px, 1.6vw, 26px);
  letter-spacing: 0.14em;
  color: $color-ink;

  // 左侧金色竖线
  &::before {
    content: '';
    position: absolute;
    top: 0.12em;
    left: 0;
    width: 2px;
    height: 0.9em;
    background: $color-gold;
  }
}

.wall {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: clamp(12px, 1.4vw, 20px);

  &__item {
    aspect-ratio: 4 / 3;
    border-radius: $radius-card;
    background-size: cover;
    background-position: center;
    box-shadow: $shadow-card;
    transition:
      transform $dur-base $ease-out-expo,
      box-shadow $dur-base $ease-out-expo;

    &:hover {
      transform: scale(1.03);
      box-shadow: $shadow-card-hover;
    }
  }
}

.reco {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: clamp(16px, 1.8vw, 26px);

  &__card {
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

    &:hover {
      transform: translateY(-8px) scale(1.02);
      box-shadow: $shadow-card-hover;
      border-color: $color-gold;

      .reco__img {
        transform: scale(1.06);
      }
    }
  }

  &__img {
    aspect-ratio: 16 / 10;
    background-size: cover;
    background-position: center;
    transition: transform 1s $ease-out-expo;
  }

  &__info {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: clamp(12px, 1.4vw, 18px);

    h3 {
      font-family: $font-title;
      font-size: clamp(15px, 1.2vw, 19px);
      letter-spacing: 0.08em;
      color: $color-text-dark;
    }

    span {
      font-family: $font-en;
      font-size: 1.05em;
      color: $color-primary;
    }
  }
}

@media (max-width: 640px) {
  .wall,
  .reco {
    grid-template-columns: 1fr;
  }
}
</style>
