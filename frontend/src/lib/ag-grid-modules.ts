// AG Grid Community 모듈 등록 — 사이드이펙트 전용 (main.ts 에서 1회 import).
// ADR-007: Community 모듈만 등록한다. Enterprise 모듈(RangeSelection·MasterDetail·
// ServerSideRowModel 등) 은 절대 import 하지 않는다 (라이선스 + 런타임 경고).
// 모듈 등록 누락은 AG Grid 의 가장 흔한 함정이므로 이 한 파일에 모아둔다.
import { ModuleRegistry, ClientSideRowModelModule } from 'ag-grid-community';

ModuleRegistry.registerModules([ClientSideRowModelModule]);
