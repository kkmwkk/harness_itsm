import { describe, it, expect } from 'vitest';
import { buildUrl } from '@/composables/usePageData';

/** 가상 샘플 api 경로만 사용한다(ADR-011). */
const API = '/api/tickets';

describe('buildUrl', () => {
  it('buildUrl_기본_page_0_size_20', () => {
    expect(buildUrl(API, {})).toBe('/api/tickets?page=0&size=20');
  });

  it('buildUrl_sort_field_asc_파라미터_포함', () => {
    expect(buildUrl(API, { sort: 'ticketNo,asc' })).toBe(
      '/api/tickets?page=0&size=20&sort=ticketNo%2Casc',
    );
  });

  it('buildUrl_filters_빈_값_제외', () => {
    expect(buildUrl(API, { filters: { status: '', priority: 'HIGH' } })).toBe(
      '/api/tickets?page=0&size=20&priority=HIGH',
    );
  });

  it('buildUrl_filters_여러_개_포함', () => {
    expect(buildUrl(API, { filters: { status: 'OPEN', priority: 'HIGH' } })).toBe(
      '/api/tickets?page=0&size=20&status=OPEN&priority=HIGH',
    );
  });

  it('buildUrl_size_50_적용', () => {
    expect(buildUrl(API, { page: 2, size: 50 })).toBe('/api/tickets?page=2&size=50');
  });
});
