<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { toast } from 'vue-sonner';
import { Trash2Icon } from '@lucide/vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import FormFieldEditor from '@/components/editor/FormFieldEditor.vue';
import GridColumnEditor from '@/components/editor/GridColumnEditor.vue';
import DynamicGrid from '@/components/dynamic/DynamicGrid.vue';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useApiFetch } from '@/lib/api';
import { buildMockRows } from '@/composables/useMetaPreview';
import { hasBlockingIssues } from '@/composables/useFormFieldEditor';
import { hasBlockingColumnIssues } from '@/composables/useGridColumnEditor';
import {
  setFieldLabel,
  removeFieldAt,
  setColumnLabel,
  setColumnWidth,
  removeColumnAt,
} from '@/composables/useWysiwygPreview';
import type { ApiEnvelope, PageMeta } from '@/types/meta';
import type { PageMetaBody, FormMeta, GridMeta, ActionMeta } from '@/types/meta-body';

/**
 * WYSIWYG PoC (ADR-016 3단계 — Stretch). 본격 page builder 가 아닌 평가용.
 *
 * 좌측: 기존 FormFieldEditor·GridColumnEditor 를 축약 패널로 재사용.
 * 우측: 편집 중 body draft 로 만든 실 미리보기(DynamicGrid + 폼 라벨) + 편집 모드 토글.
 *   - 편집 모드 ON: 폼 필드 라벨 클릭 → 인플레이스 input 으로 라벨 편집 + 삭제 아이콘.
 *                    그리드 컬럼 헤더 클릭 → 인플레이스 label·width 편집 + 삭제 아이콘.
 *   - 변경은 useWysiwygPreview 순수 헬퍼를 거쳐 body draft 의 같은 배열로 반영 → 좌측 패널 즉시 동기화.
 *
 * 한계: 신규 필드 추가·복잡 레이아웃은 좌측 패널에 의존. Builder.io/GrapeJS 수준 아님.
 */
const route = useRoute();
const router = useRouter();
const metaId = computed(() => String(route.params.metaId ?? ''));

const meta = ref<PageMeta | null>(null);
const loading = ref(false);
const loadError = ref(false);
const isDraft = computed(() => meta.value?.metaStatus === 'DRAFT');

// 우측 미리보기의 편집 가능 모드 토글.
const editMode = ref(true);

const bodyDraft = reactive<PageMetaBody>({
  api: '',
  grid: { columns: [] },
  form: { layout: 'two-column', fields: [] },
  actions: [],
});

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
}

const formFieldNames = computed(() => bodyDraft.form.fields.map((f) => f.name));
const mockRows = computed(() => buildMockRows(bodyDraft.grid.columns));
const hasBlocking = computed(
  () =>
    hasBlockingIssues(bodyDraft.form.fields) ||
    hasBlockingColumnIssues(bodyDraft.grid.columns, formFieldNames.value),
);

// ── 인플레이스 편집(우측 미리보기) — 순수 헬퍼를 거쳐 body draft 의 같은 배열로 반영 ──
function editFieldLabel(idx: number, label: string): void {
  bodyDraft.form.fields = setFieldLabel(bodyDraft.form.fields, idx, label);
}
function deleteField(idx: number): void {
  bodyDraft.form.fields = removeFieldAt(bodyDraft.form.fields, idx);
}
function editColumnLabel(idx: number, label: string): void {
  bodyDraft.grid.columns = setColumnLabel(bodyDraft.grid.columns, idx, label);
}
function editColumnWidth(idx: number, raw: string): void {
  bodyDraft.grid.columns = setColumnWidth(bodyDraft.grid.columns, idx, Number(raw));
}
function deleteColumn(idx: number): void {
  bodyDraft.grid.columns = removeColumnAt(bodyDraft.grid.columns, idx);
}

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

// ── 저장 (dry-run → PUT body) — MetaEditorDetailPage 와 동일 흐름 ──
const saving = ref(false);
const canSave = computed(() => isDraft.value && !hasBlocking.value && !saving.value);

function stripDraft(): PageMetaBody {
  return JSON.parse(JSON.stringify(bodyDraft)) as PageMetaBody;
}

