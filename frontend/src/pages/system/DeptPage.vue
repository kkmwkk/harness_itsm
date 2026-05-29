<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import DeptTreeNode from '@/components/system/DeptTreeNode.vue';
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
import type { ApiEnvelope } from '@/types/meta';
import type { DeptTreeNode as DeptNode } from '@/types/system';

const DEPT_PERM = ['ROLE_ADMIN', 'DEPT_ADMIN'];

const tree = ref<DeptNode[]>([]);
const selected = ref<DeptNode | null>(null);

function findById(nodes: DeptNode[], id: number): DeptNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const found = findById(n.children, id);
    if (found) return found;
  }
  return null;
}

async function loadTree(): Promise<void> {
  const { data, error } = await useApiFetch('/api/departments/tree').json<
    ApiEnvelope<DeptNode[]>
  >();
  if (error.value) {
    toast.error('부서 트리를 불러오지 못했습니다.');
    return;
  }
  tree.value = data.value?.data ?? [];
  // 이동·수정 후 stale 선택 갱신
  selected.value = selected.value
    ? findById(tree.value, selected.value.id)
    : null;
}

onMounted(loadTree);

function onSelect(node: DeptNode): void {
  selected.value = node;
}

// ── 편집 폼(우측) — 선택이 바뀌면 폼 동기화 ──────────────────
const editForm = reactive({ name: '', managerUserId: '' });
watch(
  selected,
  (s) => {
    editForm.name = s?.name ?? '';
    editForm.managerUserId = '';
  },
  { immediate: true },
);

async function submitEdit(): Promise<void> {
  if (!selected.value) return;
  const payload = {
    name: editForm.name,
    managerUserId: editForm.managerUserId ? Number(editForm.managerUserId) : null,
  };
  const { error } = await useApiFetch(`/api/departments/${selected.value.id}`, {
    immediate: false,
    refetch: false,
  })
    .patch(payload)
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('부서 수정에 실패했습니다.');
    return;
  }
  toast.success('부서 정보가 수정되었습니다.');
  await loadTree();
}

const moveParentId = ref('');
async function moveDept(): Promise<void> {
  if (!selected.value) return;
  const id = selected.value.id;
  const newParent = moveParentId.value ? Number(moveParentId.value) : undefined;
  const qs = newParent !== undefined ? `?newParentId=${newParent}` : '';
  const { error } = await useApiFetch(`/api/departments/${id}/move${qs}`, {
    immediate: false,
    refetch: false,
  })
    .patch()
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('부서 이동에 실패했습니다. (자기·자손으로는 이동 불가)');
    return;
  }
  toast.success('부서를 이동했습니다.');
  moveParentId.value = '';
  await loadTree();
}

async function deactivate(): Promise<void> {
  if (!selected.value) return;
  const { error } = await useApiFetch(`/api/departments/${selected.value.id}`, {
    immediate: false,
    refetch: false,
  })
    .delete()
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('부서 비활성화에 실패했습니다.');
    return;
  }
  toast.success('부서를 비활성화했습니다.');
  await loadTree();
}

// ── 등록 다이얼로그 ───────────────────────────────────────────
const createOpen = ref(false);
const createForm = reactive({ code: '', name: '', parentId: '' });

function openCreate(): void {
  createForm.code = '';
  createForm.name = '';
  createForm.parentId = selected.value ? String(selected.value.id) : '';
  createOpen.value = true;
}

