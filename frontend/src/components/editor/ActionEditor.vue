<script setup lang="ts">
import { computed } from 'vue';
import { Trash2Icon, PlusIcon } from '@lucide/vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import type { ActionMeta } from '@/types/meta-body';
import {
  ACTION_TYPES,
  ACTION_TYPE_LABELS,
  isValidActionId,
  needsTo,
  isExternalUrl,
  createBlankAction,
  duplicateActionIds,
  validateActions,
  type ActionType,
} from '@/composables/useActionEditor';

/**
 * 액션(meta.actions) GUI 편집기 (ADR-016 1단계).
 * JSON 직접 노출 없이 추가/삭제/속성 편집만 — 순수 로직은 useActionEditor 에 위임한다.
 * navigate 의 to 가 외부 URL 이면 새 탭으로 열리는 점을 안내한다(보안).
 */
const actions = defineModel<ActionMeta[]>('actions', { required: true });

const issues = computed(() => validateActions(actions.value));
const dupIds = computed(() => new Set(duplicateActionIds(actions.value)));

function addAction(): void {
  actions.value = [...actions.value, createBlankAction(actions.value)];
}

function removeAction(idx: number): void {
  actions.value = actions.value.filter((_, i) => i !== idx);
}

function onTypeChange(a: ActionMeta, type: ActionType): void {
  a.type = type;
  // navigate 가 아니면 to 는 의미 없으므로 정리.
  if (!needsTo(type)) a.to = undefined;
}

/** id 입력이 비었거나 식별자 규칙 위반/중복이면 인라인 에러. */
function idError(a: ActionMeta): string | null {
  const id = a.id?.trim() ?? '';
  if (!id) return 'id 는 필수입니다.';
  if (!isValidActionId(id)) return '영문 소문자/언더스코어로 시작하는 식별자만 가능합니다.';
  if (dupIds.value.has(id)) return 'id 가 중복됩니다.';
  return null;
}

defineExpose({ issues });
</script>

<template>
  <div class="space-y-3">
    <p class="text-[12px] text-foreground-muted">
      액션은 화면 우상단·툴바 버튼으로 렌더링됩니다.
      <span class="font-mono">dialog-form</span> 은 등록 폼 다이얼로그를 엽니다.
      <span class="font-mono">navigate</span> 의 <span class="font-mono">to</span> 가 외부 URL(http/https)이면 새 탭으로 열립니다.
    </p>

    <Card
      v-for="(a, idx) in actions"
      :key="idx"
    >
      <CardContent class="space-y-3 py-4">
        <div class="flex items-start justify-between gap-2">
          <p class="text-sm font-semibold">
            액션 {{ idx + 1 }}
            <span class="font-mono text-xs text-foreground-muted">{{ a.id || '(id 미지정)' }}</span>
            <span
              v-if="a.type === 'navigate' && isExternalUrl(a.to)"
              class="ml-1 text-xs text-info"
            >· 외부 URL · 새 탭</span>
          </p>
          <Button
            variant="ghost"
            size="icon"
            aria-label="액션 삭제"
            @click="removeAction(idx)"
          >
            <Trash2Icon class="size-4" />
          </Button>
        </div>

        <div class="grid gap-x-4 gap-y-3 md:grid-cols-2">
          <!-- id -->
          <div class="space-y-1">
            <Label :for="`a-id-${idx}`">id *</Label>
            <Input
              :id="`a-id-${idx}`"
              v-model="a.id"
              placeholder="예: create"
              autocomplete="off"
            />
            <p
              v-if="idError(a)"
              class="text-[12px] text-danger"
            >
              {{ idError(a) }}
            </p>
          </div>

          <!-- label -->
          <div class="space-y-1">
            <Label :for="`a-label-${idx}`">라벨 *</Label>
            <Input
              :id="`a-label-${idx}`"
              v-model="a.label"
              placeholder="예: 등록"
            />
          </div>

          <!-- type -->
          <div class="space-y-1">
            <Label :for="`a-type-${idx}`">타입 *</Label>
            <select
              :id="`a-type-${idx}`"
              :value="a.type"
              class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              @change="onTypeChange(a, ($event.target as HTMLSelectElement).value as ActionType)"
            >
              <option
                v-for="t in ACTION_TYPES"
                :key="t"
                :value="t"
              >
                {{ ACTION_TYPE_LABELS[t] }} ({{ t }})
              </option>
            </select>
          </div>

          <!-- to (navigate 전용) -->
          <div
            v-if="needsTo(a.type)"
            class="space-y-1"
          >
            <Label :for="`a-to-${idx}`">to (라우트 또는 URL)</Label>
            <Input
              :id="`a-to-${idx}`"
              v-model="a.to"
              placeholder="예: /itsm 또는 https://example.com"
              autocomplete="off"
            />
          </div>
        </div>
      </CardContent>
    </Card>

    <p
      v-if="actions.length === 0"
      class="rounded-md border border-dashed border-border py-8 text-center text-sm text-foreground-muted"
    >
      아직 액션이 없습니다. 아래에서 액션을 추가하세요.
    </p>

    <Button
      variant="outline"
      class="w-full"
      @click="addAction"
    >
      <PlusIcon class="mr-1 size-4" /> 액션 추가
    </Button>
  </div>
</template>
