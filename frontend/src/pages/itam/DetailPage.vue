<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select';
import { useApiFetch } from '@/lib/api';
import { usePageMeta, type MetaIdent } from '@/composables/usePageMeta';
import { useDataMutation } from '@/composables/useDataMutation';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import { UI } from '@/lib/ui-messages';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import Skeleton from '@/components/feedback/Skeleton.vue';
import EventTimeline, { type TimelineItem, type TimelineTone } from '@/components/dataviz/EventTimeline.vue';
import type { ApiEnvelope, MetaStatus } from '@/types/meta';
import type { PageMetaBody } from '@/types/meta-body';
import type { LifecycleEvent, LifecycleEventType } from '@/types/asset-category';

interface AssetResponse {
  id: number;
  assetNo: string;
  name: string;
  assetType: string;
  status: string;
  model?: string;
  serialNo?: string;
  category?: string;
  assigneeId?: string;
  location?: string;
  acquiredAt?: string;
  disposedAt?: string;
  pageMetaIdAtRegistration: string;
  createdAt: string;
  updatedAt: string;
}

const route = useRoute();
const router = useRouter();
const assetId = computed(() => String(route.params.id));

// 1) 자산 단건 fetch
const {
  data: assetEnv,
  isFetching: isAssetLoading,
  error: assetError,
} = useApiFetch<ApiEnvelope<AssetResponse>>(
  computed(() => `/api/assets/${assetId.value}`),
  { refetch: true },
).json<ApiEnvelope<AssetResponse>>();

const asset = computed<AssetResponse | null>(() => assetEnv.value?.data ?? null);

// 2) 등록 시점 메타 fetch (metaId override 모드 — step 0). 현재 PUBLISHED 와 다를 수 있다.
const metaIdAtReg = computed(() => asset.value?.pageMetaIdAtRegistration ?? '');
const ident = computed<MetaIdent>(() => ({ metaId: metaIdAtReg.value }));
const { meta: registrationMeta, error: metaError } = usePageMeta(ident);

// 3) 메타 본문 강타입 좁히기 — 실패 시 폼을 깨지 않고 null 처리.
const body = computed<PageMetaBody | null>(() => {
  const m = registrationMeta.value;
  if (!m) return null;
  try {
    return asPageMetaBody(m.id, m.metaJson);
  } catch (e) {
    if (e instanceof MetaBodyShapeError) return null;
    throw e;
  }
});

// 4) 폼 초기값 — 자산의 속성을 폼 field name 으로 매핑하여 prefil.
const initialValues = computed<Record<string, unknown>>(() => {
  const a = asset.value;
  if (!a) return {};
  return {
    name: a.name,
    assetType: a.assetType,
    category: a.category,
    model: a.model,
    serialNo: a.serialNo,
    assigneeId: a.assigneeId,
    location: a.location,
    acquiredAt: a.acquiredAt,
    pageGroupId: 'itg-asset',
  };
});

// 메타 버전 라벨 색 — UI_GUIDE §5-5 시맨틱 토큰만 사용.
function statusColor(s: MetaStatus | undefined): string {
  if (s === 'PUBLISHED') return 'text-success';
  if (s === 'DRAFT') return 'text-warning';
  if (s === 'DEPRECATED') return 'text-neutral';
  if (s === 'ARCHIVED') return 'text-foreground-subtle';
  return 'text-foreground-muted';
}

// 본 phase 의 상세 폼은 이력 보기 의도(read-only). 저장(PATCH/PUT)은 별도 phase 의 ADR.
function onSubmit(): void {
  // no-op — 이력 보기 모드는 저장 비활성
}

// ── 라이프사이클 이벤트 (PRD §4-3 · ARCHITECTURE §14-3) ──────────────────────
const {
  data: eventsEnv,
  isFetching: isEventsLoading,
  error: eventsError,
  execute: reloadEvents,
} = useApiFetch<ApiEnvelope<LifecycleEvent[]>>(
  computed(() => `/api/assets/${assetId.value}/lifecycle-events`),
  { refetch: true },
).json<ApiEnvelope<LifecycleEvent[]>>();

const events = computed<LifecycleEvent[]>(() => eventsEnv.value?.data ?? []);

const EVENT_TYPES: { value: LifecycleEventType; label: string }[] = [
  { value: 'ACQUIRED', label: '취득' },
  { value: 'TRANSFERRED', label: '이관' },
  { value: 'REPAIRED', label: '수리' },
  { value: 'DISPOSED', label: '폐기' },
  { value: 'RENEWED', label: '갱신' },
];
function eventTypeLabel(t: LifecycleEventType): string {
  return EVENT_TYPES.find((e) => e.value === t)?.label ?? t;
}

