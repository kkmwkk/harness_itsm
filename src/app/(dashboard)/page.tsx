import { getDb, listSummaries } from '@/lib/db';
import { SubmitForm } from '@/components/SubmitForm';
import { SummaryCard } from '@/components/SummaryCard';

export const dynamic = 'force-dynamic';

export default function DashboardPage() {
  const db = getDb();
  const summaries = listSummaries(db, { limit: 30 });

  return (
    <div className="flex flex-col gap-12">
      <section>
        <h1 className="mb-8 text-[34px] font-semibold leading-[1.10] tracking-[-0.374px] text-ink">
          유튜브 영상을 요약하세요
        </h1>
        <SubmitForm />
      </section>

      <section>
        <h2 className="mb-6 text-[28px] font-semibold leading-[1.14] tracking-[0.196px] text-ink">
          최근 요약
        </h2>
        {summaries.length === 0 ? (
          <p className="text-[17px] leading-[1.47] tracking-[-0.374px] text-ink-muted-48">
            아직 요약된 영상이 없습니다.
          </p>
        ) : (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {summaries.map((summary) => (
              <SummaryCard key={summary.id} summary={summary} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
