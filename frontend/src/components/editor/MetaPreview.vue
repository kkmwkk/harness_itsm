<script setup lang="ts">
import { computed, ref } from 'vue';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import { buildMockRows } from '@/composables/useMetaPreview';
import type { PageMetaBody } from '@/types/meta-body';

/**
 * 편집 중 메타의 미리보기 (ADR-016 1단계).
 * 저장된 metaId 의 화면을 <DynamicPage> 로, grid.columns 에서 만든 가짜 데이터 5건과 함께 렌더한다.
 * 폼은 "등록" 버튼으로 DynamicForm 다이얼로그를 열어 편집 중 본문(body.form) 을 그대로 미리본다.
 * 실 운영 데이터는 쓰지 않는다(mockRows 는 "샘플 " 접두사).
 */
interface Props {
  /** 미리볼 메타 ID (저장된 버전 기준). */
  metaId: string;
  /** 편집 중 본문 — 폼 미리보기·mockRows 생성에 사용. */
  body: PageMetaBody;
}
const props = defineProps<Props>();

const mockRows = computed(() => buildMockRows(props.body.grid?.columns ?? []));

const formOpen = ref(false);
const hasFields = computed(() => (props.body.form?.fields?.length ?? 0) > 0);

function closeForm(): void {
  formOpen.value = false;
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between gap-2">
      <p class="text-xs text-foreground-muted">
        샘플 데이터 5건으로 렌더링한 미리보기입니다.
      </p>
      <Button
        size="sm"
        variant="outline"
        @click="formOpen = true"
      >
        등록 폼 미리보기
      </Button>
    </div>

    <DynamicPage
      :meta-id="metaId"
      :rows="mockRows"
    />

    <!-- 폼 미리보기 다이얼로그 — 편집 중 body.form 을 그대로 사용(라이브 반영) -->
    <Dialog v-model:open="formOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>등록 폼 미리보기</DialogTitle>
          <DialogDescription>현재 편집 중인 폼 정의를 그대로 렌더합니다(저장 전 상태).</DialogDescription>
        </DialogHeader>
        <DynamicForm
          v-if="hasFields"
          :meta="body.form"
          @cancel="closeForm"
          @submit="closeForm"
        />
        <p
          v-else
          class="text-sm text-foreground-muted"
        >
          표시할 필드가 없습니다.
        </p>
      </DialogContent>
    </Dialog>
  </div>
</template>
