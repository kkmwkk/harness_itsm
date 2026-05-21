import Link from 'next/link';
import type { Summary } from '@/types/summary';

function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return '0m 0s';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds}s`;
}

function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const created = new Date(iso);
  const diffMs = now.getTime() - created.getTime();
  if (Number.isNaN(diffMs)) return '';
  const diffSec = Math.max(0, Math.floor(diffMs / 1000));
  if (diffSec < 60) return '방금 전';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.floor(diffHour / 24);
  return `${diffDay}일 전`;
}

interface SummaryCardProps {
  summary: Summary;
}

export function SummaryCard({ summary }: SummaryCardProps) {
  return (
    <Link
      href={`/${summary.id}`}
      className="block rounded-lg border border-hairline bg-canvas p-6 transition-transform active:scale-95"
    >
      <p className="text-[17px] font-semibold leading-[1.24] tracking-[-0.374px] text-ink">
        {summary.oneLiner}
      </p>
      <p className="mt-3 text-[14px] leading-[1.29] tracking-[-0.224px] text-ink-muted-48">
        {summary.channel} · {formatDuration(summary.duration)}
      </p>
      <p className="mt-1 text-[14px] leading-[1.29] tracking-[-0.224px] text-ink-muted-48">
        {formatRelativeTime(summary.createdAt)}
      </p>
    </Link>
  );
}
