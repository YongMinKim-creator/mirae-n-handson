# legacy/assignment-thymeleaf 비즈니스 규칙 후보 — "새 배포" 화면

> 범위: `GET /distributions/new` · `POST /distributions` 와 그 호출 코드(`DistributionService.distribute`, 관련 DAO, `AppClock`). 재배포 규칙은 다루지 않는다.
> 근거 표기는 `ARCHITECTURE.md` 머리말과 같다. 확신도 **확실** = 조건문 · SQL 로 확인, **추정** = 언어 동작 · 이름에서 짐작(실행해 보지 않음).

## 먼저 볼 것

### 주석 · 코드 불일치

| # | 주석 · 스키마 | 코드 | 관련 규칙 |
|---|---|---|---|
| 1 | `AppClock.java:8` `// 2024-03 QA 기간 중 임시로 고정. 운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)` | 고정값은 `2024-03` 이 아니라 `"2026-09-15 00:00:00"` (`AppClock.java:9`). 되돌려지지 않았고 `Dockerfile` · compose 에도 `app.clock` 설정이 없다 | BR-15 |
| 2 | `db/mariadb/init/01-schema.sql:87` `status ... -- O=진행 C=마감` | 코드는 `X`(`web/DistributionController.java:61`), `D`(`service/DistributionService.java:56`)도 판정에 쓴다. 폼 목록은 `O`,`X`,`C` 만 고른다(`dao/AssignmentDao.java:36`) | BR-01, BR-05, BR-08 |
| 3 | `01-schema.sql:86` `due_at DATETIME NOT NULL` | 코드는 `dueAt != null` 을 검사한다(`web/DistributionController.java:60`, `distribution_form.html:16`). 스키마상 도달하지 않는 분기다 | BR-03, BR-05 |
| 4 | `dao/DistributionDao.java:46` "같은 학급 + 같은 과제 는 한 건만 존재해야 한다" | 시드에 같은 쌍 `(2, 1)` 이 이미 두 행 있다(`02-seed.sql:133-134`). 이 화면 코드는 중복을 막지만 DB 는 막지 않는다 | BR-09 |

### 이관 시 결정이 필요한 항목

1. **마감 검사를 어디서 할지.** 마감 검사(BR-05)는 컨트롤러에만 있고 서비스 `distribute()` 에는 없다(`service/DistributionService.java:47-68`). modern/api 로 옮길 때 서비스로 내릴지 정해야 한다.
2. **`R` 상태 과제 배포 허용 여부.** 폼 목록은 `R` 을 빼지만(BR-01) 서버는 `D` 만 거부한다(BR-08). 폼을 거치지 않고 `POST` 하면 `R` 과제도 배포된다(BR-05 · BR-08 조건을 모두 통과하는 경우). 폼 목록 기준으로 맞출지, 서버 기준으로 맞출지 정해야 한다.
3. **`C`(종료) 상태 과제.** 마감 전이면 `C` 도 배포된다(BR-01 목록에 포함, BR-05 · BR-08 에 걸리지 않음). 시드의 `C` 과제는 모두 마감이 지나 있어(`02-seed.sql:120-121`) 현재 데이터로는 드러나지 않는다.
4. **마감 경계.** `isBefore(now)` 라 `due_at == now` 는 배포된다(BR-06, 추정).
5. **중복 방지 · 채번을 DB 로 옮길지.** 중복 확인(BR-09) · `MAX(id)+1` 채번(BR-10)이 잠금 없이 애플리케이션에서 이뤄진다. UNIQUE 제약 · AUTO_INCREMENT 로 옮기면 **DB 스키마 변경**이므로 먼저 승인이 필요하다. 시드의 기존 중복 행(`02-seed.sql:133-134`) 때문에 UNIQUE 추가가 바로 되지 않는다.
6. **시계.** 레거시는 기본이 고정 시각(BR-15)이다. characterization 스냅샷이 이 고정 시각 기준일 수 있으므로 modern 쪽 `Clock` 빈을 어떤 값으로 맞출지 정해야 한다.

## 규칙 후보

