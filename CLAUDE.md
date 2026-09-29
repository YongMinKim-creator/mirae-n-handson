# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)의 레거시 모듈을 `modern/` 으로 이관하는 실습 저장소다.
**답변과 문서는 한국어로 쓴다.** 코드 식별자 · 명령 · 로그 원문은 그대로 둔다.

## 1. 빌드 · 테스트 명령

명령은 저장소 루트에서 실행한다.

```bash
# modern/api — Spring Boot 3.3 · Java 21 · Gradle
cd modern/api && ./gradlew test                                        # H2 테스트 프로필. DB 컨테이너 없이 통과한다
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest'   # 클래스 하나만
cd modern/api && ./gradlew bootRun                                     # :8080. 먼저 docker compose --profile modern up -d

# modern/web — React 18 · TypeScript · Vite · Vitest
cd modern/web && npm ci                                                # 처음 한 번. npm install 은 쓰지 않는다(lock 변경)
cd modern/web && npm run dev                                           # :5173, /api 는 :8080 으로 프록시
cd modern/web && npm run lint && npm run typecheck && npm test         # 셋 다 통과해야 완료
cd modern/web && npx vitest run src/components/ItemTable.test.tsx      # 파일 하나만
cd modern/web && npm run build
```

- 작업을 끝냈다고 보고할 때는 바꾼 쪽(api · web)의 검증 명령을 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 하나라도 실패하면 "완료"라고 쓰지 않는다.

## 2. 코딩 컨벤션

### modern/api — 계층
- 패키지는 도메인 단위(`com.example.item`, `com.example.assignment`)이고, 한 도메인 안에서 Controller → Service → Repository 순으로만 호출한다.
- **`@RestController` 클래스의 필드 · 생성자 파라미터에 `*Repository` 타입이 없다.** 컨트롤러에 SQL 문자열 · `JdbcTemplate` · `EntityManager` 가 없다.
- 서비스는 다른 도메인의 `*Repository` 를 주입받지 않는다. 다른 도메인 데이터는 그 도메인의 `*Service` 로 받는다.
- 컨트롤러의 반환 타입은 `record` DTO(또는 그 `List`)다. `@Entity` 클래스를 반환하지 않는다.
- 조회 서비스는 클래스에 `@Transactional(readOnly = true)` 를 붙이고, 엔티티 → DTO 변환을 서비스 메서드 안에서 끝낸다(OSIV 꺼짐). 연관은 Repository 의 `@EntityGraph` 로 함께 가져온다.
- 현재 시각은 주입받은 `java.time.Clock` 으로 얻는다. `LocalDateTime.now()` · `Instant.now()` 를 인자 없이 호출하지 않는다(기존 `ErrorResponse.of` 는 예외).

### modern/api — 예외 · 로깅
- **`catch` 블록은 예외를 다시 던지거나 다른 예외로 감싸 던진다.** 비어 있거나 로그만 남기고 끝나는 `catch` 가 없다(예: `ReportService.java` 의 `sha256()`).
- 예외 → HTTP 응답 변환은 `common/GlobalExceptionHandler` 에서만 한다. 컨트롤러 메서드 안에 `try` · `ResponseEntity.status(...)` 로 오류 상태를 만드는 코드가 없다.
- 상태 매핑: 없는 리소스는 `NotFoundException`(404), 입력 오류는 `IllegalArgumentException` · 검증 예외(400), 상태 충돌은 `IllegalStateException`(409), 그 밖은 500. 새 예외 타입을 만들면 `GlobalExceptionHandler` 에 매핑을 추가한다.
- **로그는 `org.slf4j.Logger` 로만 남긴다.** `System.out` · `System.err` · `printStackTrace()` 가 없다.
- 로그 문자열에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다.
- 로그 레벨: 정상 흐름 `DEBUG`/`INFO`, 복구 가능한 실패 `WARN`, 요청을 실패시키는 예외 `ERROR`.

### modern/api — 테스트 · 형식
- 새 public 서비스 메서드 · 새 엔드포인트마다 테스트가 1개 이상 추가된다.
- 컨트롤러는 `@WebMvcTest` + `@MockBean` 서비스, 서비스는 Mockito 단위 테스트, Repository 쿼리는 `@DataJpaTest`(H2) 로 검증한다. 모두 `@ActiveProfiles("test")`.
- 테스트 메서드 이름은 camelCase(예: `getItemMissingReturns404`)이고 `@DisplayName` 에 한국어 설명이 있다.
- 와일드카드 import(`import x.y.*;`)가 없다. 들여쓰기는 공백 4칸, 한 줄 120자 이하.
- 도메인 상한 · 하한 값은 이름 붙인 상수로 둔다(예: `MAX_LEVEL = 5`). 조건식에 `5` 같은 리터럴을 직접 쓰지 않는다.