async function save(): Promise<void> {
  if (!meta.value || !canSave.value) return;
  saving.value = true;
  try {
    const body = stripDraft();
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
    const {
      execute: execDryRun,
      data: dryData,
      error: dryError,
    } = useApiFetch('/api/meta/dry-run', { immediate: false, refetch: false })
      .post(dryRunPayload)
      .json<ApiEnvelope<{ valid: boolean; issues: Array<{ level: string; message: string }> }>>();
    // immediate:false 이므로 명시적으로 실행해야 요청이 나간다 (execute 없이 await 하면 hang).
    await execDryRun();
    if (dryError.value || !dryData.value?.data?.valid) {
      const first = dryData.value?.data?.issues?.find((i) => i.level === 'ERROR');
      toast.error(first ? `검증 실패: ${first.message}` : '검증에 실패했습니다.');
      return;
    }
    const { execute: execPut, error: putError } = useApiFetch(
      `/api/meta/${encodeURIComponent(meta.value.id)}/body`,
      { immediate: false, refetch: false },
    )
      .put(body)
      .json<ApiEnvelope<PageMeta>>();
    // immediate:false 이므로 명시적으로 실행해야 요청이 나간다 (execute 없이 await 하면 hang).
    await execPut();
    if (putError.value) {
      toast.error('저장에 실패했습니다.');
      return;
    }
    toast.success('저장되었습니다.');
  } finally {
    saving.value = false;
  }
}

function goEditor(): void {
  void router.push(`/system/meta-editor/${encodeURIComponent(metaId.value)}`);
}

watch(metaId, () => void loadMeta());
onMounted(loadMeta);

defineExpose({ editFieldLabel, deleteField, editColumnLabel, editColumnWidth, deleteColumn, bodyDraft });
</script>

