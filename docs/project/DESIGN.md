# 실전 프로젝트 설계 — 데이터 · 인프라 / batch-check 회귀 테스트

> 상태: **잠정안.** 과제 표의 "완성 기준" · "최소 범위로 줄인다면" 칸 원문을 받지 못해, 2 · 3 · 6절은 저장소 재료만 보고 가정해 썼다(4절 1번).

## 1. 목표
`scripts/batch-check.sh` 의 현재 판정(실데이터 7건 · 이상 없는 데이터 0건 · 이상 1건 주입 시 1건)을 고정하는 bash 회귀 테스트를 만들어, 스크립트를 고쳐도 판정이 바뀌면 바로 실패하게 한다.

## 2. 재료

| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 배치 점검 스크립트 (4개 기준 계산, 인자 `[로그디렉터리] [counts.csv]`) | 있음 | `scripts/batch-check.sh` |
| 배치 로그 10일치 · 적재 건수 CSV (읽기 전용) | 있음 | `pipeline-samples/batch-logs/2026-09-08.log` ~ `2026-09-17.log`, `pipeline-samples/datamart-counts.csv` |
| 배치 점검 Skill (점검 기준 · "이상 없으면 파일 만들지 않음" 규칙) | 있음 | `.claude/skills/batch-check/SKILL.md` |
| 기대값 대조용 리포트 (이상 7건) | 있음 | `docs/batch-check/report.md` |
| 중복 적재 RCA (중복 실행 사례 근거) | 있음 | `docs/rca/c-batch-duplicate.md` |
| 데이터 파이프라인 팀 템플릿 (점검 기준 4개 정의) | 있음 | `templates/CLAUDE.data-pipeline.md` |
| IaC 리뷰 Sub-agent · Terraform 샘플 | 있음 (이번엔 쓰지 않음) | `.claude/agents/iac-reviewer.md`, `pipeline-samples/terraform/main.tf`, `variables.tf` |
| 위험 명령 · 비밀값 차단 Hook | 있음 (그대로 둠) | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `.claude/settings.json` |
| characterization 동작 보존 테스트 | 있음 (이 트랙에서 쓰지 않음) | `characterization/` |
| modern/api · modern/web | 있음 (이 트랙에서 쓰지 않음) | `modern/` |
| `terraform` · `shellcheck` · `bats` CLI | 없음 | — (`which` 결과 없음. awk · node 만 있음) |
| 회귀 테스트 실행 스크립트 | 없음 → 새로 만들 것 | `scripts/tests/batch-check.test.sh` |
| 실데이터 기대 출력 | 없음 → 새로 만들 것 | `scripts/tests/expected/real.tsv` |
| 이상 없는 소형 픽스처 (2일 × 5 job) | 없음 → 새로 만들 것 | `scripts/tests/fixtures/clean/logs/*.log`, `scripts/tests/fixtures/clean/counts.csv` |
| 중복 1건 주입 픽스처 | 없음 → 새로 만들 것 | `scripts/tests/fixtures/dup/logs/*.log`, `scripts/tests/fixtures/dup/counts.csv` |
| 프로젝트 문서 폴더 | 없음 → 새로 만들 것 | `docs/project/` (이 파일) |

## 3. 범위
### 한다
1. 실데이터(`pipeline-samples/`) 출력 7줄을 `expected/real.tsv` 로 고정하고, `docs/batch-check/report.md` 의 이상 7행과 한 줄씩 대조한다.
2. 손으로 쓴 소형 픽스처 2벌(이상 없음 → 0줄, 중복 SUCCESS 1건 주입 → `2.중복` 1줄)을 만든다.
3. 세 경우를 돌려 `PASS n/3` 을 출력하고, 하나라도 다르면 0이 아닌 종료 코드를 내는 `scripts/tests/batch-check.test.sh` 를 만든다.

### 하지 않는다
- `scripts/batch-check.sh` 본문 수정 — 이번 목표는 현재 판정을 고정하는 것이고, 고치면 기대값의 근거가 흔들린다.
- `pipeline-samples/` 로그 · CSV 수정 — batch-check Skill 이 읽기 전용으로 정했다. 픽스처는 `scripts/tests/fixtures/` 에 따로 둔다.
- `bats` · `shellcheck` 등 테스트 도구 추가 — 설치돼 있지 않고, CLAUDE.md 3절상 의존성 추가는 먼저 물어야 한다. `bash` + `diff` + `sort` 로 충분하다.
- Terraform 검토 · `fmt` · `validate` — `terraform` CLI 가 없어 통과 · 실패를 이 환경에서 확인할 수 없다.
- Jenkins · CI 연결 — `ci-ports/Jenkinsfile` 은 실제로 돌려 본 적 없는 구성안이고, `curl` 은 `.claude/settings.json` 에서 막혀 있어 로컬에서 검증할 수 없다.
- 4개 기준 외 새 점검 항목(예: SLA 시각 초과) 추가 — 한 차시 범위를 넘는다.