async function submitCreate(): Promise<void> {
  if (!createForm.code || !createForm.name) {
    toast.error('코드·부서명은 필수입니다.');
    return;
  }
  const payload = {
    code: createForm.code,
    name: createForm.name,
    parentId: createForm.parentId ? Number(createForm.parentId) : null,
  };
  const { error } = await useApiFetch('/api/departments', {
    immediate: false,
    refetch: false,
  })
    .post(payload)
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('부서 등록에 실패했습니다.');
    return;
  }
  toast.success('부서가 등록되었습니다.');
  createOpen.value = false;
  await loadTree();
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="부서 관리">
      <template #actions>
        <RequirePermission :code="DEPT_PERM">
          <Button @click="openCreate">
            부서 등록
          </Button>
        </RequirePermission>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr]">
      <!-- 좌측 트리 -->
      <div class="rounded-lg border border-border bg-surface p-3">
        <p
          v-if="tree.length === 0"
          class="text-sm text-foreground-muted"
        >
          등록된 부서가 없습니다.
        </p>
        <DeptTreeNode
          v-for="node in tree"
          :key="node.id"
          :node="node"
          :selected-id="selected?.id ?? null"
          @select="onSelect"
        />
      </div>

      <!-- 우측 상세 -->
      <div class="rounded-lg border border-border bg-surface p-6">
        <p
          v-if="!selected"
          class="text-sm text-foreground-muted"
        >
          좌측에서 부서를 선택하세요.
        </p>
        <div
          v-else
          class="space-y-5"
        >
          <div>
            <h2 class="text-xl font-semibold text-foreground">
              {{ selected.name }}
            </h2>
            <p class="text-sm text-foreground-muted">
              코드 {{ selected.code }} · path {{ selected.path }}
              <span v-if="!selected.active"> · 비활성</span>
            </p>
          </div>

          <RequirePermission :code="DEPT_PERM">
            <template #fallback>
              <p class="text-sm text-foreground-muted">
                편집 권한(DEPT_ADMIN)이 없어 조회만 가능합니다.
              </p>
            </template>
            <div class="space-y-4">
              <div class="space-y-3">
                <div class="space-y-1">
                  <Label for="d-name">부서명</Label>
                  <Input
                    id="d-name"
                    v-model="editForm.name"
                  />
                </div>
                <div class="space-y-1">
                  <Label for="d-mgr">부서장 사용자 ID</Label>
                  <Input
                    id="d-mgr"
                    v-model="editForm.managerUserId"
                    type="number"
                    placeholder="비우면 해제"
                  />
                </div>
                <div class="flex justify-end">
                  <Button
                    size="sm"
                    @click="submitEdit"
                  >
                    정보 저장
                  </Button>
                </div>
              </div>

              <div class="border-t border-border-subtle pt-4 space-y-2">
                <Label
                  for="d-move"
                  class="text-sm font-semibold"
                >부서 이동</Label>
                <div class="flex gap-2">
                  <Input
                    id="d-move"
                    v-model="moveParentId"
                    type="number"
                    class="flex-1"
                    placeholder="새 상위 부서 ID (비우면 루트)"
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="moveDept"
                  >
                    이동
                  </Button>
                </div>
              </div>

              <div class="border-t border-border-subtle pt-4">
                <Button
                  variant="destructive"
                  size="sm"
                  @click="deactivate"
                >
                  비활성화
                </Button>
              </div>
            </div>
          </RequirePermission>
        </div>
      </div>
    </div>

    <!-- 등록 다이얼로그 -->
    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>부서 등록</DialogTitle>
          <DialogDescription>새 부서를 트리에 추가합니다.</DialogDescription>
        </DialogHeader>
        <form
          class="space-y-3"
          @submit.prevent="submitCreate"
        >
          <div class="space-y-1">
            <Label for="dc-code">부서 코드 *</Label>
            <Input
              id="dc-code"
              v-model="createForm.code"
            />
          </div>
          <div class="space-y-1">
            <Label for="dc-name">부서명 *</Label>
            <Input
              id="dc-name"
              v-model="createForm.name"
            />
          </div>
          <div class="space-y-1">
            <Label for="dc-parent">상위 부서 ID</Label>
            <Input
              id="dc-parent"
              v-model="createForm.parentId"
              type="number"
              placeholder="비우면 루트"
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
            등록
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
