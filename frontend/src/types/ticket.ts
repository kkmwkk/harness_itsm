/**
 * ITSM 티켓 프론트 타입 — 백엔드 TicketSummary(목록 경량 DTO)와 1:1.
 * 칸반 보드(DynamicKanban)·목록이 공유한다. status 전이 규칙은 백엔드 책임이며
 * 프론트는 전이 매트릭스를 강제하지 않는다(금지사항).
 */
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface TicketSummary {
  id: number;
  ticketNo: string;
  title: string;
  priority: TicketPriority | null;
  status: TicketStatus;
  assigneeId: string | null;
  createdAt: string;
  /**
   * 워크플로우 SLA 마감 시각(선택). 백엔드 TicketSummary 는 현재 제공하지 않으므로
   * 보통 부재하며, 제공될 경우 카드에 잔여시간을 표시한다.
   */
  slaDueAt?: string | null;
}