### BR-01
- 규칙: 새 배포 폼의 과제 선택 목록에는 `status` 가 `O`·`X`·`C` 인 과제만 나오고, `due_at` 오름차순 → `id` 오름차순으로 정렬된다.
- 근거: `dao/AssignmentDao.java:36-37`, `web/DistributionController.java:42`
- 근거 코드:
  ```java
                  + " WHERE a.status IN ('O', 'X', 'C') "      // dao/AssignmentDao.java:36
                  + " ORDER BY a.due_at ASC, a.id ASC";         // dao/AssignmentDao.java:37
          model.addAttribute("assignments", assignmentDao.findAllForSelect());   // web/DistributionController.java:42
  ```
- 확신도: 확실
- 비고: `D` 와 `R`(범위 밖 `assignments.html:26` 에서 "검수중" 으로 표시)은 목록에 없다. 과제 목록 화면용 `findAllForList` 는 `status <> 'D'` 로 조건이 다르다(`dao/AssignmentDao.java:29`, 범위 밖).

### BR-02
- 규칙: 새 배포 폼의 학급 선택 목록은 `class` 전체를 `id` 오름차순으로 보여 준다(조건 없음).
- 근거: `dao/ClassDao.java:21`, `web/DistributionController.java:43`
- 근거 코드:
  ```java
          return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);   // dao/ClassDao.java:21
  ```
- 확신도: 확실
- 비고: 교사별 필터가 없다. 옵션 문구는 `학급명 (teacher_id)` (`distribution_form.html:25`).

### BR-03
- 규칙: 과제 옵션은 `due_at` 이 현재 시각보다 앞서고 `status` 가 `X` 가 아니면 비활성(`disabled`)이 되고, 마감이 지난 과제에는 `X` 면 ` [연장]`, 아니면 ` [마감]` 이 붙는다.
- 근거: `distribution_form.html:16`, `distribution_form.html:18`
- 근거 코드:
  ```html
                      th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"   <!-- distribution_form.html:16 -->
                               + (${a.dueAt != null and a.dueAt.isBefore(now)} ? (${a.status == 'X'} ? ' [연장]' : ' [마감]') : '')"></option>   <!-- distribution_form.html:18 -->
  ```
- 확신도: 확실
- 비고: 화면 쪽 제한일 뿐이다. 서버 쪽 같은 조건은 BR-05. 주석 `<!-- 마감 지난 과제는 선택 불가 (연장 상태 제외) -->` (`:13`)와 코드가 일치한다. `now` 는 `AppClock.now()` (`web/DistributionController.java:44`, BR-15).

### BR-04
- 규칙: 과제 · 학급 선택은 브라우저에서 필수 입력이다(`required`, 기본 옵션 값은 빈 문자열).
- 근거: `distribution_form.html:11-12`, `distribution_form.html:23-24`
- 근거 코드:
  ```html
          <select id="assignmentId" name="assignmentId" required>   <!-- distribution_form.html:11 -->
              <option value="">-- 과제 선택 --</option>             <!-- distribution_form.html:12 -->
          <select id="classId" name="classId" required>             <!-- distribution_form.html:23 -->
  ```
- 확신도: 확실
- 비고: 서버 쪽에는 빈 값 처리 코드가 없다(`ARCHITECTURE.md` 5절 미확인 참고).

### BR-05
- 규칙: 배포 등록 시 과제의 `due_at` 이 현재 시각보다 앞서면(마감 지남) `status` 가 `X`(연장)가 아닌 한 배포를 거부하고, `"마감(yyyy-MM-dd HH:mm)이 지난 과제는 배포할 수 없습니다. [과제명]"` 오류와 함께 폼으로 되돌린다.
- 근거: `web/DistributionController.java:58-65`, `AppClock.java:12`, `AppClock.java:22-27`
- 근거 코드:
  ```java
          LocalDateTime now = AppClock.now();                                   // web/DistributionController.java:58
          // 마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외                     // web/DistributionController.java:59
          if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {            // web/DistributionController.java:60
              if (!"X".equals(a.getStatus())) {                                 // web/DistributionController.java:61
                  ra.addFlashAttribute("error", "마감(" + AppClock.fmt(a.getDueAt()) + ")이 지난 과제는 배포할 수 없습니다. [" + a.getTitle() + "]");   // :62
                  return "redirect:/distributions/new";                         // web/DistributionController.java:63
  ```
