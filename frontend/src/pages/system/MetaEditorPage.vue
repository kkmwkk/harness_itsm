<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import type { ColumnDef } from '@tanstack/vue-table';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
import { DataTable } from '@/components/ui/data-table';
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableEmpty,
} from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useApiFetch } from '@/lib/api';
import type {
  ApiEnvelope,
  PageMeta,
  PageMetaGroup,
  PageMetaVersion,
  SystemType,
  PackageType,
} from '@/types/meta';

/**
 * No-code 메타 편집기 진입점 (M9 / ADR-016 1단계).
 * 좌: group_id 단위 목록(GET /api/meta/groups). 우: 선택 그룹의 버전 이력(versions).
 * 신규 그룹 생성(POST /api/meta, DRAFT)·복사·발행·보관·미리보기 + DRAFT 편집 진입.
 * 화면 가드는 META_EDIT (백엔드 @PreAuthorize 와 짝).
 */
const router = useRouter();

const SYSTEM_TYPES: SystemType[] = ['ITSM', 'ITAM', 'PMS', 'COMMON', 'SYSTEM'];
const PACKAGE_TYPES: PackageType[] = ['PACKAGE', 'CUSTOM'];

// ── 그룹 목록 ─────────────────────────────────────────────────
const groups = ref<PageMetaGroup[]>([]);
const groupsLoading = ref(false);
const selectedGroupId = ref<string | null>(null);

const groupColumns: ColumnDef<PageMetaGroup>[] = [
  { accessorKey: 'groupId', header: '그룹 ID' },
  { accessorKey: 'systemType', header: '모듈', size: 80 },
  {
    id: 'published',
    header: '배포 버전',
    accessorFn: (g) => g.publishedVersion ?? '—',
    size: 90,
  },
];

async function loadGroups(): Promise<void> {
  groupsLoading.value = true;
  try {
    const { data, error } = await useApiFetch('/api/meta/groups').json<
      ApiEnvelope<PageMetaGroup[]>
    >();
    if (error.value) {
      toast.error('메타 그룹 목록을 불러오지 못했습니다.');
      return;
    }
    groups.value = data.value?.data ?? [];
    // 선택이 없거나 사라진 그룹이면 첫 그룹을 자동 선택.
    const stillExists = groups.value.some((g) => g.groupId === selectedGroupId.value);
    if (!stillExists) {
      selectedGroupId.value = groups.value[0]?.groupId ?? null;
    }
    if (selectedGroupId.value) await loadVersions(selectedGroupId.value);
  } finally {
    groupsLoading.value = false;
  }
}

function selectGroup(g: PageMetaGroup): void {
  selectedGroupId.value = g.groupId;
  void loadVersions(g.groupId);
}

// ── 버전 이력 ─────────────────────────────────────────────────
const versions = ref<PageMetaVersion[]>([]);
const versionsLoading = ref(false);

const selectedGroup = computed<PageMetaGroup | null>(
  () => groups.value.find((g) => g.groupId === selectedGroupId.value) ?? null,
);

async function loadVersions(groupId: string): Promise<void> {
  versionsLoading.value = true;
  try {
    const { data, error } = await useApiFetch(
      `/api/meta/group/${encodeURIComponent(groupId)}/versions`,
    ).json<ApiEnvelope<PageMetaVersion[]>>();
    if (error.value) {
      toast.error('버전 이력을 불러오지 못했습니다.');
      versions.value = [];
      return;
    }
    versions.value = data.value?.data ?? [];
  } finally {
    versionsLoading.value = false;
  }
}

function versionLabel(v: PageMetaVersion): string {
  return `v${v.majorVersion}.${v.minorVersion}`;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return iso.replace('T', ' ').slice(0, 16);
}

// ── 행 액션 (편집/복사/발행/보관) ─────────────────────────────
/** DRAFT 만 직접 편집 가능 (ADR-006 — PUBLISHED 등은 복사 후 편집). */
function editDraft(v: PageMetaVersion): void {
  // 편집 상세 화면(FieldEditor·GridColumnEditor·publish flow)은 후속 step 에서 라우트 등록.
  void router.push(`/system/meta-editor/${encodeURIComponent(v.id)}`);
}

async function copyVersion(v: PageMetaVersion): Promise<void> {
  const { error } = await useApiFetch(
    `/api/meta/${encodeURIComponent(v.id)}/copy`,
    { immediate: false, refetch: false },
  )
    .post()
    .json<ApiEnvelope<PageMeta>>();
  if (error.value) {
    toast.error('복사에 실패했습니다.');
    return;
  }
  toast.success('새 DRAFT 버전이 생성되었습니다.');
  await loadGroups();
}

