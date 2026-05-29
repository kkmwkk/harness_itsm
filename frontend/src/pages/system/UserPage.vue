<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import type { ColumnDef } from '@tanstack/vue-table';
import { toast } from 'vue-sonner';
import { DataTable } from '@/components/ui/data-table';
import PageHeader from '@/components/layout/PageHeader.vue';
import RequirePermission from '@/components/common/RequirePermission.vue';
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
import { buildAdminQueryUrl } from '@/lib/admin-crud';
import type { ApiEnvelope } from '@/types/meta';
import type { PageResponse } from '@/types/page';
import type { UserListItem, UserDetail } from '@/types/system';
import type { UserStatus } from '@/types/auth';

const rows = ref<UserListItem[]>([]);
const totalElements = ref(0);
const isLoading = ref(false);

// 검색 상태
const kw = ref('');
const statusFilter = ref<'' | UserStatus>('');
const roleFilter = ref('');

const columns: ColumnDef<UserListItem>[] = [
  { accessorKey: 'id', header: 'ID', size: 70 },
  { accessorKey: 'username', header: '사용자명', size: 140 },
  { accessorKey: 'name', header: '이름', size: 120 },
  { accessorKey: 'email', header: '이메일' },
  { accessorKey: 'status', header: '상태', size: 100 },
  {
    id: 'roleCodes',
    header: '역할',
    accessorFn: (r) => r.roleCodes.join(', '),
    size: 220,
  },
];

