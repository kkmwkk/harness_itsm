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
      <CardHeader>
        <CardTitle>화면 노출용 메타 조회</CardTitle>
        <p class="text-xs text-foreground-muted mt-1">
          PageMeta 는 화면을 정의하는 JSON 메타이다. groupId(예: <code class="font-mono">itg-ticket</code>) 를
          입력하면 현재 배포(PUBLISHED) 중인 최신 버전의 메타 본문을 볼 수 있다.
        </p>
      </CardHeader>
      <CardContent class="space-y-3">
        <form
          class="flex gap-2"
          @submit.prevent="submit"
        >
          <Input
            v-model="draftId"
            placeholder="예: itg-ticket / itg-asset / itg-change"
          />
          <Button type="submit">
            조회
          </Button>
        </form>

        <p
          v-if="!groupId"
          class="text-sm text-foreground-muted"
        >
          왼쪽 입력란에 groupId 를 적고 [조회] 를 누르세요.
        </p>
        <p
          v-else-if="isFetching"
          class="text-sm text-foreground-muted"
        >
          조회 중...
        </p>
        <p
          v-else-if="notPublished"
          class="text-sm text-warning"
        >
          배포된 버전이 없습니다. DRAFT 만 있거나 그룹 자체가 존재하지 않습니다.
        </p>
        <p
          v-else-if="error"
          class="text-sm text-danger"
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
