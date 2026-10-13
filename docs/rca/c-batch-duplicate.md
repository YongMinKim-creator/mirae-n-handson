# c-batch-duplicate 장애 원인 분석 (RCA)

## 요약

- **언제**: 업무일 2026-09-15 분 `load_datamart`. 2026-09-16 (KST) 02:00:03 첫 실행 시작, 02:58:42 수동 재실행으로 중복 실행 시작, 03:15:31 두 번째 SUCCESS, 07:00:04 dq-check 경보. 중복 구간은 02:58:42 ~ 03:12:41(약 14분).
- **무엇**: `dm.daily_submission_fact` 에 09-15 분이 `mode=append` 로 두 번 적재됐다. 건수가 184,532 × 2 = 369,064 건으로 평소(직전 7일 평균 184,617.4)의 +99.9% 다. 후속 `build_report_cache` 가 이 2배 입력으로 돌았다.
- **원인(직접)**: 첫 실행이 평소보다 느려 SLA(02:45)를 넘기자 운영자가 "멈춘 것으로 보고" `force=true` 로 재실행을 요청했고, 러너가 `--force` 를 무시한다고 기록하면서도 lock 을 우회(`lock=bypassed`)해 두 번째 실행을 시작했다. 두 실행이 같은 대상에 append 했다.
- **원인(배경)**: (1) 적재가 `append` 라 같은 날짜를 두 번 넣어도 막는 키 · 멱등성 장치가 로그에서 보이지 않는다. (2) 첫 실행이 느려진 이유는 확인하지 못했다. (3) 진행 중인 실행을 "멈춤"과 구분할 근거(진행률)가 알림에 없었다.
- **확신**: **중간.** 중복 실행 → 이중 적재 인과는 로그 · CSV 가 일치(높음). 러너 코드가 저장소에 없어 lock 우회 로직 · 중복 방지 장치 유무는 대조하지 못했고, 첫 실행이 느렸던 이유도 알 수 없다(7절).
- **상태**: 코드 · 설정 · 데이터는 수정하지 않았다. 중복 행은 **아직 정리됐다는 기록이 없다**. 제안은 4~6절.

## 1. 수집 범위

- 대상: `incident-logs/c-batch-duplicate/` 의 `batch-job.log`(115줄), `datamart-counts.csv`(24행 + 머리글). 같은 사건이 `pipeline-samples/batch-logs/2026-09-15.log` 에도 있고 내용이 같은 줄은 일치한다(대조 확인).
- 시각 형식: 모든 줄이 `2026-09-16 02:58:41.077 +0900` 형식으로 **KST(`+0900` 명시)** 다. CSV 의 `loaded_at` 도 `+09:00` 이다. 변환이 필요 없다.
- 파일명 · `date=` 는 업무일이고 실제 기록은 다음 날 새벽이다.
- 기간: 09-09 01:30 ~ 09-16 07:00. 평소 구간(09-09 ~ 09-15 새벽)은 WARN 이 하루 1건(검증 건너뜀, 매 실행 동일)뿐이다.
- 이상 구간 확정: 09-16 01:30(`extract_submissions` 시작) ~ 07:00(dq-check). 시작 앞 30분 · 끝 뒤 10분 여유는 로그가 이 범위에서 끝나 별도로 잡지 않았다.
- 집계 방법: `wc -l`, `grep -n`, `sed -n`, `awk`. 파일을 통째로 읽지 않았다.

## 2. 타임라인 (KST, 09-16)