/** 이벤트 타입별 권장 payload 필드 가이드 — raw textarea 대신 안내된 입력 제공(금지사항 반영). */
interface PayloadFieldGuide {
  key: string;
  label: string;
  placeholder?: string;
}
const RECOMMENDED_FIELDS: Record<LifecycleEventType, PayloadFieldGuide[]> = {
  ACQUIRED: [
    { key: 'vendor', label: '취득처', placeholder: 'SAMPLE-공급사' },
    { key: 'amount', label: '취득 금액' },
    { key: 'note', label: '비고' },
  ],
  TRANSFERRED: [
    { key: 'fromLocation', label: '이관 전 위치', placeholder: 'SAMPLE-3층 IT실' },
    { key: 'toLocation', label: '이관 후 위치', placeholder: 'SAMPLE-5층 영업팀' },
    { key: 'toUserId', label: '인수자 사용자 ID' },
  ],
  REPAIRED: [
    { key: 'vendor', label: '수리 업체', placeholder: 'SAMPLE-수리센터' },
    { key: 'symptom', label: '증상' },
    { key: 'cost', label: '수리 비용' },
  ],
  DISPOSED: [
    { key: 'reason', label: '폐기 사유' },
    { key: 'method', label: '폐기 방법', placeholder: '예: 자산 반납 / 매각' },
  ],
  RENEWED: [
    { key: 'contractNo', label: '계약 번호', placeholder: 'SAMPLE-CONTRACT-0001' },
    { key: 'expireDate', label: '갱신 만료일', placeholder: 'YYYY-MM-DD' },
  ],
};

const eventDialogOpen = ref(false);
const eventType = ref<LifecycleEventType>('TRANSFERRED');
const byUserId = ref('');
const payloadValues = ref<Record<string, string>>({});

const recommendedFields = computed<PayloadFieldGuide[]>(
  () => RECOMMENDED_FIELDS[eventType.value],
);

// 이벤트 타입 변경 시 입력값 초기화 — 이전 타입의 키가 payload 에 섞이지 않도록.
watch(eventType, () => {
  payloadValues.value = {};
});

function openEventDialog(): void {
  eventType.value = 'TRANSFERRED';
  byUserId.value = '';
  payloadValues.value = {};
  eventDialogOpen.value = true;
}

const { submit: submitEvent, isLoading: isEventSubmitting } = useDataMutation<
  {
    eventType: LifecycleEventType;
    byUserId: number | null;
    payload: Record<string, unknown> | null;
  },
  LifecycleEvent
>();

async function onRecordEvent(): Promise<void> {
  const payload: Record<string, unknown> = {};
  for (const f of recommendedFields.value) {
    const v = payloadValues.value[f.key]?.trim();
    if (v) payload[f.key] = v;
  }
  const idText = byUserId.value.trim();
  const result = await submitEvent(`/api/assets/${assetId.value}/lifecycle-events`, {
    eventType: eventType.value,
    byUserId: idText ? Number(idText) : null,
    payload: Object.keys(payload).length ? payload : null,
  });
  if (result) {
    toast.success('이력 이벤트가 기록되었습니다.');
    eventDialogOpen.value = false;
    await reloadEvents();
  } else {
    toast.error(UI.error.submit);
  }
}

// payload 객체를 "키: 값" 한 줄 요약으로 — 타임라인 설명 표시용.
function payloadSummary(p: Record<string, unknown> | null): string {
  if (!p) return '-';
  const entries = Object.entries(p);
  if (entries.length === 0) return '-';
  return entries.map(([k, v]) => `${k}: ${String(v)}`).join(', ');
}

// 이벤트 타입별 시맨틱 톤 (UI_GUIDE §3-6 — 상태=시맨틱 색).
const EVENT_TONE: Record<LifecycleEventType, TimelineTone> = {
  ACQUIRED: 'success',
  TRANSFERRED: 'info',
  REPAIRED: 'warning',
  DISPOSED: 'neutral',
  RENEWED: 'success',
};

