<script setup lang="ts">
/**
 * 市州信息面板（CityInfoPanel）· 深色数据可视化版
 *
 * 点击 3D 地图中的市州后从右侧滑出（0.3s cubic-bezier(0.22,1,0.36,1)）：
 * - 顶部：市州名（24px 白色 思源宋体）+ 关闭
 * - 数据区：景点 / 美食 / 非遗 三列（等宽数字 + 细分隔线）
 * - 推荐景点：5 张横向卡片（60×60 缩略图 + 名称 + 评分）
 * - 底部：主色按钮「查看该市州全部景点 →」跳 /scenic?cityName={name}
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listScenic, type ScenicSpotVO } from '@/api/scenic'
import type { MapCityPoint } from '@/api/map3d'

const props = defineProps<{
  /** 当前选中的市州 */
  city: MapCityPoint | null
  /** 是否展开 */
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  /** 进入该市州的城市 3D 页面 */
  (e: 'enter'): void
}>()

const router = useRouter()

const scenics = ref<ScenicSpotVO[]>([])
const loading = ref(false)
/** 缩略图加载失败的景点 id（缺图时渲染纯色占位） */
const failedImgs = ref<Record<number, boolean>>({})

/** 拉取该市州推荐景点（按人气排序前 5） */
async function loadScenics(city: MapCityPoint) {
  loading.value = true
  try {
    const res = await listScenic({ cityName: city.name, size: 5, sort: 'hot' })
    scenics.value = res?.records ?? []
  } catch {
    scenics.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.city?.id] as const,
  ([visible, id]) => {
    if (visible && id && props.city) {
      loadScenics(props.city)
    }
  },
  { immediate: true },
)

/** ESC 关闭面板 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    emit('close')
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

/** 进入景点详情 */
function goScenic(id: number) {
  router.push(`/scenic/${id}`)
}

/** 查看该市州全部景点 */
function goAll() {
  if (props.city) {
    router.push(`/scenic?cityName=${encodeURIComponent(props.city.name)}`)
  }
}

/** 缩略图加载失败：标记后渲染纯色占位（首字） */
function onImgError(item: ScenicSpotVO) {
  failedImgs.value = { ...failedImgs.value, [item.id]: true }
}
</script>

<template>
  <aside class="panel" :class="{ 'is-open': visible }" aria-label="市州信息">
    <header class="panel__head">
      <h3>{{ city?.name ?? '—' }}</h3>
      <button type="button" class="panel__close" title="关闭" @click="emit('close')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </header>

    <div class="panel__body">
      <!-- 三列数据 -->
      <div class="stats">
        <div class="stat">
          <b>{{ city?.scenicCount ?? 0 }}</b>
          <span>景点</span>
        </div>
        <div class="stat">
          <b>{{ city?.foodCount ?? 0 }}</b>
          <span>美食</span>
        </div>
        <div class="stat">
          <b>{{ city?.heritageCount ?? 0 }}</b>
          <span>非遗</span>
        </div>
      </div>

      <!-- 推荐景点 -->
      <div class="rec">
        <h4>推荐景点</h4>

        <p v-if="loading" class="rec__tip">正在加载…</p>
        <p v-else-if="!scenics.length" class="rec__tip">该市州暂未收录景点</p>
        <ul v-else class="rec__list">
          <li v-for="s in scenics" :key="s.id" class="rec__item" @click="goScenic(s.id)">
            <img
              v-if="s.coverImage && !failedImgs[s.id]"
              class="rec__cover"
              :src="s.coverImage"
              :alt="s.name"
              loading="lazy"
              @error="onImgError(s)"
            />
            <span v-else class="rec__cover rec__cover--ph">{{ (s.name || '景').charAt(0) }}</span>
            <div class="rec__info">
              <b>{{ s.name }}</b>
              <em>{{ s.type || '景点' }}</em>
            </div>
            <span v-if="s.score" class="rec__score">{{ s.score }}</span>
          </li>
        </ul>
      </div>
    </div>

    <footer class="panel__foot">
      <button type="button" class="panel__enter" @click="emit('enter')">进入 {{ city?.name ?? '' }} 3D 城市全景 →</button>
      <button type="button" class="panel__all" @click="goAll">查看该市州全部景点 →</button>
    </footer>
  </aside>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.panel {
  position: fixed;
  top: $nav-height;
  right: 0;
  z-index: 200;
  display: flex;
  flex-direction: column;
  width: 460px;
  max-width: 94vw;
  height: calc(100vh - #{$nav-height});
  border-left: 1px solid rgba(58, 140, 220, 0.4);
  background: #0f2540;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);

  &.is-open {
    transform: translateX(0);
  }
}

/* ---------------- 头部 ---------------- */
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30px 34px 24px;
  border-bottom: 1px solid rgba(58, 140, 220, 0.3);
}