| 시각 | 사건 | 근거 |
|---|---|---|
| 01:30:02 | `extract_submissions` 시작(cron) | `batch-job.log:85` |
| 01:44:07 | WARN `source read slow: DB-1 replica lag 412s`, rows=19204 | `batch-job.log:86` |
| 01:57:50 | `extract_submissions` SUCCESS rows=41573 elapsed=1668s | `batch-job.log:87` |
| 02:00:03 | `load_datamart` 1차 `ldm-…-edf5` START(cron), `lock=acquired` pid=46735 | `batch-job.log:88` |
| 02:00:04 | 읽기 `mode=append target=dm.daily_submission_fact` | `batch-job.log:89` |
| 02:00:06 | WARN 검증 건너뜀 1건(`class_id is NULL`) — 평소 실행에도 같은 줄 있음 | `batch-job.log:90` |
| 02:09:39 | 진행률 25% (rows=46,133) | `batch-job.log:91` |
| 02:21:21 | 38% | `batch-job.log:92` |
| 02:33:57 | 51% | `batch-job.log:93` |
| 02:40:03 | WARN `elapsed 40m exceeds p95 17m` | `batch-job.log:94` |
| 02:45:00 | SLA 모니터 `NOT_FINISHED`, `last_status=RUNNING`, 당직에 알림 | `batch-job.log:95` |
| 02:47:15 | 1차 64% | `batch-job.log:96` |
| 02:58:41 | 재실행 요청 `force=true`, reason "SLA 알림 — 멈춘 것으로 보고 재실행" (요청자 `***`, OPS-1) | `batch-job.log:97` |
| 02:58:41 | 같은 요청의 API 호출 `body={"date":"2026-09-15","force":true}` → `201 Created`(헤더에 Bearer 토큰: `[TOKEN]`) | `batch-job.log:98` |
| 02:58:42.465 | WARN 2차 `ldm-…-8963`: `lock=held_by …edf5 … action=ignored reason="--force"` | `batch-job.log:99` |
| 02:58:42.477 | 2차 START `trigger=manual`, **`lock=bypassed`** pid=47948 | `batch-job.log:100` |
| 02:58:43 | 2차 읽기 `mode=append`, 같은 target | `batch-job.log:101` |
| 02:58:51 | 1차 77% (진행 계속) | `batch-job.log:103` |
| 03:02:48 / 03:07:00 / 03:11:18 | 2차 25% / 50% / 75% (25% 가 약 4분 간격 — 평소 속도) | `batch-job.log:104`, `:106`, `:107` |
| 03:06:33 | 1차 90% | `batch-job.log:105` |
| 03:12:41 | 1차 SUCCESS rows=184532 elapsed=4357s, lock released | `batch-job.log:108`, `:109` |
| 03:15:31 | 2차 SUCCESS rows=184532 elapsed=1008s, WARN `lock=release_skipped … not owner (bypassed)` | `batch-job.log:110`, `:111` |
| 03:30:01 | `build_report_cache` START(cron) | `batch-job.log:112` |
| 03:30:04 | WARN `input_rows=369064 … differs from extract_submissions rows=41573 x avg fan-out` | `batch-job.log:113` |
| 03:33:42 | `build_report_cache` SUCCESS rows=1271 input_rows=369064 (평소 1,288 입력 186,058) | `batch-job.log:114` |
| 07:00:04 | dq-check WARN `rows=369064 avg_7d=184617 deviation=+99%`, 당직에 알림 | `batch-job.log:115` |

주: `batch-job.log` 줄번호는 모두 `grep -n` 으로 확인했다. 같은 내용이 `pipeline-samples/batch-logs/2026-09-15.log:30~47` 에도 있다.

- 02:58:42.465(ignored)와 .477(bypassed)는 12ms 차이로 같은 호스트 · 같은 로그이므로 선후는 기록 순서 그대로다.

### 찾지 못한 것

- 러너 · 스케줄러의 재시작, 설정 변경, 배포 흔적: 없음(`restart` · `deploy` · `config` 검색 결과 없음).
- 1차 실행이 느려진 이유를 직접 말해 주는 줄(DB 대기, lock 대기, 오류): 없음. 1차는 02:00:04 부터 계속 정상 진행률만 찍었다.
- 중복 행을 정리한 기록: 없음.

## 3. 가설과 판정

| # | 가설 | 지지 근거 | 반증 조건 | 판정 |
|---|---|---|---|---|
| H1 | 강제 재실행이 lock 을 우회해 동시 실행을 만들었고, 그 결과 이중 적재 | 2차 START 가 `lock=bypassed`(1차 lock 보유 중 `since=02:00:03`), 두 실행 모두 `mode=append` · 같은 target | 2차가 1차 SUCCESS 이후에 시작했거나, 다른 target 에 적재했다면 기각 | **채택(직접 원인).** 2차 시작 02:58:42 < 1차 종료 03:12:41. target 동일 |
| H2 | 1차 실행이 정말로 멈춰 있었다(운영자 판단이 맞았다) | SLA `NOT_FINISHED`, `exceeds p95` | 진행률이 계속 올라가면 기각 | **기각.** 02:09 → 02:58 사이 25 → 38 → 51 → 64 → 77% 로 계속 증가, 03:12 정상 종료 |
| H3 | 재실행해도 중복이 생기지 않을 보호 장치(멱등 키 · 날짜 삭제 후 적재)가 없다 | 1차 · 2차 모두 `append`, 최종 369,064 건 = 184,532 × 2 | 테이블에 유니크 키 / 사전 삭제가 있었다면 건수가 2배가 되지 않았을 것 | **기여 요인으로 채택(배경 원인).** 단 테이블 DDL · 러너 코드를 보지 못해 "장치가 없다"는 단정은 못 함 |
| H4 | 1차가 느려진 건 데이터 양 때문(입력이 많았다) | — | 입력 건수가 평소와 비슷하면 기각 | **기각.** 출력 184,532 건은 평소(182,615 ~ 186,319)와 같은 범위, 2차는 같은 건수를 1008초에 처리 |
| H5 | 1차가 느려진 건 외부 요인(DB 부하 · 앞 job 의 replica lag 여파) | 앞 job 에 `replica lag 412s` WARN | 앞 job 이 끝난 뒤(01:57:50)라 시간이 겹치지 않고, 2차는 같은 시각대에 평소 속도로 돎 | **판단 불가.** 앞 job 과 겹치지 않고 2차는 빨랐음(1차에만 느림이 있었다). 1차 시작 직후(25% 까지 9.6분)부터 느렸다는 점만 확인됨 |

