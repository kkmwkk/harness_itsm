import { GoogleGenAI } from '@google/genai';

export class MissingApiKeyError extends Error {
  constructor(message = 'GEMINI_API_KEY is not set') {
    super(message);
    this.name = 'MissingApiKeyError';
  }
}

export class GeminiCallError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message);
    this.name = 'GeminiCallError';
    if (options?.cause !== undefined) {
      (this as { cause?: unknown }).cause = options.cause;
    }
  }
}

export interface GeminiSummaryRaw {
  markdown: string;
}

export const SUMMARY_PROMPT = `당신은 영상 콘텐츠를 한국어 마크다운 노트로 정리하는 도구입니다.
아래 영상 링크의 콘텐츠를 분석해 마크다운 한 편으로 정리하세요.

반드시 다음 형식을 지키세요:

1. 문서 맨 위는 YAML frontmatter 입니다. --- 두 줄로 감싸고 아래 키를 정확히 포함하세요:
   - title:      영상 제목 (string)
   - channel:    채널명 (string)
   - url:        원본 URL (string) — 입력으로 받은 값 그대로
   - duration:   영상 길이 (초 단위 정수). 모르면 0
   - one_liner:  영상의 한 줄 요약 (1문장, 80자 이내)

2. frontmatter 이후 본문은 영상의 성격(강의/토크/리뷰/브이로그/튜토리얼 등)에 맞춰
   적절한 섹션 구조를 직접 결정해 작성하세요. 섹션 구조는 영상마다 달라도 됩니다.

3. 본문은 한국어로 작성합니다. 단, 인명·기술 용어·고유명사는 원문 그대로 둡니다.

4. 마크다운 외 다른 텍스트 (서두 인사, 코드 펜스 \`\`\`markdown 같은 래퍼) 를 출력하지 마세요.

입력 URL: {url}`;

const DEFAULT_MODEL = 'gemini-2.5-flash';

function buildPrompt(url: string): string {
  return SUMMARY_PROMPT.replace('{url}', url);
}

function stripMarkdownFence(text: string): string {
  const trimmed = text.trim();
  const fenceMatch = trimmed.match(/^```(?:markdown|md)?\n([\s\S]*?)\n```$/);
  return fenceMatch && fenceMatch[1] !== undefined ? fenceMatch[1] : trimmed;
}

export async function summarizeYoutubeUrl(
  url: string,
): Promise<GeminiSummaryRaw> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new MissingApiKeyError();
  }

  const model = process.env.GEMINI_MODEL ?? DEFAULT_MODEL;
  const ai = new GoogleGenAI({ apiKey });

  let response;
  try {
    response = await ai.models.generateContent({
      model,
      contents: [
        {
          role: 'user',
          parts: [
            { fileData: { fileUri: url, mimeType: 'video/*' } },
            { text: buildPrompt(url) },
          ],
        },
      ],
    });
  } catch (err) {
    throw new GeminiCallError(
      `Gemini generateContent failed: ${err instanceof Error ? err.message : String(err)}`,
      { cause: err },
    );
  }

  const text = response.text;
  if (typeof text !== 'string' || text.trim().length === 0) {
    throw new GeminiCallError('Gemini returned empty response');
  }

  return { markdown: stripMarkdownFence(text) };
}