// 라이프사이클 이벤트 → EventTimeline 항목 (dl 형태 폐기, 세로 타임라인으로 교체).
const timelineItems = computed<TimelineItem[]>(() =>
  events.value.map((e) => ({
    id: e.id,
    badge: eventTypeLabel(e.eventType),
    tone: EVENT_TONE[e.eventType] ?? 'info',
    time: e.eventDate,
    label: eventTypeLabel(e.eventType),
    description: payloadSummary(e.payload),
    user: e.byUserId ? `처리자 ${e.byUserId}` : undefined,
  })),
);
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <div class="flex gap-2">
      <Button
        variant="outline"
        @click="router.push('/itam')"
      >
        ← 목록으로
      </Button>
    </div>

    <!-- 자산 단건 로딩 스켈레톤 — 상세 카드 자리(제목 + 속성 dl) (UI_GUIDE §9). -->
    <Card
      v-if="isAssetLoading"
      aria-busy="true"
    >
      <CardHeader>
        <Skeleton
          width="42%"
          height="1.25rem"
        />
      </CardHeader>
      <CardContent>
        <div class="grid grid-cols-2 gap-3">
          <Skeleton
            v-for="i in 10"
            :key="i"
            height="0.9rem"
            :width="i % 2 === 1 ? '40%' : '70%'"
          />
        </div>
      </CardContent>
    </Card>
    <Card
      v-else-if="assetError"
      class="border-danger"
    >
      <CardContent class="py-6 text-danger">
        {{ assetError.message ?? '조회 실패' }}
      </CardContent>
    </Card>

    <Card v-else-if="asset">
      <CardHeader>
        <CardTitle>{{ asset.name }} ({{ asset.assetNo }})</CardTitle>
      </CardHeader>
      <CardContent>
        <dl class="grid grid-cols-2 gap-2 text-[13px]">
          <dt class="text-foreground-muted">
            유형
          </dt>
          <dd>{{ asset.assetType }}</dd>
          <dt class="text-foreground-muted">
            상태
          </dt>
          <dd>{{ asset.status }}</dd>
          <dt class="text-foreground-muted">
            모델
          </dt>
          <dd>{{ asset.model ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            시리얼
          </dt>
          <dd>{{ asset.serialNo ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            분류
          </dt>
          <dd>{{ asset.category ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            소유자
          </dt>
          <dd>{{ asset.assigneeId ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            위치
          </dt>
          <dd>{{ asset.location ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            취득일
          </dt>
          <dd>{{ asset.acquiredAt ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            폐기일
          </dt>
          <dd>{{ asset.disposedAt ?? '-' }}</dd>
          <dt class="text-foreground-muted">
            등록 메타
          </dt>
          <dd>
            <span class="font-mono text-[12px]">{{ asset.pageMetaIdAtRegistration }}</span>
            <span
              v-if="registrationMeta"
              :class="['ml-2 text-[12px]', statusColor(registrationMeta.metaStatus)]"
            >
              v{{ registrationMeta.majorVersion }}.{{ registrationMeta.minorVersion }}
              · {{ registrationMeta.metaStatus }}
            </span>
          </dd>
        </dl>
      </CardContent>
    </Card>

    <!-- 등록 시점 메타의 form.fields 로 폼 화면 복원 (M4 핵심 — 현재 PUBLISHED 와 다를 수 있음) -->
    <Card v-if="body && asset">
      <CardHeader>
        <CardTitle>
          폼 화면 복원 — {{ registrationMeta?.title }}
          <span class="ml-2 text-[12px] font-normal text-foreground-muted">
            (등록 시점 메타로 렌더링)
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <!-- 이력 보기 의도: submit 은 no-op (저장은 별도 phase 의 ADR) -->
        <DynamicForm
          :meta="body.form"
          :initial-values="initialValues"
          @submit="onSubmit"
          @cancel="router.push('/itam')"
        />
      </CardContent>
    </Card>

    <p
      v-else-if="metaError"
      class="text-danger"
    >
      {{ metaError }}
    </p>

    <!-- 라이프사이클 이벤트 타임라인 (취득·이관·수리·폐기·갱신) -->
    <Card v-if="asset">
      <CardHeader class="flex flex-row items-center justify-between space-y-0">
        <CardTitle>이력 이벤트</CardTitle>
        <Button
          size="sm"
          @click="openEventDialog"
        >
          이벤트 기록
        </Button>
      </CardHeader>
      <CardContent>
        <p
          v-if="isEventsLoading && events.length === 0"
          class="text-foreground-muted text-sm"
        >
          {{ UI.loading.data }}
        </p>
        <p
          v-else-if="eventsError"
          class="text-danger text-sm"
        >
          {{ eventsError.message ?? UI.error.dataLoad }}
        </p>
        <EventTimeline
          v-else
          :items="timelineItems"
          empty-text="아직 기록된 이력 이벤트가 없습니다."
        />
      </CardContent>
    </Card>

    <!-- 이벤트 기록 다이얼로그 — 타입별 권장 필드 가이드 노출 -->
    <Dialog v-model:open="eventDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>이력 이벤트 기록</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-1.5">
            <Label for="event-type">
              이벤트 유형
            </Label>
            <Select
              :model-value="eventType"
              @update:model-value="(v) => (eventType = v as LifecycleEventType)"
            >
              <SelectTrigger id="event-type">
                <SelectValue placeholder="유형 선택" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="t in EVENT_TYPES"
                  :key="t.value"
                  :value="t.value"
                >
                  {{ t.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-1.5">
            <Label for="event-by-user">
              처리자 사용자 ID (옵션)
            </Label>
            <Input
              id="event-by-user"
              v-model="byUserId"
              type="number"
              placeholder="예: 5"
            />
          </div>

          <div class="space-y-3">
            <p class="text-[12px] text-foreground-muted">
              {{ eventTypeLabel(eventType) }} 권장 입력 항목 (입력한 값만 저장됩니다)
            </p>
            <div
              v-for="f in recommendedFields"
              :key="f.key"
              class="space-y-1.5"
            >
              <Label :for="`payload-${f.key}`">
                {{ f.label }}
              </Label>
              <Input
                :id="`payload-${f.key}`"
                v-model="payloadValues[f.key]"
                :placeholder="f.placeholder"
              />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="eventDialogOpen = false"
          >
            취소
          </Button>
          <Button
            :disabled="isEventSubmitting"
            @click="onRecordEvent"
          >
            기록
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
