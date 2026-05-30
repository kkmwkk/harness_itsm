/**
 * 아바타(이니셜·색) 순수 로직 — Avatar.vue 와 단위 테스트가 공유한다(DOM 없이 검증).
 *
 * 색은 절대 하드코딩하지 않는다(UI_GUIDE §2) — 모듈 컬러 토큰에 매핑된 Tailwind 유틸만
 * 팔레트로 쓰며, 이름 해시로 결정적(deterministic)으로 선택한다. 같은 이름 → 항상 같은 색.
 */

export interface AvatarColor {
  /** soft 배경 Tailwind 유틸 (토큰 기반) */
  bg: string;
  /** 텍스트 색 Tailwind 유틸 (토큰 기반) */
  text: string;
}

/**
 * 결정적 색 팔레트 — 모듈 컬러 5종의 soft/text 조합(tokens.css 토큰, light/dark 자동).
 * module-color.ts 가 동일 리터럴을 이미 쓰므로 Tailwind 가 유틸을 생성한다.
 */
export const AVATAR_PALETTE: readonly AvatarColor[] = [
  { bg: 'bg-itsm-soft', text: 'text-itsm' },
  { bg: 'bg-itam-soft', text: 'text-itam' },
  { bg: 'bg-pms-soft', text: 'text-pms' },
  { bg: 'bg-common-soft', text: 'text-common' },
  { bg: 'bg-system-soft', text: 'text-system' },
] as const;

/**
 * 이름 → 이니셜(최대 2글자). 공백·`_`·`-`·`.` 로 토큰 분리해 두 토큰의 첫 글자,
 * 단일 토큰이면 앞 2글자. 라틴 문자는 대문자화(한글은 no-op). 빈 값은 '?'.
 * 'user-sample-1' → 'US', 'admin' → 'AD', '홍길동' → '홍길'.
 */
export function avatarInitials(name: string | null | undefined): string {
  const s = (name ?? '').trim();
  if (!s) return '?';
  const tokens = s.split(/[\s_\-.]+/).filter(Boolean);
  const first = tokens[0] ?? s;
  const second = tokens[1];
  const raw = second ? first.charAt(0) + second.charAt(0) : first.slice(0, 2);
  return raw.toUpperCase();
}

/**
 * 이름 → 팔레트 인덱스(결정적). 문자 코드 합을 팔레트 길이로 나눈 나머지.
 * 같은 이름은 항상 같은 인덱스를 반환한다. 빈 값은 0.
 */
export function avatarColorIndex(name: string | null | undefined): number {
  const s = (name ?? '').trim();
  if (!s) return 0;
  let sum = 0;
  for (let i = 0; i < s.length; i += 1) {
    sum += s.charCodeAt(i);
  }
  return sum % AVATAR_PALETTE.length;
}

/** 팔레트 폴백(중립) — noUncheckedIndexedAccess 방어용 구체 값. */
const FALLBACK_COLOR: AvatarColor = { bg: 'bg-system-soft', text: 'text-system' };

/** 이름 → 결정적 색 조합. */
export function avatarColor(name: string | null | undefined): AvatarColor {
  // 인덱스는 항상 팔레트 범위 안이지만 noUncheckedIndexedAccess 방어로 폴백.
  return AVATAR_PALETTE[avatarColorIndex(name)] ?? FALLBACK_COLOR;
}
