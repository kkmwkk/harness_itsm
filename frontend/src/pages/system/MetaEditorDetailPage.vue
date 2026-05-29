<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import FormFieldEditor from '@/components/editor/FormFieldEditor.vue';
import GridColumnEditor from '@/components/editor/GridColumnEditor.vue';
import ActionEditor from '@/components/editor/ActionEditor.vue';
import MetaPreview from '@/components/editor/MetaPreview.vue';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useApiFetch } from '@/lib/api';
import { hasBlockingIssues, validateFormFields } from '@/composables/useFormFieldEditor';
import { hasBlockingColumnIssues, validateGridColumns } from '@/composables/useGridColumnEditor';
import { hasBlockingActionIssues, validateActions } from '@/composables/useActionEditor';
import type { ApiEnvelope, PageMeta } from '@/types/meta';
import type { PageMetaBody, FormMeta, GridMeta, ActionMeta } from '@/types/meta-body';

/**
 * 특정 메타(metaId) 편집 상세 — No-code 폼 편집기(ADR-016 1단계).
 * 폼·그리드·액션 3개 탭을 client bodyDraft 에서 편집하고, 미리보기는 사이드 패널로 제공한다.
 * 저장은 dry-run 통과 후 PUT /api/meta/{id}/body, 발행은 dry-run 검증된 상태에서만 PATCH publish.
 * DRAFT 메타만 편집 가능(ADR-006) — PUBLISHED 등은 "새 버전 만들기"(복사) 후 편집.
 */
const route = useRoute();
const router = useRouter();
const metaId = computed(() => String(route.params.metaId ?? ''));

const meta = ref<PageMeta | null>(null);
const loading = ref(false);
const loadError = ref(false);

const isDraft = computed(() => meta.value?.metaStatus === 'DRAFT');

// 편집 대상 본문(클라이언트 드래프트). 원본 스냅샷과 비교해 dirty 판정.
const bodyDraft = reactive<PageMetaBody>({
  api: '',
  grid: { columns: [] },
  form: { layout: 'two-column', fields: [] },
  actions: [],
});
const originalSnapshot = ref('');

type EditorTab = 'form' | 'grid' | 'actions';
const tab = ref<EditorTab>('form');
const TABS: ReadonlyArray<{ key: EditorTab; label: string }> = [
  { key: 'form', label: '폼 편집' },
  { key: 'grid', label: '그리드 편집' },
  { key: 'actions', label: '액션 편집' },
];

// 미리보기 사이드 패널 토글.
const showPreview = ref(false);

function normalizeBody(json: Record<string, unknown>): PageMetaBody {
  const api = typeof json.api === 'string' ? json.api : '';
  const rawGrid = json.grid as Partial<GridMeta> | undefined;
  const grid: GridMeta = {
    columns: Array.isArray(rawGrid?.columns) ? rawGrid.columns : [],
    inlineEdit: rawGrid?.inlineEdit,
    export: rawGrid?.export,
  };
  const rawForm = json.form as Partial<FormMeta> | undefined;
  const form: FormMeta = {
    layout: rawForm?.layout === 'single-column' ? 'single-column' : 'two-column',
    fields: Array.isArray(rawForm?.fields) ? rawForm.fields : [],
  };
  // 편집 편의를 위해 actions 는 항상 배열로 정규화(없으면 빈 배열).
  const actions = Array.isArray(json.actions) ? (json.actions as ActionMeta[]) : [];
  const detail = json.detail as PageMetaBody['detail'] | undefined;
  return { api, grid, form, detail, actions };
}

function applyBody(body: PageMetaBody): void {
  bodyDraft.api = body.api;
  bodyDraft.grid = body.grid;
  bodyDraft.form = body.form;
  bodyDraft.detail = body.detail;
  bodyDraft.actions = body.actions ?? [];
  // dirty 비교는 항상 stripDraft 직렬화 기준 — key 순서를 draft 와 일치시킨다.
  originalSnapshot.value = JSON.stringify(stripDraft());
}

const dirty = computed(() => JSON.stringify(stripDraft()) !== originalSnapshot.value);

/** reactive proxy 를 plain 객체로 — JSON 비교·전송용. */
function stripDraft(): PageMetaBody {
  return JSON.parse(JSON.stringify(bodyDraft)) as PageMetaBody;
}

const fieldIssues = computed(() => validateFormFields(bodyDraft.form.fields));
const formFieldNames = computed(() => bodyDraft.form.fields.map((f) => f.name));
const columnIssues = computed(() =>
  validateGridColumns(bodyDraft.grid.columns, formFieldNames.value),
);
const actionIssues = computed(() => validateActions(bodyDraft.actions ?? []));

