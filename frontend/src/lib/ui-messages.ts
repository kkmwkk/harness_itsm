/**
 * 사용자 화면 노출 메시지 단일 카탈로그 (ADR-020).
 * 백엔드 errorCode 토큰은 분기·로깅 용도이며 화면에는 한글 메시지만 노출.
 * 다국어 도입(v2.2) 시 본 파일만 키별 다국어 리소스로 전환.
 */
export const UI = {
  empty: {
    grid:    '아직 등록된 항목이 없습니다.',
    metaNotPublished: '아직 준비된 화면이 없습니다. 메타가 등록되지 않았거나 배포되지 않은 상태입니다.',
    metaNotPublishedHint: '시스템 관리자에게 메타 등록·배포를 요청하거나, 메타 관리에서 DRAFT 를 PUBLISHED 로 전환하세요.',
    menuEmpty: '접근 가능한 메뉴가 없습니다. 관리자에게 권한을 요청하세요.',
  },
  error: {
    metaLoad:     '메타를 불러올 수 없습니다.',
    metaShape:    '메타 본문 형식이 올바르지 않습니다. 관리자에게 문의하세요.',
    dataLoad:     '데이터를 불러올 수 없습니다.',
    submit:       '저장에 실패했습니다.',
    submitDetail: (entityName: string) => `${entityName} 저장에 실패했습니다.`,
    network:      '네트워크 연결을 확인하세요.',
    authRequired: '로그인이 필요합니다.',
    forbidden:    '이 기능에 접근할 권한이 없습니다.',
    notFound:     '요청한 데이터를 찾을 수 없습니다.',
    invalidInput: '입력값을 확인하세요.',
    unknown:      '일시적인 오류가 발생했습니다. 잠시 후 다시 시도하세요.',
  },
  loading: {
    meta:    '화면 정의를 불러오는 중...',
    data:    '데이터를 불러오는 중...',
    menu:    '메뉴를 불러오는 중...',
    submit:  '저장 중...',
  },
  success: {
    created:  (entityName: string) => `${entityName} 이(가) 생성되었습니다.`,
    updated:  (entityName: string) => `${entityName} 이(가) 수정되었습니다.`,
    deleted:  (entityName: string) => `${entityName} 이(가) 삭제되었습니다.`,
    loggedIn: (userName: string) => `${userName}님 환영합니다.`,
    loggedOut:'로그아웃되었습니다.',
  },
} as const;

/**
 * 백엔드 errorCode → 사용자 메시지 매핑.
 * 매핑되지 않은 코드는 UI.error.unknown 으로 fallback.
 */
const ERROR_CODE_MAP: Record<string, string> = {
  META_NOT_PUBLISHED: UI.empty.metaNotPublished,
  META_NOT_FOUND:     UI.error.metaLoad,
  TICKET_NOT_FOUND:   UI.error.notFound,
  ASSET_NOT_FOUND:    UI.error.notFound,
  USER_NOT_FOUND:     UI.error.notFound,
  VALIDATION_FAILED:  UI.error.invalidInput,
  AUTH_REQUIRED:      UI.error.authRequired,
  FORBIDDEN:          UI.error.forbidden,
  LOGIN_FAILED:       '아이디 또는 비밀번호가 올바르지 않습니다.',
  INVALID_TOKEN:      UI.error.authRequired,
  INVALID_REQUEST:    UI.error.invalidInput,
  DATA_INTEGRITY:     UI.error.invalidInput,
  INTERNAL_ERROR:     UI.error.unknown,
};

export function mapErrorCode(code: string | null | undefined): string {
  if (!code) return UI.error.unknown;
  return ERROR_CODE_MAP[code] ?? UI.error.unknown;
}
