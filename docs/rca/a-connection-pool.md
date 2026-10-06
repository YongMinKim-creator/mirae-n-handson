# a-connection-pool 장애 원인 분석 (RCA)

## 1. 수집 범위

- 대상: `incident-logs/a-connection-pool/` 의 로그 4개 (2026-09-16 하루치)
- 이 절은 범위 확정만 다룬다. **원인 추측은 하지 않는다.**
- 집계 방법: `wc` · `head` · `tail` · `grep -c` · `grep -n` 과, 분 단위 집계를 위한 `grep -o … | uniq -c` 출력(건수만)을 썼다. 파일을 통째로 읽지 않았다.

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 | 타임존 근거 |
|---|---:|---|---|---|---|
| `app.log` | 5,371 | 09-16 06:43:56.753 ~ 15:29:51.760 | `2026-09-16T06:43:56.753+09:00` (ISO-8601, ms) | **KST** (`+09:00` 명시) | 줄 자체에 오프셋이 있음 |
| `nginx-access.log` | 21,086 | 09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:06:43:56 +0900]` (CLF) | **KST** (`+0900` 명시) | 줄 자체에 오프셋이 있음 |
| `nginx-error.log` | 168 | 09-16 09:12:03 ~ 14:51:47 | `2026/09/16 09:12:03` | **KST 로 판단** (표기 없음) | 14:41:06 `upstream timed out` 이 access.log 의 같은 초 · 같은 클라이언트(`10.20.3.12`) · 같은 URL 의 504 와 일치 (access.log:18933) |
| `mariadb-slow.log` | 21 (쿼리 3건) | 09-16 09:14:07 ~ 14:37:52 | 헤더 `# Time: 260916  0:14:09` (`yyMMdd H:mm:ss`, 0 패딩 없음) + `SET timestamp=<epoch>` | **UTC** (표기 없음) | `SET timestamp=1789517647` = 00:14:07 UTC = 09:14:07 KST. 3건 모두 동일하게 +9시간 |

### 1.2 KST 기준 변환 규칙

- `app.log` · `nginx-access.log` · `nginx-error.log` 는 이미 KST 이므로 그대로 쓴다.
- `mariadb-slow.log` 만 UTC 이므로 **KST = UTC + 9시간** 으로 바꾼다. 날짜가 넘어갈 수 있으므로 `# Time:` 헤더 대신 `SET timestamp` 에폭에서 `TZ=Asia/Seoul date -d @<epoch>` 로 변환한다.
- slow log 의 두 시각 의미가 다르다. `# Time:` 은 쿼리 **종료** 시각(기록 시점), `SET timestamp` 는 쿼리 **시작** 시각이다(예: Query_time 2.3s → 헤더 `0:14:09`, epoch `00:14:07`). 시작 시각 기준으로 맞춘다.
- `app.log` 는 5,371줄 중 타임스탬프로 시작하지 않는 줄이 3,444줄(스택 트레이스 등)이다. **줄 수 ≠ 이벤트 수** 이므로 건수는 레벨 필드가 있는 줄(`WARN`/`ERROR`)만 센다.

| slow log 쿼리 | UTC (시작) | **KST (시작)** | Query_time | Rows_examined |
|---|---|---|---:|---:|
| `item`+`unit` 조인 (`M5-2`) | 00:14:07 | 09:14:07 | 2.32s | 41,822 |
| `unit` 전체 조회 | 02:02:40 | 11:02:40 | 2.04s (Lock_time 0.91s) | 5 |
| `submission` (`distribution_id=5`) | 05:37:42 | **14:37:42** (종료 14:37:52) | 9.81s | 1,284,310 |

### 1.3 분 단위 이상 건수

기준 정의: **app** = 레벨이 `WARN`·`ERROR` 인 줄, **access** = 응답 코드 5xx(500·502·504), **error** = `nginx-error.log` 전 줄(분 단위), **slow** = 쿼리 건수.

#### 평소 수준 (이상 구간 이전)

| 파일 | 평소 | 근거 |
|---|---|---|
| `app.log` | 분당 최대 1건, 시간당 0~4건 | 06:43~14:36 에 WARN/ERROR 가 분 단위로 1건씩 드문드문 (08:41, 09:27, 09:36, 09:40, 10:15, 10:24, 10:30, 10:41, 11:45, 12:50, 12:57, 13:21, 14:02, 14:11, 14:14, 14:33) |
| `nginx-access.log` | 5xx **0건** | 14:40 이전 5xx 없음 (전체 5xx 186건이 모두 14:40~14:51) |
| `nginx-error.log` | 시간당 0~1건 | 09:12, 10:42, 11:47, 13:05, 13:18 (5건, 내용은 미확인) |
| `mariadb-slow.log` | 하루 3건 | 09:14, 11:02, 14:37 |
| 요청량 (참고) | 분당 약 34~58건 | 14:30~14:59 구간. 이상 구간(14:40~14:51)도 34~58건으로 평소와 같은 범위 |

#### 이상 구간 분 단위 건수 (14:37 ~ 14:52)

| 시각(KST) | app WARN/ERROR | access 5xx | nginx-error | 비고 |
|---|---:|---:|---:|---|
| 14:33 | 1 | 0 | 0 | 평소 수준 (`state conflict` WARN) |
| 14:37 | 1 | 0 | 0 | slow log 쿼리(시작 14:37:42, 9.8s) · `app.log:1243` 첫 `Connection leak detection triggered` WARN (14:37:52) |
| 14:38 | 2 | 0 | 0 | |
| 14:39 | 3 | 0 | 0 | |
| 14:40 | 17 | 1 | 0 | 첫 5xx `GET /api/units/M6-2/items` 500 (14:40:52, access.log:18918) |
| 14:41 | 31 | 9 | 3 | |
| 14:42 | 17 | 20 | 20 | |
| 14:43 | **44** | 17 | 8 | app 최대 |
| 14:44 | 17 | 13 | 15 | |
| 14:45 | 33 | **27** | 22 | access 5xx 최대 |
| 14:46 | 24 | 26 | **25** | nginx-error 최대 |
| 14:47 | 24 | 14 | 10 | |
| 14:48 | 22 | 15 | 15 | |
| 14:49 | 33 | 23 | 20 | |
| 14:50 | 12 | 18 | 22 | |
| 14:51 | 4 | 3 | 3 | 마지막 5xx (504, 14:51:47, access.log:19381) · nginx-error 마지막 줄 |
| 14:52 | 1 | 0 | 0 | `state conflict` WARN 1건 (평소 수준). 이후 `app.log` 마지막 WARN/ERROR 는 5269행 14:52:09 |

