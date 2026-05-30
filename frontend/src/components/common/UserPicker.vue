<script setup lang="ts">
/**
 * 사용자 선택 위젯 — 입력 시 /api/users?kw= 자동완성(debounce 200ms).
 * 선택된 사용자는 Avatar + 이름 + 제거 버튼으로 표시. 모델 값은 username 문자열.
 * DynamicForm 의 type='user-picker' 필드에서 사용.
 */
import { ref, shallowRef, computed } from 'vue'
import { watchDebounced, onClickOutside } from '@vueuse/core'
import { Search, X } from '@lucide/vue'
import Avatar from '@/components/common/Avatar.vue'
import { Input } from '@/components/ui/input'
import { useApiFetch } from '@/lib/api'
import type { ApiEnvelope } from '@/types/meta'
import type { PageResponse } from '@/types/page'
import type { UserListItem } from '@/types/system'

interface Props {
  modelValue?: string | null
  id?: string
  placeholder?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  id: undefined,
  placeholder: '이름·아이디로 검색',
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const root = ref<HTMLElement | null>(null)
const keyword = ref('')
const open = ref(false)
const loading = ref(false)
const results = shallowRef<UserListItem[]>([])
/** 선택된 사용자 객체(이름 표시용). 초기 편집 시엔 modelValue(id)만 있을 수 있다. */
const selected = shallowRef<UserListItem | null>(null)

const selectedLabel = computed(() => selected.value?.name ?? props.modelValue ?? '')

async function search(kw: string) {
  if (kw.trim().length === 0) {
    results.value = []
    return
  }
  loading.value = true
  const url = `/api/users?kw=${encodeURIComponent(kw.trim())}&page=0&size=8`
  const { data, error } = await useApiFetch(url)
    .get()
    .json<ApiEnvelope<PageResponse<UserListItem>>>()
  loading.value = false
  if (error.value) {
    results.value = []
    return
  }
  results.value = data.value?.data?.content ?? []
}

watchDebounced(
  keyword,
  (kw) => {
    void search(kw)
  },
  { debounce: 200 },
)

function pick(u: UserListItem) {
  selected.value = u
  emit('update:modelValue', u.username)
  open.value = false
  keyword.value = ''
  results.value = []
}
function clear() {
  selected.value = null
  emit('update:modelValue', '')
}
function onFocus() {
  open.value = true
}

onClickOutside(root, () => {
  open.value = false
})
</script>

<template>
  <div
    ref="root"
    class="relative"
  >
    <!-- 선택된 사용자 칩 -->
    <div
      v-if="modelValue"
      class="flex items-center gap-2 rounded-md border border-border bg-surface-muted px-2 py-1.5"
    >
      <Avatar
        :name="selectedLabel"
        size="sm"
      />
      <span class="flex-1 truncate text-[14px] text-foreground">{{ selectedLabel }}</span>
      <button
        v-if="!disabled"
        type="button"
        class="text-foreground-subtle hover:text-foreground"
        aria-label="선택 해제"
        @click="clear"
      >
        <X class="size-4" />
      </button>
    </div>

    <!-- 검색 입력 -->
    <div
      v-else
      class="relative"
    >
      <Search class="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-foreground-muted" />
      <Input
        :id="id"
        v-model="keyword"
        :placeholder="placeholder"
        :disabled="disabled"
        class="pl-8"
        autocomplete="off"
        @focus="onFocus"
      />
      <div
        v-if="open && (results.length > 0 || loading || keyword.trim())"
        class="absolute z-50 mt-1 w-full overflow-hidden rounded-md border border-border bg-popover shadow-overlay"
      >
        <p
          v-if="loading"
          class="px-3 py-2 text-[13px] text-foreground-muted"
        >
          검색 중…
        </p>
        <p
          v-else-if="results.length === 0"
          class="px-3 py-2 text-[13px] text-foreground-muted"
        >
          일치하는 사용자가 없습니다.
        </p>
        <ul
          v-else
          class="max-h-60 overflow-auto py-1"
        >
          <li
            v-for="u in results"
            :key="u.id"
          >
            <button
              type="button"
              class="flex w-full items-center gap-2 px-3 py-1.5 text-left hover:bg-surface-hover"
              @click="pick(u)"
            >
              <Avatar
                :name="u.name"
                size="sm"
              />
              <span class="flex-1 truncate text-[14px] text-foreground">{{ u.name }}</span>
              <span class="text-[12px] text-foreground-subtle">{{ u.username }}</span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
