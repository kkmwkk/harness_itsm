<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { toast } from 'vue-sonner';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardDescription,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import { useAuthStore } from '@/stores/useAuthStore';
import type { ApiEnvelope } from '@/types/meta';
import type { TokenResponse } from '@/types/auth';

const username = ref('');
const password = ref('');
const isSubmitting = ref(false);
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

async function onSubmit() {
  if (!username.value || !password.value || isSubmitting.value) return;
  isSubmitting.value = true;
  try {
    const { data, statusCode } = await useApiFetch('/api/auth/login', {
      immediate: false,
      refetch: false,
    })
      .post({ username: username.value, password: password.value })
      .json<ApiEnvelope<TokenResponse>>();

    if (statusCode.value && statusCode.value >= 400) {
      toast.error(mapErrorCode(data.value?.errorCode ?? 'LOGIN_FAILED'));
      return;
    }
    if (!data.value?.data) {
      toast.error(mapErrorCode('LOGIN_FAILED'));
      return;
    }
    auth.setSession(data.value.data);
    toast.success(UI.success.loggedIn(data.value.data.user.name));
    const next = (route.query.next as string | undefined) ?? '/';
    void router.replace(next);
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <main
    class="flex min-h-screen items-center justify-center bg-surface-muted p-4"
  >
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-xl">
          Polestar10 ITG 로그인
        </CardTitle>
        <CardDescription>업무용 계정으로 로그인하세요.</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          class="space-y-4"
          @submit.prevent="onSubmit"
        >
          <div class="space-y-1">
            <Label
              for="login-username"
              class="text-sm font-medium"
            >아이디</Label>
            <Input
              id="login-username"
              v-model="username"
              autocomplete="username"
              placeholder="예: admin"
            />
          </div>
          <div class="space-y-1">
            <Label
              for="login-password"
              class="text-sm font-medium"
            >비밀번호</Label>
            <Input
              id="login-password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="비밀번호"
            />
          </div>
          <Button
            type="submit"
            class="w-full"
            :disabled="isSubmitting"
          >
            {{ isSubmitting ? '로그인 중...' : '로그인' }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </main>
</template>
