'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownRendererProps {
  body: string;
}

export function MarkdownRenderer({ body }: MarkdownRendererProps) {
  return (
    <div className="text-[17px] font-normal leading-[1.47] tracking-[-0.374px] text-ink">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children, ...props }) => (
            <h1
              {...props}
              className="mt-12 mb-6 text-[40px] font-semibold leading-[1.10] tracking-[0] text-ink"
            >
              {children}
            </h1>
          ),
          h2: ({ children, ...props }) => (
            <h2
              {...props}
              className="mt-10 mb-4 text-[34px] font-semibold leading-[1.10] tracking-[-0.374px] text-ink"
            >
              {children}
            </h2>
          ),
          h3: ({ children, ...props }) => (
            <h3
              {...props}
              className="mt-8 mb-3 text-[28px] font-semibold leading-[1.14] tracking-[0.196px] text-ink"
            >
              {children}
            </h3>
          ),
          p: ({ children, ...props }) => (
            <p
              {...props}
              className="my-4 text-[17px] font-normal leading-[1.47] tracking-[-0.374px] text-ink"
            >
              {children}
            </p>
          ),
          a: ({ children, ...props }) => (
            <a
              {...props}
              className="text-primary underline underline-offset-2"
            >
              {children}
            </a>
          ),
          ul: ({ children, ...props }) => (
            <ul {...props} className="my-4 list-disc pl-6">
              {children}
            </ul>
          ),
          ol: ({ children, ...props }) => (
            <ol {...props} className="my-4 list-decimal pl-6">
              {children}
            </ol>
          ),
          li: ({ children, ...props }) => (
            <li
              {...props}
              className="my-1 text-[17px] leading-[1.47] tracking-[-0.374px] text-ink"
            >
              {children}
            </li>
          ),
          blockquote: ({ children, ...props }) => (
            <blockquote
              {...props}
              className="my-6 border-l-4 border-hairline pl-4 text-ink-muted-80"
            >
              {children}
            </blockquote>
          ),
          code: ({ children, ...props }) => (
            <code
              {...props}
              className="rounded-sm bg-canvas-parchment px-1 py-0.5 text-[15px] font-normal text-ink"
            >
              {children}
            </code>
          ),
          pre: ({ children, ...props }) => (
            <pre
              {...props}
              className="my-6 overflow-x-auto rounded-lg bg-canvas-parchment p-4 text-[15px] leading-[1.47] text-ink"
            >
              {children}
            </pre>
          ),
        }}
      >
        {body}
      </ReactMarkdown>
    </div>
  );
}
