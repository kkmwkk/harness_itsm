export interface Summary {
  id: string;
  url: string;
  title: string;
  channel: string;
  duration: number;
  oneLiner: string;
  mdPath: string;
  createdAt: string;
}

export type NewSummary = Omit<Summary, 'id' | 'createdAt'>;