- 5xx 합계: 186건 = 500 49건 + 502 82건 + 504 55건. 14:40:52 부터 14:51:47 까지에만 있다.
- 15:00 이후: access 5xx 0건, app WARN/ERROR 0건. 15:29 까지 로그가 이어지는데 5xx 는 다시 나오지 않는다.
- `app.log` 전체 레벨: INFO 1,626 / WARN 203 / ERROR 98. 이 중 이상 구간(14시대)이 WARN/ERROR 289건이다(14시대 합계).

### 1.4 제안: 이상 구간과 수집 범위

**이상 구간 (KST)**

- 시작: **14:37:42** — slow log 쿼리 시작 시각. `app.log` 에 처음 이상 징후(`Connection leak detection` WARN)가 나오는 시각은 14:37:52 이다. 사용자 영향(5xx)은 14:40:52 부터다. 따라서 "징후 시작 14:37, 사용자 영향 시작 14:40" 으로 구분해 둔다.
- 끝: **14:52:00** — 마지막 5xx 14:51:47, 마지막 nginx-error 14:51:47. 14:52 의 app WARN 1건은 평소 수준 업무 WARN 이다.
- 길이: 약 15분.

**여유 (앞쪽을 더 넉넉히)**

- 앞 **30분**, 뒤 **10분**.
- 앞쪽을 넓게 잡는 이유:
  1. 징후(14:37)가 사용자 영향(14:40)보다 먼저 시작했다. 징후 이전 상황이 필요하다.
  2. 누수 감지 WARN 은 연결을 잡은 뒤 일정 시간이 지나서 찍히는 로그라, 관련 요청은 로그 시각보다 앞선 시점에 시작됐다.
  3. 같은 파일 안 14:02, 14:11, 14:14, 14:33 의 `state conflict` WARN 처럼 이상 구간과 무관한 WARN 을 앞쪽에서 걸러내려면 평소 패턴을 같이 봐야 한다.
- 뒤쪽은 짧게 잡아도 된다. 14:52 이후 15:29 까지 5xx · 오류가 0건이다. 복구 확인용으로 10분이면 충분하다.

**권장 수집 범위**

| 구분 | 시각 (KST) |
|---|---|
| 이상 구간 | 14:37:42 ~ 14:52:00 |
| **수집 범위 (앞 30분 · 뒤 10분)** | **14:07:00 ~ 15:02:00** (55분) |
| 평소 비교 구간 (별도) | 13:00:00 ~ 13:30:00 (5xx 0건, app WARN/ERROR 1건) |
| `mariadb-slow.log` | 파일이 21줄뿐이므로 3건 전부 보존. 14:37 건만 수집 범위 안이고, 09:14 · 11:02 건은 평소 비교용 |

### 1.5 이 단계의 한계

- 분 단위 건수만 셌다. 건수가 많은 WARN/ERROR 의 메시지 종류 분류, 요청 경로별 분포, 스택 트레이스 확인은 다음 단계에서 한다.
- `nginx-error.log` 의 타임존은 표기가 없어 access.log 와의 1건 대조(같은 초 · 같은 클라이언트)로 KST 로 판단했다. 다음 단계에서 다른 건으로도 한 번 더 대조한다.
- `mariadb-slow.log` 는 하루 3건뿐이라 slow 임계값 설정을 알 수 없다. 14:40~14:51 에 slow 쿼리가 없다는 사실이 "느린 쿼리가 없었다"는 뜻인지는 미확인이다.

## 2. 타임라인

- 대상: 1.4절에서 정한 수집 범위 **2026-09-16 14:07:00 ~ 15:02:00 (KST)**. 로그 4개를 KST 한 시간축으로 합쳤다. `mariadb-slow.log` 는 UTC 를 +9시간 변환했다.
- 로그에 있는 사건만 적었다. **원인 해석은 하지 않는다.** "관련 여부 미판단" 은 두 사건이 같은 값(ID 등)을 갖지만 연결해서 읽지 않았다는 뜻이다.
- 출처의 `파일:줄번호` 는 해당 사건의 첫 줄이다. 묶음 행은 첫 발생 줄이고, 끝 줄은 원문 발췌 열이 아니라 사건 요약에 적었다. `access` = `nginx-access.log`, `error` = `nginx-error.log`, `slow` = `mariadb-slow.log`.
- 원문 발췌는 80자 이내로 잘랐다(`…` 는 생략). 학생 식별자(`STU-…`)는 원문에 있어도 `…` 로 가렸다.
- `(선후 불확실)`: 파일이 달라 시각이 같은 초(±2초 이내)여서 순서를 단정할 수 없다. access 는 응답이 끝난 시각, app 은 로그를 남긴 시각, slow 는 초 단위(종료 시각)로 기록되므로 같은 초 안에서는 비교하지 않는다.
- 분당 건수는 `grep -o … | uniq -c` 집계다.

### 2.1 14:07 ~ 14:37 — 평소 수준 (이상 징후 이전)

| 시각 (KST) | 출처 (파일:줄번호) | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:09:10 | access:17500 | 숫자가 아닌 id 로 문항 조회 → 400. 첫 발생 14:09:10, 이후 2건/28분 (14:31:47 `access:18519`, 14:37:15 `access:18761`) | `GET /api/items/NaN HTTP/1.1" 400 150` |
| 14:10:05.155 | app:1148 | 학급 보고서 생성(ReportService INFO). 14:07~14:37 에 5건/30분(분당 최대 1건). 14:10, 14:13, 14:15, 14:17, 14:18 | `built class report for class 3: 3 distributions, 10 submissions` |
| 14:11:38.554 | app:1154 (access:17605 = 409) | 마감된 과제 재배포 요청 → 409 `state conflict` WARN. 첫 발생 14:11:38, 이후 2건/30분 (14:14:03 `app:1165`, 14:33:45 `app:1229`). 평소 수준 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 14:19:38.987 | app:1182 (access:17977 = 200) | 재배포 성공(distribution 7, 사유 있음) | `redistributed distribution 7 (assignment 5, class 3) reason=…` |
| 14:07~14:39 | access:17405 ~ (grep -c 집계) | 404 101건(분당 약 3건). `/favicon.ico` · `/robots.txt` · `/apple-touch-icon.png` · `/api/unit` · `/api/item/N` 등. app 은 `no handler for …` INFO 로 남김. 5xx 는 0건 | `GET /favicon.ico HTTP/1.1" 404` |

### 2.2 14:37 ~ 14:52 — 징후 시작부터 종료까지

