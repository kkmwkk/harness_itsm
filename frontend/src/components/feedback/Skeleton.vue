<script setup lang="ts">
/**
 * Skeleton 자리 표시자 (UI_GUIDE §9 — blink 금지, 좌→우 shimmer loop).
 * 로딩 중 텍스트/카드/숫자 자리를 회색 블록 + shimmer 로 채운다(스피너 대체 금지).
 * 색·모션은 토큰만 사용 — bg-surface-muted · via-foreground/5 · .animate-shimmer(1.2s).
 * prefers-reduced-motion 사용자에게는 base.css 전역 규칙이 애니메이션을 제거한다.
 */
interface Props {
  width?: string;
  height?: string;
  rounded?: 'sm' | 'md' | 'lg' | 'full';
}
const props = withDefaults(defineProps<Props>(), {
  width: '100%',
  height: '1em',
  rounded: 'md',
});
</script>

<template>
  <div
    :class="[
      'relative overflow-hidden bg-surface-muted',
      props.rounded === 'full' ? 'rounded-full' : `rounded-${props.rounded}`,
    ]"
    :style="{ width: props.width, height: props.height }"
    aria-hidden="true"
  >
    <div
      class="absolute inset-0 -translate-x-full animate-shimmer
             bg-gradient-to-r from-transparent via-foreground/5 to-transparent"
    />
  </div>
</template>
