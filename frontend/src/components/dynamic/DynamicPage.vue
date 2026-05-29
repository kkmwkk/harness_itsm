<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { toast } from 'vue-sonner';
import { usePageMeta, type MetaIdent } from '@/composables/usePageMeta';
import { usePageData } from '@/composables/usePageData';
import { useDataMutation } from '@/composables/useDataMutation';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import DynamicGrid from './DynamicGrid.vue';
import DynamicForm from './DynamicForm.vue';
import type { PageMetaBody, ActionMeta } from '@/types/meta-body';

/**
 * 메타 한 건으로 화면 전체를 자동 구성하는 No-code 진입 컴포넌트 (ADR-004).
 * usePageMeta(group PUBLISHED 최신 | metaId 특정 버전) → asPageMetaBody(강타입 좁히기) → DynamicGrid + DynamicForm(dialog).
 * `rows` 가 주어지면 그것을 그리드 데이터로 사용(phase 2 DynamicPageSampler 호환 경로),
 * 미제공이면 usePageData 가 meta.api 로 PageResponse<T> 를 자동 fetch(페이지·정렬·필터 setQuery).
 */
interface Props {
  /** 그룹 기반 — PUBLISHED 최신 메타 사용 */
  groupId?: string;
  /** 메타 ID 직접 — 특정 버전 메타 사용 (이력 복원, ADR-006) */
  metaId?: string;
  /** props.rows 우선 (phase 2 호환) */
  rows?: unknown[];
}
const props = defineProps<Props>();

// 그리드 행 클릭을 부모(라우트 래퍼)로 전파 — 상세 라우팅은 meta-driven 으로 부모가 결정(ADR-004).
const emit = defineEmits<{ 'row-click': [row: unknown] }>();

// 둘 다 제공되면 metaId 우선(이력 복원이 더 명시적 의도). 둘 다 없으면 식별자 미정.
const ident = computed<MetaIdent | null>(() => {
  if (props.metaId) return { metaId: props.metaId };
  if (props.groupId) return { groupId: props.groupId };
  return null;
});
const hasIdent = computed(() => ident.value !== null);

const {
  meta,
  notPublished,
  error: metaError,
  isFetching: isMetaFetching,
} = usePageMeta(computed(() => ident.value ?? { groupId: '' }));

// 메타 본문 강타입 좁히기 — 실패 시 페이지를 깨지 않고 명시 에러 카드만 노출.
const body = ref<PageMetaBody | null>(null);
const bodyError = ref<string | null>(null);

watchEffect(() => {
  body.value = null;
  bodyError.value = null;
  if (!meta.value) return;
  try {
    body.value = asPageMetaBody(meta.value.id, meta.value.metaJson);
  } catch (e) {
    if (e instanceof MetaBodyShapeError) bodyError.value = e.message;
    else throw e;
  }
});

// 데이터 fetch — props.rows 우선(phase 2 호환), 미제공 시 meta.api 로 자동 fetch.
// api 가 null 이면 usePageData 는 no-op(빈 결과)로 동작한다.
const api = computed<string | null>(() =>
  props.rows ? null : (body.value?.api ?? null),
);
const {
  rows: fetchedRows,
  isFetching: isDataFetching,
  error: dataError,
  reload,
  setQuery,
} = usePageData<Record<string, unknown>>(api);

const rows = computed<unknown[]>(() => props.rows ?? fetchedRows.value ?? []);

// 그리드의 page/sort 이벤트 연결과 reload 트리거는 후속 step 에서 사용 (defineExpose 로 노출).
defineExpose({ reload, setQuery });

// dialog-form 액션
const dialogOpen = ref(false);
const dialogFormMeta = computed(() => body.value?.form ?? null);

function onAction(a: ActionMeta): void {
  if (a.type === 'dialog-form') dialogOpen.value = true;
  // navigate / export / custom 은 다음 phase 의 책임
}

// 폼 submit → meta.api 로 실 POST (도메인-중립: ticket 전용 분기 없음, ADR-004).
// 성공 시 그리드 reload + 성공 토스트 + 다이얼로그 닫힘, 실패 시 에러 토스트.
const { submit: submitForm } = useDataMutation<
  Record<string, unknown>,
  Record<string, unknown>
>();

async function onFormSubmit(values: Record<string, unknown>): Promise<void> {
  const path = body.value?.api;
  if (!path) return;
  const result = await submitForm(path, values);
  if (result) {
    toast.success(`${meta.value?.title ?? '항목'} 이(가) 생성되었습니다.`);
    dialogOpen.value = false;
    await reload();
  } else {
    toast.error('생성에 실패했습니다.');
  }
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader :title="meta?.title">
      <template
        v-if="body?.actions?.length"
        #actions
      >
        <div class="flex gap-2">
          <Button
            v-for="a in body.actions"
            :key="a.id"
            :variant="a.type === 'dialog-form' ? 'default' : 'outline'"
            @click="onAction(a)"
          >
            {{ a.label }}
          </Button>
        </div>
      </template>
    </PageHeader>

    <Card v-if="!hasIdent">
      <CardContent class="py-6">
        <p class="text-danger">
          groupId 또는 metaId 가 필요합니다.
        </p>
      </CardContent>
    </Card>
    <p
      v-else-if="isMetaFetching"
      class="text-foreground-muted"
    >
      조회 중...
    </p>
    <Card v-else-if="notPublished">
      <CardContent class="py-8 text-center space-y-2">
        <p class="text-base font-semibold">아직 준비된 화면이 없습니다</p>
        <p class="text-sm text-foreground-muted">
          이 모듈(<code class="font-mono">{{ groupId }}</code>)은 메타가 등록되지 않았거나
          아직 배포되지 않은 상태입니다.
        </p>
        <p class="text-xs text-foreground-subtle">
          시스템 관리자에게 메타 등록·배포를 요청하거나, 메타 관리에서 DRAFT 를 PUBLISHED 로 전환하세요.
        </p>
      </CardContent>
    </Card>
    <Card v-else-if="metaError">
      <CardContent class="py-6 space-y-1">
        <p class="text-sm font-semibold text-danger">메타를 불러올 수 없습니다</p>
        <p class="text-sm text-foreground-muted">{{ metaError }}</p>
      </CardContent>
    </Card>
    <Card v-else-if="bodyError">
      <CardContent class="py-6">
        <p class="text-danger">
          메타 본문이 손상되었습니다. {{ bodyError }}
        </p>
      </CardContent>
    </Card>

    <template v-else-if="body">
      <Card v-if="dataError">
        <CardContent class="py-6">
          <p class="text-danger">
            {{ dataError }}
          </p>
        </CardContent>
      </Card>
      <p
        v-else-if="isDataFetching && !props.rows"
        class="text-foreground-muted"
      >
        데이터 조회 중...
      </p>

      <DynamicGrid
        :meta="body.grid"
        :rows="rows"
        @row-click="(r) => emit('row-click', r)"
      />

      <Dialog v-model:open="dialogOpen">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{{ meta?.title }}</DialogTitle>
          </DialogHeader>
          <DynamicForm
            v-if="dialogFormMeta"
            :meta="dialogFormMeta"
            @submit="onFormSubmit"
            @cancel="dialogOpen = false"
          />
        </DialogContent>
      </Dialog>
    </template>
  </section>
</template>