판정 계산(`awk`): 직전 7일 `load_datamart` 건수 평균 184,617.4, 09-15 CSV 369,064 → +99.9%, 369,064 / 2 = 184,532 로 1차 · 2차 SUCCESS rows 와 일치. 직전 7회 elapsed 중앙값 876초 → 1차 4,357초는 약 5.0배, 2차 1,008초는 약 1.15배(`pipeline-samples` 점검 스크립트는 중앙값을 923초로 잡아 4.7배로 표기하는데, 기준 집합이 달라 생긴 차이다).

## 4. 원인

인과 순서:

1. (배경, 미확인) 09-16 02:00 1차 `load_datamart` 가 평소보다 4~5배 느리게 진행됨 — 이유 불명.
2. 02:45 SLA 알림(`NOT_FINISHED`)이 "끝나지 않음"만 알리고 진행률은 알리지 않았다. 운영자는 이를 "멈춤"으로 읽음(요청 사유 문구).
3. 02:58 운영자가 `force=true` 로 재실행을 요청. API 는 `201 Created`.
4. 러너가 `--force` 를 "ignored" 로 기록하면서도 `lock=bypassed` 로 2차 실행을 시작(직접 원인).
5. 두 실행이 `append` 로 같은 `dm.daily_submission_fact` 에 09-15 분을 넣음 → 369,064 건.
6. 03:30 `build_report_cache` 가 경고(`input_rows` 불일치)만 남기고 그대로 돌아 중복 입력으로 1,271 건을 산출. 07:00 dq-check 가 처음으로 건수 이상을 알림.

- **직접 원인**: lock 우회로 시작된 동시 2차 실행(H1).
- **배경 원인**: append 적재의 멱등성 부재 가능성(H3), 진행 중 실행과 멈춘 실행을 구분하지 못하는 알림 · 운영 절차, 중복 입력에 대한 후속 job 의 경고만 하고 통과하는 동작.
- 기록상 모순: `action=ignored reason="--force"` 인데 `lock=bypassed` 로 시작했다. 어느 쪽이 실제 동작인지는 러너 코드 없이 알 수 없다(7절).

## 5. 근거 로그 줄

| 서술 | 근거 | 발췌 |
|---|---|---|
| 1차가 cron 으로 lock 을 잡고 시작 | `batch-job.log:88` | `status=START … trigger=cron … lock=acquired … pid=46735` |
| 1차는 append | `batch-job.log:89` | `mode=append target=dm.daily_submission_fact` |
| 1차 지연 경고 | `batch-job.log:94` | `elapsed 40m exceeds p95 17m` |
| SLA 알림은 RUNNING 상태 | `batch-job.log:95` | `status=NOT_FINISHED … last_status=RUNNING` |
| 수동 재실행 요청 | `batch-job.log:97` | `reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true` |
| lock 보유 중인데 force 무시 기록 | `batch-job.log:99` | `lock=held_by run_id=…edf5 … since=02:00:03 action=ignored reason="--force"` |
| lock 우회로 2차 시작 | `batch-job.log:100` | `trigger=manual … lock=bypassed` |
| 2차도 같은 target 에 append | `batch-job.log:101` | `mode=append target=dm.daily_submission_fact` |
| 1차 정상 종료 | `batch-job.log:108` | `status=SUCCESS rows=184532 elapsed=4357s` |
| 2차 정상 종료, lock 해제 못 함 | `batch-job.log:110`, `:111` | `rows=184532 elapsed=1008s`, `lock=release_skipped reason="not owner (bypassed)"` |
| 후속 job 이 중복 입력으로 실행 | `batch-job.log:113`, `:114` | `input_rows=369064`, `status=SUCCESS rows=1271` |
| 이상 탐지 | `batch-job.log:115` | `rows=369064 avg_7d=184617 deviation=+99%` |
| 적재 건수 | `datamart-counts.csv` 24행(09-15 `load_datamart`) | `2026-09-15,load_datamart,369064,2026-09-16T03:15:31+09:00` |

