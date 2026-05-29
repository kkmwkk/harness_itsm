<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/lib/api';
import { usePageMeta, type MetaIdent } from '@/composables/usePageMeta';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import WorkflowPanel from '@/components/dynamic/WorkflowPanel.vue';
import type { ApiEnvelope } from '@/types/meta';
import type { PageMetaBody } from '@/types/meta-body';

interface TicketResponse {
  id: number;
  ticketNo: string;
  title: string;
  content?: string;
  priority?: string;
  status: string;
  category?: string;
  assigneeId?: string;
  createdAt: string;
  updatedAt: string;
  closedAt?: string;
  requestTypeCode?: string;
  workflowInstanceId?: number;
}

const route = useRoute();
const router = useRouter();
const ticketId = computed(() => Number(route.params.id));

// 1) 티켓 단건 fetch (액션 후 워크플로우가 티켓을 CLOSED 로 바꿀 수 있어 reload 노출).
const {
  data: ticketEnv,
  isFetching: isTicketLoading,
  error: ticketError,
  execute: reloadTicket,
} = useApiFetch<ApiEnvelope<TicketResponse>>(
  computed(() => `/api/tickets/${ticketId.value}`),
  { refetch: true },
).json<ApiEnvelope<TicketResponse>>();

const ticket = computed<TicketResponse | null>(() => ticketEnv.value?.data ?? null);

// 2) 폼 화면 복원 — 요청 유형의 form_meta_group_id 로 메타를 찾는다(meta-driven, 도메인 하드코딩 없음).
//    ticket 은 pageMetaIdAtRegistration 을 노출하지 않으므로 requestTypeCode → 요청 유형 → 폼 그룹으로 해석한다.
const rtCode = computed(() => ticket.value?.requestTypeCode ?? '');
const rtUrl = computed(() =>
  rtCode.value ? `/api/ticket-request-types/${encodeURIComponent(rtCode.value)}` : '',
);
const { data: rtEnv } = useApiFetch(rtUrl, { refetch: true }).json<
  ApiEnvelope<{ formMetaGroupId: string }>
>();

// 요청 유형이 있으면 그 폼 그룹, 없으면 기본 티켓 그룹으로 폴백.
const formGroupId = computed<string>(
  () => rtEnv.value?.data?.formMetaGroupId ?? (rtCode.value ? '' : 'itg-ticket'),
);
const ident = computed<MetaIdent>(() => ({ groupId: formGroupId.value }));
const { meta: formMeta, error: metaError } = usePageMeta(ident);

const body = computed<PageMetaBody | null>(() => {
  const m = formMeta.value;
  if (!m) return null;
  try {
    return asPageMetaBody(m.id, m.metaJson);
  } catch (e) {
    if (e instanceof MetaBodyShapeError) return null;
    throw e;
  }
});

const initialValues = computed<Record<string, unknown>>(() => {
  const t = ticket.value;
  if (!t) return {};
  return {
    title: t.title,
    content: t.content,
    priority: t.priority,
    category: t.category,
    assigneeId: t.assigneeId,
  };
});

// 상세 폼은 이력 보기 의도(read-only). 저장(PATCH/PUT)은 별도 phase 의 ADR.
function onSubmit(): void {
  // no-op
}

// 워크플로우 액션 성공 → 티켓 재조회(상태·closedAt 갱신).
async function onActed(): Promise<void> {
  await reloadTicket();
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <div class="flex gap-2">
      <Button
        variant="outline"
        @click="router.push('/itsm')"
      >
        ← 목록으로
      </Button>
    </div>

    <p
      v-if="isTicketLoading"
      class="text-foreground-muted"
    >
      티켓 조회 중…
    </p>
    <Card
      v-else-if="ticketError"
      class="border-danger"
    >
      <CardContent class="py-6 text-danger">
        {{ ticketError.message ?? '조회 실패' }}
      </CardContent>
    </Card>

    <template v-else-if="ticket">
      <Card>
        <CardHeader>
          <CardTitle>{{ ticket.title }} ({{ ticket.ticketNo }})</CardTitle>
        </CardHeader>
        <CardContent>
          <dl class="grid grid-cols-2 gap-2 text-[13px]">
            <dt class="text-foreground-muted">
              상태
            </dt>
            <dd>{{ ticket.status }}</dd>
            <dt class="text-foreground-muted">
              우선순위
            </dt>
            <dd>{{ ticket.priority ?? '-' }}</dd>
            <dt class="text-foreground-muted">
              요청 유형
            </dt>
            <dd>{{ ticket.requestTypeCode ?? '-' }}</dd>
            <dt class="text-foreground-muted">
              담당자
            </dt>
            <dd>{{ ticket.assigneeId ?? '-' }}</dd>
            <dt class="text-foreground-muted">
              분류
            </dt>
            <dd>{{ ticket.category ?? '-' }}</dd>
            <dt class="text-foreground-muted">
              등록일
            </dt>
            <dd>{{ ticket.createdAt }}</dd>
          </dl>
        </CardContent>
      </Card>

      <!-- 워크플로우 패널 (단계 진행·액션·이력) -->
      <WorkflowPanel
        :ticket-id="ticketId"
        @acted="onActed"
      />

      <!-- 요청 유형 폼 메타로 화면 복원 (이력 보기 — submit no-op) -->
      <Card v-if="body">
        <CardHeader>
          <CardTitle>
            폼 화면 복원 — {{ formMeta?.title }}
            <span class="ml-2 text-[12px] font-normal text-foreground-muted">
              (요청 유형 메타로 렌더링)
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <DynamicForm
            :meta="body.form"
            :initial-values="initialValues"
            @submit="onSubmit"
            @cancel="router.push('/itsm')"
          />
        </CardContent>
      </Card>
      <p
        v-else-if="metaError"
        class="text-danger"
      >
        {{ metaError }}
      </p>
    </template>
  </section>
</template>