<template>
  <RequirePermission code="META_EDIT">
    <template #fallback>
      <section class="space-y-4">
        <PageHeader title="WYSIWYG 미리보기 편집" />
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
      <PageHeader :title="meta ? `${meta.title} — WYSIWYG (PoC)` : 'WYSIWYG 미리보기 편집'">
        <template #actions>
          <div class="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              @click="goEditor"
            >
              ← 폼 편집기
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
        <Card>
          <CardContent class="flex flex-wrap items-center gap-x-4 gap-y-1 py-4">
            <span class="font-mono text-sm">{{ meta.id }}</span>
            <StatusBadge :value="meta.metaStatus" />
            <span
              v-if="!isDraft"
              class="text-xs text-warning"
            >
              DRAFT 만 편집·저장 가능합니다. 복사 후 진행하세요.
            </span>
          </CardContent>
        </Card>

        <div class="grid items-start gap-4 lg:grid-cols-2">
          <!-- 좌측: 기존 편집기 축약 패널 -->
          <div class="min-w-0 space-y-4">
            <p class="text-sm font-semibold">
              메타 편집 패널
            </p>
            <fieldset
              :disabled="!isDraft"
              class="space-y-4"
              :class="!isDraft ? 'opacity-60' : ''"
            >
              <details open>
                <summary class="cursor-pointer text-sm font-medium text-foreground-muted">
                  폼 필드
                </summary>
                <div class="mt-2">
                  <FormFieldEditor v-model:fields="bodyDraft.form.fields" />
                </div>
              </details>
              <details>
                <summary class="cursor-pointer text-sm font-medium text-foreground-muted">
                  그리드 컬럼
                </summary>
                <div class="mt-2">
                  <GridColumnEditor
                    v-model:columns="bodyDraft.grid.columns"
                    v-model:inline-edit="bodyDraft.grid.inlineEdit"
                    v-model:export-enabled="bodyDraft.grid.export"
                    :form-field-names="formFieldNames"
                  />
                </div>
              </details>
            </fieldset>
          </div>

          <!-- 우측: 실 미리보기 + 편집 모드 토글 -->
          <Card class="min-w-0">
            <CardContent class="space-y-4 py-4">
              <div class="flex items-center justify-between gap-2">
                <p class="text-sm font-semibold">
                  미리보기
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  :disabled="!isDraft"
                  aria-label="편집 모드 토글"
                  :aria-pressed="editMode"
                  @click="editMode = !editMode"
                >
                  편집 모드: {{ editMode ? 'ON' : 'OFF' }}
                </Button>
              </div>
              <p class="text-xs text-foreground-muted">
                {{
                  editMode
                    ? '라벨을 클릭해 직접 고치거나 휴지통으로 삭제하세요. 변경은 좌측 패널에 즉시 반영됩니다.'
                    : '실 화면과 동일하게 렌더링한 미리보기입니다(샘플 데이터).'
                }}
              </p>

              <!-- 그리드 미리보기 -->
              <div class="space-y-2">
                <p class="text-xs font-semibold text-foreground-muted">
                  목록(그리드)
                </p>
                <!-- 편집 모드: 헤더 인플레이스 편집 -->
                <div
                  v-if="editMode"
                  class="space-y-1 rounded-md border border-border p-2"
                >
                  <p
                    v-if="bodyDraft.grid.columns.length === 0"
                    class="py-3 text-center text-xs text-foreground-muted"
                  >
                    컬럼이 없습니다. 좌측 패널에서 추가하세요.
                  </p>
                  <div
                    v-for="(c, idx) in bodyDraft.grid.columns"
                    :key="idx"
                    class="flex items-center gap-2"
                  >
                    <Input
                      :model-value="c.label"
                      :aria-label="`컬럼 ${idx + 1} 라벨`"
                      class="flex-1"
                      placeholder="컬럼 라벨"
                      @update:model-value="(v) => editColumnLabel(idx, String(v ?? ''))"
                    />
                    <Input
                      :model-value="c.width"
                      :aria-label="`컬럼 ${idx + 1} 너비`"
                      type="number"
                      min="1"
                      class="w-24"
                      placeholder="px"
                      @update:model-value="(v) => editColumnWidth(idx, String(v ?? ''))"
                    />
                    <Button
                      variant="ghost"
                      size="icon"
                      :aria-label="`컬럼 ${idx + 1} 삭제`"
                      @click="deleteColumn(idx)"
                    >
                      <Trash2Icon class="size-4" />
                    </Button>
                  </div>
                </div>
                <!-- 보기 모드: 실 DynamicGrid -->
                <DynamicGrid
                  v-else
                  :meta="bodyDraft.grid"
                  :rows="mockRows"
                />
              </div>

              <!-- 폼 미리보기 -->
              <div class="space-y-2">
                <p class="text-xs font-semibold text-foreground-muted">
                  등록 폼
                </p>
                <p
                  v-if="bodyDraft.form.fields.length === 0"
                  class="py-3 text-center text-xs text-foreground-muted"
                >
                  필드가 없습니다. 좌측 패널에서 추가하세요.
                </p>
                <div
                  v-for="(f, idx) in bodyDraft.form.fields"
                  :key="idx"
                  class="space-y-1"
                >
                  <!-- 편집 모드: 라벨 인플레이스 + 삭제 -->
                  <div
                    v-if="editMode"
                    class="flex items-center gap-2"
                  >
                    <Input
                      :model-value="f.label"
                      :aria-label="`필드 ${idx + 1} 라벨`"
                      class="flex-1"
                      placeholder="필드 라벨"
                      @update:model-value="(v) => editFieldLabel(idx, String(v ?? ''))"
                    />
                    <span class="font-mono text-[11px] text-foreground-subtle">{{ f.type }}</span>
                    <Button
                      variant="ghost"
                      size="icon"
                      :aria-label="`필드 ${idx + 1} 삭제`"
                      @click="deleteField(idx)"
                    >
                      <Trash2Icon class="size-4" />
                    </Button>
                  </div>
                  <!-- 보기 모드: 라벨 + 비활성 입력 placeholder -->
                  <template v-else>
                    <label class="text-[14px] font-medium">
                      {{ f.label }}<span
                        v-if="f.required"
                        class="ml-0.5 text-danger"
                      >*</span>
                    </label>
                    <Input
                      disabled
                      :placeholder="f.placeholder ?? `(${f.type})`"
                    />
                  </template>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </template>
    </section>
  </RequirePermission>
</template>
