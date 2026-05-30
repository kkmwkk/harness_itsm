<script setup lang="ts">
/**
 * 파일 업로드 위젯 — 드래그앤드롭 + "파일 선택" 버튼, 다중 파일, 이미지 thumbnail 미리보기.
 * 실 저장은 stub — POST /api/attachments(multipart) 가 메타(id·fileName·mime·size)만 반환한다(저장소 도입은 별도 ADR).
 * 모델 값은 업로드된 파일명 콤마 join(useFormSchema 의 file → string 계약과 호환).
 */
import { ref, shallowRef, computed } from 'vue'
import { UploadCloud, FileIcon, X } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { useApiFetch } from '@/lib/api'
import type { ApiEnvelope } from '@/types/meta'

interface AttachmentMeta {
  id: string
  fileName: string
  mime: string
  size: number
}
/** 화면에 표시하는 업로드 항목(미리보기 URL 포함). */
interface UploadItem extends AttachmentMeta {
  previewUrl?: string
  status: 'uploading' | 'done' | 'error'
}

interface Props {
  modelValue?: string | null
  id?: string
  multiple?: boolean
  accept?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  id: undefined,
  multiple: true,
  accept: undefined,
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const items = shallowRef<UploadItem[]>([])

const hasItems = computed(() => items.value.length > 0)

function isImage(mime: string): boolean {
  return mime.startsWith('image/')
}

function syncModel() {
  const names = items.value.filter((i) => i.status !== 'error').map((i) => i.fileName)
  emit('update:modelValue', names.join(', '))
}

async function uploadOne(file: File): Promise<void> {
  const previewUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined
  const pending: UploadItem = {
    id: `tmp-${file.name}-${file.size}`,
    fileName: file.name,
    mime: file.type || 'application/octet-stream',
    size: file.size,
    previewUrl,
    status: 'uploading',
  }
  items.value = [...items.value, pending]

  const form = new FormData()
  form.append('file', file)
  const { data, error } = await useApiFetch('/api/attachments')
    .post(form)
    .json<ApiEnvelope<AttachmentMeta>>()

  const meta = data.value?.data
  items.value = items.value.map((i) => {
    if (i.id !== pending.id) return i
    if (error.value || !meta) return { ...i, status: 'error' }
    return { ...meta, previewUrl, status: 'done' }
  })
  syncModel()
}

function handleFiles(fileList: FileList | null) {
  if (!fileList || props.disabled) return
  const files = Array.from(fileList)
  const picked = props.multiple ? files : files.slice(0, 1)
  if (!props.multiple) items.value = []
  for (const f of picked) void uploadOne(f)
}

function onInputChange(e: Event) {
  handleFiles((e.target as HTMLInputElement).files)
  if (inputRef.value) inputRef.value.value = ''
}
function onDrop(e: DragEvent) {
  dragOver.value = false
  handleFiles(e.dataTransfer?.files ?? null)
}
function remove(id: string) {
  const target = items.value.find((i) => i.id === id)
  if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl)
  items.value = items.value.filter((i) => i.id !== id)
  syncModel()
}
function openPicker() {
  if (!props.disabled) inputRef.value?.click()
}
</script>

<template>
  <div class="space-y-2">
    <div
      :class="[
        'flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-4 py-6 text-center transition-colors',
        dragOver ? 'border-primary bg-primary/5' : 'border-border bg-surface-muted',
        disabled ? 'opacity-50' : 'cursor-pointer',
      ]"
      role="button"
      tabindex="0"
      @click="openPicker"
      @keydown.enter.prevent="openPicker"
      @keydown.space.prevent="openPicker"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
    >
      <UploadCloud class="size-6 text-foreground-muted" />
      <p class="text-[13px] text-foreground-muted">
        파일을 끌어다 놓거나 클릭하여 선택하세요
      </p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        :disabled="disabled"
        @click.stop="openPicker"
      >
        파일 선택
      </Button>
      <input
        :id="id"
        ref="inputRef"
        type="file"
        class="hidden"
        :multiple="multiple"
        :accept="accept"
        :disabled="disabled"
        @change="onInputChange"
      >
    </div>

    <ul
      v-if="hasItems"
      class="space-y-1.5"
    >
      <li
        v-for="item in items"
        :key="item.id"
        class="flex items-center gap-3 rounded-md border border-border bg-surface px-2.5 py-1.5"
      >
        <img
          v-if="item.previewUrl && isImage(item.mime)"
          :src="item.previewUrl"
          :alt="item.fileName"
          class="size-9 shrink-0 rounded object-cover"
        >
        <span
          v-else
          class="inline-flex size-9 shrink-0 items-center justify-center rounded bg-surface-muted text-foreground-muted"
        >
          <FileIcon class="size-4" />
        </span>
        <div class="min-w-0 flex-1">
          <p class="truncate text-[13px] text-foreground">
            {{ item.fileName }}
          </p>
          <p class="text-[12px] text-foreground-subtle">
            <span v-if="item.status === 'uploading'">업로드 중…</span>
            <span
              v-else-if="item.status === 'error'"
              class="text-danger"
            >업로드 실패</span>
            <span v-else>{{ (item.size / 1024).toFixed(1) }} KB</span>
          </p>
        </div>
        <button
          type="button"
          class="text-foreground-subtle hover:text-foreground"
          aria-label="파일 제거"
          @click="remove(item.id)"
        >
          <X class="size-4" />
        </button>
      </li>
    </ul>
  </div>
</template>
