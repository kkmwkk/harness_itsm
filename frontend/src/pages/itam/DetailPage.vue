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
import type { ApiEnvelope, MetaStatus } from '@/types/meta';
import type { PageMetaBody } from '@/types/meta-body';

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

    <p
      v-if="isAssetLoading"
      class="text-foreground-muted"
    >
      자산 조회 중…
    </p>
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
  </section>
</template>