// ActionEditor v-model 용 — actions 를 항상 배열로 노출(PageMetaBody.actions 는 optional).
const draftActions = computed<ActionMeta[]>({
  get: () => bodyDraft.actions ?? [],
  set: (v) => {
    bodyDraft.actions = v;
  },
});

const hasBlocking = computed(
  () =>
    hasBlockingIssues(bodyDraft.form.fields) ||
    hasBlockingColumnIssues(bodyDraft.grid.columns, formFieldNames.value) ||
    hasBlockingActionIssues(bodyDraft.actions ?? []),
);

const canSave = computed(() => isDraft.value && !hasBlocking.value && !saving.value);

// 발행 게이트 — dry-run 검증을 통과한 스냅샷에서만 발행 가능(ADR-006·금지: dry-run 누락 발행 금지).
const validatedSnapshot = ref<string | null>(null);
const canPublish = computed(
  () =>
    isDraft.value &&
    !dirty.value &&
    !hasBlocking.value &&
    validatedSnapshot.value === originalSnapshot.value &&
    !publishing.value,
);

async function loadMeta(): Promise<void> {
  if (!metaId.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    const { data, error } = await useApiFetch(
      `/api/meta/${encodeURIComponent(metaId.value)}`,
    ).json<ApiEnvelope<PageMeta>>();
    if (error.value || !data.value?.data) {
      loadError.value = true;
      toast.error('메타를 불러오지 못했습니다.');
      return;
    }
    meta.value = data.value.data;
    applyBody(normalizeBody(meta.value.metaJson));
    // 로드 직후 1회 dry-run — 무수정 유효 DRAFT 도 곧바로 발행 가능하도록 검증 스냅샷을 채운다.
    if (isDraft.value && !hasBlocking.value) void runDryRun();
  } finally {
    loading.value = false;
  }
}

const saving = ref(false);
const publishing = ref(false);
const copying = ref(false);
const archiving = ref(false);

/**
 * 현재 bodyDraft 를 dry-run 검증한다(DB 변경 없음). 통과하면 검증 스냅샷을 갱신한다.
 * 발행 게이트(canPublish)·저장(save) 양쪽이 공유한다.
 */
async function runDryRun(): Promise<boolean> {
  if (!meta.value) return false;
  const snapshot = JSON.stringify(stripDraft());
  const body = JSON.parse(snapshot) as PageMetaBody;
  const dryRunPayload = {
    id: meta.value.id,
    title: meta.value.title,
    systemType: meta.value.systemType,
    packageType: meta.value.packageType,
    groupId: meta.value.groupId,
    majorVersion: meta.value.majorVersion,
    minorVersion: meta.value.minorVersion,
    metaStatus: 'DRAFT',
    ...body,
  };
  const { data: dryData, error: dryError } = await useApiFetch('/api/meta/dry-run', {
    immediate: false,
    refetch: false,
  })
    .post(dryRunPayload)
    .json<ApiEnvelope<{ valid: boolean; issues: Array<{ level: string; message: string }> }>>();
  if (dryError.value) {
    toast.error('검증 요청에 실패했습니다.');
    return false;
  }
  const result = dryData.value?.data;
  if (!result?.valid) {
    const first = result?.issues?.find((i) => i.level === 'ERROR') ?? result?.issues?.[0];
    toast.error(first ? `검증 실패: ${first.message}` : '검증에 실패했습니다.');
    validatedSnapshot.value = null;
    return false;
  }
  validatedSnapshot.value = snapshot;
  return true;
}

async function save(): Promise<void> {
  if (!meta.value || !isDraft.value) return;
  if (hasBlocking.value) {
    toast.error('필수 속성(name/field·라벨·타입)·중복·액션 오류를 먼저 수정하세요.');
    return;
  }
  saving.value = true;
  try {
    const body = stripDraft();
    // 1) dry-run — 형식 검증(저장 전 필수).
    if (!(await runDryRun())) return;
    // 2) 본문 교체 — DRAFT 만 허용(백엔드 도메인 가드와 짝).
    const { error: putError } = await useApiFetch(
      `/api/meta/${encodeURIComponent(meta.value.id)}/body`,
      { immediate: false, refetch: false },
    )
      .put(body)
      .json<ApiEnvelope<PageMeta>>();
    if (putError.value) {
      toast.error('저장에 실패했습니다.');
      return;
    }
    toast.success('저장되었습니다.');
    originalSnapshot.value = JSON.stringify(body);
    validatedSnapshot.value = originalSnapshot.value;
  } finally {
    saving.value = false;
  }
}

// ── 발행 흐름 ─────────────────────────────────────────────────
const publishOpen = ref(false);

function openPublish(): void {
  if (!canPublish.value) return;
  publishOpen.value = true;
}

