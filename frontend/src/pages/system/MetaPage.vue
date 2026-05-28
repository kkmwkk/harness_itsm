<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { usePageMeta } from '@/composables/usePageMeta';

const route = useRoute();
const router = useRouter();

const groupId = computed(() => (route.query.groupId as string | undefined) ?? '');
const draftId = ref(groupId.value);

const { meta, notPublished, error, isFetching } = usePageMeta(groupId);

function submit(): void {
  const next = draftId.value.trim();
  void router.replace({ query: next ? { groupId: next } : {} });
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <Card>
      <CardHeader><CardTitle>화면 노출용 메타 조회</CardTitle></CardHeader>
      <CardContent class="space-y-3">
        <form
          class="flex gap-2"
          @submit.prevent="submit"
        >
          <Input
            v-model="draftId"
            placeholder="예: itg-ticket"
          />
          <Button type="submit">
            조회
          </Button>
        </form>

        <p
          v-if="!groupId"
          class="text-foreground-muted"
        >
          groupId 를 입력하라.
        </p>
        <p
          v-else-if="isFetching"
          class="text-foreground-muted"
        >
          조회 중...
        </p>
        <p
          v-else-if="notPublished"
          class="text-warning"
        >
          배포된 버전이 없습니다 (META_NOT_PUBLISHED).
        </p>
        <p
          v-else-if="error"
          class="text-danger"
        >
          {{ error }}
        </p>
      </CardContent>
    </Card>

    <Card v-if="meta">
      <CardHeader><CardTitle>{{ meta.title }} ({{ meta.id }})</CardTitle></CardHeader>
      <CardContent>
        <dl class="grid grid-cols-2 gap-2 text-[13px]">
          <dt class="text-foreground-muted">
            systemType
          </dt>
          <dd>{{ meta.systemType }}</dd>
          <dt class="text-foreground-muted">
            packageType
          </dt>
          <dd>{{ meta.packageType }}</dd>
          <dt class="text-foreground-muted">
            version
          </dt>
          <dd>v{{ meta.majorVersion }}.{{ meta.minorVersion }}</dd>
          <dt class="text-foreground-muted">
            metaStatus
          </dt>
          <dd>{{ meta.metaStatus }}</dd>
        </dl>
        <pre
          class="mt-3 overflow-auto rounded-md bg-surface-muted p-3 font-mono text-[12px]"
        >{{ JSON.stringify(meta.metaJson, null, 2) }}</pre>
      </CardContent>
    </Card>
  </section>
</template>
