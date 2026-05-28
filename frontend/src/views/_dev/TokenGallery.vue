<script setup lang="ts">
type ColorChip = {
  token: string;
  hex: string;
  bg: string;
  text: string;
  border?: string;
};

const accentChips: ColorChip[] = [
  { token: '--color-primary', hex: '#0066cc', bg: 'bg-primary', text: 'text-primary-foreground' },
  {
    token: '--color-primary-hover',
    hex: '#0057b3',
    bg: 'bg-primary-hover',
    text: 'text-primary-foreground',
  },
  {
    token: '--color-primary-foreground',
    hex: '#ffffff',
    bg: 'bg-primary-foreground',
    text: 'text-foreground',
    border: 'border-border',
  },
];

const surfaceChips: ColorChip[] = [
  {
    token: '--color-background',
    hex: '#ffffff',
    bg: 'bg-background',
    text: 'text-foreground',
    border: 'border-border',
  },
  {
    token: '--color-surface',
    hex: '#ffffff',
    bg: 'bg-surface',
    text: 'text-foreground',
    border: 'border-border',
  },
  {
    token: '--color-surface-muted',
    hex: '#f5f7fa',
    bg: 'bg-surface-muted',
    text: 'text-foreground',
  },
  {
    token: '--color-surface-hover',
    hex: '#eef2f7',
    bg: 'bg-surface-hover',
    text: 'text-foreground',
  },
  {
    token: '--color-surface-selected',
    hex: '#e6f0fb',
    bg: 'bg-surface-selected',
    text: 'text-foreground',
  },
];

const textChips: ColorChip[] = [
  {
    token: '--color-foreground',
    hex: '#1d1d1f',
    bg: 'bg-surface',
    text: 'text-foreground',
    border: 'border-border',
  },
  {
    token: '--color-foreground-muted',
    hex: '#5a6270',
    bg: 'bg-surface',
    text: 'text-foreground-muted',
    border: 'border-border',
  },
  {
    token: '--color-foreground-subtle',
    hex: '#9097a3',
    bg: 'bg-surface',
    text: 'text-foreground-subtle',
    border: 'border-border',
  },
  {
    token: '--color-link',
    hex: '#0066cc',
    bg: 'bg-surface',
    text: 'text-link',
    border: 'border-border',
  },
];

const borderChips: ColorChip[] = [
  {
    token: '--color-border',
    hex: '#e3e6ea',
    bg: 'bg-surface',
    text: 'text-foreground',
    border: 'border-border',
  },
  {
    token: '--color-border-subtle',
    hex: '#eef0f3',
    bg: 'bg-surface',
    text: 'text-foreground',
    border: 'border-border-subtle',
  },
  {
    token: '--color-ring',
    hex: '#0066cc',
    bg: 'bg-surface',
    text: 'text-foreground',
    border: 'border-ring',
  },
];

const semanticChips: ColorChip[] = [
  { token: '--color-success', hex: '#16a34a', bg: 'bg-success', text: 'text-white' },
  { token: '--color-warning', hex: '#d97706', bg: 'bg-warning', text: 'text-white' },
  { token: '--color-danger', hex: '#dc2626', bg: 'bg-danger', text: 'text-white' },
  { token: '--color-info', hex: '#0284c7', bg: 'bg-info', text: 'text-white' },
  { token: '--color-neutral', hex: '#525252', bg: 'bg-neutral', text: 'text-white' },
];

const radii = [
  { name: 'sm', cls: 'rounded-sm', value: '6px' },
  { name: 'md', cls: 'rounded-md', value: '8px' },
  { name: 'lg', cls: 'rounded-lg', value: '12px' },
  { name: 'pill', cls: 'rounded-pill', value: '9999px' },
  { name: 'full', cls: 'rounded-full', value: '50%' },
] as const;
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-10">
    <section>
      <h1>Token Gallery</h1>
      <p class="text-foreground-muted">
        UI_GUIDE.md 토큰 적용 검증용 (dev only)
      </p>
    </section>

    <section>
      <h2>Accent</h2>
      <div class="grid grid-cols-3 gap-3 mt-3">
        <div
          v-for="c in accentChips"
          :key="c.token"
          :class="[c.bg, c.text, c.border ?? '', 'rounded-lg border p-4 min-h-20']"
        >
          <div class="font-mono text-xs">
            {{ c.token }}
          </div>
          <div class="font-mono text-xs opacity-80">
            {{ c.hex }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Surface</h2>
      <div class="grid grid-cols-3 gap-3 mt-3">
        <div
          v-for="c in surfaceChips"
          :key="c.token"
          :class="[c.bg, c.text, c.border ?? '', 'rounded-lg border border-border p-4 min-h-20']"
        >
          <div class="font-mono text-xs">
            {{ c.token }}
          </div>
          <div class="font-mono text-xs opacity-80">
            {{ c.hex }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Text</h2>
      <div class="grid grid-cols-2 gap-3 mt-3">
        <div
          v-for="c in textChips"
          :key="c.token"
          :class="[c.bg, c.border ?? '', 'rounded-lg border p-4']"
        >
          <div :class="[c.text, 'font-mono text-xs']">
            {{ c.token }}
          </div>
          <div :class="[c.text, 'font-mono text-xs']">
            {{ c.hex }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Border</h2>
      <div class="grid grid-cols-3 gap-3 mt-3">
        <div
          v-for="c in borderChips"
          :key="c.token"
          :class="[c.bg, c.text, c.border, 'rounded-lg border-2 p-4 min-h-20']"
        >
          <div class="font-mono text-xs">
            {{ c.token }}
          </div>
          <div class="font-mono text-xs text-foreground-muted">
            {{ c.hex }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Semantic (상태 전용)</h2>
      <div class="grid grid-cols-5 gap-3 mt-3">
        <div
          v-for="c in semanticChips"
          :key="c.token"
          :class="[c.bg, c.text, 'rounded-lg p-4 min-h-20']"
        >
          <div class="font-mono text-xs">
            {{ c.token }}
          </div>
          <div class="font-mono text-xs opacity-90">
            {{ c.hex }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Radius</h2>
      <div class="flex gap-4 mt-3 items-end">
        <div
          v-for="r in radii"
          :key="r.name"
          class="flex flex-col items-center gap-2"
        >
          <div :class="['size-16 bg-primary', r.cls]" />
          <div class="font-mono text-xs text-foreground-muted">
            {{ r.name }} · {{ r.value }}
          </div>
        </div>
      </div>
    </section>

    <section>
      <h2>Typography</h2>
      <div class="mt-3 space-y-1">
        <h1>Heading 1 — 28px / 700</h1>
        <h2>Heading 2 — 20px / 600</h2>
        <h3>Heading 3 — 17px / 600</h3>
        <p>본문 14px / 400 — Pretendard Variable (없으면 Inter 폴백)</p>
        <p class="text-[13px]">
          그리드 셀 13px / 400
        </p>
        <p class="text-xs text-foreground-muted">
          캡션 12px / 400
        </p>
        <button class="text-[14px] font-medium text-primary">
          버튼 라벨 14px / 500
        </button>
      </div>
    </section>
  </main>
</template>
