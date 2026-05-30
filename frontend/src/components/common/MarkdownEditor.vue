<script setup lang="ts">
/**
 * 마크다운 에디터 — 좌측 textarea + 우측 미리보기(marked + DOMPurify).
 * DynamicForm 의 type='textarea' + 옵션 markdown:true 일 때 사용.
 * 미리보기는 항상 새니타이즈된 HTML 만 렌더한다(raw HTML 금지).
 */
import { computed } from 'vue'
import { Textarea } from '@/components/ui/textarea'
import { renderMarkdown } from '@/lib/markdown'

interface Props {
  modelValue?: string | null
  id?: string
  placeholder?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  id: undefined,
  placeholder: '마크다운으로 작성하세요',
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const previewHtml = computed(() => renderMarkdown(props.modelValue))

function onInput(val: string | number) {
  emit('update:modelValue', String(val))
}
</script>

<template>
  <div class="grid gap-3 md:grid-cols-2">
    <div class="space-y-1">
      <p class="text-[12px] font-medium text-foreground-subtle">
        편집
      </p>
      <Textarea
        :id="id"
        :model-value="modelValue ?? ''"
        :placeholder="placeholder"
        :disabled="disabled"
        class="min-h-40 font-mono text-[13px]"
        @update:model-value="onInput"
      />
    </div>
    <div class="space-y-1">
      <p class="text-[12px] font-medium text-foreground-subtle">
        미리보기
      </p>
      <!-- eslint-disable vue/no-v-html -- renderMarkdown 가 DOMPurify 로 새니타이즈한 HTML 만 바인딩 -->
      <div
        class="markdown-preview min-h-40 rounded-md border border-border bg-surface-muted px-3 py-2 text-[14px] text-foreground"
        v-html="previewHtml || '<p class=\'text-foreground-subtle\'>미리볼 내용이 없습니다.</p>'"
      />
      <!-- eslint-enable vue/no-v-html -->
    </div>
  </div>
</template>

<style scoped>
.markdown-preview :deep(h1) {
  font-size: 20px;
  font-weight: 700;
  margin: 0.4em 0;
}
.markdown-preview :deep(h2) {
  font-size: 17px;
  font-weight: 600;
  margin: 0.4em 0;
}
.markdown-preview :deep(p) {
  margin: 0.4em 0;
}
.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
  list-style: revert;
}
.markdown-preview :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.92em;
  background: var(--color-surface-hover);
  padding: 0.1em 0.3em;
  border-radius: 4px;
}
.markdown-preview :deep(a) {
  color: var(--color-link);
  text-decoration: underline;
}
.markdown-preview :deep(blockquote) {
  border-left: 3px solid var(--color-border);
  padding-left: 0.8em;
  color: var(--color-foreground-muted);
}
</style>
