<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue';
import { toast } from 'vue-sonner';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import MenuAdminTreeNode from '@/components/system/MenuAdminTreeNode.vue';
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
import { buildMenuTree, isDescendant } from '@/lib/admin-crud';
import type { ApiEnvelope } from '@/types/meta';
import type { MenuItem, MenuAdminNode } from '@/types/system';

const MENU_PERM = ['ROLE_ADMIN', 'MENU_ADMIN'];

const flat = ref<MenuItem[]>([]);
const tree = computed<MenuAdminNode[]>(() => buildMenuTree(flat.value));
const selected = ref<MenuAdminNode | null>(null);

async function loadMenus(): Promise<void> {
  const { data, error } = await useApiFetch('/api/menus').json<
    ApiEnvelope<MenuItem[]>
  >();
  if (error.value) {
    toast.error('메뉴 목록을 불러오지 못했습니다.');
    return;
  }
  flat.value = data.value?.data ?? [];
  if (selected.value) {
    selected.value = flat.value.find((m) => m.id === selected.value!.id)
      ? findNode(tree.value, selected.value.id)
      : null;
  }
}

function findNode(nodes: MenuAdminNode[], id: number): MenuAdminNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const found = findNode(n.children, id);
    if (found) return found;
  }
  return null;
}

onMounted(loadMenus);

function onSelect(node: MenuAdminNode): void {
  selected.value = node;
}

// ── 편집 폼 — 선택 변경 시 동기화 ────────────────────────────
const editForm = reactive({
  label: '',
  icon: '',
  route: '',
  groupId: '',
  permissionCode: '',
});
watch(
  selected,
  (s) => {
    editForm.label = s?.label ?? '';
    editForm.icon = s?.icon ?? '';
    editForm.route = s?.route ?? '';
    editForm.groupId = s?.groupId ?? '';
    editForm.permissionCode = s?.permissionCode ?? '';
  },
  { immediate: true },
);

async function submitEdit(): Promise<void> {
  if (!selected.value) return;
  const payload = {
    label: editForm.label,
    icon: editForm.icon || null,
    route: editForm.route || null,
    groupId: editForm.groupId || null,
    permissionCode: editForm.permissionCode || null,
  };
  const { error } = await useApiFetch(`/api/menus/${selected.value.id}`, {
    immediate: false,
    refetch: false,
  })
    .patch(payload)
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('메뉴 수정에 실패했습니다.');
    return;
  }
  toast.success('메뉴가 수정되었습니다.');
  await loadMenus();
}

const moveParentId = ref('');
const moveSortOrder = ref('0');
async function moveMenu(): Promise<void> {
  if (!selected.value) return;
  const id = selected.value.id;
  const newParent = moveParentId.value ? Number(moveParentId.value) : undefined;
  // 자기·자손으로의 이동을 클라이언트에서 사전 차단 (백엔드도 거부)
  if (newParent !== undefined && isDescendant(tree.value, id, newParent)) {
    toast.error('자기 자신 또는 하위 메뉴로는 이동할 수 없습니다.');
    return;
  }
  const params = new URLSearchParams();
  if (newParent !== undefined) params.set('newParentId', String(newParent));
  params.set('newSortOrder', String(Number(moveSortOrder.value) || 0));
  const { error } = await useApiFetch(`/api/menus/${id}/move?${params.toString()}`, {
    immediate: false,
    refetch: false,
  })
    .patch()
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('메뉴 이동에 실패했습니다.');
    return;
  }
  toast.success('메뉴를 이동했습니다.');
  moveParentId.value = '';
  moveSortOrder.value = '0';
  await loadMenus();
}

async function deactivate(): Promise<void> {
  if (!selected.value) return;
  const { error } = await useApiFetch(`/api/menus/${selected.value.id}`, {
    immediate: false,
    refetch: false,
  })
    .delete()
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('메뉴 비활성화에 실패했습니다.');
    return;
  }
  toast.success('메뉴를 비활성화했습니다.');
  await loadMenus();
}

// ── 등록 다이얼로그 ───────────────────────────────────────────
const createOpen = ref(false);
const createForm = reactive({
  code: '',
  label: '',
  parentId: '',
  icon: '',
  sortOrder: '0',
  route: '',
  groupId: '',
  permissionCode: '',
});

function openCreate(): void {
  Object.assign(createForm, {
    code: '',
    label: '',
    parentId: selected.value ? String(selected.value.id) : '',
    icon: '',
    sortOrder: '0',
    route: '',
    groupId: '',
    permissionCode: '',
  });
  createOpen.value = true;
}

