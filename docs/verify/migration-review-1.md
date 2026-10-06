# 이관 리뷰 1 — 문항 검색 `search.php` → `/api/items/search`

- 검증 대상: `git diff upstream/main...HEAD` (32개 파일, +4695/-2)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 기준 문서: `templates/approval-checklist.md`, `templates/verification-loop.md`, `CLAUDE.md`
- 리뷰 세션은 코드를 수정하지 않았다. 이 파일만 덮어썼다(이전 버전은 git 이력에 있음).

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 53건 중 통과 53 · 실패 0 · 건너뜀 0. `cd characterization && npm test`(레거시) → 35 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npx vitest run`(새 API) → 34 통과 · 0 실패 · 1 건너뜀. 건너뜀은 `characterization/tests/example-units.test.js:21` 의 `skipIf(legacyOnly)` 로, 이 diff 가 추가한 것이 아니며 `units.php` 케이스다. 단 POST 케이스는 어느 테스트에도 없음(이슈 1) |
| 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음. 검색은 Specification 파라미터 바인딩이라 SQL 문자열 연결 없음. 데이터 삭제 · 덮어쓰기 코드 없음. 치명 이슈 없음 |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관". `modern/api` 15개 파일(+1058/-2)은 범위 안이다. 그러나 `CLAUDE.md`, `.claude/skills/*` 4개, `docs/**` 12개, `characterization/**` 2개(스냅샷 +1567줄)도 섞여 있다. 이슈 2 참고 |
| 컨벤션 준수 | 충족 | 변경 코드에 와일드카드 import · `System.out` · `printStackTrace` · 인자 없는 `now()` · `@Disabled` · 120자 초과 줄 없음. `PhpCompat.java` 에 `catch` 없음. 컨트롤러는 서비스만 호출. 상한 · 하한은 이름 붙은 상수(`ItemSearchCondition.java:25-34`). 새 테스트 클래스는 모두 `@DisplayName` 사용 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemController.java:31`, `modern/api/src/test/java/com/example/item/ItemControllerTest.java:88-122` | 컨트롤러가 `GET · POST` 를 모두 받도록 바뀌었다(레거시가 메서드를 가리지 않는다는 근거, `docs/item-bank/BUSINESS-RULES.md:437-441`). 하지만 컨트롤러 테스트에는 `get(...)` 케이스만 있고 `post(...)` 케이스가 없으며 characterization 에도 POST 케이스가 없다. POST 가 레거시와 같다는 증거가 자동 검증에 없다. CLAUDE.md §2 "새 엔드포인트마다 테스트" 의 취지에도 부분적으로 못 미친다 | `@WebMvcTest` 에 POST 케이스 1개 추가. characterization 은 스냅샷을 건드리지 말고 새 케이스로 추가 |
| 2 | 경고 | `CLAUDE.md`, `.claude/skills/*/SKILL.md`, `docs/assignment/*`, `docs/item-bank/*`, `docs/verify/*`, `characterization/tests/item-bank.test.js`, `characterization/tests/__snapshots__/item-bank.test.js.snap` | 요청 문장으로 설명되지 않는 파일이 32개 중 17개다. `docs/assignment/*` 는 이관 대상(item-bank)과도 무관하다. `upstream/main` 이후 앞선 작업이 같은 브랜치에 쌓인 결과로 보인다. characterization 스냅샷은 이관 검증의 기준이라 성격이 다르지만, 이번 diff 에 새로 추가된 파일이다 | 리뷰 단위를 나누거나(스킬 · 문서 / characterization 기준선 / 이관 코드), 사람이 이 묶음을 요청 범위로 인정한다고 명시 |
| 3 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:55-67` | 페이지 조회 후 `findWithDetailsByIdIn` 으로 다시 읽는 2회 조회 구조인데 이유 주석이 없다. 컬렉션 페치와 페이징 충돌을 피하려는 의도로 읽힌다 | 이유 한 줄 주석 추가 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSpecifications.java:41-43` | 키워드 `%` · `_` 를 이스케이프하지 않는다. 레거시 동일 동작을 보존한 것이고(`legacy/item-bank-php/search.php:97-98`) 의도가 주석에 있다. CLAUDE.md §5 에 따라 고치지 않는다 | "의심 동작" 목록에 유지 |

## 의심 동작(레거시 버그 후보, 이관에서 고치지 않음)

- 키워드 LIKE 와일드카드 미이스케이프(`search.php:97-98`, 이슈 4).

## 확인 필요

- `PhpCompat.java`(260줄)의 모든 분기를 `PhpCompatTest`(9건)가 덮는지는 이번 리뷰에서 커버리지를 측정하지 않았다. 줄번호를 특정할 수 없어 이슈로 올리지 않았다.
- 새 API 대상 실행은 이미 떠 있던 `localhost:8080` 서버를 썼다. 그 서버가 현재 HEAD 로 빌드된 것인지는 확인하지 못했다. 사람이 현재 HEAD 로 `bootRun` 한 서버에서 한 번 더 돌려 주기 바란다.

## 판정 사유

테스트와 컨벤션은 충족이지만 요청 범위 이탈 기준이 미충족이라 반려다. 이슈 2 를 사람이 범위로 인정하거나 diff 를 나누면 해소된다. 그 경우 남는 것은 이슈 1(POST 테스트 부재)이며 경고 수준이다.
