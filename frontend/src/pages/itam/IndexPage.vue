<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardContent } from '@/components/ui/card';
import AssetCategoryTree from '@/components/itam/AssetCategoryTree.vue';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
import {
  useAssetCategoryTree,
  findByCode,
} from '@/composables/useAssetCategory';
import { UI } from '@/lib/ui-messages';

/**
 * ITAM 자산원장 진입 (PRD §4-3 · ADR-004).
 * 좌측 분류 트리에서 분류를 고르면 그 분류의 form_meta_group_id 로 DynamicPage 메타를 분기한다.
 * 분류 미선택·상위 분류(폼 메타 없음) 선택 시에는 그리드를 fetch 하지 않는다(금지사항).
 */
const router = useRouter();
const { tree, isFetching, error } = useAssetCategoryTree();

const selectedCode = ref<string | null>(null);
const selectedNode = computed(() =>
  selectedCode.value ? findByCode(tree.value, selectedCode.value) : null,
);

// 선택된 분류의 폼 메타 그룹 — 없으면(상위 분류) 그리드 미노출.
const formGroupId = computed<string | null>(
  () => selectedNode.value?.formMetaGroupId ?? null,
);

// 폼 submit 시 자동 첨부할 분류 코드(meta-driven, 도메인 하드코딩 없음).
const submitDefaults = computed<Record<string, unknown>>(() => ({
  categoryCode: selectedCode.value,
}));

function onSelect(code: string): void {
  selectedCode.value = code;
}

// 그리드 행 클릭 → 자산 상세(/itam/:id) — 등록 시점 메타로 복원.
function onRowClick(row: unknown): void {
  const r = row as { id?: number };
  if (typeof r.id === 'number') void router.push(`/itam/${r.id}`);
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader title="ITAM 자산원장" />

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[240px_1fr]">
      <!-- 좌: 분류 트리 -->
      <Card class="self-start">
        <CardContent class="py-4">
          <h2 class="mb-2 text-[13px] font-semibold text-foreground-muted">
            자산 분류
          </h2>
          <p
            v-if="isFetching && tree.length === 0"
            class="text-[13px] text-foreground-muted"
          >
            {{ UI.loading.data }}
          </p>
          <p
            v-else-if="error"
            class="text-[13px] text-danger"
          >
            {{ error }}
          </p>
          <p
            v-else-if="tree.length === 0"
            class="text-[13px] text-foreground-muted"
          >
            {{ UI.empty.grid }}
          </p>
          <AssetCategoryTree
            v-else
            :nodes="tree"
            :selected-code="selectedCode"
            @select="onSelect"
          />
        </CardContent>
      </Card>

      <!-- 우: 선택된 분류의 동적 화면 -->
      <div>
        <DynamicPage
          v-if="formGroupId"
          :key="formGroupId"
          :group-id="formGroupId"
          :submit-defaults="submitDefaults"
          @row-click="onRowClick"
        />
        <Card v-else>
          <CardContent class="py-12 text-center space-y-1">
            <p class="text-base font-semibold">
              분류를 선택하세요.
            </p>
            <p class="text-xs text-foreground-subtle">
              {{ selectedNode
                ? '이 분류에는 등록 폼이 연결되어 있지 않습니다. 하위 분류를 선택하세요.'
                : '좌측 분류 트리에서 자산 분류를 선택하면 해당 분류의 자산 목록과 등록 폼이 표시됩니다.' }}
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  </section>
</template>