async function publishVersion(v: PageMetaVersion): Promise<void> {
  const { error } = await useApiFetch(
    `/api/meta/${encodeURIComponent(v.id)}/publish`,
    { immediate: false, refetch: false },
  )
    .patch()
    .json<ApiEnvelope<PageMeta>>();
  if (error.value) {
    toast.error('발행에 실패했습니다.');
    return;
  }
  toast.success('배포되었습니다.');
  await loadGroups();
}

async function archiveVersion(v: PageMetaVersion): Promise<void> {
  const { error } = await useApiFetch(
    `/api/meta/${encodeURIComponent(v.id)}/archive`,
    { immediate: false, refetch: false },
  )
    .patch()
    .json<ApiEnvelope<PageMeta>>();
  if (error.value) {
    toast.error('보관에 실패했습니다.');
    return;
  }
  toast.success('보관 처리되었습니다.');
  await loadGroups();
}

// ── 미리보기 ──────────────────────────────────────────────────
const previewOpen = ref(false);
const previewMetaId = ref<string | null>(null);

function openPreview(v: PageMetaVersion): void {
  previewMetaId.value = v.id;
  previewOpen.value = true;
}

// ── 신규 그룹 만들기 ──────────────────────────────────────────
interface CreateForm {
  groupId: string;
  title: string;
  systemType: SystemType;
  packageType: PackageType;
  major: number;
  minor: number;
  api: string;
}
const createOpen = ref(false);
const createForm = reactive<CreateForm>({
  groupId: '',
  title: '',
  systemType: 'ITSM',
  packageType: 'PACKAGE',
  major: 1,
  minor: 1,
  api: '',
});

function openCreate(): void {
  Object.assign(createForm, {
    groupId: '',
    title: '',
    systemType: 'ITSM',
    packageType: 'PACKAGE',
    major: 1,
    minor: 1,
    api: '',
  });
  createOpen.value = true;
}

async function submitCreate(): Promise<void> {
  const groupId = createForm.groupId.trim();
  const title = createForm.title.trim();
  if (!groupId || !title) {
    toast.error('그룹 ID·타이틀은 필수입니다.');
    return;
  }
  if (!/^[A-Za-z0-9-]+$/.test(groupId)) {
    toast.error('그룹 ID 는 영숫자·하이픈만 허용됩니다.');
    return;
  }
  const api = createForm.api.trim() || `/api/${groupId}`;
  const payload = {
    id: `${groupId}-v${createForm.major}-${createForm.minor}`,
    title,
    systemType: createForm.systemType,
    packageType: createForm.packageType,
    groupId,
    majorVersion: createForm.major,
    minorVersion: createForm.minor,
    // 빈 골격 — 필드·컬럼은 후속 step 의 편집 UI 에서 채운다.
    metaJson: {
      api,
      grid: { columns: [] },
      form: { layout: 'two-column', fields: [] },
    },
  };
  const { error } = await useApiFetch('/api/meta', {
    immediate: false,
    refetch: false,
  })
    .post(payload)
    .json<ApiEnvelope<PageMeta>>();
  if (error.value) {
    toast.error('메타 그룹 생성에 실패했습니다.');
    return;
  }
  toast.success('새 메타 그룹(DRAFT)이 생성되었습니다.');
  createOpen.value = false;
  selectedGroupId.value = groupId;
  await loadGroups();
}

onMounted(loadGroups);
</script>

