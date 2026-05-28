import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useLayoutStore = defineStore('layout', () => {
  const sidebarCollapsed = ref(false);
  const sidebarMobileOpen = ref(false);

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }
  function toggleMobile() {
    sidebarMobileOpen.value = !sidebarMobileOpen.value;
  }
  function closeMobile() {
    sidebarMobileOpen.value = false;
  }

  return { sidebarCollapsed, sidebarMobileOpen, toggleSidebar, toggleMobile, closeMobile };
});