async function doPublish(): Promise<void> {
  if (!meta.value || !canPublish.value) return;
  publishing.value = true;
  try {
    const { error } = await useApiFetch(
      `/api/meta/${encodeURIComponent(meta.value.id)}/publish`,
      { immediate: false, refetch: false },
    )
      .patch()
      .json<ApiEnvelope<PageMeta>>();
    if (error.value) {
      toast.error('발행에 실패했습니다.');
      return;
    }
    publishOpen.value = false;
    toast.success('배포되었습니다. 기존 PUBLISHED 버전은 자동 보관(DEPRECATED)되었습니다.');
    // 발행 후 상태가 바뀌므로 목록으로 이동(dirty 아님 → 가드 통과).
    void router.push('/system/meta-editor');
  } finally {
    publishing.value = false;
  }
}

// ── 복사(새 버전 만들기) ──────────────────────────────────────
async function copyNewVersion(): Promise<void> {
  if (!meta.value || copying.value) return;
  copying.value = true;
  try {
    const { data, error } = await useApiFetch(
      `/api/meta/${encodeURIComponent(meta.value.id)}/copy`,
      { immediate: false, refetch: false },
    )
      .post()
      .json<ApiEnvelope<PageMeta>>();
    if (error.value || !data.value?.data) {
      toast.error('새 버전 생성에 실패했습니다.');
      return;
    }
    toast.success('새 DRAFT 버전이 생성되었습니다.');
    void router.push(`/system/meta-editor/${encodeURIComponent(data.value.data.id)}`);
  } finally {
    copying.value = false;
  }
}

// ── 보관 ──────────────────────────────────────────────────────
async function archive(): Promise<void> {
  if (!meta.value || archiving.value) return;
  archiving.value = true;
  try {
    const { error } = await useApiFetch(
      `/api/meta/${encodeURIComponent(meta.value.id)}/archive`,
      { immediate: false, refetch: false },
    )
      .patch()
      .json<ApiEnvelope<PageMeta>>();
    if (error.value) {
      toast.error('보관에 실패했습니다.');
      return;
    }
    toast.success('보관 처리되었습니다.');
    void router.push('/system/meta-editor');
  } finally {
    archiving.value = false;
  }
}

function goBack(): void {
  void router.push('/system/meta-editor');
}

// 변경사항이 있으면 이탈 전 확인(vue-router 가드).
onBeforeRouteLeave(() => {
  if (!dirty.value) return true;
  return window.confirm('저장하지 않은 변경사항이 있습니다. 페이지를 떠나시겠습니까?');
});

// metaId 가 바뀌면(같은 컴포넌트 재사용) 재로딩.
watch(metaId, () => void loadMeta());
onMounted(loadMeta);
</script>

