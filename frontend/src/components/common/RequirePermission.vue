<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/stores/useAuthStore';

/**
 * 권한 가드 슬롯 컴포넌트.
 * 백엔드 @PreAuthorize 와 짝을 이루는 화면 단 가드 — 권한(또는 역할) 보유자에게만 기본 슬롯을 노출한다.
 * 없으면 fallback 슬롯(기본 아무것도 안 보임). UI 만 숨길 뿐 보안 경계는 백엔드가 책임진다.
 *
 *   <RequirePermission code="USER_ADMIN"><Button>등록</Button></RequirePermission>
 */
interface Props {
  /** 권한 코드 또는 역할 코드. 배열이면 하나라도 보유 시 통과(OR). */
  code: string | string[];
}
const props = defineProps<Props>();
const auth = useAuthStore();

const allowed = computed<boolean>(() => {
  const codes = Array.isArray(props.code) ? props.code : [props.code];
  return codes.some((c) => auth.hasPermission(c) || auth.hasRole(c));
});
</script>

<template>
  <slot v-if="allowed" />
  <slot
    v-else
    name="fallback"
  />
</template>
