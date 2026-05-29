<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import type { ColumnDef } from '@tanstack/vue-table';
import { toast } from 'vue-sonner';
import { DataTable } from '@/components/ui/data-table';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useApiFetch } from '@/lib/api';
import { useAuthStore } from '@/stores/useAuthStore';
import type { ApiEnvelope } from '@/types/meta';
import type { RoleItem, PermissionItem } from '@/types/system';

const auth = useAuthStore();
const canEdit = computed(() => auth.hasRole('ROLE_ADMIN') || auth.hasPermission('ROLE_ADMIN'));

const roles = ref<RoleItem[]>([]);
const permissions = ref<PermissionItem[]>([]);
const selected = ref<RoleItem | null>(null);

const columns: ColumnDef<RoleItem>[] = [
  { accessorKey: 'code', header: '코드', size: 180 },
  { accessorKey: 'name', header: '이름', size: 160 },
  { accessorKey: 'description', header: '설명' },
];

async function loadRoles(): Promise<void> {
  const { data, error } = await useApiFetch('/api/roles').json<
    ApiEnvelope<RoleItem[]>
  >();
  if (error.value) {
    toast.error('역할 목록을 불러오지 못했습니다.');
    return;
  }
  roles.value = data.value?.data ?? [];
  if (selected.value) {
    selected.value =
      roles.value.find((r) => r.id === selected.value!.id) ?? null;
  }
}

async function loadPermissions(): Promise<void> {
  // 권한 목록은 ROLE_ADMIN 만 조회 가능 — 편집 권한 있을 때만 호출
  if (!canEdit.value) return;
  const { data, error } = await useApiFetch('/api/permissions').json<
    ApiEnvelope<PermissionItem[]>
  >();
  if (error.value) return;
  permissions.value = data.value?.data ?? [];
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadPermissions()]);
});

function onSelect(role: RoleItem): void {
  selected.value = role;
}

function hasPerm(code: string): boolean {
  return selected.value?.permissionCodes.includes(code) ?? false;
}

async function togglePerm(code: string, next: boolean): Promise<void> {
  if (!selected.value) return;
  const roleCode = encodeURIComponent(selected.value.code);
  const permCode = encodeURIComponent(code);
  const path = `/api/roles/${roleCode}/permissions/${permCode}`;
  const req = useApiFetch(path, { immediate: false, refetch: false });
  const { error } = next
    ? await req.post().json<ApiEnvelope<unknown>>()
    : await req.delete().json<ApiEnvelope<unknown>>();
  if (error.value) {
    toast.error('권한 변경에 실패했습니다.');
    return;
  }
  toast.success(next ? `${code} 부여` : `${code} 회수`);
  await loadRoles();
}

// ── 등록 다이얼로그 ───────────────────────────────────────────
const createOpen = ref(false);
const createForm = reactive({ code: '', name: '', description: '' });

function openCreate(): void {
  createForm.code = '';
  createForm.name = '';
  createForm.description = '';
  createOpen.value = true;
}

async function submitCreate(): Promise<void> {
  if (!createForm.code || !createForm.name) {
    toast.error('코드·이름은 필수입니다.');
    return;
  }
  const payload = {
    code: createForm.code,
    name: createForm.name,
    description: createForm.description || null,
  };
  const { error } = await useApiFetch('/api/roles', {
    immediate: false,
    refetch: false,
  })
    .post(payload)
    .json<ApiEnvelope<RoleItem>>();
  if (error.value) {
    toast.error('역할 등록에 실패했습니다.');
    return;
  }
  toast.success('역할이 등록되었습니다.');
  createOpen.value = false;
  await loadRoles();
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="역할 관리">
      <template #actions>
        <RequirePermission code="ROLE_ADMIN">
          <Button @click="openCreate">
            역할 등록
          </Button>
        </RequirePermission>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- 좌측: 역할 목록 -->
      <div>
        <DataTable
          :rows="roles"
          :columns="columns"
          selectable
          :page-size="0"
          @row-click="onSelect"
        />
      </div>

      <!-- 우측: 선택 역할의 권한 -->
      <div class="rounded-lg border border-border bg-surface p-6">
        <p
          v-if="!selected"
          class="text-sm text-foreground-muted"
        >
          좌측에서 역할을 선택하세요.
        </p>
        <div
          v-else
          class="space-y-4"
        >
          <div>
            <h2 class="text-xl font-semibold text-foreground">
              {{ selected.name }}
            </h2>
            <p class="text-sm text-foreground-muted">
              코드 {{ selected.code }}
            </p>
          </div>

          <RequirePermission code="ROLE_ADMIN">
            <template #fallback>
              <div class="space-y-1">
                <p class="text-sm font-semibold text-foreground">
                  부여된 권한
                </p>
                <p class="text-sm text-foreground-muted">
                  {{ selected.permissionCodes.join(', ') || '없음' }}
                </p>
              </div>
            </template>
            <div class="space-y-2">
              <p class="text-sm font-semibold text-foreground">
                권한 부여/회수
              </p>
              <div class="max-h-96 space-y-1 overflow-auto pr-2">
                <label
                  v-for="perm in permissions"
                  :key="perm.id"
                  class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-surface-hover"
                >
                  <Checkbox
                    :model-value="hasPerm(perm.code)"
                    @update:model-value="(v) => togglePerm(perm.code, v === true)"
                  />
                  <span class="flex-1">{{ perm.name }}</span>
                  <span class="text-xs text-foreground-subtle">{{ perm.code }}</span>
                </label>
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
          <DialogTitle>역할 등록</DialogTitle>
          <DialogDescription>새 역할을 추가합니다.</DialogDescription>
        </DialogHeader>
        <form
          class="space-y-3"
          @submit.prevent="submitCreate"
        >
          <div class="space-y-1">
            <Label for="rc-code">역할 코드 *</Label>
            <Input
              id="rc-code"
              v-model="createForm.code"
              placeholder="예: ROLE_IT_SUPPORT"
            />
          </div>
          <div class="space-y-1">
            <Label for="rc-name">역할명 *</Label>
            <Input
              id="rc-name"
              v-model="createForm.name"
            />
          </div>
          <div class="space-y-1">
            <Label for="rc-desc">설명</Label>
            <Input
              id="rc-desc"
              v-model="createForm.description"
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
