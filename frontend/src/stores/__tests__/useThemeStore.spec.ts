import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useThemeStore } from '@/stores/useThemeStore';

// vitest 환경이 node 라 브라우저 전역을 직접 stub 한다.
const STORAGE_KEY = 'itg.theme';

class MemoryStorage {
  private map = new Map<string, string>();
  getItem(key: string) {
    return this.map.has(key) ? (this.map.get(key) as string) : null;
  }
  setItem(key: string, value: string) {
    this.map.set(key, value);
  }
  clear() {
    this.map.clear();
  }
}

const classList = {
  toggle: vi.fn(),
};

// prefers-color-scheme: dark 가 true 인 환경을 흉내낸다.
const matchMediaMock = vi.fn().mockReturnValue({
  matches: true,
  addEventListener: vi.fn(),
});

beforeEach(() => {
  vi.stubGlobal('localStorage', new MemoryStorage());
  vi.stubGlobal('document', { documentElement: { classList } });
  vi.stubGlobal('window', { matchMedia: matchMediaMock });
  classList.toggle.mockClear();
  matchMediaMock.mockClear();
  setActivePinia(createPinia());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useThemeStore', () => {
  it('기본 모드는 system 이다 (localStorage 비어 있을 때)', () => {
    const theme = useThemeStore();
    expect(theme.mode).toBe('system');
  });

  it('setMode(dark) 는 localStorage 저장 + .dark 클래스 토글', () => {
    const theme = useThemeStore();
    theme.setMode('dark');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
    expect(classList.toggle).toHaveBeenLastCalledWith('dark', true);
    expect(theme.effective()).toBe('dark');
  });

  it('setMode(light) 는 .dark 클래스를 제거 토글', () => {
    const theme = useThemeStore();
    theme.setMode('light');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');
    expect(classList.toggle).toHaveBeenLastCalledWith('dark', false);
    expect(theme.effective()).toBe('light');
  });

  it('system 모드는 prefers-color-scheme 를 따른다', () => {
    const theme = useThemeStore();
    theme.setMode('system');
    // matchMediaMock.matches === true → dark
    expect(theme.effective()).toBe('dark');
    expect(classList.toggle).toHaveBeenLastCalledWith('dark', true);
  });

  it('저장된 모드가 있으면 초기값으로 복원한다', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    const theme = useThemeStore();
    expect(theme.mode).toBe('light');
  });
});
