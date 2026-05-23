'use client';

import { useState } from 'react';

interface SyncResponse {
  pageId: string;
  pageUrl: string;
}

interface ApiError {
  error: string;
  message: string;
}

type State =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'synced'; pageUrl: string }
  | { kind: 'error'; message: string }
  | { kind: 'not_configured' };

interface Props {
  summaryId: string;
}

export function SyncToNotionButton({ summaryId }: Props) {
  const [state, setState] = useState<State>({ kind: 'idle' });

  async function handleClick() {
    setState({ kind: 'loading' });
    try {
      const res = await fetch('/api/notion', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ summaryId }),
      });
      const data = (await res.json()) as SyncResponse | ApiError;
      if (res.status === 503) {
        setState({ kind: 'not_configured' });
        return;
      }
      if (!res.ok) {
        const apiErr = data as ApiError;
        setState({
          kind: 'error',
          message: apiErr.message ?? '노션 동기화에 실패했습니다.',
        });
        return;
      }
      const ok = data as SyncResponse;
      setState({ kind: 'synced', pageUrl: ok.pageUrl });
    } catch (err) {
      setState({
        kind: 'error',
        message: err instanceof Error ? err.message : String(err),
      });
    }
  }

  if (state.kind === 'synced') {
    return (
      <a
        href={state.pageUrl}
        target="_blank"
        rel="noreferrer"
        className="inline-flex h-11 items-center rounded-pill border border-white px-[22px] text-[17px] leading-[1.47] tracking-[-0.374px] text-white transition-transform active:scale-95"
      >
        노션에서 열기 ↗
      </a>
    );
  }

  const label =
    state.kind === 'loading' ? '동기화 중…' : '노션으로 보내기';

  return (
    <div className="flex flex-col items-start gap-2">
      <button
        type="button"
        onClick={handleClick}
        disabled={state.kind === 'loading'}
        className="h-11 rounded-pill border border-white bg-transparent px-[22px] text-[17px] leading-[1.47] tracking-[-0.374px] text-white transition-transform active:scale-95 disabled:opacity-60"
      >
        {label}
      </button>
      {state.kind === 'not_configured' ? (
        <p
          role="status"
          className="text-[14px] leading-[1.29] tracking-[-0.224px] text-body-muted"
        >
          노션 미연결
        </p>
      ) : null}
      {state.kind === 'error' ? (
        <p
          role="alert"
          className="text-[14px] leading-[1.29] tracking-[-0.224px] text-[#ef4444]"
        >
          {state.message}
        </p>
      ) : null}
    </div>
  );
}
