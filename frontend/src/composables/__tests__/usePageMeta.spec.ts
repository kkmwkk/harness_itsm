import { describe, it, expect } from 'vitest';
import { resolvePageMetaUrl, isMetaIdMode } from '@/composables/usePageMeta';

/** 가상 샘플 식별자만 사용한다(ADR-011). */
describe('resolvePageMetaUrl', () => {
  it('resolvePageMetaUrl_string_groupId_단축', () => {
    expect(resolvePageMetaUrl('itg-asset')).toBe('/api/meta/active/itg-asset');
  });

  it('resolvePageMetaUrl_groupId_object', () => {
    expect(resolvePageMetaUrl({ groupId: 'itg-asset' })).toBe(
      '/api/meta/active/itg-asset',
    );
  });

  it('resolvePageMetaUrl_metaId_object', () => {
    expect(resolvePageMetaUrl({ metaId: 'itg-asset-v1-2' })).toBe(
      '/api/meta/itg-asset-v1-2',
    );
  });

  it('resolvePageMetaUrl_encodeURIComponent_적용', () => {
    expect(resolvePageMetaUrl({ groupId: '자산/원장' })).toBe(
      '/api/meta/active/%EC%9E%90%EC%82%B0%2F%EC%9B%90%EC%9E%A5',
    );
    expect(resolvePageMetaUrl({ metaId: 'a/b' })).toBe('/api/meta/a%2Fb');
  });
});

describe('isMetaIdMode', () => {
  it('groupId_단축_string_은_active_API_호출', () => {
    // string 단축은 group(active) 모드 — metaId 모드 아님
    expect(isMetaIdMode('itg-asset')).toBe(false);
  });

  it('MetaIdent_groupId_는_active_API', () => {
    expect(isMetaIdMode({ groupId: 'itg-asset' })).toBe(false);
  });

  it('MetaIdent_metaId_는_단건_API', () => {
    expect(isMetaIdMode({ metaId: 'itg-asset-v1-2' })).toBe(true);
  });

  it('usePageMeta_metaId_모드_notPublished_항상_false', () => {
    // metaId 모드 판별 → notPublished 분기가 항상 false 가 되는 근거.
    expect(isMetaIdMode({ metaId: 'itg-asset-v1-0' })).toBe(true);
  });
});