## 6. 수정안 · 재발 방지 · 모니터링 (제안만 함)

### 수정안

1. **데이터 정리(최우선, 결정 필요)**: `dm.daily_submission_fact` 의 09-15 분에서 2차 실행(`run_id …0258-8963`)이 넣은 행을 식별해 삭제하거나, 09-15 분 전체를 삭제 후 재적재한다. 행에 `run_id` 가 남는지는 확인하지 못했다. 실행 전 행 수 · 중복 키를 확인하고 백업한다.
2. `build_report_cache` 를 정리 후 재실행(`rows=1271` 산출물이 2배 입력 기반이다 — 영향 정도는 미확인).
3. CSV 의 09-15 행과 이후 7일 평균 기준값(369,064 가 포함되면 부풀음)을 정정한다.

### 재발 방지

- 동시 실행 방지: lock 이 살아 있으면 `force` 여도 시작하지 않도록 한다. 최소한 우회(`bypassed`) 시 같은 job · 같은 날짜의 실행이 진행 중이면 거부.
- 적재 멱등성: 날짜 단위 삭제 후 적재(또는 `run_id` 단위 롤백 가능한 구조, 유니크 키)로 `append` 중복을 막는다.
- 운영 절차: 재실행 전에 "RUNNING 이고 진행률이 오르는지"를 먼저 확인한다. 런북에 반영.
- 후속 job 방어: `build_report_cache` 가 `input_rows` 불일치 WARN 을 내면 중단하거나 확인 대기.

### 모니터링 항목

| 항목 | 제안 임계값 | 이번 로그에서 울렸을 시각 | 소급 |
|---|---|---|---|
| 같은 날짜 · job 의 동시 실행(`lock=bypassed` 로그) | 1건이라도 | 02:58:42 | 가능(로그에 있음) |
| 같은 날짜 · job SUCCESS 2회 | 1건이라도 | 03:15:31 | 가능 |
| SLA 알림에 진행률 · 마지막 갱신 시각 포함 | `last_status=RUNNING` 이고 진행률 증가 중이면 "지연" 표시 | 02:45 | 가능(진행률 로그가 있음) |
| 적재 건수 편차 | ±30% 초과 | 03:15:31 (1차 SUCCESS 직후 CSV 는 이미 정상, 2차 후 기준) | 가능 — 실제로는 07:00 에 울림. 즉시 계산하면 약 3시간 45분 빨랐을 것 |
| 후속 job 입력 건수 불일치 | 입력 / 전 job 출력 비율 이상 | 03:30:04 (WARN 은 이미 있었음) | 가능 |

## 7. 확인하지 못한 것

- **러너 · lock 코드**: 저장소에 없다(검색해도 `load_datamart` 가 로그 · CSV · 점검 문서에만 있음). `--force` 가 왜 "ignored" 로 기록되고도 `lock=bypassed` 로 시작됐는지, 정상 경로에서 중복 실행을 막는지 대조하지 못했다. 다음에 남길 로그: 우회 판단 분기(어떤 옵션이 우회를 허용했는지).
- **1차가 느려진 이유**: 로그에 단서가 없다. 다음에 남길 것: 단계별(읽기 · 변환 · 쓰기) 소요 시간, DB 대기 시간, 쓰기 배치 크기.
- **`dm.daily_submission_fact` 의 실제 상태**: 행 수 · 중복 키 · `run_id` 컬럼 유무. 로그 · CSV 로는 건수만 확인된다.
- **이중 적재가 하류에 미친 영향**: `build_report_cache` 산출물과 그것을 읽는 화면 · 리포트. 미확인.
- **dq-check 알림 이후 대응**: 07:00 알림 뒤 누가 무엇을 했는지 기록이 없다.
- **`ignored` 와 `bypassed` 의 모순**(4절).

마스킹 적용: IP 4종(→ BATCH-1, OPS-1, MON-1, DB-1) · 이메일 2종(`***@example.com`) · 학생 식별자 1종(`STU-…`) · 토큰 1건(`[TOKEN]`) · 비밀번호/접속 문자열 0건