- 확신도: 확실
- 비고: **컨트롤러에만 있다.** 서비스 `distribute()` 는 마감을 보지 않는다(`service/DistributionService.java:47-68`). 이 검사가 `D`(BR-08) 검사보다 먼저 실행되므로 마감 지난 `D` 과제는 마감 오류 문구를 받는다. 주석(`:59`)과 코드가 일치한다.

### BR-06
- 규칙: `due_at` 이 현재 시각과 **같으면** 마감 전으로 취급되어 배포된다.
- 근거: `web/DistributionController.java:60`, `distribution_form.html:16`
- 근거 코드:
  ```java
          if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {   // web/DistributionController.java:60
  ```
- 확신도: 추정 (`LocalDateTime.isBefore` 가 같은 시각에 `false` 를 돌려주는 언어 동작에서 짐작. 실행해 보지 않음)
- 비고: 시드 마감은 모두 `23:59:00`(`02-seed.sql:120-125`)이고 고정 시각은 `00:00:00`(`AppClock.java:9`)이라 현재 데이터로는 경계가 드러나지 않는다. modern 에서 `!now.isAfter(due)` 등으로 바꾸면 경계가 뒤집힌다.

### BR-07
- 규칙: 과제 id 에 해당하는 과제가 없으면 컨트롤러가 `"존재하지 않는 과제입니다."` 오류로 폼에 되돌리고, 학급 id 에 해당하는 학급이 없으면 서비스가 `IllegalArgumentException("존재하지 않는 학급입니다. (id=…)")` 을 던져 같은 방식으로 되돌린다.
- 근거: `web/DistributionController.java:53-57`, `service/DistributionService.java:48-55`, `web/DistributionController.java:69-71`
- 근거 코드:
  ```java
          AssignmentRow a = assignmentDao.findById(assignmentId);             // web/DistributionController.java:53
          if (a == null) {                                                    // web/DistributionController.java:54
              ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");       // web/DistributionController.java:55
          ClassRow c = classDao.findById(classId);                            // service/DistributionService.java:52
          if (c == null) {                                                    // service/DistributionService.java:53
              throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");   // service/DistributionService.java:54
          } catch (IllegalArgumentException | IllegalStateException e) {       // web/DistributionController.java:69
              ra.addFlashAttribute("error", e.getMessage());                  // web/DistributionController.java:70
  ```
- 확신도: 확실
- 비고: 과제 없음은 컨트롤러(`:54-56`)와 서비스(`:49-51`) 두 곳에 있고 문구가 다르다. 서비스 쪽 문구(`"(id=…)"` 포함)는 컨트롤러가 먼저 걸러서 이 화면에서는 나오지 않는다. modern 매핑은 `NotFoundException`(404) 이 CLAUDE.md 기준이다.

### BR-08
- 규칙: 과제 `status` 가 `D`(삭제)면 `IllegalStateException("삭제된 과제는 배포할 수 없습니다.")` 로 배포를 거부한다. 서버가 상태 값으로 거부하는 것은 `D` 하나뿐이다.
- 근거: `service/DistributionService.java:56-58`
- 근거 코드:
  ```java
          if ("D".equals(a.getStatus())) {                                    // service/DistributionService.java:56
              throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");   // service/DistributionService.java:57
  ```
- 확신도: 확실
- 비고: `R`, `C`, 그 밖의 값은 서버에서 막지 않는다(먼저 볼 것 · 이관 결정 2, 3). 검사 순서는 과제 존재 → 학급 존재 → `D` → 중복(`:48-61`).

### BR-09
- 규칙: 같은 과제가 같은 학급에 이미 배포된 행이 1건 이상 있으면 `IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.")` 로 거부한다.
- 근거: `service/DistributionService.java:59-61`, `dao/DistributionDao.java:47-52`
- 근거 코드:
  ```java
          if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {   // service/DistributionService.java:59
              throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.");   // :60
                  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",   // dao/DistributionDao.java:49
  ```
