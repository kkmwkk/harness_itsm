<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useLayoutStore } from '@/stores/useLayoutStore';
import TopBar from './TopBar.vue';
import Sidebar from './Sidebar.vue';

const layout = useLayoutStore();
const { sidebarMobileOpen } = storeToRefs(layout);
</script>

<template>
  <div class="flex min-h-screen bg-background text-foreground">
    <Sidebar />
    <div
      v-if="sidebarMobileOpen"
      class="fixed inset-0 z-30 bg-foreground/40 md:hidden"
      aria-hidden="true"
      @click="layout.closeMobile()"
    />
    <div class="flex flex-1 flex-col">
      <TopBar />
      <main class="flex-1 px-6 py-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