| 시각 (KST) | 출처 (파일:줄번호) | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:37:42 (시작) ~ 14:37:52 (종료) | slow:15~21 (`SET timestamp` 20행 = 05:37:42 UTC) | **하루 3건 중 가장 느린 slow 쿼리**. `submission where distribution_id=5`, 9.81초, 1,284,310행 검사, 4행 반환. 같은 파일의 앞선 2건은 2.3초 · 2.0초(검사 41,822행 · 5행). 이 구간에서 slow 쿼리는 이 1건뿐 | `Query_time: 9.812417 … Rows_examined: 1284310` |
| 14:37:52.199 | app:1243 | **첫 `Connection leak detection triggered` WARN**(thread exec-6) + 스택 트레이스. 이 메시지는 이후 134건: 14:37 1건, 14:38 2건, 14:39 3건, 14:40 14건, 이후 분당 9~14건, 마지막 14:51:37 `app:5251`. 위 slow 쿼리 종료(14:37:52)와 같은 초 (선후 불확실) | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3` |
| 14:38:01.900 | app:1258~1259 (access:18801 = 200) | class 3 보고서 생성 완료 직후 `Previously reported leaked connection … returned to the pool (unleaked)` INFO. 이 메시지도 134건, 마지막 14:51:49 `app:5267`. access 의 같은 초 200 과 (선후 불확실) | `…was returned to the pool (unleaked)` |
| 14:38:01 | access:18801 | **보고서 요청 빈도 변화 시작**. 14:07~14:37 은 분당 ≤1건이었으나 14:38 3건, 14:39 3건. class 3 요청이 14:38:01 · 14:38:27 · 14:38:57 · 14:39:24 (`access:18801`, `18815`, `18839`, `18862`) | `GET /api/classes/3/report HTTP/1.1" 200 266` |
| 14:38:29.715 | app:1278 (access:18817 = 200) | 재배포 성공 distribution 5, **`reason=null`**(14:19:38 의 재배포는 사유 있음). slow 쿼리의 `distribution_id=5` 와 같은 ID (관련 여부 미판단). access 의 같은 초와 (선후 불확실) | `redistributed distribution 5 (assignment 3, class 3) reason=null` |
| 14:40:00 ~ 14:51:59 | access (분당 집계) | 보고서 요청이 분당 10~19건: 14:40 10, 14:41 16, 14:42 15, 14:43 19, 14:44 14, 14:45 15, 14:46 19, 14:47 12, 14:48 14, 14:49 16, 14:50 10, 14:51 5. 전체 요청수는 분당 34~58건으로 14:30~14:39 와 같은 범위 | `GET /api/classes/N/report` |
| 14:40:04.730 | app:1351 | leak WARN 이 분당 14건으로 늘어남(서로 다른 Connection 객체 · 여러 exec 스레드) | `Connection leak detection triggered for org.mariadb.jdbc.Connection@3f6c2a91` |
| 14:40:52 | access:18918 | **첫 5xx**: `GET /api/units/M6-2/items` 500. 이후 5xx 186건(500 49 · 502 82 · 504 55)이 14:51:47 까지. 바로 아래 app 첫 ERROR 와 같은 초 (선후 불확실) | `GET /api/units/M6-2/items HTTP/1.1" 500 162` |
| 14:40:52.319 | app:1521~1523 | **첫 풀 고갈 ERROR** `Connection is not available … 3000ms` 와 `unhandled exception` ERROR(`/api/units/M6-2/items`). ERROR 는 총 98건 = 풀 고갈 49 + unhandled 49. 분당 14:40 2 · 14:41 12 · 14:42 4 · 14:43 20 · 14:44 4 · 14:45 16 · 14:46 8 · 14:47 10 · 14:48 8 · 14:49 14. 마지막 ERROR 14:49:51 `app:4981`. 모든 메시지가 `total=5, active=5, idle=0`, `waiting` 1~7. 위 access 500 과 같은 초 (선후 불확실) | `…timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` |
| 14:41:06 | error:6 (access:18933 = 504) | **첫 `upstream timed out (110)`**. 55건이 모두 `/api/classes/N/report` 이고 access 504 55건과 건수 일치, 14:41:06 ~ 14:51:47. access 의 같은 초 504 와 (선후 불확실) | `upstream timed out (110: Connection timed out) while reading response he` |
| 14:42:01 | error:10 · error:11 · access:18970 | 같은 초에 ① 첫 `upstream server temporarily disabled`(26건, 마지막 14:50:52 `error:158`) ② 첫 `connect() failed (111: Connection refused)`(14건, 마지막 14:50:52 `error:159`) ③ 첫 502(`/api/classes/2/report`). 502 82건의 경로: report 32 · items 13 · units/items 12 · units 12 · distributions 9 외. 세 줄의 선후 (선후 불확실) | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:42:04 | error:12 | **첫 `no live upstreams`**. 68건, 마지막 14:51:02 `error:166` | `no live upstreams while connecting to upstream` |
| 14:42:42 ~ 14:52:09 | access:18998 ~ 19401 | 재배포 POST 5건: 409 4건(14:42:42, 14:50:22, 14:51:52, 14:52:09), 200 1건(14:44:19). 409 는 14:07~14:37 의 `state conflict` 와 같은 종류 | `POST /api/distributions/8/redistribute HTTP/1.1" 200 191` |
| 14:51:37.674 | app:5251 | 마지막 `Connection leak detection triggered` WARN | `Connection leak detection triggered for org.mariadb.jdbc.Connection@…` |
| 14:51:47 | access:19381 · error:168 | **마지막 5xx**(504 `/api/classes/3/report`)와 nginx-error 마지막 줄. 같은 초 (선후 불확실) | `GET /api/classes/3/report HTTP/1.1" 504 167` |
| 14:51:49.148 | app:5267 | 마지막 `Previously reported leaked connection … returned to the pool` INFO | `…was returned to the pool (unleaked)` |
| 14:52:09.185 | app:5269 (access:19401 = 409) | 마감된 과제 재배포 409 `state conflict` WARN. 이후 14:52 ~ 15:02 의 app WARN/ERROR 는 없음 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=4` |

### 2.3 14:52 ~ 15:02 — 종료 후

| 시각 (KST) | 출처 (파일:줄번호) | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:52:52 ~ 15:02:00 | access (grep 집계) | 5xx 0건. 비-2xx 는 404 잡음(`/favicon.ico`, `/apple-touch-icon.png`, `/api/item/12`, `/robots.txt`)뿐 | `GET /apple-touch-icon.png HTTP/1.1" 404` |
| 14:59:15.312 | app:5283 | 보고서 생성 재개 후 빈도: 14:59 2건(`app:5283`, `app:5284`), 15:01 1건(`app:5287`). 분당 ≤2건. 이 구간에 leak WARN 없음 | `built class report for class 3: 3 distributions, 10 submissions` |

### 2.4 로그에서 찾지 못한 것

아래는 "사건이 없었다"가 아니라 **아래 키워드로 찾았을 때 로그에 없었다**는 뜻이다.

