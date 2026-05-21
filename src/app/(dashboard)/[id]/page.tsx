import fs from 'node:fs';
import matter from 'gray-matter';
import { notFound } from 'next/navigation';
import { getDb, getSummary } from '@/lib/db';
import { MarkdownRenderer } from '@/components/MarkdownRenderer';

export const dynamic = 'force-dynamic';

interface PageProps {
  params: Promise<{ id: string }>;
}

function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return '0m 0s';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds}s`;
}

export default async function SummaryDetailPage({ params }: PageProps) {
  const { id } = await params;
  const db = getDb();
  const summary = getSummary(db, id);
  if (!summary) {
    notFound();
  }

  const raw = fs.readFileSync(summary.mdPath, 'utf8');
  const body = matter(raw).content;

  return (
    <article className="-mx-6 -my-12 md:-my-16">
      <header className="bg-surface-tile-1 px-6 py-20">
        <div className="mx-auto max-w-[980px]">
          <p className="text-[14px] leading-[1.29] tracking-[-0.224px] text-body-muted">
            {summary.channel} · {formatDuration(summary.duration)}
          </p>
          <h1 className="mt-4 text-[40px] font-semibold leading-[1.10] tracking-[0] text-body-on-dark">
            {summary.title}
          </h1>
          <p className="mt-6 text-[17px] leading-[1.47] tracking-[-0.374px] text-body-muted">
            {summary.oneLiner}
          </p>
          <a
            href={summary.url}
            target="_blank"
            rel="noreferrer"
            className="mt-6 inline-block text-[14px] leading-[1.29] tracking-[-0.224px] text-primary-on-dark"
          >
            원본 영상 열기 ↗
          </a>
        </div>
      </header>
      <div className="mx-auto max-w-[980px] px-6 py-12 md:py-16">
        <MarkdownRenderer body={body} />
      </div>
    </article>
  );
}
