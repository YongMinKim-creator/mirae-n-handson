# legacy/assignment-thymeleaf ERD · 데이터 흐름 — "새 배포" 화면

> 범위: `GET /distributions/new` · `POST /distributions` 가 읽고 쓰는 테이블만 다룬다. 근거 표기는 `ARCHITECTURE.md` 머리말과 같다.
> 스키마 근거는 저장소 루트 기준(`db/mariadb/init/01-schema.sql:92`)으로 적는다.

## 1. 스키마 출처

- `db/mariadb/init/01-schema.sql` — 과제 배포 테이블은 `:72-115` 절("과제 배포 (assignment-thymeleaf 모듈이 같은 DB를 쓴다)" `:73`).
- `unit` 은 문항 은행 절에 정의되어 있다(`db/mariadb/init/01-schema.sql:11-18`).
- 시드: `db/mariadb/init/02-seed.sql:111-138` (학급 3 · 과제 6 · 배포 8).

## 2. 테이블

| 테이블 | 정의 | 이 화면에서 쓰는 컬럼 | 비고 |
|---|---|---|---|
| `assignment` | `01-schema.sql:82-90` | `id`, `title`, `unit_id`, `due_at`, `status` | `due_at DATETIME NOT NULL` `:86`. `status CHAR(1) NOT NULL DEFAULT 'O'` 주석은 `O=진행 C=마감` 뿐 `:87` |
| `unit` | `01-schema.sql:11-18` | `id`, `code`, `name` | 과제 옵션 문구의 단원 코드에 쓴다 |
| `class` | `01-schema.sql:75-80` | `id`, `name`, `teacher_id` | 예약어라 SQL 에서 `` `class` `` 로 쓴다(`dao/ClassDao.java:21`) |
| `distribution` | `01-schema.sql:92-103` | `id`, `assignment_id`, `class_id`, `distributed_at`, `redistributed` | `id` 는 AUTO_INCREMENT 가 아니다(`:93`). `(assignment_id, class_id)` UNIQUE 없음 — 인덱스만 `:99-100` |
| `submission` | `01-schema.sql:105-115` | — | 이 화면 경로에서 읽지 않는다. `DistributionDao.BASE_SELECT` (`dao/DistributionDao.java:26`)에 있으나 `distribute()` 는 그 쿼리를 쓰지 않는다 |

