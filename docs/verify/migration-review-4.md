# 검증 리포트 · migration-review-4

- 대상: `git diff upstream/main...HEAD` (기본 대상, 43개 파일, +5611 −38, HEAD `98b5671`)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 코드는 수정하지 않았다(리뷰 세션). `modern` · `characterization` 한정 검증은 `migration-review-3.md`(승인)를 본다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `modern/api` `./gradlew cleanTest test` → 54 통과 · 0 실패. `characterization` 레거시 → 35 통과, 새 API(`TARGET_BASE_URL=http://localhost:8080`) → 34 통과 · 1 skip(기존 `example-units.test.js:21` `skipIf(legacyOnly)`). `modern/web` 변경 없음 → 실행하지 않음. `mcp-skeleton` 은 테스트 명령이 없어 실행하지 않음 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. `.mcp.json` · `dbhub.toml` 은 `${DB_DSN}` · `${GITHUB_PAT}` 환경 변수 참조뿐이고 리터럴 비밀값 없음. 문서 속 비밀번호 · 토큰 문자열은 마스킹 규칙의 예시 |
| 요청 범위 이탈 없음 | **미충족** | 요청 문장으로 설명되는 파일은 `modern/**` 15개 + `characterization/**` 2개(17개)뿐이다. 나머지 26개 파일은 이관과 무관하다(이슈 1) |
| 컨벤션 준수 | 충족 | 반려 항목 없음(`modern/api` 상세는 review-3 참고). 새 TS 코드 `mcp-skeleton/src/index.ts` 에 `any` · `console.log` 없음 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고(범위 이탈 → 반려 사유) | `.claude/skills/*`(6개), `CLAUDE.md`, `docs/**`(14개), `templates/*`(2개), `.mcp.json:1`, `dbhub.toml:1`, `mcp-skeleton/src/index.ts:36` | 요청은 "엔드포인트 1개 이관"인데 스킬 정의 · RCA 리포트 · 모듈 문서 · 체크리스트 개정 · MCP 연동 설정 · MCP 도구 추가가 같은 브랜치에 섞여 있다. 체크리스트 3번 "변경 파일 목록의 모든 파일이 요청 문장으로 설명된다"를 충족하지 못한다 | 이관(`modern` · `characterization`)만 따로 PR 로 분리하고 나머지는 각자의 요청 문장으로 별도 PR 로 올린다. 또는 사람이 이 파일들을 이번 요청 범위에 포함한다고 확정한다 |
| 2 | 경고 | `.mcp.json:6` | `npx -y @bytebase/dbhub@1.3.1` 로 외부 패키지를 받아 실행하는 설정이 추가됐다. `CLAUDE.md` 가 말하는 `package.json` 의존성은 아니지만 사실상 새 외부 도구 도입이다 | 별도 PR 에서 도입 이유와 대안을 적어 승인받는다 |
| 3 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:51` | `total == 0` 리터럴(review-3 이슈 1) | 안 고쳐도 무방 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemController.java:32` | POST 를 받지만 `WebConfig.java:20` CORS 는 GET 만(review-3 이슈 2) | 의도라면 유지 |

## 확인 필요

- 이슈 1 의 26개 파일을 이번 요청에 포함할지는 사람이 정한다. 포함한다고 확정하면 범위 기준은 충족으로 바뀌고 판정은 승인이 된다(다른 세 기준은 충족).
- `docs/approval-checklist.md` 와 `templates/approval-checklist.md` 가 둘 다 바뀌었다. 같은 내용의 복사본인지는 이번에 대조하지 않았다.
- `mcp-skeleton` · `.mcp.json` 의 동작은 서버를 띄워 확인하지 않았다(테스트 명령 없음).
- 새 API 서버(:8080)는 떠 있던 것을 그대로 썼다. `/api/items/search` 가 있는 빌드임은 스냅샷 34건 통과로 확인했다.

## 의심 동작 (레거시 동작이라 이관 중에는 고치지 않음)

review-3 과 같다: 키워드 `%` · `_` 비이스케이프(`search.php:97-98`), 메서드 무관 처리, 난이도 `05` · `5abc` 정수 변환, 페이지 정규식의 끝 줄바꿈 허용.