.panel__head h3 {
  font-family: $font-title;
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #ffffff;
}

.panel__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 1px solid rgba(58, 140, 220, 0.4);
  border-radius: 6px;
  background: transparent;
  color: #7a95b8;
  cursor: pointer;
  transition:
    border-color 0.3s $ease-out-expo,
    color 0.3s $ease-out-expo;

  &:hover {
    border-color: #4ad8f0;
    color: #4ad8f0;
  }
}

/* ---------------- 主体 ---------------- */
.panel__body {
  flex: 1;
  padding: 28px 34px;
  overflow-y: auto;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}

.stat {
  padding: 4px 0;
  text-align: center;

  & + .stat {
    border-left: 1px solid rgba(58, 140, 220, 0.15);
  }

  b {
    display: block;
    font-family: $font-mono;
    font-size: 48px;
    font-weight: 700;
    color: #ffffff;
  }

  span {
    display: block;
    margin-top: 6px;
    font-family: $font-mono;
    font-size: 20px;
    letter-spacing: 0.05em;
    color: #7a95b8;
  }
}

/* ---------------- 推荐景点 ---------------- */
.rec {
  margin-top: 32px;
}

.rec h4 {
  margin-bottom: 12px;
  font-family: $font-mono;
  font-size: 21px;
  font-weight: 400;
  letter-spacing: 0.1em;
  color: #7a95b8;
}

.rec__tip {
  padding: 22px 0;
  font-family: $font-mono;
  font-size: 21px;
  text-align: center;
  color: #7a95b8;
}

.rec__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rec__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(58, 140, 220, 0.08);
  cursor: pointer;
  transition: background-color 0.25s $ease-out-expo;

  &:hover {
    background: rgba(58, 140, 220, 0.16);
  }
}

.rec__cover {
  flex: none;
  width: 96px;
  height: 96px;
  border-radius: 6px;
  object-fit: cover;
  background: rgba(58, 140, 220, 0.12);
}

/* 缺图占位：纯色背景 + 景点名首字 */
.rec__cover--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: $font-mono;
  font-size: 24px;
  color: #7a95b8;
}

.rec__info {
  flex: 1;
  min-width: 0;

  b {
    display: block;
    overflow: hidden;
    font-size: 21px;
    color: #ffffff;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    display: block;
    margin-top: 5px;
    font-family: $font-mono;
    font-size: 18px;
    font-style: normal;
    color: #7a95b8;
  }
}

.rec__score {
  flex: none;
  font-family: $font-mono;
  font-size: 21px;
  color: #4ad8f0;
}

/* ---------------- 底部 ---------------- */
.panel__foot {
  padding: 18px 26px 26px;
  border-top: 1px solid rgba(58, 140, 220, 0.3);
}

.panel__enter {
  width: 100%;
  height: 80px;
  margin-bottom: 12px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #1565c0, #4ad8f0);
  font-family: $font-mono;
  font-size: 21px;
  letter-spacing: 0.04em;
  color: #ffffff;
  cursor: pointer;
  transition: filter 0.25s $ease-out-expo;

  &:hover {
    filter: brightness(1.1);
  }
}

.panel__all {
  width: 100%;
  height: 80px;
  border: none;
  border-radius: 8px;
  background: #c62828;
  font-family: $font-mono;
  font-size: 21px;
  letter-spacing: 0.04em;
  color: #ffffff;
  cursor: pointer;
  transition: background-color 0.25s $ease-out-expo;

  &:hover {
    background: #d63a3a;
  }
}
</style>