- 설정 재적재 · 재시작 흔적: `app.log` 전체에서 `reload|refresh|restart|shutdown|Started|HikariPool-1 - |maximumPoolSize` 0건. `nginx-error.log` 전체에서 `reload|signal|restart|worker process` 0건.
- 14:07 ~ 14:37 에 5xx 0건, app ERROR 0건, slow 쿼리는 14:37 의 1건뿐이다.
- 트래픽 총량 변화: 분당 전체 요청수는 이상 구간 안팎이 같은 범위(34~58건)다. 변한 것은 보고서(`/api/classes/N/report`) 요청 빈도뿐이다.

### 2.5 이 단계의 한계

- `nginx-access.log` 형식에 응답 시간 필드가 없어 요청별 지연은 알 수 없다. 504 가 14:41:06 에 기록됐다는 것만 알 수 있다.
- `app.log` 의 leak 스택 트레이스는 첫 블록만 확인했다. 블록별 비교는 하지 않았다.
- app ERROR 의 마지막이 14:49:51 인데 5xx 는 14:51:47 까지 이어진다. 이 간격의 의미는 해석하지 않았다.
- `mariadb-slow.log` 는 임계값을 몰라 14:38~14:51 에 slow 쿼리가 없었다는 것의 의미를 알 수 없다.

## 3. 가설과 검증

- 3.2~3.5 는 가설과 확인 방법이고, "실행 결과" 열과 3.1 의 판정은 로그로 확인 가능한 항목만 실행한 뒤 채웠다(3.7). 가설은 서로 배타적이지 않다(예: H1 과 H3 이 함께 유지될 수 있다). 판정은 **유지 / 기각 / 판단 불가(로그 부족)** 셋 중 하나이며, 이번 실행에서 기각된 가설은 없다.
- **증상과 원인을 구분한다.** `Connection is not available … 3000ms (total=5, active=5, idle=0)` (2절 `a-connection-pool.md:134`)은 가장 많이 나온 ERROR 지만 "풀이 비었다"는 **결과**다. 아래 가설은 "왜 풀의 연결 5개가 모두 점유됐는가"에 답하려는 것이다.
- 줄번호 `a-connection-pool.md:N` 은 이 문서의 2절 타임라인 줄이다. 코드 · 설정은 별도로 표기한다.
- 사전 확인한 설정: `modern/api/src/main/resources/application.yml:14-18` — `maximum-pool-size: 5`, `connection-timeout: 3000`, `leak-detection-threshold: 10000`. (장애 서버가 이 설정과 같다는 보장은 없다. 아래 H4 에서 확인한다.)

### 3.1 가설 요약

| ID | 계층 | 한 줄 | 판정 | 이유 (한 줄) | 판단하려면 필요한 로그 |
|---|---|---|---|---|---|
| H1 | 애플리케이션 코드 | 학급 보고서 생성이 DB 연결을 오래 쥔 채 일하고, 보고서 요청이 몰리면서 연결이 반환되지 않았다 | **유지** | leak WARN 134건의 연결 획득 스택이 전부 `ReportController.classReport` → `buildClassReport` 트랜잭션 시작이고, 보유 시간은 약 15.5~21.5초다. | (보유 시간이 길어진 *이유*는 로그로 못 가른다) 보고서 요청 한 건 안의 쿼리별 소요 · 건수(JDBC/Hibernate statement 로그 또는 트레이스) |
| H2 | DB · 쿼리 | `submission` 조회가 인덱스 없이 128만 행을 훑어 연결 점유 시간을 늘렸다 | **판단 불가 (로그 부족)** | 첫 연결(14:37:42)과는 맞지만 slow 쿼리는 그 1건뿐이라 이후 leak 133건을 설명하지 못하고, 인덱스는 스키마에 있어(`01-schema.sql:112`) "없다"는 전제도 확인되지 않는다. | 운영 DB 의 `EXPLAIN` · 실제 인덱스 목록 · `long_query_time` 값, 14:37~14:52 의 general log 또는 `performance_schema` 쿼리 이력 |
| H3 | 트래픽 | 보고서 요청 빈도가 늘었고(재시도 포함), 이 증가분이 풀 용량을 넘겼다 | **유지** | 같은 4개 IP 의 보고서 요청이 평소 30분 5건에서 이상 구간 14분 171건으로 늘었고, 증가는 첫 504(14:41:06)보다 앞선다. 증가 *원인*(자동 새로고침 여부)은 못 가린다. | 프런트(`lms.example.com`) 의 보고서 화면 호출 로직 · 클라이언트 측 이벤트 로그, 14:38 에 무엇이 바뀌었는지 알 수 있는 업무 일정 |
| H4 | 배포 · 설정 | 풀 크기 · 타임아웃 · 코드가 장애 전에 바뀌었거나, 풀 5개가 구조적으로 작았다 | **판단 불가 (로그 부족)** | 풀 로그는 14:40:52 이전이 없고(`total=5` 만 확인), git 이력은 장애(09-16) 뒤인 09-22 에 시작해 배포 변경 여부를 알 수 없다. | 09-16 이전 배포 · 설정 변경 이력(CI/CD 로그), 기동 시 Hikari 설정 출력, 풀 사용량 시계열(`hikaricp_connections_active` 등) |

> 가설 사이에서 하나만 남기는 판정이 아니다. H1 · H3 이 함께 유지되는 것은 모순이 아니다. 3.7절에 실행한 명령과 원 출력이 있다.

### 3.2 H1 — 애플리케이션 코드 (보고서 생성의 트랜잭션 · 연결 점유)

