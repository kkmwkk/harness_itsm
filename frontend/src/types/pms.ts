/** PMS(프로젝트·태스크) 도메인 타입 (phase 16 step 5). 백엔드 ProjectResponse/TaskResponse 와 1:1. */
export type ProjectStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'ON_HOLD';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface TaskSummary {
  id: number;
  title: string;
  status: TaskStatus;
  assigneeUserId: number | null;
  startDate: string | null;
  dueDate: string | null;
  progress: number;
}

export interface ProjectSummary {
  id: number;
  code: string;
  name: string;
  status: ProjectStatus;
  ownerUserId: number | null;
  deptId: number | null;
  startDate: string | null;
  dueDate: string | null;
  progress: number;
  tasks: TaskSummary[];
}
