<script setup lang="ts">
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import type { FormMeta } from '@/types/meta-body';

const meta: FormMeta = {
  layout: 'two-column',
  fields: [
    { name: 'title', label: '제목', type: 'text', required: true, span: 2 },
    {
      name: 'category',
      label: '분류',
      type: 'select',
      options: [
        { value: 'BUG', label: '버그' },
        { value: 'REQ', label: '요청' },
        { value: 'QNA', label: '문의' },
      ],
    },
    {
      name: 'priority',
      label: '우선순위',
      type: 'radio',
      options: [
        { value: 'LOW', label: '낮음' },
        { value: 'MEDIUM', label: '보통' },
        { value: 'HIGH', label: '높음' },
        { value: 'CRITICAL', label: '긴급' },
      ],
    },
    { name: 'dueDate', label: '마감일', type: 'date' },
    { name: 'assignee', label: '담당자', type: 'user-picker' },
    { name: 'public', label: '공개 여부', type: 'checkbox', placeholder: '외부에 공개' },
    { name: 'content', label: '내용', type: 'textarea', span: 2, helpText: '최대 2000자' },
  ],
};

function onSubmit(v: Record<string, unknown>): void {
  console.warn('[DynamicFormSampler] submit', v); // ESLint: warn 만 허용
}
</script>

<template>
  <main class="mx-auto max-w-3xl p-6 space-y-4">
    <h1>Dynamic Form Sampler</h1>
    <DynamicForm
      :meta="meta"
      @submit="onSubmit"
    />
  </main>
</template>
