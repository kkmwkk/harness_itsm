<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/lib/api';
import { usePageMeta, type MetaIdent } from '@/composables/usePageMeta';
import type { ApiEnvelope } from '@/types/meta';

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

// 2) 등록 시점 메타 fetch (metaId override 사용 — step 0 의 metaId 모드)
//    asset 이 로드된 뒤에 metaId 결정
const metaIdAtReg = computed(() => asset.value?.pageMetaIdAtRegistration ?? '');
const ident = computed<MetaIdent>(() => ({ metaId: metaIdAtReg.value }));
const { meta: registrationMeta, error: metaError } = usePageMeta(ident);

const metaJsonPreview = computed<string>(() =>
  registrationMeta.value ? JSON.stringify(registrationMeta.value.metaJson, null, 2) : '',
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
          <dd class="font-mono text-[12px]">
            {{ asset.pageMetaIdAtRegistration }}
          </dd>
        </dl>
      </CardContent>
    </Card>

    <!-- 등록 메타 카드: 어떤 상태든 표시 (step 2 에서 폼 복원으로 발전) -->
    <Card v-if="registrationMeta">
      <CardHeader>
        <CardTitle>
          등록 메타 — {{ registrationMeta.title }}
          <span class="text-[12px] font-normal text-foreground-muted">
            v{{ registrationMeta.majorVersion }}.{{ registrationMeta.minorVersion }}
            · {{ registrationMeta.metaStatus }}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <pre
          class="overflow-auto rounded-md bg-surface-muted p-3 font-mono text-[12px]"
        >{{ metaJsonPreview }}</pre>
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