- 확신도: 확실
- 비고: `redistributed` 값 · 과제 상태와 무관하게 행 수만 센다. DB UNIQUE 제약은 없고(`01-schema.sql:92-103`), 잠금 없이 COUNT 후 INSERT 한다. 시드에 이미 중복 쌍이 있다(먼저 볼 것 4).

### BR-10
- 규칙: 새 배포 id 는 `distribution` 의 최대 `id` + 1 이다(행이 없으면 1).
- 근거: `dao/DistributionDao.java:54-57`, `service/DistributionService.java:62`
- 근거 코드:
  ```java
          Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);   // dao/DistributionDao.java:55
          return (max == null ? 0L : max.longValue()) + 1L;                                              // dao/DistributionDao.java:56
  ```
- 확신도: 확실
- 비고: `distribution.id` 에 AUTO_INCREMENT 가 없다(`01-schema.sql:93`). 동시 요청 시 같은 id 를 얻어 PK 충돌이 날 수 있다(미확인, `ERD.md` 5절).

### BR-11
- 규칙: 새 배포 행은 `distributed_at` = 현재 시각(`AppClock.now()`), `redistributed` = `0` 으로 저장된다.
- 근거: `service/DistributionService.java:63`, `dao/DistributionDao.java:60-62`
- 근거 코드:
  ```java
          int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());   // service/DistributionService.java:63
                  "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",   // dao/DistributionDao.java:61
  ```
- 확신도: 확실
- 비고: 기본 설정에서는 모든 배포 시각이 `2026-09-15 00:00:00` 으로 저장된다(BR-15).

### BR-12
- 규칙: INSERT 영향 행 수가 1이 아니면 `IllegalStateException("배포 저장에 실패했습니다.")` 으로 실패 처리한다.
- 근거: `service/DistributionService.java:64-66`
- 근거 코드:
  ```java
          if (n != 1) {                                                   // service/DistributionService.java:64
              throw new IllegalStateException("배포 저장에 실패했습니다.");   // service/DistributionService.java:65
  ```
- 확신도: 확실
- 비고: 단일 행 INSERT 라 정상 경로에서는 도달하기 어렵다.

### BR-13
- 규칙: 배포가 성공하면 `"배포가 등록되었습니다. (배포 #<id>)"` 메시지와 함께 배포 목록(`/distributions`)으로 이동한다.
- 근거: `web/DistributionController.java:67-68`, `web/DistributionController.java:76`
- 근거 코드:
  ```java
              long id = distributionService.distribute(assignmentId, classId);          // web/DistributionController.java:67
              ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");  // web/DistributionController.java:68
          return "redirect:/distributions";                                             // web/DistributionController.java:76
  ```
- 확신도: 확실
- 비고: 메시지 표시는 `layout.html:29`.

### BR-14
- 규칙: 서비스 안에서 난 DB 오류(`DataAccessException`)는 `"DB 오류로 배포에 실패했습니다."` 로 폼에 되돌리고, 컨트롤러의 과제 조회(`:53`) · 폼 조회(`:42-43`)에서 난 DB 오류는 `db_error` 화면으로 보낸다.
- 근거: `web/DistributionController.java:72-74`, `web/DbErrorAdvice.java:11-16`
- 근거 코드:
  ```java
          } catch (DataAccessException e) {                                  // web/DistributionController.java:72
              ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");   // web/DistributionController.java:73
      @ExceptionHandler(DataAccessException.class)                           // web/DbErrorAdvice.java:11
          return "db_error";                                                 // web/DbErrorAdvice.java:16
  ```
- 확신도: 확실
- 비고: `DbErrorAdvice` 는 원인 메시지를 `System.out.println` 으로 찍고(`:13`) 화면에도 그대로 보여 준다(`:15`, `db_error.html:8`). modern 에서는 `GlobalExceptionHandler` · slf4j 로 바꿔야 한다(CLAUDE.md 2절).