### modern/web
- 서버 호출은 `src/api/client.ts` 의 `getJson()` 만 거친다. `client.ts` 밖에 `fetch(` 가 없다.
- 엔드포인트별 호출 함수는 `src/api/items.ts`, 응답 타입은 `src/api/types.ts` 에 두고 필드명은 백엔드 JSON 과 같게 쓴다.
- 조회 훅은 `src/hooks/use*.ts` 에 두고 `useApiQuery` 를 쓴다. 컴포넌트는 `QueryState.status` 로 분기하고 HTTP 상태 코드를 직접 비교하지 않는다(오류 문구 변환은 `toErrorMessage`).
- 화면에 로딩 · 오류 · 빈 결과 세 상태가 모두 표시된다.
- 함수 컴포넌트만 쓴다. 클래스 컴포넌트 · `React.FC` · `defaultProps` 가 없다.
- 새 컴포넌트 `X.tsx` 옆에 `X.test.tsx` 가 있고, 렌더 확인 1개와 사용자 상호작용(클릭 · 입력) 1개 이상을 포함한다. 조회는 `getByRole` · `getByText` 를 쓰고 `getByTestId` 를 쓰지 않는다.
- 테스트는 실제 `localhost:8080` 을 호출하지 않는다. `src/test/mockFetch.ts` · `fixtures.ts` 를 쓴다.
- 형식은 `eslint.config.js` 를 따른다. `eslint-disable` 주석에는 같은 줄에 사유가 있다.

## 3. 금지 사항

- **`legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.**
- **의존성 추가 · 업그레이드는 먼저 묻는다.** 대상: `modern/api/build.gradle` 의 `plugins` · `dependencies`, `modern/web/package.json` 의 `dependencies` · `devDependencies`. 물을 때 이유와 대안을 함께 적는다.
- **DB 스키마 변경은 먼저 묻는다.** 대상: `db/**` 의 SQL, `@Entity` 의 `@Table` · `@Column` · 연관 매핑, 새 마이그레이션 파일, 시드 데이터.
- `package-lock.json` 을 손으로 고치거나 지우지 않는다.
- `modern/api/src/main/resources/application.yml` 의 datasource · hikari 설정을 바꾸지 않는다. 풀이 작게(max 5, timeout 3s) 맞춰져 있는 것은 의도다.
- 실제 비밀값(운영 계정 · 토큰 · 키)을 코드 · 설정에 리터럴로 넣지 않는다. 실습용 더미(`app-pass` 등)는 예외다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출을 하지 않는다.
- TypeScript 에 `any` · `as unknown as` · `@ts-ignore` 가 없다. 타입을 맞출 수 없으면 이유를 답변에 적고 묻는다.
- `dangerouslySetInnerHTML` · `console.log` 가 없다.
- `characterization/__snapshots__/` 를 손으로 고치지 않는다. 동작 보존 테스트를 깨뜨리는 변경은 먼저 알린다.
- 요청 범위 밖의 파일을 "정리" 명목으로 고치지 않는다. 범위 밖 파일을 고쳤다면 답변에 파일과 이유를 적는다.

## 4. 아키텍처 안내

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그 (Item*, Unit*, Tag*)
├── assignment/    과제 배포 · 재배포 · 학급 리포트 (Distribution*, Report*)
├── common/        GlobalExceptionHandler, ErrorResponse, NotFoundException, ClockConfig
└── config/        WebConfig — CORS: http://localhost:5173 의 GET 만 /api/** 허용
modern/web/src/
├── api/           client.ts(getJson · ApiError) · items.ts(엔드포인트 함수) · types.ts
├── hooks/         useApiQuery(공통) · useUnits · useUnitItems · useItem
├── components/    화면 조각 + 같은 이름의 *.test.tsx
└── test/          fixtures · mockFetch
```

- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 공개 문항만 목록에 노출한다: `status = 'A'`(`ItemStatus.ACTIVE`). 삭제 플래그가 아니라 상태 코드로 판단한다.
- 설정: `application.yml` = 로컬 MariaDB(`itembank`, docker compose `modern` 프로필), `src/test/resources/application-test.yml` = H2(MariaDB 모드, `ddl-auto: create-drop`).
- web 데이터 흐름: 컴포넌트 → `hooks/use*` → `api/items.ts` → `getJson()` → modern/api. `VITE_API_BASE` 가 없으면 `http://localhost:8080`, 빈 문자열이면 Vite 프록시를 탄다.
- 이관 관계: `legacy/` 의 화면 · 프로시저 규칙을 modern/api 로 옮기고, `characterization/` 스냅샷(레거시 응답 기준)을 `TARGET_BASE_URL=http://localhost:8080` 으로 다시 돌려 같은지 본다. 경로가 바뀐 화면은 `characterization/lib/target.mjs` 의 `PATH_ALIASES` 에 있다(예: `/search.php` → `/api/items/search`).
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다(예: `legacy/item-bank-php/search.php:120`).

## 5. 완료 기준

- **이관 · 리팩토링 작업은 `characterization/` 의 `npm test` 가 전부 통과하기 전에는 "완료"라고 보고하지 않는다.**
- 테스트가 실패하면 실패한 케이스 이름과 스냅샷 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. 그런 동작은 답변에 "의심 동작" 목록으로 따로 보고한다.
