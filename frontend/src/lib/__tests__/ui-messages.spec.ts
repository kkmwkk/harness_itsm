import { describe, it, expect } from 'vitest';
import { UI, mapErrorCode } from '@/lib/ui-messages';

/**
 * 사용자 화면 노출 메시지 단일 카탈로그(ADR-020) 검증.
 * raw 백엔드 errorCode 토큰이 화면 메시지로 새지 않음을 보장한다.
 */
describe('UI 카탈로그', () => {
  it('UI_카탈로그_모든_키_string_타입', () => {
    // 함수형 라벨(인자 받는 키) 을 제외한 모든 말단 값은 한글 string 이어야 한다.
    const assertLeaf = (v: unknown) => {
      if (typeof v === 'function') return; // success.created 등 함수형 라벨
      if (typeof v === 'object' && v !== null) {
        Object.values(v).forEach(assertLeaf);
        return;
      }
      expect(typeof v).toBe('string');
      // 한글 메시지만 — 백엔드 errorCode 토큰(대문자_언더스코어) 형태가 아니어야 한다.
      expect(v).not.toMatch(/^[A-Z_]+$/);
    };
    assertLeaf(UI);
  });

  it('UI_empty_error_loading_success_4_카테고리_풀세트', () => {
    expect(UI.empty).toBeTruthy();
    expect(UI.error).toBeTruthy();
    expect(UI.loading).toBeTruthy();
    expect(UI.success).toBeTruthy();
  });

  it('UI.success.created_함수형_라벨_생성', () => {
    expect(UI.success.created('사용자')).toBe('사용자 이(가) 생성되었습니다.');
    expect(UI.success.loggedIn('홍길동')).toBe('홍길동님 환영합니다.');
  });
});

describe('mapErrorCode', () => {
  it('mapErrorCode_META_NOT_PUBLISHED_매핑', () => {
    expect(mapErrorCode('META_NOT_PUBLISHED')).toBe(UI.empty.metaNotPublished);
  });

  it('mapErrorCode_LOGIN_FAILED_매핑', () => {
    expect(mapErrorCode('LOGIN_FAILED')).toBe(
      '아이디 또는 비밀번호가 올바르지 않습니다.',
    );
  });

  it('mapErrorCode_unknown_code_fallback', () => {
    expect(mapErrorCode('SOME_UNMAPPED_CODE')).toBe(UI.error.unknown);
  });

  it('mapErrorCode_null_fallback', () => {
    expect(mapErrorCode(null)).toBe(UI.error.unknown);
    expect(mapErrorCode(undefined)).toBe(UI.error.unknown);
  });

  it('mapErrorCode_결과에_raw_토큰_미포함', () => {
    // 매핑 결과 어디에도 errorCode 토큰 문자열이 남지 않아야 한다.
    expect(mapErrorCode('META_NOT_PUBLISHED')).not.toContain('META_NOT_PUBLISHED');
    expect(mapErrorCode('TICKET_NOT_FOUND')).not.toContain('TICKET_NOT_FOUND');
  });
});
