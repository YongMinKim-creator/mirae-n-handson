# 이관 검증 리뷰 2 — `GET·POST /api/items/search` (legacy `search.php`)

- 검증 대상: `git diff upstream/main...HEAD` (브랜치 `day1`, HEAD `b9a91be`)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준 문서: `templates/approval-checklist.md`, `templates/verification-loop.md`, `CLAUDE.md`
- 이 리뷰는 코드를 수정하지 않았다. 작업 트리의 미커밋 변경(`docs/item-bank/BUSINESS-RULES.md`, `.claude/skills/verify/`)은 범위(`upstream/main...HEAD`)에 포함되지 않아 보지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `modern/api` `./gradlew test` 53건 중 통과 53 · 실패 0 · 오류 0 · skip 0. `characterization` 레거시 대상 `npm test` 35 통과 · 0 실패. 새 API 대상 `TARGET_BASE_URL=http://localhost:8080 npm test` 34 통과 · 0 실패 · 1 skip (아래 확인 필요 1) |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 리터럴 · SQL 문자열 결합 · 삭제/덮어쓰기 코드 없음(검색은 JPA `Specification` 바인딩) |
| 요청 범위 이탈 없음 | **미충족** | 요청은 "엔드포인트 1개 이관"인데 `upstream/main...HEAD` 는 30개 파일이고, 이관 코드 · 테스트(`modern/api` 20개 파일)로 설명되지 않는 파일이 있다 — 이슈 1 |
| 컨벤션 준수 | 충족 | 위반 없음(점검 항목은 아래 "컨벤션 대조") |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 (범위 기준 미충족 사유) | `CLAUDE.md`, `.claude/skills/{convention-check,document-module,impact}/SKILL.md`, `docs/assignment/*`, `docs/item-bank/*`, `docs/verify/migration-review-1.md` | 변경 30개 중 이관 코드 · 테스트(`modern/api/src/**` 20개)와 `characterization/` 베이스라인(2개)을 뺀 나머지 문서 · Skill · CLAUDE.md 는 "엔드포인트 1개 이관"으로 설명되지 않는다. 앞선 회차 작업이 `upstream/main` 기준 범위에 함께 잡힌 것으로 보이며, 이관 코드 자체의 이탈은 아니다. 의존성 · DB 스키마 · `application.yml` · `legacy/` 변경은 없음(확인함) | 이관 커밋만 범위로 좁혀 다시 검증하거나(예: `80f1454..HEAD` 중 `modern/api` 경로), 사람이 이 문서들을 범위에 포함한다고 확정한다. 확정되면 이 기준은 충족으로 바뀐다 |
| 2 | 경고 | `ItemController.java:32` · `ItemControllerTest.java:89-122` | `POST /api/items/search` 를 받도록 열었지만(레거시가 메서드를 가리지 않는다는 근거) 컨트롤러 테스트 3건은 GET 만 호출한다. "새 엔드포인트마다 테스트 1개 이상" 의 취지상 POST 경로가 비어 있다. 또 `WebConfig` CORS 는 GET 만 허용하므로 브라우저에서의 POST 는 막힌다(동작 보존 여부는 사람이 판단) | POST 로 같은 결과가 나오는 `@WebMvcTest` 1건 추가. POST 를 유지할지 사람이 확정 |
| 3 | 제안 | `PhpCompat.java:1-260` | PHP 7.4 의 `$_GET` 파싱 · `(int)` 변환 · `trim` 을 직접 흉내 내는 260줄. 동작 보존에는 필요하나 이후 유지보수 부담이 크다. 단위 테스트(`PhpCompatTest`)가 있어 당장 문제는 아니다 | 이관 완료 후 실제 사용 입력을 기준으로 단순화 가능한 범위(다중 값 · 중첩 배열 등)를 사람과 정한다 |
| 4 | 제안 | `ItemSearchService.java:54-67` | 페이지 조회 후 `findWithDetailsByIdIn` 으로 다시 읽는 2단계 조회. 정렬 순서는 `ids` 로 복원해 정확하지만 쿼리가 2번 나간다. 페이징과 컬렉션 fetch 를 함께 쓰는 문제를 피하려는 의도로 읽힌다 | 의도라면 주석에 이유 한 줄 추가 |

### 의심 동작 (레거시 그대로 옮긴 것 — 고치지 않았음. CLAUDE.md §5)

- 난이도를 비우면 5 는 제외하고 1~4 만 조회한다 — `ItemSpecifications.java:50`(BR-07, `search.php:214-215`)
- 키워드의 `%` · `_` 를 이스케이프하지 않는다 — `ItemSpecifications.java:39`(`search.php:97-98`)
- 정렬 방향 해석이 기준마다 비대칭이다(id · title · unit 은 `desc` 일 때만 내림, level · created 는 `asc` 일 때만 오름) — `ItemSearchSort.java:40`(BR-15)
- 단원 정렬은 방향과 무관하게 난이도 내림차순을 덧붙인다 — `ItemSearchSort.java:48-50`(BR-17)
- 레거시가 GET 외 메서드도 200 으로 처리해 POST 도 받는다 — `ItemController.java:32`

## 컨벤션 대조 (변경된 Java 파일 대상)

- 컨트롤러에 Repository · SQL 없음, 서비스가 다른 도메인 Repository 를 주입받지 않음, 조회 서비스에 `@Transactional(readOnly = true)`(`ItemSearchService.java:21`) — 충족
- 컨트롤러 반환이 `record` DTO(`ItemSearchResponse`) — 충족
- 빈 `catch` · `System.out` · `printStackTrace` · 인자 없는 `now()` · `@Disabled` — grep 결과 없음
- 와일드카드 import 없음, 120자 초과 줄 없음, 탭 없음 — 충족
- 테스트: `@WebMvcTest`(+`@ActiveProfiles("test")`) · `@DataJpaTest`(+`@ActiveProfiles("test")`) · Mockito 서비스 테스트, 메서드명 camelCase — 충족
- 상수화: `MAX_LEVEL`, `PAGE_SIZE`, `MAX_PAGE`, `KEYWORD_MAX`, `TAG_MAX` — 충족
- 로그: `log.debug` 에 학생 식별자 · 이메일 · 토큰 없음 — 충족

## 확인 필요

1. `characterization` 새 API 대상 실행의 skip 1건은 `tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)`(단원 목록 `units.php`, 이번 이관 대상이 아님)이다. 검색 케이스(`tests/item-bank.test.js`)는 skip 없이 통과했다. 다만 CLAUDE.md §5 의 "전부 통과" 를 skip 포함으로 볼지는 사람이 판단한다.
2. 새 API 대상 테스트는 이미 떠 있던 `localhost:8080` 서버로 실행했다. 그 서버가 HEAD(`b9a91be`) 빌드인지는 확인하지 못했다(이 세션에서 기동하지 않음). 확실히 하려면 `cd modern/api && ./gradlew bootRun` 으로 다시 띄운 뒤 같은 명령을 한 번 더 실행해 달라.
3. 코드 주석의 레거시 근거 줄번호(`search.php:85-89` 등)는 이 리뷰에서 `legacy/` 원문과 대조하지 않았다. 동작 일치는 characterization 통과로만 확인했다.
4. 이슈 1 의 범위 판단(앞선 회차 문서 · Skill 이 이번 요청에 포함되는지)은 사람이 확정한다.
