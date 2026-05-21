import { randomUUID } from 'node:crypto';
import path from 'node:path';
import fs from 'node:fs';
import BetterSqlite3 from 'better-sqlite3';
import type { Database } from 'better-sqlite3';
import type { NewSummary, Summary } from '@/types/summary';

export type { Database };

const DEFAULT_DB_PATH = path.join(process.cwd(), 'data', 'tubenote.db');

let cached: { path: string; db: Database } | null = null;

export function getDb(dbPath: string = DEFAULT_DB_PATH): Database {
  if (cached && cached.path === dbPath && cached.db.open) {
    return cached.db;
  }
  if (dbPath !== ':memory:') {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  }
  const db = new BetterSqlite3(dbPath);
  db.pragma('journal_mode = WAL');
  migrate(db);
  if (dbPath !== ':memory:') {
    cached = { path: dbPath, db };
  }
  return db;
}

export function migrate(db: Database): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS summaries (
      id          TEXT PRIMARY KEY,
      url         TEXT NOT NULL,
      title       TEXT NOT NULL,
      channel     TEXT NOT NULL,
      duration    INTEGER NOT NULL DEFAULT 0,
      one_liner   TEXT NOT NULL,
      md_path     TEXT NOT NULL,
      created_at  TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_summaries_created_at ON summaries (created_at DESC);
  `);
}

interface SummaryRow {
  id: string;
  url: string;
  title: string;
  channel: string;
  duration: number;
  one_liner: string;
  md_path: string;
  created_at: string;
}

function rowToSummary(row: SummaryRow): Summary {
  return {
    id: row.id,
    url: row.url,
    title: row.title,
    channel: row.channel,
    duration: row.duration,
    oneLiner: row.one_liner,
    mdPath: row.md_path,
    createdAt: row.created_at,
  };
}

export function insertSummary(db: Database, input: NewSummary): Summary {
  const id = randomUUID();
  const createdAt = new Date().toISOString();
  db.prepare(
    `INSERT INTO summaries (id, url, title, channel, duration, one_liner, md_path, created_at)
     VALUES (@id, @url, @title, @channel, @duration, @oneLiner, @mdPath, @createdAt)`,
  ).run({
    id,
    url: input.url,
    title: input.title,
    channel: input.channel,
    duration: input.duration,
    oneLiner: input.oneLiner,
    mdPath: input.mdPath,
    createdAt,
  });
  return { id, createdAt, ...input };
}

export function getSummary(db: Database, id: string): Summary | null {
  const row = db
    .prepare('SELECT * FROM summaries WHERE id = ?')
    .get(id) as SummaryRow | undefined;
  return row ? rowToSummary(row) : null;
}

export function listSummaries(
  db: Database,
  opts: { limit?: number; offset?: number } = {},
): Summary[] {
  const limit = opts.limit ?? 50;
  const offset = opts.offset ?? 0;
  const rows = db
    .prepare(
      'SELECT * FROM summaries ORDER BY created_at DESC LIMIT ? OFFSET ?',
    )
    .all(limit, offset) as SummaryRow[];
  return rows.map(rowToSummary);
}

export function deleteSummary(db: Database, id: string): number {
  const info = db.prepare('DELETE FROM summaries WHERE id = ?').run(id);
  return info.changes;
}
