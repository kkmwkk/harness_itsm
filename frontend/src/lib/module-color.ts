/**
 * 모듈(systemType)별 시각 변별성 헬퍼 — UI_GUIDE §3 v2.
 *
 * 각 모듈 페이지는 헤더 띠·아이콘 배경·강조 텍스트에 모듈 컬러를 쓴다.
 * 색 값은 절대 하드코딩하지 않는다 — tokens.css 의 `--color-{module}` / `--color-{module}-soft`
 * 토큰(light/dark 자동 전환)과 그에 매핑된 Tailwind 유틸(`text-itsm`·`bg-itsm-soft` 등)만 노출한다.
 *
 * 글로벌 액센트(--color-primary)를 대체하지 않는다 — 모듈 컬러는 모듈 식별 표식 전용.
 */

import type { SystemType } from '@/types/meta';

export interface ModuleVisual {
  /** CSS var 참조 — 인라인 style 바인딩용 (예: 차트 시리즈 색) */
  primaryVar: string;
  /** soft(배경) CSS var 참조 */
  softVar: string;
  /** Tailwind text 색 유틸 */
  textClass: string;
  /** Tailwind soft 배경 유틸 */
  bgSoftClass: string;
  /** Tailwind border 색 유틸 */
  borderClass: string;
  /** lucide 아이콘 이름 (PascalCase) */
  icon: string;
}

const MODULE_VISUAL: Record<SystemType, ModuleVisual> = {
  ITSM: {
    primaryVar: 'var(--color-itsm)',
    softVar: 'var(--color-itsm-soft)',
    textClass: 'text-itsm',
    bgSoftClass: 'bg-itsm-soft',
    borderClass: 'border-itsm',
    icon: 'LifeBuoy',
  },
  ITAM: {
    primaryVar: 'var(--color-itam)',
    softVar: 'var(--color-itam-soft)',
    textClass: 'text-itam',
    bgSoftClass: 'bg-itam-soft',
    borderClass: 'border-itam',
    icon: 'Boxes',
  },
  PMS: {
    primaryVar: 'var(--color-pms)',
    softVar: 'var(--color-pms-soft)',
    textClass: 'text-pms',
    bgSoftClass: 'bg-pms-soft',
    borderClass: 'border-pms',
    icon: 'FolderKanban',
  },
  COMMON: {
    primaryVar: 'var(--color-common)',
    softVar: 'var(--color-common-soft)',
    textClass: 'text-common',
    bgSoftClass: 'bg-common-soft',
    borderClass: 'border-common',
    icon: 'Layers',
  },
  SYSTEM: {
    primaryVar: 'var(--color-system)',
    softVar: 'var(--color-system-soft)',
    textClass: 'text-system',
    bgSoftClass: 'bg-system-soft',
    borderClass: 'border-system',
    icon: 'Settings',
  },
};

/** SYSTEM 을 기본값으로 — 미지정/비정상 입력 방어 (slate 중립) */
export function moduleVisual(systemType: SystemType): ModuleVisual {
  return MODULE_VISUAL[systemType] ?? MODULE_VISUAL.SYSTEM;
}