async function submitCreate(): Promise<void> {
  if (!createForm.code || !createForm.label) {
    toast.error('코드·라벨은 필수입니다.');
    return;
  }
  const payload = {
    code: createForm.code,
    label: createForm.label,
    parentId: createForm.parentId ? Number(createForm.parentId) : null,
    icon: createForm.icon || null,
    sortOrder: Number(createForm.sortOrder) || 0,
    route: createForm.route || null,
    groupId: createForm.groupId || null,
    permissionCode: createForm.permissionCode || null,
  };
  const { error } = await useApiFetch('/api/menus', {
    immediate: false,
    refetch: false,
  })
    .post(payload)
    .json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('메뉴 등록에 실패했습니다.');
    return;
  }
  toast.success('메뉴가 등록되었습니다.');
  createOpen.value = false;
  await loadMenus();
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="메뉴 관리">
      <template #actions>
        <RequirePermission :code="MENU_PERM">
          <Button @click="openCreate">
            메뉴 등록
          </Button>
        </RequirePermission>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[300px_1fr]">
      <!-- 좌측 트리 -->
      <div class="rounded-lg border border-border bg-surface p-3">
        <p
          v-if="tree.length === 0"
          class="text-sm text-foreground-muted"
        >
          등록된 메뉴가 없습니다.
        </p>
        <MenuAdminTreeNode
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
          좌측에서 메뉴를 선택하세요.
        </p>
        <div
          v-else
          class="space-y-5"
        >
          <div>
            <h2 class="text-xl font-semibold text-foreground">
              {{ selected.label }}
            </h2>
            <p class="text-sm text-foreground-muted">
              코드 {{ selected.code }} · ID {{ selected.id }}
              <span v-if="!selected.active"> · 비활성</span>
            </p>
          </div>

          <RequirePermission :code="MENU_PERM">
            <template #fallback>
              <p class="text-sm text-foreground-muted">
                편집 권한(MENU_ADMIN)이 없어 조회만 가능합니다.
              </p>
            </template>
            <div class="space-y-4">
              <div class="space-y-3">
                <div class="space-y-1">
                  <Label for="m-label">라벨</Label>
                  <Input
                    id="m-label"
                    v-model="editForm.label"
                  />
                </div>
                <div class="space-y-1">
                  <Label for="m-icon">아이콘 (lucide)</Label>
                  <Input
                    id="m-icon"
                    v-model="editForm.icon"
                    placeholder="예: BoxesIcon"
                  />
                </div>
                <div class="space-y-1">
                  <Label for="m-route">라우트</Label>
                  <Input
                    id="m-route"
                    v-model="editForm.route"
                    placeholder="예: /itsm"
                  />
                </div>
                <div class="space-y-1">
                  <Label for="m-group">groupId</Label>
                  <Input
                    id="m-group"
                    v-model="editForm.groupId"
                    placeholder="예: itg-ticket"
                  />
                </div>
                <div class="space-y-1">
                  <Label for="m-perm">권한 코드</Label>
                  <Input
                    id="m-perm"
                    v-model="editForm.permissionCode"
                    placeholder="비우면 누구나"
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
                <Label class="text-sm font-semibold">메뉴 이동</Label>
                <div class="flex gap-2">
                  <Input
                    v-model="moveParentId"
                    type="number"
                    class="flex-1"
                    placeholder="새 상위 메뉴 ID (비우면 루트)"
                  />
                  <Input
                    v-model="moveSortOrder"
                    type="number"
                    class="w-24"
                    placeholder="정렬"
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="moveMenu"
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
          <DialogTitle>메뉴 등록</DialogTitle>
          <DialogDescription>새 메뉴를 트리에 추가합니다.</DialogDescription>
        </DialogHeader>
        <form
          class="space-y-3"
          @submit.prevent="submitCreate"
        >
          <div class="space-y-1">
            <Label for="mc-code">메뉴 코드 *</Label>
            <Input
              id="mc-code"
              v-model="createForm.code"
            />
          </div>
          <div class="space-y-1">
            <Label for="mc-label">라벨 *</Label>
            <Input
              id="mc-label"
              v-model="createForm.label"
            />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <Label for="mc-parent">상위 메뉴 ID</Label>
              <Input
                id="mc-parent"
                v-model="createForm.parentId"
                type="number"
                placeholder="비우면 루트"
              />
            </div>
            <div class="space-y-1">
              <Label for="mc-sort">정렬 순서</Label>
              <Input
                id="mc-sort"
                v-model="createForm.sortOrder"
                type="number"
              />
            </div>
          </div>
          <div class="space-y-1">
            <Label for="mc-icon">아이콘 (lucide)</Label>
            <Input
              id="mc-icon"
              v-model="createForm.icon"
              placeholder="예: BoxesIcon"
            />
          </div>
          <div class="space-y-1">
            <Label for="mc-route">라우트</Label>
            <Input
              id="mc-route"
              v-model="createForm.route"
            />
          </div>
          <div class="space-y-1">
            <Label for="mc-group">groupId</Label>
            <Input
              id="mc-group"
              v-model="createForm.groupId"
            />
          </div>
          <div class="space-y-1">
            <Label for="mc-perm">권한 코드</Label>
            <Input
              id="mc-perm"
              v-model="createForm.permissionCode"
              placeholder="비우면 누구나"
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