### BR-15
- 규칙: 현재 시각은 시스템 속성 `app.clock` 이 `system` 일 때만 실제 시각(나노초 0)이고, 그 밖에는 항상 `2026-09-15 00:00:00` 이다.
- 근거: `AppClock.java:9`, `AppClock.java:14-20`
- 근거 코드:
  ```java
      private static final String FIXED = "2026-09-15 00:00:00";   // AppClock.java:9
          String sys = System.getProperty("app.clock");             // AppClock.java:15
          if (sys != null && sys.equals("system")) {                // AppClock.java:16
              return LocalDateTime.now().withNano(0);               // AppClock.java:17
          return LocalDateTime.parse(FIXED, FMT);                   // AppClock.java:19
  ```
- 확신도: 확실
- 비고: 주석(`:8`)은 "2024-03 QA" 라고 하지만 값은 2026-09 다(먼저 볼 것 1). `AppClockTest.java:12` 가 고정값을 검증한다. 이 값 기준으로 시드 과제 1·2·3 은 마감이 지났고 4·5·6 은 마감 전이다(`02-seed.sql:120-125`).

## 뽑지 않은 것 · 주석만 있는 것

| 항목 | 출처 | 뽑지 않은 이유 |
|---|---|---|
| 상태 코드 의미 `O=진행 C=마감` | `db/mariadb/init/01-schema.sql:87` (주석) | 스키마 주석뿐. 코드는 `C` 를 "종료" 로 표시(범위 밖 `assignments.html:26`)하며 마감 판정은 `due_at` 으로 한다(BR-05) |
| 상태 `X`=연장, `R`=검수중, `D`=삭제의 의미 | `assignments.html:26`(범위 밖 화면 문구), `web/DistributionController.java:59` 주석, `service/DistributionService.java:57` 예외 문구 | 이름 · 문구에서 온 의미다. 규칙에는 값(`X`, `D`)과 결과만 적었다 |
| "다시 내려면 배포 목록에서 재배포를 사용하세요" | `service/DistributionService.java:60` (예외 문구) | 안내 문구. 재배포 규칙은 범위 밖 |
| 교사 본인 학급에만 배포 | — | 코드 없음. 학급 목록 · 배포 모두 교사 조건이 없다(BR-02, BR-07) |
| 권한 · 로그인 확인 | — | 코드 없음 |
| 배포 수정 · 삭제 | — | 코드 없음(`ERD.md` 4절 UPDATE · DELETE) |
| 과제 1건당 배포 학급 수 상한 | — | 코드 없음. `class_cnt` 는 조회만 한다(`dao/AssignmentDao.java:23`) |
| `QA 기간 중 임시로 고정 … 운영 반영 전 되돌릴 것` | `AppClock.java:8` (주석) | 할 일 메모다. 실제 동작은 BR-15 |

## 매직 넘버

| 값 | 위치 | 의미(코드에서 확인한 쓰임) | 이관 시 |
|---|---|---|---|
| `"2026-09-15 00:00:00"` | `AppClock.java:9` | 기본 현재 시각 | modern 은 주입 `Clock` 사용(CLAUDE.md). 테스트 · 스냅샷 기준 시각으로만 남길지 결정 |
| `'O'`, `'X'`, `'C'` | `dao/AssignmentDao.java:36` | 폼 선택 목록에 넣을 상태 | 상태 enum 으로 |
| `"X"` / `'X'` | `web/DistributionController.java:61`, `distribution_form.html:16`, `:18` | 마감 검사 예외 상태 | 〃 |
| `"D"` | `service/DistributionService.java:56` | 배포 거부 상태 | 〃 |
| `0` | `dao/DistributionDao.java:61` | 새 배포의 `redistributed` 초기값 | 스키마 기본값도 `0`(`01-schema.sql:97`) |
| `> 0` | `service/DistributionService.java:59` | 중복 판정 기준 | — |
| `!= 1` | `service/DistributionService.java:64` | INSERT 성공 판정 | — |
| `0` · `+ 1L` | `dao/DistributionDao.java:55-56` | 채번 시작값 · 증분 | AUTO_INCREMENT 전환 여부(스키마 변경, 승인 필요) |
| `"yyyy-MM-dd HH:mm"` | `AppClock.java:12` | 마감 오류 문구의 시각 형식(BR-05) | 오류 메시지 호환이 필요하면 유지 |
| `'MM-dd HH:mm'` | `distribution_form.html:17` | 과제 옵션의 마감 표시 형식 | web 쪽 표시 형식 |