async function loadUsers(): Promise<void> {
  isLoading.value = true;
  try {
    const url = buildAdminQueryUrl(
      '/api/users',
      { page: 0, size: 100, kw: kw.value },
      { status: statusFilter.value, role: roleFilter.value },
    );
    const { data, error } = await useApiFetch(url).json<
      ApiEnvelope<PageResponse<UserListItem>>
    >();
    if (error.value) {
      toast.error('사용자 목록을 불러오지 못했습니다.');
      return;
    }
    rows.value = data.value?.data?.content ?? [];
    totalElements.value = data.value?.data?.totalElements ?? 0;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadUsers);

// ── 등록 다이얼로그 ───────────────────────────────────────────
const createOpen = ref(false);
const createForm = reactive({
  username: '',
  password: '',
  name: '',
  email: '',
  phone: '',
  departmentId: '',
  roleCodes: '',
});

function openCreate(): void {
  Object.assign(createForm, {
    username: '',
    password: '',
    name: '',
    email: '',
    phone: '',
    departmentId: '',
    roleCodes: '',
  });
  createOpen.value = true;
}

async function submitCreate(): Promise<void> {
  if (!createForm.username || !createForm.password || !createForm.name) {
    toast.error('사용자명·비밀번호·이름은 필수입니다.');
    return;
  }
  const payload = {
    username: createForm.username,
    password: createForm.password,
    name: createForm.name,
    email: createForm.email || null,
    phone: createForm.phone || null,
    departmentId: createForm.departmentId ? Number(createForm.departmentId) : null,
    roleCodes: createForm.roleCodes
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
  };
  const { error } = await useApiFetch('/api/users', {
    immediate: false,
    refetch: false,
  })
    .post(payload)
    .json<ApiEnvelope<UserDetail>>();
  if (error.value) {
    toast.error('사용자 등록에 실패했습니다.');
    return;
  }
  toast.success('사용자가 등록되었습니다.');
  createOpen.value = false;
  await loadUsers();
}

// ── 편집 다이얼로그 ───────────────────────────────────────────
const editOpen = ref(false);
const editId = ref<number | null>(null);
const editForm = reactive({ name: '', email: '', phone: '', departmentId: '' });
const editRoleCode = ref('');
const newPassword = ref('');

function openEdit(row: UserListItem): void {
  editId.value = row.id;
  editForm.name = row.name;
  editForm.email = row.email ?? '';
  editForm.phone = '';
  editForm.departmentId = row.departmentId !== null ? String(row.departmentId) : '';
  editRoleCode.value = '';
  newPassword.value = '';
  editOpen.value = true;
}

async function submitEdit(): Promise<void> {
  if (editId.value === null) return;
  const payload = {
    name: editForm.name,
    email: editForm.email || null,
    phone: editForm.phone || null,
    departmentId: editForm.departmentId ? Number(editForm.departmentId) : null,
  };
  const { error } = await useApiFetch(`/api/users/${editId.value}`, {
    immediate: false,
    refetch: false,
  })
    .patch(payload)
    .json<ApiEnvelope<UserDetail>>();
  if (error.value) {
    toast.error('사용자 수정에 실패했습니다.');
    return;
  }
  toast.success('사용자 정보가 수정되었습니다.');
  editOpen.value = false;
  await loadUsers();
}

/** 단순 PATCH 액션(잠금·잠금해제·퇴직) 공통 처리. */
async function userAction(
  suffix: string,
  okMessage: string,
): Promise<void> {
  if (editId.value === null) return;
  const { error } = await useApiFetch(`/api/users/${editId.value}/${suffix}`, {
    immediate: false,
    refetch: false,
  })
    .patch()
    .json<ApiEnvelope<void>>();
  if (error.value) {
    toast.error('처리에 실패했습니다.');
    return;
  }
  toast.success(okMessage);
  editOpen.value = false;
  await loadUsers();
}

async function assignRole(): Promise<void> {
  if (editId.value === null || !editRoleCode.value.trim()) return;
  const code = editRoleCode.value.trim();
  const { error } = await useApiFetch(
    `/api/users/${editId.value}/roles/${encodeURIComponent(code)}`,
    { immediate: false, refetch: false },
  )
    .post()
    .json<ApiEnvelope<void>>();
  if (error.value) {
    toast.error('역할 할당에 실패했습니다.');
    return;
  }
  toast.success(`역할 ${code} 을(를) 할당했습니다.`);
  await loadUsers();
}

async function revokeRole(): Promise<void> {
  if (editId.value === null || !editRoleCode.value.trim()) return;
  const code = editRoleCode.value.trim();
  const { error } = await useApiFetch(
    `/api/users/${editId.value}/roles/${encodeURIComponent(code)}`,
    { immediate: false, refetch: false },
  )
    .delete()
    .json<ApiEnvelope<void>>();
  if (error.value) {
    toast.error('역할 해제에 실패했습니다.');
    return;
  }
  toast.success(`역할 ${code} 을(를) 해제했습니다.`);
  await loadUsers();
}

async function changePassword(): Promise<void> {
  if (editId.value === null || newPassword.value.length < 8) {
    toast.error('새 비밀번호는 8자 이상이어야 합니다.');
    return;
  }
  const { error } = await useApiFetch(`/api/users/${editId.value}/password`, {
    immediate: false,
    refetch: false,
  })
    .patch({ newPassword: newPassword.value })
    .json<ApiEnvelope<void>>();
  if (error.value) {
    toast.error('비밀번호 변경에 실패했습니다.');
    return;
  }
  toast.success('비밀번호가 변경되었습니다.');
  newPassword.value = '';
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="사용자 관리">
      <template #actions>
        <RequirePermission code="USER_ADMIN">
          <Button @click="openCreate">
            사용자 등록
          </Button>
        </RequirePermission>
      </template>
    </PageHeader>

    <!-- 검색바 -->
    <form
      class="flex flex-wrap items-end gap-3"
      @submit.prevent="loadUsers"
    >
      <div class="space-y-1">
        <Label
          for="user-kw"
          class="text-sm"
        >검색어</Label>
        <Input
          id="user-kw"
          v-model="kw"
          class="w-56"
          placeholder="사용자명·이름·이메일"
        />
      </div>
      <div class="space-y-1">
        <Label
          for="user-status"
          class="text-sm"
        >상태</Label>
        <select
          id="user-status"
          v-model="statusFilter"
          class="h-9 rounded-md border border-border bg-surface px-2 text-sm text-foreground"
        >
          <option value="">
            전체
          </option>
          <option value="ACTIVE">
            ACTIVE
          </option>
          <option value="LOCKED">
            LOCKED
          </option>
          <option value="RETIRED">
            RETIRED
          </option>
        </select>
      </div>
      <div class="space-y-1">
        <Label
          for="user-role"
          class="text-sm"
        >역할 코드</Label>
        <Input
          id="user-role"
          v-model="roleFilter"
          class="w-48"
          placeholder="예: ROLE_USER"
        />
      </div>
      <Button
        type="submit"
        variant="secondary"
      >
        검색
      </Button>
    </form>

    <p class="text-sm text-foreground-muted">
      총 {{ totalElements }}명
    </p>

    <DataTable
      :rows="rows"
      :columns="columns"
      selectable
      @row-click="openEdit"
    />

    <!-- 등록 다이얼로그 -->
    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>사용자 등록</DialogTitle>
          <DialogDescription>새 사용자 계정을 생성합니다.</DialogDescription>
        </DialogHeader>
        <form
          class="space-y-3"
          @submit.prevent="submitCreate"
        >
          <div class="space-y-1">
            <Label for="c-username">사용자명 *</Label>
            <Input
              id="c-username"
              v-model="createForm.username"
              autocomplete="off"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-password">비밀번호 *</Label>
            <Input
              id="c-password"
              v-model="createForm.password"
              type="password"
              autocomplete="new-password"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-name">이름 *</Label>
            <Input
              id="c-name"
              v-model="createForm.name"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-email">이메일</Label>
            <Input
              id="c-email"
              v-model="createForm.email"
              type="email"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-phone">전화번호</Label>
            <Input
              id="c-phone"
              v-model="createForm.phone"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-dept">부서 ID</Label>
            <Input
              id="c-dept"
              v-model="createForm.departmentId"
              type="number"
            />
          </div>
          <div class="space-y-1">
            <Label for="c-roles">역할 코드 (쉼표 구분)</Label>
            <Input
              id="c-roles"
              v-model="createForm.roleCodes"
              placeholder="ROLE_USER, ROLE_IT_SUPPORT"
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

    <!-- 편집 다이얼로그 -->
    <Dialog v-model:open="editOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>사용자 편집</DialogTitle>
          <DialogDescription>정보 수정·상태 변경·역할·비밀번호를 관리합니다.</DialogDescription>
        </DialogHeader>
        <RequirePermission code="USER_ADMIN">
          <template #fallback>
            <p class="text-sm text-foreground-muted">
              편집 권한(USER_ADMIN)이 없어 조회만 가능합니다.
            </p>
          </template>
          <div class="space-y-3">
            <div class="space-y-1">
              <Label for="e-name">이름</Label>
              <Input
                id="e-name"
                v-model="editForm.name"
              />
              <div class="space-y-1">
                <Label for="e-email">이메일</Label>
                <Input
                  id="e-email"
                  v-model="editForm.email"
                  type="email"
                />
              </div>
              <div class="space-y-1">
                <Label for="e-phone">전화번호</Label>
                <Input
                  id="e-phone"
                  v-model="editForm.phone"
                />
              </div>
              <div class="space-y-1">
                <Label for="e-dept">부서 ID</Label>
                <Input
                  id="e-dept"
                  v-model="editForm.departmentId"
                  type="number"
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

              <div class="border-t border-border-subtle pt-3 space-y-2">
                <Label class="text-sm font-semibold">상태 변경</Label>
                <div class="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="userAction('lock', '계정을 잠갔습니다.')"
                  >
                    잠금
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="userAction('unlock', '잠금을 해제했습니다.')"
                  >
                    잠금해제
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    @click="userAction('retire', '계정을 퇴직 처리했습니다.')"
                  >
                    퇴직
                  </Button>
                </div>
              </div>

              <div class="border-t border-border-subtle pt-3 space-y-2">
                <Label
                  for="e-role"
                  class="text-sm font-semibold"
                >역할 할당/해제</Label>
                <div class="flex gap-2">
                  <Input
                    id="e-role"
                    v-model="editRoleCode"
                    class="flex-1"
                    placeholder="예: ROLE_IT_SUPPORT"
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="assignRole"
                  >
                    할당
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    @click="revokeRole"
                  >
                    해제
                  </Button>
                </div>
              </div>

              <div class="border-t border-border-subtle pt-3 space-y-2">
                <Label
                  for="e-pw"
                  class="text-sm font-semibold"
                >비밀번호 변경</Label>
                <div class="flex gap-2">
                  <Input
                    id="e-pw"
                    v-model="newPassword"
                    type="password"
                    class="flex-1"
                    autocomplete="new-password"
                    placeholder="새 비밀번호 (8자 이상)"
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    @click="changePassword"
                  >
                    변경
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </RequirePermission>
        <DialogFooter>
          <Button
            variant="outline"
            @click="editOpen = false"
          >
            닫기
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
