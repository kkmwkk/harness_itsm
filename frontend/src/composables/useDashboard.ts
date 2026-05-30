import { computed, onScopeDispose, type Ref } from 'vue';
import { useIntervalFn } from '@vueuse/core';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';
import type { CountByKey, DashboardSummary } from '@/types/dashboard';
import type { DonutItem } from '@/lib/chart-theme';

/** 티켓 우선순위 코드 → 한글 라벨 (순수 함수, 테스트 대상). */
export function priorityLabel(key: string): string {
  switch (key) {
    case 'LOW':
      return '낮음';
    case 'MEDIUM':
      return '보통';
    case 'HIGH':
      return '높음';
    case 'CRITICAL':
      return '긴급';
    default:
      return key;
  }
}

/** 티켓 상태 코드 → 한글 라벨. */
export function statusLabel(key: string): string {
  switch (key) {
    case 'OPEN':
      return '접수';
    case 'IN_PROGRESS':
      return '진행';
    case 'RESOLVED':
      return '해결';
    case 'CLOSED':
      return '종료';
    default:
      return key;
  }
}

/** CountByKey[] → DonutChart items (라벨 매핑 적용). */
export function toDonutItems(
  counts: CountByKey[],
  labelFn: (key: string) => string = (k) => k,
): DonutItem[] {
  return counts
    .filter((c) => c.count > 0)
    .map((c) => ({ name: labelFn(c.key), value: c.count }));
}

/** 합계 (KPI·도넛 센터 라벨용). */
export function sumCounts(counts: CountByKey[]): number {
  return counts.reduce((acc, c) => acc + c.count, 0);
}

/**
 * ISO 시각 → 상대 시간 한글 표기 (순수 함수, nowMs 주입으로 테스트 가능).
 * 미래/파싱 불가 시 빈 문자열.
 */
export function relativeTime(iso: string | null, nowMs: number): string {
  if (!iso) return '';
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return '';
  const diffSec = Math.floor((nowMs - t) / 1000);
  if (diffSec < 0) return '방금 전';
  if (diffSec < 60) return '방금 전';
  const min = Math.floor(diffSec / 60);
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day === 1) return '어제';
  if (day < 7) return `${day}일 전`;
  const week = Math.floor(day / 7);
  if (week < 5) return `${week}주 전`;
  return `${Math.floor(day / 30)}개월 전`;
}

export interface UseDashboardOptions {
  /** 30초 폴링 활성화 (기본 false). */
  poll?: boolean;
  /** 폴링 주기(ms). */
  intervalMs?: number;
}

export interface UseDashboardResult {
  summary: Ref<DashboardSummary | null>;
  isFetching: Ref<boolean>;
  error: Ref<string | null>;
  reload: () => Promise<void>;
}

/**
 * 대시보드 요약 조회 (`GET /api/dashboard/summary`). 결과는 인증 사용자 기준.
 * opts.poll 이 true 면 intervalMs(기본 30초)마다 재조회한다(스코프 종료 시 자동 정지).
 */
export function useDashboard(opts: UseDashboardOptions = {}): UseDashboardResult {
  const {
    data,
    isFetching,
    error: fetchError,
    execute,
  } = useApiFetch('/api/dashboard/summary', { refetch: true }).json<
    ApiEnvelope<DashboardSummary>
  >();

  const summary = computed<DashboardSummary | null>(() => data.value?.data ?? null);

  const error = computed<string | null>(() => {
    if (!fetchError.value) return null;
    const code = data.value?.errorCode;
    return code ? mapErrorCode(code) : UI.error.dataLoad;
  });

  async function reload(): Promise<void> {
    await execute();
  }

  if (opts.poll) {
    const { pause } = useIntervalFn(() => {
      void reload();
    }, opts.intervalMs ?? 30_000);
    onScopeDispose(pause);
  }

  return { summary, isFetching, error, reload };
}
