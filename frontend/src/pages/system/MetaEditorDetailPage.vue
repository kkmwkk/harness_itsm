<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import FormFieldEditor from '@/components/editor/FormFieldEditor.vue';
import GridColumnEditor from '@/components/editor/GridColumnEditor.vue';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/lib/api';
import { hasBlockingIssues, validateFormFields } from '@/composables/useFormFieldEditor';
import { hasBlockingColumnIssues, validateGridColumns } from '@/composables/useGridColumnEditor';
import type { ApiEnvelope, PageMeta } from '@/types/meta';
import type { PageMetaBody, FormMeta, GridMeta } from '@/types/meta-body';

/**
 * 특정 메타(metaId) 편집 상세 — No-code 폼 편집기(ADR-016 1단계).
 * 본 step(2)은 [폼 편집] 탭(FormFieldEditor)만 구현하고, 그리드·액션 탭은 후속 step(3·4)이 채운다.
 * 모든 편집은 client bodyDraft 에서 일어나고, 저장은 dry-run 통과 후 PUT /api/meta/{id}/body.
 * DRAFT 메타만 편집 가능(ADR-006) — PUBLISHED 등은 복사 후 편집.
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
});
const originalSnapshot = ref('');

type EditorTab = 'form' | 'grid' | 'actions' | 'preview';
const tab = ref<EditorTab>('form');
const TABS: ReadonlyArray<{ key: EditorTab; label: string }> = [
  { key: 'form', label: '폼 편집' },
  { key: 'grid', label: '그리드 편집' },
  { key: 'actions', label: '액션 편집' },
  { key: 'preview', label: '미리보기' },
];

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
  const actions = Array.isArray(json.actions) ? json.actions : undefined;
  const detail = json.detail as PageMetaBody['detail'] | undefined;
  return { api, grid, form, detail, actions };
}

function applyBody(body: PageMetaBody): void {
  bodyDraft.api = body.api;
  bodyDraft.grid = body.grid;
  bodyDraft.form = body.form;
  bodyDraft.detail = body.detail;
  bodyDraft.actions = body.actions;
  originalSnapshot.value = JSON.stringify(body);
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
const canSave = computed(
  () =>
    isDraft.value &&
    !hasBlockingIssues(bodyDraft.form.fields) &&
    !hasBlockingColumnIssues(bodyDraft.grid.columns, formFieldNames.value) &&
    !saving.value,
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
  } finally {
    loading.value = false;
  }
}

const saving = ref(false);

async function save(): Promise<void> {
  if (!meta.value || !isDraft.value) return;
  if (hasBlockingIssues(bodyDraft.form.fields)) {
    toast.error('필수 속성(name·라벨·타입)·중복 name 을 먼저 수정하세요.');
    return;
  }
  if (hasBlockingColumnIssues(bodyDraft.grid.columns, formFieldNames.value)) {
    toast.error('그리드 컬럼의 필수 속성(field·라벨·타입)·중복 field 를 먼저 수정하세요.');
    return;
  }
  saving.value = true;
  try {
    const body = stripDraft();
    // 1) dry-run — 분류 축 + 평탄화 본문으로 형식 검증(저장 전 필수).
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
      return;
    }
    const result = dryData.value?.data;
    if (!result?.valid) {
      const first = result?.issues?.find((i) => i.level === 'ERROR') ?? result?.issues?.[0];
      toast.error(first ? `검증 실패: ${first.message}` : '검증에 실패했습니다.');
      return;
    }
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
  } finally {
    saving.value = false;
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

// 미리보기용 FormMeta — bodyDraft.form 을 그대로 사용(라이브 반영).
const previewForm = computed<FormMeta>(() => bodyDraft.form);
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
          <div class="flex gap-2">
            <Button
              variant="outline"
              @click="goBack"
            >
              목록
            </Button>
            <Button
              :disabled="!canSave"
              @click="save"
            >
              {{ saving ? '저장 중...' : '저장' }}
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

        <!-- 액션 편집 (step 4) -->
        <Card v-else-if="tab === 'actions'">
          <CardContent class="py-8 text-center">
            <p class="text-sm text-foreground-muted">
              액션·발행 흐름은 다음 단계에서 제공됩니다.
            </p>
          </CardContent>
        </Card>

        <!-- 미리보기 -->
        <Card v-else-if="tab === 'preview'">
          <CardContent class="py-6">
            <p class="mb-4 text-xs text-foreground-muted">
              현재 편집 중인 폼의 라이브 미리보기 (저장 전 상태 반영)
            </p>
            <DynamicForm
              v-if="previewForm.fields.length"
              :meta="previewForm"
            />
            <p
              v-else
              class="text-sm text-foreground-muted"
            >
              표시할 필드가 없습니다.
            </p>
          </CardContent>
        </Card>
      </template>
    </section>
  </RequirePermission>
</template>