<template>
  <RequirePermission code="META_EDIT">
    <template #fallback>
      <section class="space-y-4">
        <PageHeader title="메타 편집" />
        <Card>
          <CardContent class="py-8 text-center">
            <p class="text-sm text-foreground-muted">
              메타 편집 권한(META_EDIT)이 없어 접근할 수 없습니다.
            </p>
          </CardContent>
        </Card>
      </section>
    </template>

    <section class="space-y-4">
      <PageHeader :title="meta ? meta.title : '메타 편집'">
        <template #actions>
          <div class="flex flex-wrap gap-2">
            <Button
              variant="outline"
              @click="goBack"
            >
              ← 목록
            </Button>
            <Button
              variant="ghost"
              @click="showPreview = !showPreview"
            >
              {{ showPreview ? '미리보기 닫기' : '미리보기' }}
            </Button>
            <Button
              :disabled="!canSave"
              @click="save"
            >
              {{ saving ? '저장 중...' : '저장' }}
            </Button>
            <Button
              variant="secondary"
              :disabled="!canPublish"
              @click="openPublish"
            >
              {{ publishing ? '발행 중...' : '발행' }}
            </Button>
            <Button
              variant="outline"
              :disabled="copying"
              @click="copyNewVersion"
            >
              새 버전 만들기
            </Button>
            <Button
              v-if="meta && meta.metaStatus !== 'ARCHIVED'"
              variant="ghost"
              :disabled="archiving"
              @click="archive"
            >
              보관
            </Button>
          </div>
        </template>
      </PageHeader>

      <p
        v-if="loading"
        class="text-sm text-foreground-muted"
      >
        불러오는 중...
      </p>

      <Card v-else-if="loadError || !meta">
        <CardContent class="py-8 text-center">
          <p class="text-sm text-danger">
            메타를 불러올 수 없습니다.
          </p>
        </CardContent>
      </Card>

      <template v-else>
        <!-- 메타 정보 -->
        <Card>
          <CardContent class="flex flex-wrap items-center gap-x-4 gap-y-1 py-4">
            <span class="font-mono text-sm">{{ meta.id }}</span>
            <StatusBadge :value="meta.metaStatus" />
            <span class="text-xs text-foreground-muted">
              {{ meta.systemType }} · {{ meta.packageType }} ·
              v{{ meta.majorVersion }}.{{ meta.minorVersion }}
            </span>
            <span
              v-if="!isDraft"
              class="text-xs text-warning"
            >
              DRAFT 만 편집 가능합니다. 편집하려면 목록에서 복사 후 진행하세요.
            </span>
          </CardContent>
        </Card>

        <div
          class="items-start gap-4"
          :class="showPreview ? 'grid lg:grid-cols-2' : ''"
        >
          <!-- 편집 영역 (폼·그리드·액션 3개 탭) -->
          <div class="min-w-0 space-y-3">
            <!-- 탭 -->
            <div class="flex gap-1 border-b border-border">
              <button
                v-for="t in TABS"
                :key="t.key"
                type="button"
                class="border-b-2 px-3 py-2 text-sm"
                :class="
                  tab === t.key
                    ? 'border-primary font-semibold text-foreground'
                    : 'border-transparent text-foreground-muted hover:text-foreground'
                "
                @click="tab = t.key"
              >
                {{ t.label }}
              </button>
            </div>

            <!-- 폼 편집 -->
            <div v-if="tab === 'form'">
              <fieldset
                :disabled="!isDraft"
                class="space-y-3"
                :class="!isDraft ? 'opacity-60' : ''"
              >
                <FormFieldEditor v-model:fields="bodyDraft.form.fields" />
              </fieldset>
              <ul
                v-if="fieldIssues.length"
                class="mt-3 space-y-1"
              >
                <li
                  v-for="(i, n) in fieldIssues"
                  :key="n"
                  class="text-[12px]"
                  :class="i.level === 'ERROR' ? 'text-danger' : 'text-warning'"
                >
                  [{{ i.level }}] {{ i.message }}
                </li>
              </ul>
            </div>

            <!-- 그리드 편집 -->
            <div v-else-if="tab === 'grid'">
              <fieldset
                :disabled="!isDraft"
                class="space-y-3"
                :class="!isDraft ? 'opacity-60' : ''"
              >
                <GridColumnEditor
                  v-model:columns="bodyDraft.grid.columns"
                  v-model:inline-edit="bodyDraft.grid.inlineEdit"
                  v-model:export-enabled="bodyDraft.grid.export"
                  :form-field-names="formFieldNames"
                />
              </fieldset>
              <ul
                v-if="columnIssues.length"
                class="mt-3 space-y-1"
              >
                <li
                  v-for="(i, n) in columnIssues"
                  :key="n"
                  class="text-[12px]"
                  :class="i.level === 'ERROR' ? 'text-danger' : 'text-warning'"
                >
                  [{{ i.level }}] {{ i.message }}
                </li>
              </ul>
            </div>

            <!-- 액션 편집 -->
            <div v-else-if="tab === 'actions'">
              <fieldset
                :disabled="!isDraft"
                class="space-y-3"
                :class="!isDraft ? 'opacity-60' : ''"
              >
                <ActionEditor v-model:actions="draftActions" />
              </fieldset>
              <ul
                v-if="actionIssues.length"
                class="mt-3 space-y-1"
              >
                <li
                  v-for="(i, n) in actionIssues"
                  :key="n"
                  class="text-[12px]"
                  :class="i.level === 'ERROR' ? 'text-danger' : 'text-warning'"
                >
                  [{{ i.level }}] {{ i.message }}
                </li>
              </ul>
            </div>

            <p
              v-if="isDraft && !canPublish"
              class="text-[12px] text-foreground-muted"
            >
              발행하려면 먼저 저장(dry-run 검증)을 완료하세요. 변경 사항이 검증을 통과해야 발행할 수 있습니다.
            </p>
          </div>

          <!-- 미리보기 사이드 패널 -->
          <Card
            v-if="showPreview"
            class="min-w-0"
          >
            <CardContent class="py-4">
              <p class="mb-3 text-sm font-semibold">
                미리보기
              </p>
              <MetaPreview
                :meta-id="meta.id"
                :body="bodyDraft"
              />
            </CardContent>
          </Card>
        </div>
      </template>
    </section>

    <!-- 발행 확인 다이얼로그 -->
    <Dialog v-model:open="publishOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>이 버전을 발행할까요?</DialogTitle>
          <DialogDescription>
            발행하면 화면에 노출됩니다. 동일 그룹의 기존 PUBLISHED 버전은 자동으로
            보관(DEPRECATED) 처리됩니다(ADR-006).
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            @click="publishOpen = false"
          >
            취소
          </Button>
          <Button
            :disabled="publishing"
            @click="doPublish"
          >
            {{ publishing ? '발행 중...' : '발행' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </RequirePermission>
</template>
