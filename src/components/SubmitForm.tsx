'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Summary } from '@/types/summary';

interface ApiError {
  error: string;
  message: string;
}

export function SubmitForm() {
  const router = useRouter();
  const [url, setUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [elapsedSec, setElapsedSec] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!submitting) {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      setElapsedSec(0);
      return;
    }
    const startedAt = Date.now();
    timerRef.current = setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [submitting]);

  const disabled = submitting || url.trim().length === 0;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (disabled) return;
    setError(null);
    setSubmitting(true);
    try {
      const res = await fetch('/api/summarize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url.trim() }),
      });
      const data = (await res.json()) as Summary | ApiError;
      if (!res.ok) {
        const apiErr = data as ApiError;
        setError(apiErr.message ?? '요약에 실패했습니다.');
        return;
      }
      const summary = data as Summary;
      router.refresh();
      router.push(`/${summary.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="w-full">
      <div className="flex flex-col gap-3 md:flex-row md:items-center">
        <input
          type="text"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="유튜브 URL을 붙여 넣으세요"
          disabled={submitting}
          className="h-11 flex-1 rounded-pill border border-[rgba(0,0,0,0.08)] bg-canvas px-5 text-[17px] leading-[1.47] tracking-[-0.374px] text-ink outline-none focus:border-primary-focus disabled:opacity-60"
          aria-label="유튜브 URL"
        />
        <button
          type="submit"
          disabled={disabled}
          className="h-11 rounded-pill bg-primary px-[22px] text-[17px] leading-[1.47] tracking-[-0.374px] text-white transition-transform active:scale-95 disabled:opacity-60"
        >
          {submitting ? `요약 중 ${elapsedSec}s` : '요약하기'}
        </button>
      </div>
      {error ? (
        <p
          role="alert"
          className="mt-3 text-[14px] leading-[1.29] tracking-[-0.224px] text-[#ef4444]"
        >
          {error}
        </p>
      ) : null}
    </form>
  );
}