## 4. 미해결 질문
1. **과제 표의 "완성 기준" · "최소 범위로 줄인다면" 칸 원문.** 이 설계의 2 · 3 · 6절은 위 가정으로 썼다. 원문이 다르면 그 범위로 줄여 다시 쓴다.
2. 설계 제목의 이름 표기 — 저장소에서 확인한 표기는 `docs/rules/KimYongMin.md` 뿐이라 제목에 이름을 넣지 않았다. 넣을 이름을 알려 주면 바꾼다.
3. `scripts/batch-check.sh` 의 출력 순서가 날짜순이 아니다(현재 09-12, 09-15 ×3, 09-09, 09-17, 09-15). awk 배열 순회 순서에 따른 것으로 보이며, awk 구현이 바뀌면 순서가 달라질 수 있는지는 확인하지 않았다. 테스트는 양쪽을 `sort` 한 뒤 비교해 순서에 의존하지 않게 한다.
4. 테스트 파일을 커밋할지, 커밋한다면 이 `day1` 브랜치에 할지.

## 5. 단계

### 단계 1 — 실데이터 기대값 고정 (약 10분)
- 할 일: 현재 출력 7줄을 정렬해 기대값 파일로 저장하고, `docs/batch-check/report.md` 의 이상 목록 7행(날짜 · job · 점검 항목)과 한 줄씩 맞춰 본다.- 만들거나 고칠 파일: `scripts/tests/expected/real.tsv` (새 파일)
- 확인 방법:
  ```bash
  wc -l scripts/tests/expected/real.tsv                                             # 7
  diff <(bash scripts/batch-check.sh | sort) scripts/tests/expected/real.tsv && echo SAME   # SAME
  ```
  눈으로: `real.tsv` 의 7줄이 `report.md` 의 `## 이상 목록` 7행과 날짜 · job · 항목 번호가 모두 일치한다.

### 단계 2 — 소형 픽스처 2벌 (약 15분)
- 할 일: 2026-09-08 로그 형식을 본떠 job 5개가 날마다 START · SUCCESS 1쌍씩 있는 깨끗한 로그와 CSV를 손으로 쓴다(`clean`). 그 사본에서 하루 `load_datamart` 의 SUCCESS 줄을 하나 더 넣은 것을 `dup` 으로 만든다. 학생 식별자 · 이메일 · IP 는 넣지 않는다.
  - 픽스처를 쓸 때 지킬 것(스크립트에서 확인함): 건수 판정은 job 의 첫 행을 건너뛰고 둘째 행부터 직전 행들의 평균과 비교한다(`scripts/batch-check.sh:56-57`) → 이틀째 건수를 첫날의 ±30% 안으로 둔다. 지연 기준은 job 별 전체 SUCCESS `elapsed` 의 중앙값이다(`:36`, `:42`) → `dup` 에 넣는 SUCCESS 줄의 `elapsed` 를 원래 값과 같게 둬서 `4.지연` 이 덤으로 나오지 않게 한다. `dup` 의 CSV 는 `clean` 과 같게 둔다.
- 만들거나 고칠 파일: `scripts/tests/fixtures/clean/logs/*.log`, `scripts/tests/fixtures/clean/counts.csv`, `scripts/tests/fixtures/dup/logs/*.log`, `scripts/tests/fixtures/dup/counts.csv` (모두 새 파일)
- 확인 방법:
  ```bash
  bash scripts/batch-check.sh scripts/tests/fixtures/clean/logs scripts/tests/fixtures/clean/counts.csv | wc -l   # 0
  bash scripts/batch-check.sh scripts/tests/fixtures/dup/logs scripts/tests/fixtures/dup/counts.csv               # 1줄, 3번째 칸이 2.중복
  ```

### 단계 3 — 테스트 실행 스크립트 (약 10분)
- 할 일: 위 세 경우를 차례로 돌려 정렬 후 기대값과 비교하고, 경우별 `PASS`/`FAIL <이름>` 과 마지막 줄 `PASS n/3` 을 출력한다. 하나라도 실패하면 `exit 1`.
- 만들거나 고칠 파일: `scripts/tests/batch-check.test.sh` (새 파일)
- 확인 방법:
  ```bash
  bash scripts/tests/batch-check.test.sh; echo "exit=$?"            # 마지막 줄 PASS 3/3, exit=0
  git status --porcelain pipeline-samples docs/batch-check scripts/batch-check.sh   # 빈 출력
  ```
  실패 경로 확인: `real.tsv` 의 한 줄을 임시로 지운 사본을 기대값으로 쓰게 바꿔 돌리면 `FAIL real`, `exit=1` 이 나오는지 본 뒤 원래대로 돌린다.

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `bash scripts/tests/batch-check.test.sh` 의 마지막 줄이 `PASS 3/3` 이고 종료 코드가 0이다.
- [ ] `diff <(bash scripts/batch-check.sh | sort) scripts/tests/expected/real.tsv` 출력이 비어 있고, `real.tsv` 가 7줄이다.
- [ ] `real.tsv` 7줄의 날짜 · job · 항목 번호가 `docs/batch-check/report.md` 이상 목록 7행과 모두 일치한다.
- [ ] `clean` 픽스처 실행 결과가 0줄, `dup` 픽스처 실행 결과가 정확히 1줄이고 그 줄에 `2.중복` 이 있다.
- [ ] 기대값 한 줄을 지운 상태로 실행하면 `FAIL` 이 출력되고 종료 코드가 1이다.
- [ ] `git status --porcelain pipeline-samples docs/batch-check scripts/batch-check.sh` 출력이 비어 있다(원본 데이터 · 리포트 · 스크립트 미수정).
- [ ] `build.gradle` · `package.json` 등 의존성 파일 변경이 없다(`git diff --stat` 에 나타나지 않는다).
