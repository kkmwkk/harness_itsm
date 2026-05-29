/**
 * Spring Data Page 직렬화 형태 (백엔드 PageResponse<T> 와 1:1 대응).
 * GET /api/tickets 응답 = ApiEnvelope<PageResponse<TicketSummary>>.
 * DynamicPage 는 도메인-중립이므로 T 를 generic 으로 다룬다.
 */
export interface PageResponse<T> {
  content:       T[];
  totalElements: number;
  totalPages:    number;
  number:        number;   // page index (0-based)
  size:          number;
}