| 항목 | 내용 | 실행 결과 (로그 4개 기준) |
|---|---|---|
| 1. 가설 | `GET /api/classes/{id}/report` 가 호출하는 `ReportService.buildClassReport` 가 한 트랜잭션 안에서 배포 목록을 읽고 배포마다 제출물을 다시 조회하느라 연결을 10초 넘게 쥐고 있고, 이런 요청이 겹쳐 5개 연결이 모두 점유됐다. |  |
| 2. 지지 근거 | ① leak WARN 은 연결을 `leak-detection-threshold`(10s) 이상 쥘 때만 나온다. 첫 WARN 14:37:52 (`a-connection-pool.md:127`), 총 134건. ② 14:38:01 class 3 보고서 응답(`access:18801`) 직후 "returned to the pool (unleaked)" (`:128`) — 보고서 요청이 끝나면서 연결이 돌아온 것처럼 보인다(선후 불확실). ③ 이상 구간에 보고서 요청이 분당 10~19건으로 늘었고(`:131`), 504 55건이 모두 `/api/classes/N/report` (`:135`). ④ 코드: `ReportService.java:41` `@Transactional`(readOnly 아님), `:46` 배포 목록 조회, `:54` 배포마다 `findByDistributionId…` 호출. |  |
| 3. 반증 조건 | ⓐ leak WARN 스택 트레이스의 애플리케이션 프레임이 `ReportService` 가 아닌 곳(예: 다른 컨트롤러 · 스케줄러)에 몰려 있다. ⓑ leak WARN 의 시각이 보고서 요청 시각과 맞지 않는다(보고서 요청이 없는 분에도 WARN 이 있다). ⓒ `buildClassReport` 가 호출하는 쿼리가 모두 1초 안에 끝나고 트랜잭션도 짧다는 것이 확인된다. | ⓐ **해당 없음** — 프레임이 전부 `ReportController.classReport`(134/134). ⓑ **해당 없음** — leak WARN 분(14:37~14:51)이 보고서 요청 분(14:38~14:51)과 겹치고, 14:37 의 1건은 14:38:01 에 끝난 요청(14:37:42 시작 추정)이다. ⓒ **미확인** — 로그에는 쿼리 소요가 없고 코드 확인은 로그 밖이다. |
| 4. 확인 방법 | `grep -n -A12 'Connection leak detection' incident-logs/a-connection-pool/app.log \| grep -o 'com\.example\.[A-Za-z.]*' \| sort \| uniq -c \| sort -rn` (스택 프레임별 집계; 134 블록 전체) / `modern/api/.../assignment/ReportService.java:41-75` 를 열어 연결 보유 구간 · 반복문 안 쿼리 수 확인 / 분당 `leak WARN` 건수와 분당 `GET /api/classes/N/report` 건수를 나란히 비교(`grep -o` + `uniq -c`) | 3.7절 H1-①~③. 프레임 집계 `ReportController.classReport` 134 · `ReportService` 186. 분당 leak 와 report 건수가 같은 구간에 동시에 올라감(14:40 leak 14 / report 10, 14:43 14 / 19). 평소(14:05~14:37)에는 report 6건에 leak WARN 0건. |

### 3.3 H2 — DB · 쿼리 (제출물 조회 풀스캔)

| 항목 | 내용 | 실행 결과 (로그 4개 기준) |
|---|---|---|
| 1. 가설 | `submission` 테이블에서 `distribution_id` 로 조회할 때 쓸 인덱스가 없거나 타지 않아, 쿼리 한 번이 수 초~10초 걸리고 그동안 연결이 점유된다. |  |
| 2. 지지 근거 | ① slow 쿼리 `submission where distribution_id=5`: 9.81초, 검사 1,284,310행, 반환 4행(`:126`, `:29`). 검사 행 대비 반환 행이 극단적으로 적다. ② 시작 14:37:42 + 10초 = 14:37:52 가 첫 leak WARN 시각과 같은 초(`:127`; 선후 불확실, 시작 시각은 `:22` 의 변환 규칙 기준). ③ 14:38:29 `distribution 5` 재배포(`:130`)가 같은 ID 를 가진다(관련 여부 미판단). |  |
| 3. 반증 조건 | ⓐ `EXPLAIN` 에서 `submission.distribution_id` 인덱스를 타고 검사 행이 수십 행 수준이다. ⓑ slow 임계값(`long_query_time`)이 1초 이하인데 14:38~14:51 에 slow 쿼리가 더 없다 — 장애 중 쿼리는 느리지 않았다는 뜻이므로 연결 점유의 원인이 쿼리 속도가 아니다. ⓒ 같은 쿼리를 장애 후에 실행해도 빠르다면서 14:37 에만 느렸던 이유(락 · 캐시 · 다른 세션)가 따로 확인된다. | ⓐ **일부 확인** — `db/mariadb/init/01-schema.sql:112` 에 `idx_submission_distribution` 이 있다. 그런데 `Rows_examined=1,284,310` 은 이 인덱스를 탔다면 어색하다(실제 운영 DB · 실행 계획은 로그에 없음). ⓑ **판단 불가** — slow 3건의 최소 `Query_time` 이 2.04초라 임계값이 ≤2초로 *추정*될 뿐 값은 모른다. 그 추정이 맞다면 14:38~14:51 에 2초 넘는 쿼리는 없었다. ⓒ 확인 불가(장애 후 실행 기록 없음). |
| 4. 확인 방법 | `grep -n -i 'submission' -A12 db/mariadb/*.sql` 로 인덱스 정의 확인 / 개발 DB 에서 `EXPLAIN SELECT … FROM submission WHERE distribution_id = 5;` / `SHOW VARIABLES LIKE 'long_query_time';` (운영 DB 직접 접속은 하지 않는다 — 운영 담당에게 값 요청) / `grep -n 'Query_time\|Lock_time' incident-logs/a-connection-pool/mariadb-slow.log` 로 3건 비교 | 3.7절 H2-①②. slow 3건은 `Query_time` 2.32 · 2.04 · 9.81초, 검사 행 41,822 · 5 · 1,284,310. 스키마에 인덱스 정의 있음. `EXPLAIN` · `long_query_time` 는 운영 DB 접속이 필요해 실행하지 않았다. |

### 3.4 H3 — 트래픽 (보고서 요청 증가 · 재시도 증폭)

| 항목 | 내용 | 실행 결과 (로그 4개 기준) |
|---|---|---|
| 1. 가설 | 특정 클라이언트(화면 폴링 · 자동 새로고침 · 504 후 재시도)가 보고서를 14:38 부터 자주 호출해, 총 요청량은 같은데 무거운 요청의 비중이 늘어 풀 용량을 넘겼다. |  |
| 2. 지지 근거 | ① 보고서 요청이 14:07~14:37 분당 ≤1건 → 14:38 3건 → 14:40 이후 10~19건(`:129`, `:131`). ② 전체 요청수는 분당 34~58건으로 같은 범위(`:131`, `:157`) — 변한 것은 보고서 비중뿐. ③ 504 가 보고서에만 55건(`:135`). 504 후 재요청이 있었다면 증폭 가능(미확인). ④ 보고서 요청이 정상화(분당 ≤2건)된 뒤 leak WARN 이 사라짐(`:149`). |  |
| 3. 반증 조건 | ⓐ 14:38~14:51 보고서 요청이 소수 클라이언트가 아니라 평소와 같은 클라이언트 분포 · 같은 간격이다(요청량 증가가 아니라 요청 하나의 처리 시간이 길어진 것). ⓑ 504 직후 같은 클라이언트의 재요청이 없다. ⓒ 평소(13:00~13:30)에도 보고서 요청이 분당 10건 이상인 시간대가 있는데 그때는 장애가 없었다. | ⓐ **해당 없음(반증 안 됨)** — 보고서 호출 IP 는 하루 종일 같은 4개이지만 호출 수가 5건 → 171건으로 늘었다. 평소와 같은 간격이 아니다. ⓑ **해당 없음** — 504 55건 중 53건이 같은 IP · 같은 URL 로 재요청됐고 52건이 60초 안(간격 중앙값 19초)이다. ⓒ **해당 없음** — 평소 시간당 최대 11건(10시 · 12시)이라 분당 10건 이상은 이 장애 구간뿐이다. |
| 4. 확인 방법 | `awk '/\/api\/classes\/[0-9]+\/report/ {print $1}' incident-logs/a-connection-pool/nginx-access.log \| sort \| uniq -c \| sort -rn \| head` (클라이언트 IP별 호출 수; 시간대는 `grep '16/Sep/2026:14:'` 로 좁혀 반복) / 같은 IP · URL 의 504 직후 재요청 간격 확인(`grep -n '10.20.3.12' …`) / 13:00~13:30 · 14:07~14:37 의 분당 보고서 호출 수와 비교 | 3.7절 H3-①~③. 증가는 첫 504(14:41:06) 이전에 시작했다(14:38~14:41:05 에 18건). 즉 재시도는 증폭 요인일 수 있어도 증가의 시작 원인은 아니다. User-Agent 는 브라우저 2종이라 자동화 도구 호출 흔적은 없다. |

