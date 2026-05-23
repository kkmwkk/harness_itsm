import fs from 'node:fs';
import matter from 'gray-matter';
import { markdownToBlocks } from '@tryfabric/martian';
import { getDb, getSummary } from '@/lib/db';
import { getNotionClient } from '@/lib/notion';
import type { Summary } from '@/types/summary';

export interface NotionSyncResult {
  pageId: string;
  pageUrl: string;
}

export class NotFoundError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'NotFoundError';
  }
}

export class NotionSyncError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message);
    this.name = 'NotionSyncError';
    if (options?.cause !== undefined) {
      (this as { cause?: unknown }).cause = options.cause;
    }
  }
}

function requireDatabaseId(): string {
  const databaseId = process.env.NOTION_DATABASE_ID;
  if (!databaseId || databaseId.length === 0) {
    throw new NotionSyncError('NOTION_DATABASE_ID is not set');
  }
  return databaseId;
}

interface NotionPageLite {
  id: string;
  url: string;
}

function isNotionPage(value: unknown): value is NotionPageLite {
  if (!value || typeof value !== 'object') return false;
  const v = value as Record<string, unknown>;
  return typeof v.id === 'string' && typeof v.url === 'string';
}

async function resolveDataSourceId(databaseId: string): Promise<string> {
  const notion = getNotionClient();
  const db = await notion.databases.retrieve({ database_id: databaseId });
  const sources = (db as { data_sources?: Array<{ id: string }> })
    .data_sources;
  const first = sources && sources.length > 0 ? sources[0] : undefined;
  if (!first) {
    throw new NotionSyncError(
      'Notion database has no data sources (integration may lack access)',
    );
  }
  return first.id;
}

async function findExistingPage(
  dataSourceId: string,
  summaryId: string,
): Promise<NotionPageLite | null> {
  const notion = getNotionClient();
  const res = await notion.dataSources.query({
    data_source_id: dataSourceId,
    filter: {
      property: 'TubenoteId',
      rich_text: { equals: summaryId },
    },
    page_size: 1,
  });
  const first = res.results[0];
  return isNotionPage(first) ? { id: first.id, url: first.url } : null;
}

function buildProperties(summary: Summary): Record<string, unknown> {
  return {
    Title: {
      title: [{ type: 'text', text: { content: summary.title } }],
    },
    URL: { url: summary.url },
    OneLiner: {
      rich_text: [{ type: 'text', text: { content: summary.oneLiner } }],
    },
    Channel: {
      rich_text: [{ type: 'text', text: { content: summary.channel } }],
    },
    TubenoteId: {
      rich_text: [{ type: 'text', text: { content: summary.id } }],
    },
  };
}

function readMarkdownBody(mdPath: string): string {
  let raw: string;
  try {
    raw = fs.readFileSync(mdPath, 'utf8');
  } catch (err) {
    throw new NotionSyncError(
      `Failed to read markdown file: ${err instanceof Error ? err.message : String(err)}`,
      { cause: err },
    );
  }
  return matter(raw).content;
}

export async function syncSummaryToNotion(
  summaryId: string,
): Promise<NotionSyncResult> {
  const db = getDb();
  const summary = getSummary(db, summaryId);
  if (!summary) {
    throw new NotFoundError(`Summary not found: ${summaryId}`);
  }

  const databaseId = requireDatabaseId();
  const dataSourceId = await resolveDataSourceId(databaseId);

  const existing = await findExistingPage(dataSourceId, summary.id);
  if (existing) {
    return { pageId: existing.id, pageUrl: existing.url };
  }

  const body = readMarkdownBody(summary.mdPath);

  let blocks;
  try {
    blocks = markdownToBlocks(body);
  } catch (err) {
    throw new NotionSyncError(
      `Failed to convert markdown to Notion blocks: ${err instanceof Error ? err.message : String(err)}`,
      { cause: err },
    );
  }

  const notion = getNotionClient();
  let page;
  try {
    page = await notion.pages.create({
      parent: { database_id: databaseId },
      properties: buildProperties(summary) as Parameters<
        typeof notion.pages.create
      >[0]['properties'],
      children: blocks as Parameters<typeof notion.pages.create>[0]['children'],
    });
  } catch (err) {
    throw new NotionSyncError(
      `Notion pages.create failed: ${err instanceof Error ? err.message : String(err)}`,
      { cause: err },
    );
  }

  if (!isNotionPage(page)) {
    throw new NotionSyncError('Notion pages.create returned unexpected shape');
  }

  return { pageId: page.id, pageUrl: page.url };
}
