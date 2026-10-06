| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| pr-1-missing-tests | 반려 | 테스트 통과 | `UnitService.java:36` `countActiveItemsByUnit()` · `UnitController.java:27` `itemCounts()` — 새 public 메서드에 테스트 없음 | 두 메서드를 호출하는 테스트를 추가해 주세요(문항 0개 단원 포함, 학년·코드 순서 확인). 테스트 실행 결과(통과·실패 수)도 함께 보내 주세요. |
| pr-2-hardcoded-secret | 반려 | 치명 이슈 0건, 테스트 통과 | `GradingServerClient.java:29` 비밀번호 리터럴(비밀값 노출, 치명) · `GradingServerClient.java:38` public `requestGrading` 에 테스트 없음 | 아이디·비밀번호를 코드에서 빼고 설정(환경변수·시크릿 저장소)에서 읽게 바꿔 주세요. 이미 노출된 비밀번호는 폐기·교체해 주세요. `requestGrading` 을 호출하는 테스트를 추가해 주세요. (경고: `:56` 의 JSON 본문 문자열 이어붙이기는 직렬화 라이브러리 사용을 권장합니다.) |
| pr-3-out-of-scope | 반려 | 요청 범위 이탈 없음, 테스트 통과 | `DistributionService.java:52` 메서드 추출 · `ReportService.java:65` 메서드 추출과 로그 변경(`average` 추가) — import 정리가 아닌 범위 밖 변경 · `UnitController.java:32` `listItemsByLevel` 에 테스트 없음 | `DistributionService` · `ReportService` 변경을 이 PR 에서 빼고 필요하면 별도 PR 로 보내 주세요. `listItemsByLevel` 을 호출하는 테스트를 추가해 주세요. |

기준: v1 개정안(아직 템플릿에 반영 전), 2026-10-06 정적 판정 — 테스트는 실행하지 않음. 줄번호는 패치 hunk 에서 계산한 값이라 패치를 적용한 뒤 한 번 대조해 주세요.