### 3.5 H4 — 배포 · 설정 (풀 설정 · 코드 변경)

| 항목 | 내용 | 실행 결과 (로그 4개 기준) |
|---|---|---|
| 1. 가설 | 장애 전에 풀 크기 · 타임아웃 · 보고서 코드가 바뀌었거나(배포 · 설정 변경), 5개 풀이 원래부터 이 부하에 구조적으로 작았다. 둘 중 하나가 장애 시점에 드러났다. |  |
| 2. 지지 근거 | ① 모든 풀 고갈 ERROR 가 `total=5, active=5, idle=0` (`:134`) — 5개가 전부 점유됐다. ② 이 장애가 하루 중 14:37 에만 나타났다(`:39`~`:42`의 평소 수준). **근거는 약하다**: 설정 재적재 · 재시작 흔적은 로그에서 찾지 못했다(`:155`). 이 가설은 "로그에 흔적이 없다 = 변경이 없었다"가 아님을 확인하려는 것이다. |  |
| 3. 반증 조건 | ⓐ 장애 서버의 실제 설정이 `application.yml` 과 같고, 장애 전날까지 같은 값으로 운영됐다(배포 이력에 변경 없음). ⓑ 14:07~14:37 에 풀 사용량(active)이 5 에 가깝게 오른 적이 없다 — 구조적 부족이 아니다. ⓒ `ReportService` · `application.yml` 의 최근 커밋이 장애일 이전에 없다. | ⓐ **일부 확인** — 풀 크기는 로그의 `total=5` 가 `application.yml:14` 와 같다(147건 모두 동일). 배포 이력은 로그 · 저장소에 없다. ⓑ **판단 불가** — 풀 상태 로그의 첫 시각이 14:40:52 라 14:37 이전 사용량을 알 수 없다. ⓒ **판단 불가** — 두 파일의 커밋이 `1260542`(2026-09-22) 하나뿐이다. 장애일(09-16) 이후 만든 저장소여서 장애 전 변경 이력이 될 수 없다. |
| 4. 확인 방법 | `git log --since=2026-09-01 --format='%h %ad %s' --date=iso -- modern/api/src/main/resources/application.yml modern/api/src/main/java/com/example/assignment/ReportService.java` / 배포 시스템 이력에서 09-16 이전 배포 시각(저장소 밖; 운영 담당에게 요청) / `grep -n 'active=' incident-logs/a-connection-pool/app.log` 로 14:37 이전 풀 상태 로그 유무 확인(없으면 이전 사용량은 알 수 없음) | 3.7절 H4-①②. `active=` 147건 전부 `total=5, active=5, idle=0`. 14:37 이전의 풀 관련 WARN/ERROR 0건(= 풀 상태가 평소에 기록되지 않아 사용량 정보 없음). |

### 3.6 가설 사이에서 가르는 관찰 (판정이 아니라 다음 확인의 순서)

- H1 ↔ H2: leak WARN 스택(3.2 ⓐ)과 `EXPLAIN`(3.3 ⓐ)을 둘 다 보면 "코드가 연결을 오래 쥔다"와 "쿼리가 오래 걸린다"를 구분할 수 있다. 둘이 같이 참이면 H1 은 보유 방식, H2 는 보유 시간의 문제다.
- H1 ↔ H3: 보고서 호출 클라이언트 분포(3.4 4번)가 평소와 같으면 요청 수 증가보다 요청 1건의 처리 시간이 원인에 가깝다.
- 모든 가설 공통 한계: `nginx-access.log` 에 응답 시간이 없고(`:161`), slow 임계값을 모르며(`:164`), `app.log` leak 스택을 첫 블록만 봤다(`:162`). 위 확인 방법은 이 빈칸을 채우는 쪽으로 골랐다.

### 3.7 실행한 명령과 원 출력

경로는 `incident-logs/a-connection-pool/` 기준이다. `app.log` · `nginx-access.log` · `mariadb-slow.log` 는 읽기만 했고 수정하지 않았다. 아래 파이썬은 `python3 -I` 로 실행했다. 운영 DB 에는 접속하지 않았다.

**H1-①** leak 스택 프레임 집계

```
$ grep -c 'Connection leak detection' app.log
134
$ grep -A15 'Connection leak detection' app.log | grep -o 'com\.example\.[A-Za-z0-9_.]*' | sort | uniq -c | sort -rn
    186 com.example.assignment.ReportService
    134 com.example.assignment.ReportController.classReport
```

첫 블록(`app.log:1243`)의 스택은 `JpaTransactionManager.doBegin` → `ReportService$$SpringCGLIB$$0.buildClassReport` → `ReportController.classReport(ReportController.java:22)` 다. leak 스택은 연결을 *얻은* 지점이므로 "보고서 트랜잭션이 연결을 잡았다"까지만 말해 준다.

**H1-②** 분당 leak WARN 과 분당 보고서 요청 (14시대)

```
leak  : 14:37=1 14:38=2 14:39=3 14:40=14 14:41=13 14:42=10 14:43=14 14:44=11 14:45=9 14:46=12 14:47=9 14:48=10 14:49=12 14:50=11 14:51=3  (합계 134, 전부 14시대)
report: 14:05=1 14:10=1 14:13=1 14:15=1 14:17=1 14:18=1 | 14:38=3 14:39=3 14:40=10 14:41=16 14:42=15 14:43=19 14:44=14 14:45=15 14:46=19 14:47=12 14:48=14 14:49=16 14:50=10 14:51=5 | 14:59=2
시간대별 report: 07시=2 08시=5 09시=2 10시=11 11시=7 12시=11 13시=8 14시=179 15시=4
```

