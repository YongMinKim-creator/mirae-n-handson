# 이관 리뷰 1 — 문항 검색 `search.php` → `GET /api/items/search`

- 검증 대상: `git diff upstream/main...HEAD` (커밋 6개, 29개 파일, +4530/-2)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준 문서: `templates/approval-checklist.md`, `templates/verification-loop.md`, `CLAUDE.md`
- 리뷰 세션은 코드를 수정하지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | `cd modern/api && ./gradlew test` → 53건 중 통과 53 · 실패 0 · 건너뜀 0 (단, 태스크가 `UP-TO-DATE` 라 이번 세션이 다시 컴파일 · 실행한 것이 아니라 직전 결과를 재사용). `cd characterization && npm test`(레거시) → 35 통과 · 0 실패. **`TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) 는 실행하지 못함** — `localhost:8080` 이 응답하지 않음(서버 꺼짐). 이관 완료 기준(CLAUDE.md §5)의 핵심 검증이 비어 있다 |
| 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음, SQL 문자열 연결 없음(Specification 바인딩), 데이터 삭제 · 덮어쓰기 코드 없음. 이슈 1 은 동작 차이지만 데이터 피해가 아니라 경고로 분류 |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관". 코드 변경(`modern/api` 14개 파일, 검색 전용 클래스 · 테스트)은 범위 안. 그러나 같은 diff 에 `CLAUDE.md`, `.claude/skills/*` 3개, `docs/**` 9개, `characterization/**` 2개(+1567줄 스냅샷)가 섞여 있다. 요청 문장으로 설명되지 않는 파일 목록은 이슈 3 참고 |
| 컨벤션 준수 | 충족 | 아래 대조 참고. 위반 항목 없음 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemController.java:29`, `docs/item-bank/BUSINESS-RULES.md:437-441` | 이관 회고 표가 "검색을 POST 로 호출 — 레거시 200, AI 가 만든 동작 500(405 매핑 없음), 테스트가 못 잡음" 이라고 직접 기록했다. 이 diff 의 `GlobalExceptionHandler` · 컨트롤러 테스트에는 `HttpRequestMethodNotSupportedException` 처리도, POST 케이스 테스트도 없다(`grep` 결과 없음). 동작 보존 이관인데 알려진 차이가 열려 있고 "다음에 막을 방법" 칸도 비어 있다 | 레거시 POST 응답(200, 쿼리 문자열로 조회)을 그대로 따를지 사람이 정한다. 정하면 그에 맞는 매핑과 `@WebMvcTest` 케이스 1개, 그리고 characterization 케이스를 추가(스냅샷은 건드리지 않고 새 케이스로). 회고 표의 빈 칸도 채운다 |
| 2 | 경고 | `characterization/lib/target.mjs:22` | `/search.php` → `/api/items/search` 별칭은 있으나, 새 API 를 대상으로 한 실행 결과가 이 diff 에 없다. 레거시와 같다는 증거가 없는 상태 | 서버 기동 후 새 API 대상 `npm test` 를 돌려 케이스별 결과를 첨부. 실패하면 실패 케이스 이름과 스냅샷 차이를 그대로 보고 |
| 3 | 경고 | `CLAUDE.md`, `.claude/skills/{convention-check,document-module,impact}/SKILL.md`, `docs/assignment/*`, `docs/item-bank/*` | 요청 문장("엔드포인트 1개 이관")으로 설명되지 않는 변경이 파일 수 기준 절반 이상. 앞선 커밋(`832c8f7`, `b3f8f50`, `3fb4d33`, `80f1454`)이 `upstream/main` 이후 같은 브랜치에 쌓여 있어서 생긴 것으로 보인다. `docs/assignment/*` 는 이관 대상(item-bank)과 무관하다 | 리뷰 단위를 나누거나(문서 · 스킬 / characterization 베이스라인 / 이관 코드), 사람이 이 묶음을 요청 범위로 인정한다고 명시 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:55-67` | 페이지 조회 후 `findWithDetailsByIdIn` 으로 다시 읽는 2회 조회 구조. 정렬 순서를 id 목록으로 복원하는 것은 맞고, 컬렉션 페치와 페이징 충돌을 피하려는 의도로 읽힌다. 다만 의도를 설명하는 주석이 없다 | 이유 한 줄 주석 추가 |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/PhpCompat.java:1-260` | 260줄짜리 PHP 파서 흉내(urldecode, `$_GET` 배열 규칙, `(int)` 캐스트). 동작 보존 근거는 주석에 있으나 단위 테스트(`PhpCompatTest` 104줄)가 모든 분기를 덮는지는 이 리뷰에서 확인하지 못했다 | 분기 커버리지 확인 |

## 체크리스트 대조

### 1. 테스트 통과
- [미충족] 변경 모듈 테스트 실행: api 는 캐시 결과(UP-TO-DATE), characterization 새 API 대상은 미실행.
- [충족] 실패 · 오류 0건(api 53, characterization 레거시 35).
- [확인 필요] 테스트가 충분한가: 서비스 · 조건 · 저장소 · 컨트롤러 · PhpCompat 테스트가 있으나 POST 케이스 없음(이슈 1).
- [충족] `@Disabled` · `skip` 로 바꾼 기존 테스트 없음.

### 2. 치명 이슈 0건
- [충족] 비밀값 리터럴 없음. [충족] SQL 연결 없음(`ItemSpecifications.java:17-74`). [충족] 삭제 · 덮어쓰기 코드 없음. [충족] 치명 문제 없음.

### 3. 요청 범위
- [충족] 요청 문장 확인됨. [미충족] 모든 파일이 설명되지는 않음(이슈 3). [충족] 의존성 · DB 스키마 · `application.yml` 변경 없음(`build.gradle`, `db/`, `resources` diff 비어 있음). [확인 필요] 변경 규모 적정성은 사람이 판단.

### 4. 컨벤션 (`CLAUDE.md` §2 · §3 대조, 수작업 grep)
- 컨트롤러는 `ItemSearchService` 만 주입(`ItemController.java:15-22`), Repository 없음.
- 서비스 `@Transactional(readOnly = true)`(`ItemSearchService.java:21`), DTO 변환을 서비스 안에서 완료, `@EntityGraph` 사용(`ItemRepository.java:29-31`).
- `catch` · `System.out` · `printStackTrace` · 와일드카드 import · 인자 없는 `now()` 없음(grep 결과 없음). 로그는 slf4j `debug` 이고 학생 식별자 · 토큰 없음(`ItemSearchService.java:48`).
- 도메인 상수는 이름 붙임(`MAX_LEVEL`, `PAGE_SIZE`, `MAX_PAGE` 등, `ItemSearchCondition.java:25-34`).
- `characterization/` 의 기존 스냅샷 · 테스트를 고친 흔적 없음(신규 추가만).

## 확인 필요
- 새 API 대상 characterization 결과: 서버가 꺼져 있어 실행하지 못했다. 사람이 `docker compose --profile modern up -d && cd modern/api && ./gradlew bootRun` 로 기동한 뒤 `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` 를 돌려 결과를 알려 주세요.
- `./gradlew test` 는 이번에 `UP-TO-DATE` 였다. 새로 실행한 결과가 필요하면 `./gradlew cleanTest test` 로 다시 돌려야 한다.
- 이슈 1 의 레거시 POST 동작 원문(`legacy/item-bank-php/search.php` 의 메서드 처리)은 이 리뷰에서 줄번호까지 확인하지 않았다.
- 레거시 동작이 버그로 보이는 항목("의심 동작")은 이번 리뷰에서 따로 추리지 않았다.

## 승인으로 바꾸려면
1. 새 API 대상 characterization 전부 통과(실패 시 케이스 이름과 차이 보고).
2. 이슈 1(POST 동작) 처리 방침 결정과 반영.
3. 이슈 3(범위) 를 사람이 확정.
