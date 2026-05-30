<script setup lang="ts">
/**
 * 이니셜 아바타 — 이름 첫 글자(최대 2)를 결정적 해시 색(토큰 기반)으로 표시한다(UI_GUIDE §5-2).
 * 그리드·칸반·알림·워크플로우에서 담당자/요청자 표식으로 재사용한다.
 * 색은 lib/avatar 의 deterministic 매핑 — 같은 이름은 항상 같은 색.
 */
import { computed } from 'vue';
import { avatarInitials, avatarColor } from '@/lib/avatar';

interface Props {
  /** 표시 이름(담당자 ID·사용자명 등). 빈 값이면 '?'. */
  name?: string | null;
  /** 크기 — sm 24px / md 32px / lg 40px */
  size?: 'sm' | 'md' | 'lg';
}
const props = withDefaults(defineProps<Props>(), { name: null, size: 'md' });

const initials = computed(() => avatarInitials(props.name));
const color = computed(() => avatarColor(props.name));

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'size-6 text-[11px]';
    case 'lg':
      return 'size-10 text-sm';
    default:
      return 'size-8 text-[13px]';
  }
});
</script>

<template>
  <span
    :title="name ?? '미지정'"
    :class="[
      'inline-flex shrink-0 items-center justify-center rounded-full font-semibold',
      sizeClass,
      color.bg,
      color.text,
    ]"
  >
    {{ initials }}
  </span>
</template>