<template>
  <RequirePermission code="META_EDIT">
    <template #fallback>
      <section class="space-y-4">
        <PageHeader title="메타 편집기" />
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
      <PageHeader title="메타 편집기">
        <template #actions>
          <Button @click="openCreate">
            신규 그룹 만들기
          </Button>
        </template>
      </PageHeader>

      <div class="grid gap-4 lg:grid-cols-[340px_1fr]">
        <!-- 좌: 그룹 목록 -->
        <Card>
          <CardContent class="space-y-2 py-4">
            <p class="text-sm font-semibold">
              메타 그룹
            </p>
            <p
              v-if="groupsLoading"
              class="text-sm text-foreground-muted"
            >
              불러오는 중...
            </p>
            <DataTable
              v-else
              :rows="groups"
              :columns="groupColumns"
              :page-size="0"
              selectable
              density="compact"
              @row-click="selectGroup"
            />
          </CardContent>
        </Card>

        <!-- 우: 선택 그룹의 버전 이력 -->
        <Card>
          <CardContent class="space-y-3 py-4">
            <div
              v-if="selectedGroup"
              class="space-y-0.5"
            >
              <p class="text-base font-semibold">
                {{ selectedGroup.title }}
                <span class="font-mono text-xs text-foreground-muted">({{ selectedGroup.groupId }})</span>
              </p>
              <p class="text-xs text-foreground-muted">
                {{ selectedGroup.systemType }} · {{ selectedGroup.packageType }} ·
                버전 {{ selectedGroup.versionCount }}개
              </p>
            </div>
            <p
              v-else
              class="text-sm text-foreground-muted"
            >
              왼쪽에서 메타 그룹을 선택하세요.
            </p>

            <Table v-if="selectedGroup">
              <TableHeader>
                <TableRow>
                  <TableHead class="w-20">
                    버전
                  </TableHead>
                  <TableHead class="w-24">
                    상태
                  </TableHead>
                  <TableHead class="w-40">
                    수정일
                  </TableHead>
                  <TableHead>작업</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableEmpty
                  v-if="!versionsLoading && versions.length === 0"
                  :colspan="4"
                >
                  버전이 없습니다.
                </TableEmpty>
                <TableRow
                  v-for="v in versions"
                  :key="v.id"
                >
                  <TableCell class="font-mono">
                    {{ versionLabel(v) }}
                  </TableCell>
                  <TableCell>
                    <StatusBadge :value="v.metaStatus" />
                  </TableCell>
                  <TableCell class="text-xs text-foreground-muted">
                    {{ formatDate(v.updatedAt) }}
                  </TableCell>
                  <TableCell>
                    <div class="flex flex-wrap gap-1.5">
                      <Button
                        v-if="v.metaStatus === 'DRAFT'"
                        size="sm"
                        @click="editDraft(v)"
                      >
                        편집
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        @click="openPreview(v)"
                      >
                        미리보기
                      </Button>
                      <Button
                        v-if="v.metaStatus === 'DRAFT'"
                        variant="secondary"
                        size="sm"
                        @click="publishVersion(v)"
                      >
                        발행
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        @click="copyVersion(v)"
                      >
                        복사
                      </Button>
                      <Button
                        v-if="v.metaStatus !== 'ARCHIVED'"
                        variant="ghost"
                        size="sm"
                        @click="archiveVersion(v)"
                      >
                        보관
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </section>

    <!-- 신규 그룹 만들기 다이얼로그 -->
    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>신규 메타 그룹</DialogTitle>
          <DialogDescription>빈 DRAFT 메타를 생성합니다. 필드·컬럼은 편집기에서 채웁니다.</DialogDescription>
        </DialogHeader>
        <form
          class="space-y-3"
          @submit.prevent="submitCreate"
        >
          <div class="space-y-1">
            <Label for="g-group">그룹 ID *</Label>
            <Input
              id="g-group"
              v-model="createForm.groupId"
              placeholder="예: itg-proposal"
              autocomplete="off"
            />
          </div>
          <div class="space-y-1">
            <Label for="g-title">타이틀 *</Label>
            <Input
              id="g-title"
              v-model="createForm.title"
              placeholder="예: 제안 요청"
            />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <Label for="g-system">systemType</Label>
              <select
                id="g-system"
                v-model="createForm.systemType"
                class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              >
                <option
                  v-for="s in SYSTEM_TYPES"
                  :key="s"
                  :value="s"
                >
                  {{ s }}
                </option>
              </select>
            </div>
            <div class="space-y-1">
              <Label for="g-package">packageType</Label>
              <select
                id="g-package"
                v-model="createForm.packageType"
                class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              >
                <option
                  v-for="p in PACKAGE_TYPES"
                  :key="p"
                  :value="p"
                >
                  {{ p }}
                </option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <Label for="g-major">majorVersion</Label>
              <Input
                id="g-major"
                v-model.number="createForm.major"
                type="number"
                min="1"
              />
            </div>
            <div class="space-y-1">
              <Label for="g-minor">minorVersion</Label>
              <Input
                id="g-minor"
                v-model.number="createForm.minor"
                type="number"
                min="1"
              />
            </div>
          </div>
          <div class="space-y-1">
            <Label for="g-api">api (선택 — 비우면 /api/{groupId})</Label>
            <Input
              id="g-api"
              v-model="createForm.api"
              placeholder="예: /api/proposals"
            />
          </div>
        </form>
        <DialogFooter>
          <Button
            variant="outline"
            @click="createOpen = false"
          >
            취소
          </Button>
          <Button @click="submitCreate">
            생성
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 미리보기 다이얼로그 -->
    <Dialog v-model:open="previewOpen">
      <DialogContent class="max-w-4xl">
        <DialogHeader>
          <DialogTitle>미리보기</DialogTitle>
          <DialogDescription>
            <span class="font-mono">{{ previewMetaId }}</span> 의 화면 렌더 (데이터 없는 빈 상태)
          </DialogDescription>
        </DialogHeader>
        <div class="max-h-[70vh] overflow-auto">
          <DynamicPage
            v-if="previewMetaId"
            :meta-id="previewMetaId"
            :rows="[]"
          />
        </div>
      </DialogContent>
    </Dialog>
  </RequirePermission>
</template>
