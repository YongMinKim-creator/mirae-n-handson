# 검증 리포트 · migration-review-3

- 대상: `git diff upstream/main...HEAD -- modern characterization` (17개 파일, +2761 −2)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 실제 변경 내용: `legacy/item-bank-php/search.php` → `GET · POST /api/items/search`
- 체크리스트: `templates/approval-checklist.md` v1 · 2회차
- 코드는 수정하지 않았다(리뷰 세션).

판정: 승인

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `modern/api` `./gradlew cleanTest test` → 54 통과 · 0 실패 · 0 오류 · 0 skip. `characterization` `npm test`(레거시 :8081) → 35 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 34 통과 · 0 실패 · 1 skip(아래 참고). `modern/web` 은 변경이 없어 실행하지 않음 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 검색 조건은 JPA Criteria 로 만들고 문자열 SQL 이 없다(`ItemSpecifications.java:85-142`). 비밀값 · 삭제 · 인증 변경 없음 |
| 요청 범위 이탈 없음 | 충족 | 변경 17개 파일이 모두 문항 검색 이관(`item` 패키지 9개 + 테스트 5개)과 동작 보존 테스트(`characterization/tests/item-bank.test.js`, 스냅샷)로 설명된다. 의존성 · 스키마 · `application.yml` 변경 없음. 기존 `characterization` 파일(`lib/target.mjs` 등)은 수정되지 않았다 |
| 컨벤션 준수 | 충족 | 반려 항목 없음. 경고(네이밍) 없음. 제안 2건(아래) |

## 확인한 사항

1. **계층 규칙** — `ItemController` 필드는 `ItemService` · `ItemSearchService` 뿐이다(`ItemController.java:18-24`). Repository 주입 · SQL 없음. 반환 타입은 record DTO(`ItemSearchResponse`).
2. **서비스** — 클래스에 `@Transactional(readOnly = true)`(`ItemSearchService.java:21`), 엔티티 → DTO 변환을 서비스 안에서 끝낸다(`:59-66`). 연관은 `@EntityGraph`(`ItemRepository.java:30`)로 가져온다.
3. **예외 · 로깅** — `catch` 없음, `System.out` · `printStackTrace` 없음. 로그는 slf4j `DEBUG` 한 줄(`ItemSearchService.java:48`)이고 학생 식별자 · 이메일 · 토큰이 없다.
4. **상수** — `MAX_LEVEL = 5`, `PAGE_SIZE`, `MAX_PAGE`, `KEYWORD_MAX`, `TAG_MAX` 를 이름 붙인 상수로 뒀다(`ItemSearchCondition.java:168-177`).
5. **형식** — 와일드카드 import 없음, 120자 초과 줄 없음.
6. **public 메서드 테스트** — 새 public 메서드마다 호출하는 테스트가 있다: `ItemController.searchItems`(`ItemControllerTest` 4건), `ItemSearchService.search`(`ItemSearchServiceTest` 4건), `ItemSearchParams.fromQueryString`(조건 · 서비스 · 저장소 테스트에서 호출), `ItemRepository.findWithDetailsByIdIn`(`ItemRepositoryTest`). 모든 새 테스트 메서드에 `@DisplayName`(한국어)과 `@ActiveProfiles("test")`가 있다.
7. **기존 테스트** — 삭제 · `@Disabled` 전환 없음. `ItemControllerTest` · `ItemRepositoryTest` 는 추가만 있다.
8. **동작 보존 테스트** — `characterization` 은 파일 추가만 있다(기존 스냅샷 · 테스트 코드 수정 없음). 같은 스냅샷이 레거시와 새 API 양쪽에서 통과한다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:51` | `total == 0` 의 0 이 "결과 없음" 판정 기준인데 리터럴이다(BR-21). 컨벤션의 "도메인 값은 이름 붙인 상수" 에 걸리는 경계 사례이고, 0 은 보통 허용되는 값이다 | 필요하면 `total == 0` 을 `page.isEmpty()` 가 아닌 전체 건수 기준 헬퍼(`hasNoResult(total)`)로 감싼다. 안 해도 무방 |
| 2 | 제안 | `modern/api/src/main/java/com/example/item/ItemController.java:32` | 검색은 POST 도 받지만(레거시가 메서드를 가리지 않음) `WebConfig.java:20` 의 CORS 는 GET 만 허용한다. 브라우저 교차 출처 POST 는 막힌다 | 의도라면 그대로 둔다(레거시 호환 목적이 "서버 간 호출"일 때). 브라우저에서 POST 가 필요하면 별도 요청으로 `WebConfig` 변경을 논의한다 |

## 의심 동작 (레거시 동작이라 이관 중에는 고치지 않음)

- `search.php:97-98` — 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`ItemSpecifications.java:107-111` 이 그대로 따른다).
- 메서드를 가리지 않고 쿼리 문자열을 읽는다(POST 도 200). 새 API 가 이를 따른다(`ItemController.java:32`).
- 난이도 `"05"` · `"5abc"` 가 정수 변환으로 5 · 5 가 되고, 범위 밖 값은 0건이다(`ItemSearchCondition.java:189-192`, `PhpCompat.java:82-100`).
- 페이지 정규식 `/^[0-9]+$/` 가 끝의 줄바꿈 하나를 허용한다(`PhpCompat.java:26-27`).

## 확인 필요

- characterization 새 API 실행의 skip 1건은 `tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)` 로, 이번 변경 이전부터 있던 설계(레거시 전용 케이스)다. 이번 이관 변경과 무관하다고 판단하지만, 팀이 "새 API 에서 skip 0건"을 요구한다면 사람이 확인한다.
- 새 API 서버(:8080)는 이미 떠 있어 그대로 사용했다. 이 서버가 HEAD 의 빌드인지는 프로세스 기동 시각(13:08)만으로 확정할 수 없다. 검색 스냅샷 34건이 통과해 `/api/items/search` 가 있는 빌드임은 확인했다.