## 3. 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "unit_id"
    assignment ||--o{ distribution : "assignment_id"
    class ||--o{ distribution : "class_id"
    distribution ||--o{ submission : "distribution_id (범위 밖)"

    unit {
        INT id PK
        VARCHAR code UK
        VARCHAR name
    }
    assignment {
        INT id PK
        VARCHAR title
        INT unit_id FK
        DATETIME due_at
        CHAR status
    }
    class {
        INT id PK
        VARCHAR name
        VARCHAR teacher_id
    }
    distribution {
        INT id PK
        INT assignment_id FK
        INT class_id FK
        DATETIME distributed_at
        TINYINT redistributed
    }
    submission {
        INT id PK
        INT distribution_id FK
    }
```

### 관계별 근거

| 관계 | 구분 | 스키마 근거 | 코드 근거 |
|---|---|---|---|
| `unit` 1 — N `assignment` | 선언 | `CONSTRAINT fk_assignment_unit FOREIGN KEY (unit_id) REFERENCES unit (id)` `01-schema.sql:89` | `LEFT JOIN unit u ON u.id = a.unit_id` `dao/AssignmentDao.java:24` |
| `assignment` 1 — N `distribution` | 선언 | `fk_distribution_assignment` `01-schema.sql:101` | `WHERE d.assignment_id = a.id` `dao/AssignmentDao.java:23`, `WHERE assignment_id = ? AND class_id = ?` `dao/DistributionDao.java:49` |
| `class` 1 — N `distribution` | 선언 | `fk_distribution_class` `01-schema.sql:102` | `dao/DistributionDao.java:49`, INSERT 의 `class_id` `:61` |
| `distribution` 1 — N `submission` | 선언 | `fk_submission_distribution` `01-schema.sql:114` | 이 화면 경로에서는 쓰지 않음 |

- `unit_id` 는 `NOT NULL` + FK(`01-schema.sql:85`, `:89`)인데 코드는 `LEFT JOIN` 이다(`dao/AssignmentDao.java:24`). 스키마상 결과 차이는 없다.
- `(assignment_id, class_id)` 쌍의 유일성은 DB 가 보장하지 않는다. 코드 주석이 이를 밝힌다: `// 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)` `dao/DistributionDao.java:46`. 시드에는 이미 같은 쌍 `(2, 1)` 이 두 행 있다(`02-seed.sql:133-134`, 두 번째 행은 `redistributed = 1`).

## 4. 읽기 · 쓰기 위치

### SELECT

| 위치 | SQL 요지 | 테이블 | 부르는 쪽 |
|---|---|---|---|
| `dao/AssignmentDao.java:21-24`, `:35-37` `findAllForSelect` | `BASE_SELECT ... WHERE a.status IN ('O', 'X', 'C') ORDER BY a.due_at ASC, a.id ASC` | `assignment`, `unit`, `distribution`(서브쿼리 `COUNT(DISTINCT d.class_id)`) | `web/DistributionController.java:42` |
| `dao/AssignmentDao.java:42` `findById` | `BASE_SELECT WHERE a.id = ?` | `assignment`, `unit`, `distribution`(서브쿼리) | `web/DistributionController.java:53`, `service/DistributionService.java:48` |
| `dao/ClassDao.java:21` `findAll` | ``SELECT id, name, teacher_id FROM `class` ORDER BY id ASC`` | `class` | `web/DistributionController.java:43` |
| `dao/ClassDao.java:25` `findById` | ``SELECT id, name, teacher_id FROM `class` WHERE id = ?`` | `class` | `service/DistributionService.java:52` |
| `dao/DistributionDao.java:48-50` `countByAssignmentAndClass` | `SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?` | `distribution` | `service/DistributionService.java:59` |
| `dao/DistributionDao.java:55` `nextId` | `SELECT COALESCE(MAX(id), 0) FROM distribution` (+1 은 `:56`) | `distribution` | `service/DistributionService.java:62` |

### INSERT

| 위치 | SQL | 값 | 부르는 쪽 |
|---|---|---|---|
| `dao/DistributionDao.java:60-62` `insert` | `INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)` | `id` = `nextId()`, `distributed_at` = `AppClock.now()`, `redistributed` = 리터럴 `0` | `service/DistributionService.java:63` |

### UPDATE · DELETE

- 코드 없음. (`DistributionDao.markRedistributed` `dao/DistributionDao.java:65-67` 의 UPDATE 는 재배포 경로라 범위 밖이다.)

### 트랜잭션

- 위 SELECT(서비스 쪽) · INSERT 는 `@Transactional` 메서드 `distribute` 안에서 실행된다(`service/DistributionService.java:46-47`). 컨트롤러의 `findById` `:53` 와 폼의 조회 `:42-43` 는 트랜잭션 밖이다.
- 격리 수준 · 잠금 지정은 없다. 중복 확인(`COUNT`)과 `MAX(id)+1` 채번, INSERT 사이에 잠금이 없다.

## 5. 미확인

- 두 요청이 동시에 같은 `(assignment_id, class_id)` 로 배포할 때 결과(중복 행 또는 PK 충돌). 코드상 잠금이 없다는 것만 확인했고 실행해 보지 않았다.
- 시드의 `(2, 1)` 중복 행(`02-seed.sql:133-134`)이 어떤 경로로 만들어진 데이터인지. 이 화면의 코드로는 만들 수 없다(`service/DistributionService.java:59-60`).
- `assignment.status` 에 들어갈 수 있는 값 전체. 스키마는 `O`, `C` 만 적고(`01-schema.sql:87`) CHECK 제약이 없다. 코드는 `X`, `D`(`web/DistributionController.java:61`, `service/DistributionService.java:56`)를, 범위 밖 화면은 `R` 도 쓴다(`assignments.html:26`). 시드에는 `C`, `O` 만 있다(`02-seed.sql:120-125`).
