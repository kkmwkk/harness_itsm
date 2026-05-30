import 'pretendard/dist/web/variable/pretendardvariable.css';
import './assets/styles/tokens.css';
import './assets/styles/shadcn-mapping.css';
import './assets/styles/base.css';
import './assets/styles/ag-theme-itg.css';
import 'vue-sonner/style.css';
import './assets/styles/toast.css';
import '@/lib/ag-grid-modules';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useThemeStore } from '@/stores/useThemeStore';

const pinia = createPinia();
const app = createApp(App).use(pinia).use(router);
// 부팅 시 즉시 테마 적용 — 다크 모드 flash(FOUC) 회피
useThemeStore(pinia).apply();
app.mount('#app');
