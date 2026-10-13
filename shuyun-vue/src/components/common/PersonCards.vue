<script lang="ts">
/** 传承人 / 相关人物卡片数据项 */
export interface PersonCardItem {
  /** 传承人 id；有值时卡片可点击，跳转传承人详情 */
  id?: number | null
  /** 姓名 */
  name: string
  /** 头像地址 */
  avatar?: string | null
  /** 一句话简介 / 身份说明 */
  brief?: string
}
</script>

<script setup lang="ts">
/**
 * 传承人 / 相关人物卡片
 *
 * 统一「传承人」「相关人物」等人物信息在各详情页中的展示样式：
 * - 头像（缺图时用姓氏首字兜底）+ 姓名 + 一句话简介；
 * - 传入 id 时整卡可点击，跳转 /person/:id 查看传承人详情；
 * - 只要有可点击的卡片，就在卡片上方给出「可点击查看详情」的提醒语，
 *   并在卡片右侧显示箭头，避免用户不知道卡片能点进去。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { onAvatarError } from '@/utils/avatar'

const props = withDefaults(
  defineProps<{
    persons: PersonCardItem[]
    /** 自定义提醒语；留空时使用默认文案 */
    hint?: string
  }>(),
  { hint: '' },
)

const router = useRouter()

/** 是否存在可点击（可跳转详情）的卡片 */
const hasLink = computed(() => props.persons.some((p) => p.id))

/** 提醒语文案 */
const hintText = computed(() => props.hint || '点击卡片可查看详情')

/** 跳转传承人详情 */
function go(id: number) {
  router.push(`/person/${id}`)
}
</script>

<template>
  <div class="person-cards">
    <p v-if="hint || hasLink" class="person-cards__hint">
      <span class="person-cards__hint-dot" aria-hidden="true" />
      {{ hintText }}
      <span class="person-cards__hint-arrow" aria-hidden="true">→</span>
    </p>

    <div class="person-cards__grid">
      <component
        :is="p.id ? 'button' : 'div'"
        v-for="p in persons"
        :key="p.name"
        class="person-cards__item"
        :class="{ 'is-link': !!p.id }"
        :type="p.id ? 'button' : undefined"
        :title="p.id ? '点击查看传承人详情' : undefined"
        @click="p.id && go(p.id)"
      >
        <img
          v-if="p.avatar"
          class="person-cards__avatar"
          :src="p.avatar"
          :alt="p.name"
          loading="lazy"
          decoding="async"
          @error="onAvatarError($event, p.name)"
        />
        <span v-else class="person-cards__avatar person-cards__avatar--char">{{ p.name.charAt(0) }}</span>

        <span class="person-cards__info">
          <b class="person-cards__name">{{ p.name }}</b>
          <span v-if="p.brief" class="person-cards__brief">{{ p.brief }}</span>
        </span>

        <span v-if="p.id" class="person-cards__go" aria-hidden="true">›</span>
      </component>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;
@use '@/styles/mixins' as *;

.person-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 提醒语：可点击查看详情 */
.person-cards__hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: $font-body;
  font-size: $fs-tiny;
  letter-spacing: 0.06em;
  color: rgba(var(--sy-text-rgb), 0.55);
}

.person-cards__hint-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $color-gold;
  box-shadow: 0 0 0 3px rgba(212, 179, 116, 0.18);
}

.person-cards__hint-arrow {
  color: $color-gold;
  transition: transform $duration-normal $ease-out-expo;
}

.person-cards:hover .person-cards__hint-arrow {
  transform: translateX(3px);
}

.person-cards__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.person-cards__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid $color-border-gold;
  border-radius: $radius-md;
  background: rgba(var(--sy-surface-rgb), 0.55);
  text-align: left;
  transition:
    border-color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo,
    box-shadow $duration-normal $ease-out-expo;

  &.is-link {
    cursor: pointer;

    &:hover {
      border-color: $color-gold;
      transform: translateY(-2px);
      box-shadow: $shadow-md;

      .person-cards__name {
        color: var(--sy-accent);
      }

      .person-cards__go {
        color: var(--sy-accent);
        transform: translateX(2px);
      }
    }
  }
}

.person-cards__avatar {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  object-fit: cover;
  border: 2px solid rgba(212, 179, 116, 0.6);
  border-radius: 50%;
}

.person-cards__avatar--char {
  display: grid;
  place-items: center;
  background: rgba(212, 179, 116, 0.12);
  font-family: $font-title;
  font-weight: 900;
  font-size: 22px;
  color: var(--sy-accent);
}

.person-cards__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.person-cards__name {
  font-family: $font-title;
  font-weight: 900;
  font-size: clamp(14px, 1.15vw, 17px);
  letter-spacing: 0.06em;
  color: $color-ink;
  transition: color $duration-normal $ease-out-expo;
  @include text-ellipsis(1);
}

.person-cards__brief {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(var(--sy-text-rgb), 0.62);
  @include text-ellipsis(2);
}

/* 可点击箭头 */
.person-cards__go {
  flex-shrink: 0;
  font-size: 20px;
  line-height: 1;
  color: rgba(var(--sy-text-rgb), 0.3);
  transition:
    color $duration-normal $ease-out-expo,
    transform $duration-normal $ease-out-expo;
}
</style>
