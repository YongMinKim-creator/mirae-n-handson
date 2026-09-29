---
name: convention-check
description: CLAUDE.md 의 코딩 컨벤션 · 금지 사항 중 코드로 예/아니오 판정이 가능한 8개 항목을 변경 파일(또는 지정 경로)에 대해 점검하고 위반을 파일:줄번호로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 어긴 데 있는지 봐 줘" 같은 요청에 사용한다.
argument-hint: "[경로 ...] (생략하면 git 변경 파일)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

## 원칙
- **코드를 직접 고치지 않는다.** 위반 위치와 수정 방향만 보고한다.
- **근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.**

## 1. 점검 대상 정하기
- 인자(`$ARGUMENTS`)가 있으면 그 경로(파일 · 디렉터리)만 점검한다.
- 인자가 없으면 아래 두 명령으로 변경 파일을 모은다. 아직 add 하지 않은 새 파일(`??`)도 포함한다.
  - `git status --porcelain`
  - `git diff --name-only HEAD`
- 삭제된 파일은 빼고, 대상이 없으면 "점검 대상 없음"만 출력하고 끝낸다.

## 2. 점검 항목
각 항목을 대상 파일에 대해 예(준수) / 아니오(위반) / 해당 없음 으로 판정한다.

| # | 항목 | 대상 |
|---|---|---|
| 1 | `@RestController` 클래스의 필드 · 생성자 파라미터에 `*Repository` 타입이 없고, SQL 문자열 · `JdbcTemplate` · `EntityManager` 가 없다 | `modern/api/**/*.java` |
| 2 | 모든 `catch` 블록이 예외를 다시 던지거나 감싸 던진다. 비어 있거나 로그만 남기는 `catch` 가 없다 | `modern/api/**/*.java` |
| 3 | `System.out` · `System.err` · `printStackTrace()` 가 없다 | `modern/api/**/*.java` |
| 4 | `LocalDateTime.now()` · `Instant.now()` 를 인자 없이 호출하지 않는다(`ErrorResponse.of` 는 예외) | `modern/api/**/*.java` |
| 5 | 와일드카드 import(`import x.y.*;`)가 없다 | `modern/api/**/*.java` |
| 6 | `src/api/client.ts` 밖에 `fetch(` 가 없다 | `modern/web/src/**/*.{ts,tsx}` |
| 7 | `any` · `as unknown as` · `@ts-ignore` · `dangerouslySetInnerHTML` · `console.log` 가 없다 | `modern/web/src/**/*.{ts,tsx}` |
| 8 | 변경 파일에 `legacy/**` · `characterization/__snapshots__/**` · `package-lock.json` 이 없다 | 변경 파일 목록 |

- Grep 으로 후보 줄을 찾은 뒤 Read 로 앞뒤 문맥을 읽고 판정한다. 주석 · 문자열 안의 일치는 위반이 아니다.
- 2번은 `catch` 블록 전체를 읽어 `throw` 가 있는지 확인한다.
- 7번의 `any` 는 타입 위치(`: any`, `<any>`, `any[]`)만 위반으로 본다.
- 8번은 인자로 경로를 받은 경우 "해당 없음"으로 판정한다.
- 경로 규칙은 `CLAUDE.md` 를 기준으로 하고, 이 파일과 다르면 `CLAUDE.md` 를 따른다.

## 3. 출력 형식 (순서 고정)

### 판정 요약
점검 대상: N개 파일 · 위반 M건 · 확인 필요 K건

| # | 항목 | 판정 |
|---|---|---|
| 1 | 컨트롤러 → Repository 직접 의존 없음 | 예 / 아니오 / 해당 없음 |

### 위반 목록
| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| `modern/api/.../XController.java:42` | #1 컨트롤러에 `ItemRepository` 필드 | `ItemService` 를 주입받아 호출 |

- 위반이 없으면 표 대신 "위반 없음"이라고 쓴다.
- 확인 필요 항목은 위반 목록 표 아래에 "확인 필요: 파일 — 이유" 형식으로 적는다.
