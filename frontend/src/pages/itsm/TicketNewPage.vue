<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from 'vue-sonner';
import { useApiFetch } from '@/lib/api';
import { usePageMeta, type MetaIdent } from '@/composables/usePageMeta';
import { useDataMutation } from '@/composables/useDataMutation';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import { UI } from '@/lib/ui-messages';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import type { ApiEnvelope } from '@/types/meta';
import type { PageMetaBody } from '@/types/meta-body';

/**
 * 요청 유형별 신규 티켓 등록 (PRD §5-3 · ADR-004).
 * 라우트 파라미터 requestType(예: INCIDENT) → 요청 유형의 form_meta_group_id → 그 유형 폼 메타로 DynamicForm 렌더.
 * 제출 시 meta.api(/api/tickets) 로 POST 하며 requestTypeCode 를 주입한다(워크플로우 자동 생성은 백엔드 책임).
 */
const route = useRoute();
const router = useRouter();
const requestType = computed(() => String(route.params.requestType));

// 요청 유형 → 폼 메타 그룹 해석 (meta-driven, 도메인 하드코딩 없음).
const rtUrl = computed(
  () => `/api/ticket-request-types/${encodeURIComponent(requestType.value)}`,
);
const { data: rtEnv, error: rtError } = useApiFetch(rtUrl, { refetch: true }).json<
  ApiEnvelope<{ label: string; formMetaGroupId: string }>
>();

const formGroupId = computed<string>(() => rtEnv.value?.data?.formMetaGroupId ?? '');
const ident = computed<MetaIdent>(() => ({ groupId: formGroupId.value }));
const {
  meta,
  notPublished,
  error: metaError,
} = usePageMeta(ident);

const body = computed<PageMetaBody | null>(() => {
  const m = meta.value;
  if (!m) return null;
  try {
    return asPageMetaBody(m.id, m.metaJson);
  } catch (e) {
    if (e instanceof MetaBodyShapeError) return null;
    throw e;
  }
});

const { submit } = useDataMutation<
  Record<string, unknown>,
  Record<string, unknown>
>();

async function onSubmit(values: Record<string, unknown>): Promise<void> {
  const path = body.value?.api;
  if (!path) return;
  // 요청 유형을 주입 — 백엔드가 default_workflow 로 WorkflowInstance 를 자동 생성한다.
  const payload = { ...values, requestTypeCode: requestType.value };
  const result = await submit(path, payload);
  if (result) {
    toast.success(UI.success.created(meta.value?.title ?? '티켓'));
    await router.push('/itsm');
  } else {
    toast.error(UI.error.submit);
  }
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader :title="`${rtEnv?.data?.label ?? requestType} 등록`" />

    <div class="flex gap-2">
      <Button
        variant="outline"
        @click="router.push('/itsm')"
      >
        ← 목록으로
      </Button>
    </div>

    <Card v-if="rtError">
      <CardContent class="py-6 text-danger">
        {{ UI.error.notFound }}
      </CardContent>
    </Card>
    <Card v-else-if="notPublished">
      <CardContent class="py-8 text-center space-y-2">
        <p class="text-base font-semibold">
          {{ UI.empty.metaNotPublished }}
        </p>
        <p class="text-xs text-foreground-subtle">
          {{ UI.empty.metaNotPublishedHint }}
        </p>
      </CardContent>
    </Card>
    <Card v-else-if="metaError">
      <CardContent class="py-6 text-danger">
        {{ metaError }}
      </CardContent>
    </Card>

    <Card v-else-if="body">
      <CardHeader>
        <CardTitle>{{ meta?.title }}</CardTitle>
      </CardHeader>
      <CardContent>
        <DynamicForm
          :meta="body.form"
          @submit="onSubmit"
          @cancel="router.push('/itsm')"
        />
      </CardContent>
    </Card>
  </section>
</template>
