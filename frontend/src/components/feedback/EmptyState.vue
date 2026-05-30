<script setup lang="ts">
/**
 * 풍부한 빈 상태(UI_GUIDE §5-4) — lucide 아이콘(모듈 컬러 강조) + 안내 문구 + 선택적 행동 유도.
 * raw 텍스트 카드 대신 본 컴포넌트로 빈/미발행/0건 상태를 통일한다(메시지는 카탈로그 사용 권장).
 *
 * module 이 주어지면 아이콘 배경/색을 해당 모듈 컬러(soft)로 — module-color.ts 의 정적 유틸만
 * 쓰므로 Tailwind 가 클래스를 생성한다(동적 문자열 클래스 금지, UI_GUIDE §2).
 * actionLabel 이 없으면 행동 유도 버튼을 렌더하지 않는다.
 */
import { computed, type Component } from 'vue';
import { Button } from '@/components/ui/button';
import { moduleVisual } from '@/lib/module-color';
import type { SystemType } from '@/types/meta';

interface Props {
  icon?: Component;
  title: string;
  description?: string;
  actionLabel?: string;
  module?: SystemType;
}
const props = defineProps<Props>();
defineEmits<{ action: [] }>();

// 모듈 지정 시 soft 배경 + 모듈 텍스트색, 미지정 시 중립 서피스.
const iconWrapClass = computed(() =>
  props.module
    ? [moduleVisual(props.module).bgSoftClass, moduleVisual(props.module).textClass]
    : ['bg-surface-muted', 'text-foreground-subtle'],
);
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-3 px-6 py-12 text-center">
    <div
      :class="[
        'flex size-14 items-center justify-center rounded-full',
        ...iconWrapClass,
      ]"
    >
      <component
        :is="icon"
        v-if="icon"
        class="size-7"
        :stroke-width="1.5"
      />
    </div>
    <p class="text-base font-semibold text-foreground">
      {{ title }}
    </p>
    <p
      v-if="description"
      class="max-w-md text-sm text-foreground-muted"
    >
      {{ description }}
    </p>
    <Button
      v-if="actionLabel"
      class="mt-2"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </Button>
  </div>
</template>