**H1-③** 연결 보유 시간 추정(연결 객체별 leak WARN → "returned to the pool" 간격)

```
matched 134  unmatched leak 0
간격(초) min 5.538  median 8.932  p90 11.049  max 11.508
```

leak WARN 은 10초(`application.yml:18`) 보유 후에 찍히므로 보유 시간 ≈ 10초 + 간격 = **약 15.5~21.5초(중앙값 약 19초)**. 풀 5개 · 보고서 요청 분당 10~19건(위 H1-②)이면 동시에 쥐는 연결은 대략 `요청률 × 보유 시간` = 3~6개로, 풀 크기 5 와 같은 범위다(추정, 로그가 직접 보여 주는 값은 아님).

**H2-①** slow 로그 3건과 쿼리문

```
Query_time 2.318446  Rows_sent 7  Rows_examined 41822     SET timestamp=1789517647  item join unit
Query_time 2.041903  Rows_sent 5  Rows_examined 5         SET timestamp=1789524160  unit order by code (Lock_time 0.912004)
Query_time 9.812417  Rows_sent 4  Rows_examined 1284310   SET timestamp=1789537062
  select … from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id;
```

**H2-②** 스키마의 인덱스

```
$ grep -rn -i 'index\|key' db/mariadb/ | grep -i submission
db/mariadb/init/01-schema.sql:112:  KEY idx_submission_distribution (distribution_id),
db/mariadb/init/01-schema.sql:113:  KEY idx_submission_student (student_id),
```

**H3-①** 보고서 요청 IP (이상 구간 14:38~14:51 / 평소 14:07~14:37)

```
이상 구간: 10.20.3.11=67  10.20.3.12=40  10.20.3.17=35  10.20.3.23=29   (합계 171)
평소    : 10.20.3.12=3    10.20.3.17=1    10.20.3.11=1                   (합계 5)
하루 전체: 10.20.3.11=87  10.20.3.12=57  10.20.3.17=56  10.20.3.23=29
classId : /api/classes/1/report=64  /api/classes/2/report=32  /api/classes/3/report=75   (합계 171, 요청 경로 필드 `$7` 만 집계)
```

> 정정: 처음에 `grep -o 'classes/[0-9]*'` 로 센 값(128 · 64 · 150)은 Referer(`…/teacher/classes/N/report`)까지 한 줄에서 두 번 잡혀 정확히 2배였다. 위 값이 맞다. 3개 학급에 고르게 분산되고(64 · 32 · 75) 학급 3 이 가장 많다.

**H3-②** 504 이후 같은 IP · 같은 URL 재요청

```
504 total 55
retry within log 53  no retry 2
gap min 1.0  median 19.0  max 448.0  <=60s: 52
```

**H3-③** 증가 시작 시점과 클라이언트 종류

```
14:38~14:41:05 (첫 504 이전) 보고서 요청: 18건
첫 요청: 10.20.3.12 14:38:01, :27, :57, 14:39:24, :55 (class 3, 27~33초 간격)
User-Agent(이상 구간 보고서): Firefox/130 Windows=102, Safari/17.6 macOS=69
Referer 예: https://lms.example.com/teacher/classes/3/report
```

**H4-①** 풀 상태 로그

```
$ grep -c 'active=' app.log                      → 147
$ grep -o 'total=[0-9]*, active=[0-9]*, idle=[0-9]*' app.log | sort | uniq -c
    147 total=5, active=5, idle=0
첫 active= 시각: 2026-09-16T14:40:52.319+09:00
14:37 이전 풀 관련 WARN/ERROR: 0건
```

**H4-②** 설정 · 코드 변경 이력

```
$ git log --format='%h %ad %s' --date=iso -- modern/api/src/main/resources/application.yml modern/api/src/main/java/com/example/assignment/ReportService.java
1260542 2026-09-22 10:40:42 +0900 실습 저장소 초기 구성: …
```

실행하지 못한 확인 방법: `EXPLAIN` · `SHOW VARIABLES LIKE 'long_query_time'`(운영 DB 접속 필요), 배포 시스템 이력(저장소 밖), `ReportService.java:41-75` 정독(로그가 아니라 코드).

### 3.8 판정

코드 · 설정은 읽기만 했다(벤치마크 · 테스트 실행 없음). 설정값은 `modern/api/src/main/resources/application.yml` 에서 읽은 값만 썼다. 경로 약어: `RS` = `modern/api/src/main/java/com/example/assignment/ReportService.java`, `YML` = `modern/api/src/main/resources/application.yml`.

#### 3.8.1 코드 · 설정 대조

| ID | 로그의 단서 | 읽은 코드 · 설정 | 판정 |
|---|---|---|---|
| H1 | leak 스택 `ReportService$$SpringCGLIB$$0.buildClassReport` ← `ReportController.classReport(ReportController.java:22)` (`app.log:1243~`) | `ReportController.java:20-23` 이 `buildClassReport` 를 그대로 호출. `RS:41` `@Transactional`(readOnly 아님, 메서드 전체가 하나의 트랜잭션). `RS:43-63` DB 조회(`:43` 학급, `:46` 배포 목록, `:53-54` 배포마다 제출물). **`RS:69-71` 주석 "DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다" 와 `sign()` 호출**, `RS:88-91` SHA-256 을 `SIGNATURE_ROUNDS = 200_000`(`RS:23`)회 반복. `YML:22` `open-in-view: false` 라 연결은 트랜잭션 경계(= 메서드 전체)에서 반환된다. | **지지한다** |
| H2 | slow 쿼리 `select … from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id` (`mariadb-slow.log:21`) | `SubmissionRepository.java:8` `findByDistributionIdOrderByStudentIdAsc` 가 위 SQL 모양과 일치하고, 호출은 `RS:53-54` 한 곳뿐이다. 스키마 `db/mariadb/init/01-schema.sql:112` 에 `KEY idx_submission_distribution (distribution_id)`, `:113` 에 `idx_submission_student (student_id)` 가 있다. `Submission.java:25` `@JoinColumn(name = "distribution_id")` 도 같은 컬럼이다. | **반박한다** (전제 "인덱스 없음" 에 한해. 아래 참고) |
| H3 | 보고서 요청 5건 → 171건(`3.7` H3-①), 504 후 재요청 53/55 | `ReportController.java` · `RS` 에 캐시 · 호출 제한 · 재시도 관련 코드가 없다(`grep -i "Cacheable\|RateLimit\|bucket4j\|@Async\|Scheduled\|retry" modern/api/src/main` → 0건). nginx 설정 파일은 저장소에 없다. 클라이언트(`lms.example.com`) 코드도 저장소에 없다. | **관련 코드를 찾지 못했다** (증가의 *원인*) / 코드에 호출을 막는 장치가 없다는 점은 H3 이 만든 부하를 그대로 통과시킨다는 뜻이라 증폭 조건으로는 **지지** |
| H4 | `total=5` (`app.log` 147건), 설정 키 `maximumPoolSize` · `connection-timeout` · `leak-detection-threshold` | `YML:14` `maximum-pool-size: 5`, `YML:15` `connection-timeout: 3000`, `YML:18` `leak-detection-threshold: 10000`. `YML:11-12` 주석: "운영 값과 같게 맞춘다. 풀이 작고 대기 시간이 짧아, 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다." 로그의 `total=5` 와 3000ms 가 이 값과 같다. 이 값이 장애 전에 바뀌었다는 흔적은 코드 · 설정에 없다. | **관련 코드를 찾지 못했다** (설정 *변경* 쪽). 풀 크기 5 는 원인이 아니라 고갈을 빨리 드러낸 **조건** (`YML:11-12`) |

