// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 대상 규칙: docs/item-bank/BUSINESS-RULES.md 의 BR-01 ~ BR-21 (문항 검색 · 정렬 · 페이지 · 빈 결과)
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 item-bank 레거시 기본 주소).
//   이관 후에는 PATH_ALIASES 로 /search.php → /api/items/search 를 탄다. 테스트 코드에 주소를 적지 않는다.
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기(skipIf)를 두지 않는다.
//
// 시드 기준 참고(db/mariadb/init/02-seed.sql:28-34): 공개(status='A') 문항 26건,
// 그중 level < 5 는 정확히 20건(= 한 페이지), 난이도 5 공개 문항은 6건.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

// 경계값 · 이상한 값에 쓰는 길이. 레거시 상한(BR-05 키워드 100자, BR-11 태그 50자)의 안쪽 · 바깥쪽.
const KEYWORD_MAX = 100;
const TAG_MAX = 50;
// 아주 긴 키워드. ASCII 로 두어 URL 이 웹 서버 요청 줄 한도(Apache 기본 8190바이트) 안에 들게 한다.
const VERY_LONG_KEYWORD_LENGTH = 5000;

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  // ---------------------------------------------------------------- 정상 입력
  it('정상 · 파라미터 없는 기본 검색 — 공개만 · 난이도 5 제외 · 기본 정렬 · 20건 페이지 (BR-01, BR-07, BR-13, BR-19)', async () => {
    expect(await search()).toMatchSnapshot();
  });

  it('정상 · 단원 M5-1 + 난이도 3 AND 결합 (BR-02, BR-06, BR-08)', async () => {
    expect(await search({ unit: 'M5-1', level: '3' })).toMatchSnapshot();
  });

  it('정상 · 태그 "계산" 정확 일치 + 제목 내림차순, 동순위 id 오름차순 (BR-10, BR-15, BR-16)', async () => {
    expect(await search({ tag: '계산', sort: 'title', dir: 'desc' })).toMatchSnapshot();
  });

  it('정상 · 키워드 "분수" + 단원 정렬, 단원 안 난이도 내림차순 (BR-04, BR-15, BR-17)', async () => {
    expect(await search({ q: '분수', sort: 'unit' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 경계값
  it('경계 · 난이도 5 명시 — 빈 난이도로는 빠지는 5 를 직접 조회 (BR-07, BR-08)', async () => {
    expect(await search({ level: '5' })).toMatchSnapshot();
  });

  it('경계 · 난이도 "05" — 정규식 밖이지만 정수 변환하면 5 (BR-08, BR-09)', async () => {
    expect(await search({ level: '05' })).toMatchSnapshot();
  });

  it('경계 · 2페이지 — 전체 건수가 딱 한 페이지일 때 빈 표 vs 결과 없음 문구 (BR-19, BR-21)', async () => {
    expect(await search({ page: '2' })).toMatchSnapshot();
  });

  it('경계 · 페이지 999 — 상한 안쪽, 보정 없음 (BR-20)', async () => {
    expect(await search({ page: '999' })).toMatchSnapshot();
  });

  it('경계 · 페이지 1000 — 상한 바깥쪽, 999 로 보정 (BR-20)', async () => {
    expect(await search({ page: '1000' })).toMatchSnapshot();
  });

  it('경계 · 키워드 100자 — 잘라내기 상한 안쪽 (BR-05)', async () => {
    expect(await search({ q: '가'.repeat(KEYWORD_MAX) })).toMatchSnapshot();
  });

  it('경계 · 키워드 101자 — 잘라내기 상한 바깥쪽 (BR-05)', async () => {
    expect(await search({ q: '가'.repeat(KEYWORD_MAX + 1) })).toMatchSnapshot();
  });

  it('경계 · 태그 50자 — 잘라내기 상한 안쪽, 미등록 태그 (BR-11, BR-12)', async () => {
    expect(await search({ tag: 'a'.repeat(TAG_MAX) })).toMatchSnapshot();
  });

  it('경계 · 태그 51자 — 잘라내기 상한 바깥쪽, 미등록 태그 (BR-11, BR-12)', async () => {
    expect(await search({ tag: 'a'.repeat(TAG_MAX + 1) })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 빈 값 · 누락
  it('빈 값 · 모든 파라미터를 빈 문자열로 — 파라미터 없는 기본 검색과 비교 (BR-02, BR-07, BR-13, BR-20)', async () => {
    expect(
      await search({ q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
    ).toMatchSnapshot();
  });

  it('빈 값 · 공백만 있는 키워드 — 앞뒤 공백 제거 후 조건 없음 (BR-04, BR-05)', async () => {
    expect(await search({ q: '   ' })).toMatchSnapshot();
  });

  it('누락 · 정렬 기준 level 만 있고 방향 없음 — 기준별 기본 방향 (BR-15, BR-16)', async () => {
    expect(await search({ sort: 'level' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 이상한 값
  it('이상 · 음수 페이지 -1 — 숫자 형식 아님 경로 (BR-20)', async () => {
    expect(await search({ page: '-1' })).toMatchSnapshot();
  });

  it('이상 · 음수 난이도 -1 — 정수 변환 후 0건 (BR-09, BR-21)', async () => {
    expect(await search({ level: '-1' })).toMatchSnapshot();
  });

  it('이상 · 아주 긴 키워드 5000자 — 100자로 잘린 뒤 조회 (BR-05, BR-21)', async () => {
    expect(await search({ q: 'a'.repeat(VERY_LONG_KEYWORD_LENGTH) })).toMatchSnapshot();
  });

  it('이상 · 형식은 맞지만 없는 단원 코드 Z9-99 (BR-06, BR-21)', async () => {
    expect(await search({ unit: 'Z9-99' })).toMatchSnapshot();
  });

  it('이상 · 배열 파라미터 level[]=5,1 + 모르는 정렬 기준 " DROP " — 첫 값만 · 기본 정렬 (BR-03, BR-08, BR-14)', async () => {
    expect(await search({ 'level[]': ['5', '1'], sort: ' DROP ' })).toMatchSnapshot();
  });
});
