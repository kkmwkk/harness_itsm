import { Client } from '@notionhq/client';

export class MissingNotionTokenError extends Error {
  constructor(message = 'NOTION_TOKEN is not set') {
    super(message);
    this.name = 'MissingNotionTokenError';
  }
}

let cached: { token: string; client: Client } | null = null;

export function getNotionClient(): Client {
  const token = process.env.NOTION_TOKEN;
  if (!token || token.length === 0) {
    throw new MissingNotionTokenError();
  }
  if (cached && cached.token === token) {
    return cached.client;
  }
  const client = new Client({ auth: token });
  cached = { token, client };
  return client;
}