**H2 를 "반박" 이라고 쓴 범위.** 쿼리 모양과 호출 위치는 코드에서 확인했다. 인덱스는 스키마 정의상 있으므로 "인덱스가 없어서 풀스캔" 은 코드로 지지되지 않는다. 다만 `Rows_examined = 1,284,310` 이 인덱스가 있는데도 나온 이유(실행 계획 · 운영 DB 의 실제 인덱스 · 통계)는 코드로 알 수 없고 확인하지 못했다. 그래서 "slow 쿼리 자체가 문제 없었다" 는 아니다.

#### 3.8.2 채택하는 가설

**채택: H1 — `ReportService.buildClassReport` 가 `@Transactional` 안에서 DB 작업이 끝난 뒤에도 `sign()` 을 돌며 연결을 쥔다. 이 보유가 보고서 요청이 몰릴 때(H3) 풀 5개(`YML:14`)를 고갈시켰다.**

- 로그와 코드가 같은 지점을 가리킨다: leak 스택 134/134 이 `ReportController.classReport` → `buildClassReport` 의 트랜잭션 시작이고(`3.7` H1-①), 코드가 연결을 쥔 채 `sign()` 을 호출한다(`RS:41`, `RS:71`). 코드 주석(`RS:69-70`)이 이미 같은 문제를 TODO 로 적어 두었다.
- 보유 시간(약 15.5~21.5초, `3.7` H1-③)이 `leak-detection-threshold` 10초(`YML:18`)를 넘어 WARN 이 나왔고, 요청이 늘 때(14:38~) WARN 도 같이 늘었다(`3.7` H1-②).
- 고갈의 직접 증상(`Connection is not available … 3000ms`)이 보고서가 아닌 `/api/units/**` 요청에서 났다(`a-connection-pool.md:134`, 첫 500 이 `/api/units/M6-2/items`). 보고서 요청이 연결을 쥐고 있어 다른 요청이 3초(`YML:15`) 안에 연결을 얻지 못한 것으로 읽힌다.

**확신 수준: 중간.**

- 높은 부분: "보고서 트랜잭션이 연결을 쥐는 구조" 는 코드 · 로그 · 설정이 모두 같은 곳을 가리키고, 반증 조건(3.2 ⓐ ⓑ)에 걸리지 않았다.
- 낮추는 부분 ① **보유 시간 15~21초가 코드 읽기만으로 설명되지 않는다.** 코드상 긴 구간은 SHA-256 20만 회(`RS:88`)와 배포마다의 쿼리(`RS:53-54`)다. 그 소요 시간은 실행하지 않아 알 수 없다. 같은 코드가 평소 14:05~14:37 의 보고서 6건에서는 leak WARN 없이(10초 미만) 끝났다는 점은 로그로 확인했으므로, 이상 구간에서 같은 코드가 느려진 *이유*(동시 요청의 CPU 경쟁, 쿼리 지연 등)가 비어 있다. ② 14:37 첫 건에는 9.8초 slow 쿼리(`mariadb-slow.log:21`, 코드 경로 `RS:54`)가 같은 요청 안에 있어 H2 가 일부 기여했을 수 있다. 이후 133건은 slow 로그가 없어 H2 로 설명되지 않는다. ③ H3 의 증가 원인이 코드 밖이라 미확인이다.

#### 3.8.3 기각한 가설과 사유

| ID | 기각(또는 격하) 사유 |
|---|---|
| H2 | 원인으로서 기각이 아니라 **기여 요인으로 격하**. ① 스키마에 `distribution_id` 인덱스가 있다(`01-schema.sql:112`). ② slow 쿼리는 이상 구간에 1건뿐이고 leak 은 134건이라 나머지 133건의 점유를 설명하지 못한다. ③ 첫 1건(14:37:42 시작, 9.81초)은 같은 보고서 요청 안에서 `RS:54` 로 실행됐으므로, 그 요청의 보유 시간을 늘렸을 가능성은 남는다. `EXPLAIN` · `long_query_time` 은 확인하지 못했다. |
| H3 | 원인으로서 기각이 아니라 **트리거 · 증폭 조건으로 격하**. 요청 증가(5 → 171건)는 사실이지만, 평소 요청량이어도 보고서 한 건이 연결을 오래 쥐는 구조(H1)가 없으면 풀이 고갈되지 않는다는 점이 코드에서 확인된다(`RS:69-71`). 증가의 *시작 원인* 은 코드에 없어 판단하지 못했고, 호출 제한 장치가 코드에 없어(`grep` 0건) 증가가 그대로 통과했다. |
| H4 | 설정 값은 로그와 일치하고(`YML:14` · `:15` · `:18`), 변경 흔적은 로그 · 코드 · git 어디에도 없다. 풀이 작은 것은 의도된 설정이며 주석이 "트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다" 고 적었다(`YML:11-12`). 따라서 풀 크기는 원인이 아니라 H1 을 드러낸 **조건** 이다. 단, 배포 이력은 저장소 밖이라 "변경 없음" 을 확인한 것은 아니다. |

#### 3.8.4 남은 확인 (코드 · 설정만으로는 닫히지 않는 것)

- 이상 구간에서 `buildClassReport` 가 느려진 이유: 요청별 쿼리 · 서명 소요를 남기는 로그 또는 트레이스가 필요하다.
- `Rows_examined = 1,284,310` 의 실행 계획: 운영 DB 의 `EXPLAIN` 과 실제 인덱스 목록이 필요하다.
- 14:38 에 보고서 호출이 늘어난 이유: 프런트(`lms.example.com`) 쪽 호출 로직과 업무 일정이 필요하다.
- 이 장애가 수정 대상인지, 어떻게 고칠지(예: 서명을 트랜잭션 밖으로 이동)는 이 문서의 범위가 아니다. 코드는 수정하지 않았다.
