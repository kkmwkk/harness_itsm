/** 백엔드 DashboardSummary(`GET /api/dashboard/summary`) 대응 타입. */

export interface CountByKey {
  key: string;
  count: number;
}

export interface RecentActivity {
  type: 'TICKET' | 'ASSET';
  title: string;
  detail: string;
  at: string;
}

export interface MyWorkflowStep {
  instanceId: number;
  ticketId: number | null;
  stepIndex: number;
  stepName: string;
  assigneeRole: string | null;
  startedAt: string;
  slaDueAt: string | null;
  overdue: boolean;
}

export interface AdminStats {
  userCount: number;
  menuCount: number;
  metaGroupCount: number;
}

export interface DashboardSummary {
  openTickets: number;
  totalAssets: number;
  slaBreachCount: number;
  myOpenTickets: number;
  ticketsByPriority: CountByKey[];
  ticketsByStatus: CountByKey[];
  assetsByCategory: CountByKey[];
  lifecycleTrend: number[];
  recentActivities: RecentActivity[];
  myWorkflowSteps: MyWorkflowStep[];
  adminStats: AdminStats | null;
}
