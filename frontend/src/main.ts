import 'pretendard/dist/web/variable/pretendardvariable.css';
import './assets/styles/tokens.css';
import './assets/styles/shadcn-mapping.css';
import './assets/styles/base.css';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

createApp(App).use(createPinia()).use(router).mount('#app');
