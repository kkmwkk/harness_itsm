/**
 * 알림 타입 — 백엔드 NotificationResponse 와 1:1 대응 (phase 16 step 7, PRD §6).
 * body 는 plain text 만 (raw HTML 금지 — 화면에서 v-html 사용 금지).
 */
export type NotificationType = 'WORKFLOW_STEP_ASSIGNED' | 'TICKET_STATUS_CHANGED';

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  body: string | null;
  relatedUrl: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}
