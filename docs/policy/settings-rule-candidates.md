# 실 공통 규칙 후보 — `.claude/settings.json` · `hooks/`

작성일: 2026-10-13 · 작성자 주 스택: `data-infra` · 상태: **후보(미적용)**

이 저장소의 `.claude/settings.json`(permissions · hooks)과 `hooks/` 규칙을 실 전체 개발자에게 걸 규칙 후보로 정리했다. 기존 파일은 고치지 않았다. 2절의 규칙은 모두 **제안 · 미설치** 상태이고, 정규식은 scratchpad에서 샘플 입력으로만 시험했다.

## 0. 분류 기준

| 적용 범위 | 뜻 |
|---|---|
| **공통** | 스택과 상관없이 모든 개발자에게 건다. 운영 DB 접근 차단은 ERP를 포함해 여기에 넣는다. |
| `react` | modern/web 계열(npm, MDN 문서). |
| `spring` | modern/api 계열(gradlew, Spring · Gradle 문서). |
| `data-infra` | Terraform · kubectl · aws, 배치, ERP 도구(MS-SQL 명령줄 도구 `sqlcmd` · `bcp` 등). ERP용 스택 코드가 없어서 여기에 넣었다. |
| 저장소 전용 | 이 실습 저장소에만 있는 서버 · 도구. 조직에 퍼뜨리지 않는 후보. |

- `settings.local.json` 은 `.gitignore` 에 들어가 개인 설정이 되었으므로 후보에서 뺐다(`docker compose ps` allow 1줄).
- `pytest` · `fastapi.tiangolo.com` 은 react · spring 어느 쪽도 아니다. `templates/CLAUDE.data-pipeline.md` 가 `pytest` 를 쓰는 Python 파이프라인 스택이라서 `data-infra` 로 넣었다. **확인 필요:** Python 스택 코드가 따로 생기면 그쪽으로 옮긴다.

## 1. 기존 규칙과 적용 범위

출처: `.claude/settings.json`. "추가분"은 교안 기본 정책 외에 이 저장소에서 더한 줄이다(Terraform deny 두 줄, Hook 두 개).

### 1-1. `deny`

| 규칙 | 적용 범위 | 비고 |
|---|---|---|
| `Bash(rm -rf *)` | 공통 | Hook `RULES[0]` 이 `-fr` · `-r -f` 변형까지 잡는다 |
| `Bash(sudo *)` | 공통 | |
| `Bash(git push --force *)` | 공통 | `git push origin main --force` 처럼 플래그가 뒤에 오면 빠진다. `ask: git push *` 가 대신 묻는다 |
| `Bash(git push -f *)` | 공통 | 위와 같음 |
| `Bash(git reset --hard *)` | 공통 | |
| `Bash(*prod-db*)` | 공통 | 운영 DB. 이름에 `prod-db` 가 없는 ERP 운영 서버는 못 막는다 → 2-1 |
| `Bash(*prod.db*)` | 공통 | 위와 같음 |
| `Bash(curl *)` | 공통 | 정상 조회도 막는다(`templates/secure-coding-checklist.md` 빈틈 목록 참고) |
| `Bash(wget *)` | 공통 | |
| `Read(./.env)` | 공통 | |
| `Read(./.env.*)` | 공통 | |
| `Read(./secrets/**)` | 공통 | |
| `Read(**/*.pem)` | 공통 | `.pfx` · `.p12` · `.der` · `.key` 는 빠진다 → 2-1 |
| `Read(~/.ssh/**)` | 공통 | |
| `Read(~/.aws/**)` | 공통 | 다른 클라우드 로그인 파일은 빠진다 → 2-1 |
| `Edit(./.env)` | 공통 | |
| `Edit(./.env.*)` | 공통 | |
| `Edit(./secrets/**)` | 공통 | |
| `mcp__github__delete_file` | 공통 | GitHub MCP를 쓰는 사람에게만 의미가 있다 |
| `mcp__github__merge_pull_request` | 공통 | 위와 같음 |
| `Bash(terraform apply *)` (추가분) | `data-infra` | **구멍:** `terraform -chdir=infra apply` 는 접두어가 달라 비켜 간다 → 2-2 |
| `Bash(terraform destroy *)` (추가분) | `data-infra` | 같은 구멍 |

### 1-2. `ask`

| 규칙 | 적용 범위 | 비고 |
|---|---|---|
| `Bash(git push *)` | 공통 | |
| `Bash(npm install *)` | `react` | |
| `Bash(npm i *)` | `react` | |
| `Bash(npm ci *)` | `react` | |
| `Bash(docker compose down *)` | 저장소 전용 | compose 프로필 운영 방식이 이 저장소 기준 |
| `Bash(docker compose --profile *)` | 저장소 전용 | |
| `Bash(docker compose up *)` | 저장소 전용 | |
| `Bash(./gradlew bootRun *)` | `spring` | |
| `mcp__github__create_pull_request` | 공통 | |
| `mcp__github__push_files` | 공통 | |
| `mcp__dbhub` | 공통 | DB 접근 MCP. 서버 이름은 저장소마다 다르므로 이름을 맞춰 넣는다 |
| `mcp__dbhub-mssql` | `data-infra` | MS-SQL(ERP 도구) 쪽 |

### 1-3. `allow`

| 규칙 | 적용 범위 | 비고 |
|---|---|---|
| `Bash(npm test *)` · `Bash(npm run test *)` · `Bash(npm run lint *)` · `Bash(npm run typecheck *)` · `Bash(npm run build *)` | `react` | |
| `Bash(./gradlew test *)` · `Bash(./gradlew check *)` · `Bash(./gradlew build *)` | `spring` | |
| `Bash(git status *)` · `Bash(git diff *)` · `Bash(git log *)` · `Bash(git add *)` · `Bash(git commit *)` | 공통 | `git commit` 은 secret-scan Hook이 먼저 검사한다 |
| `Bash(pytest *)` | `data-infra` | 0절 "확인 필요" 참고 |
| `WebFetch(domain:docs.spring.io)` · `WebFetch(domain:docs.gradle.org)` | `spring` | `docs.gradle.org` 가 **두 번** 들어 있다. 조직 배포본에서는 한 줄로 줄인다 |
| `WebFetch(domain:developer.mozilla.org)` | `react` | |
| `WebFetch(domain:fastapi.tiangolo.com)` | `data-infra` | 0절 "확인 필요" 참고 |
| `mcp__github__get_file_contents` · `mcp__github__list_pull_requests` · `mcp__github__pull_request_read` · `mcp__github__list_issues` | 공통 | 읽기 전용 |
| `mcp__item-bank__search_items` | 저장소 전용 | 실습용 MCP 서버 |

### 1-4. Hook (추가분)

`PreToolUse` matcher `Bash` 에 걸린다. 실행기 `scripts/hook-node.sh` 가 node 를 찾지 못하면 명령을 막는다(fail-closed). Hook 을 퍼뜨릴 때는 실행기도 같이 배포해야 한다.

| Hook · 규칙 | 적용 범위 | 비고 |
|---|---|---|
| `scripts/hook-node.sh` (실행기) | 공통 | |
| `hooks/block-dangerous.mjs` `RULES[0]` — `rm` 재귀 + 강제 | 공통 | |
| `hooks/block-dangerous.mjs` `RULES[1]` — `DROP TABLE` / `DROP DATABASE` | 공통 | `TRUNCATE` · `WHERE` 없는 `DELETE`/`UPDATE` 는 못 막는다 → 2-1 |
| `hooks/block-dangerous.mjs` `RULES[2]` — `/prod[-.]db/` | 공통 | |
| `hooks/secret-scan.mjs` — `AWS Access Key ID` · `하드코딩된 password` · `PEM 개인키` | 공통 | 따옴표 없는 값 · 연결 문자열은 놓친다 → 2-1 |

Hook 의 다른 한계(Bash 에만 걸림, 입력을 못 읽으면 통과, `git commit` 으로 시작하는 명령만 검사)는 `templates/secure-coding-checklist.md` 의 "확인하면서 발견한 빈틈"에 이미 적혀 있어 여기서 다시 쓰지 않는다.

## 2. 추가 제안 (제안 · 미설치)

### 2-0. 지금 규칙이 놓치는 것 — 실제 Hook 실행 결과

현재 `hooks/block-dangerous.mjs` 에 아래 명령을 넣었을 때 모두 `exit=0`(통과)이었다.

```
exit=0 :: terraform -chdir=infra apply -auto-approve
exit=0 :: TRUNCATE TABLE t
exit=0 :: sqlcmd -S erp-prod01 -Q "DELETE FROM slip"
exit=0 :: cat terraform.tfstate
exit=0 :: bcp dbo.pay out x.dat -S erp01
```

현재 `hooks/secret-scan.mjs` 의 `PATTERNS` 세 개를 아래 줄에 대 보면 모두 놓친다.

```
놓침  password=Sa-pass1234!
놓침  DB_PASSWORD: secret
놓침  jdbc:sqlserver://h;user=sa;password=x1
놓침  Server=h;User Id=sa;Password=abc;
놓침  postgres://u:pw@h/db
놓침  주민 900101-1234567
```

`erp-prod01` · `erp01` 은 시험용 샘플 이름이다. 실제 ERP 운영 서버 이름이 아니다.

### 2-1. 공통

| # | 제안 | 종류 | 제안 규칙 원문 | 오탐 · 주의 |
|---|---|---|---|---|
| C1 | ERP 운영 서버 이름 차단 | deny + Hook | `deny: Bash(*<ERP-PROD-HOST>*)` (서버마다 한 줄), `block-dangerous.mjs` 에 `{ pattern: /<ERP-PROD-HOST>/i, reason: "ERP 운영 DB 접근 금지" }` | **`<ERP-PROD-HOST>` 는 사람이 채운다.** 호스트명 · IP · 인스턴스명(`호스트\인스턴스`)을 모두 넣는다. MCP 연결 대상은 Hook 이 못 보므로 `.mcp.json` 검토로 따로 막는다 |
| C2 | `TRUNCATE` 차단 | Hook | `/\bTRUNCATE\s+(TABLE\s+)?\S+/i` | |
| C3 | `WHERE` 없는 `DELETE` · `UPDATE` 차단 | Hook | 명령을 `;` 로 나눠 문장마다 `/\bDELETE\s+FROM\s+\S+/i` 또는 `/\bUPDATE\s+\S+\s+SET\b/i` 에 맞고 `/\bWHERE\b/i` 가 없으면 차단 | `UPDATE` 만 보면 `npm update` · `apt update` 가 걸리므로 `SET` · `FROM` 까지 본다. `WHERE 1=1` 은 못 막는다. SQL 파일을 `-i` 로 넘기면 내용을 못 본다 |
| C4 | 따옴표 없는 `password=` · `password:` | secret-scan 패턴 | `/\b\w*(password\|passwd\|pwd)\s*[:=]\s*[^\s'"$<{][^\s;'"]*/i` | 아래 오탐 참고 |
| C5 | DB 연결 문자열 | secret-scan 패턴 | URL 형: `/\b[a-z][a-z0-9+.-]*:\/\/[^\s:@/]+:[^\s@/]+@/i`. `Server=…;Password=…` · `jdbc:…;password=…` 형은 C4 가 잡는다 | |
| C6 | 주민등록번호 | secret-scan 패턴 | `/\b\d{2}(0[1-9]\|1[0-2])(0[1-9]\|[12]\d\|3[01])-?[1-4]\d{6}\b/` | 앞 6자리는 날짜, 7번째 자리는 1–4 로 좁혔다. 그래도 날짜+일련번호 꼴 ID 가 걸릴 수 있다. 커밋이 막히는 정도라 감수하는 쪽을 권한다. 외국인등록번호(5–8)까지 막으려면 `[1-8]` |
| C7 | 클라우드 로그인 파일 읽기 금지 | deny | `Read(~/.azure/**)`, `Read(~/.config/gcloud/**)`, `Read(~/.kube/config)`, `Read(~/.docker/config.json)`, `Read(**/*credentials*.json)` | `~/.aws/**` 는 이미 있다. `~/.kube/config` 는 `data-infra` 에서 `kubectl` 을 쓸 때도 Claude 가 파일을 직접 열 필요는 없다 |
| C8 | 인증서 파일 읽기 금지(전자세금계산서 인증서 포함) | deny | `Read(**/*.pfx)`, `Read(**/*.p12)`, `Read(**/*.der)`, `Read(**/*.key)`, `Read(**/NPKI/**)`, `Read(**/GPKI/**)` | 공동 · 공인인증서는 `NPKI/**/signCert.der` · `signPri.key` 로 저장된다. `*.der` 는 공개 인증서도 막는다 |

C4~C6 시험 결과(제안 패턴, scratchpad):

```
잡음 password=Sa-pass1234!                  ← C4
잡음 DB_PASSWORD: secret                    ← C4
잡음 jdbc:sqlserver://h;user=sa;password=x1 ← C4
잡음 Server=h;User Id=sa;Password=abc;      ← C4
잡음 postgres://u:pw@h/db                   ← C5
잡음 주민 900101-1234567                     ← C6
통과 password=${DB_PASSWORD}
통과 password: <masked>
통과 주문번호 20261013-1234567
통과 https://example.com/path
```

C4 를 이 저장소 파일에 대 보면 아래가 걸린다(값은 생략).

| 위치 | 걸리는 이유 | 처리 제안 |
|---|---|---|
| `pipeline-samples/terraform/main.tf:59` | `password = var.…` (변수 참조) | `var.` · `local.` 로 시작하는 값은 제외 |
| `templates/CLAUDE.iac.md:62` | `db_password: sensitive` (설명 문장) | `sensitive` 제외, 또는 `*.md` 는 검사 대상에서 뺀다 |
| `.claude/skills/rca/masking-rules.md:12` | `password=[PASSWORD]` (마스킹 예시) | 값이 `[` 로 시작하면 제외 |
| `modern/api/src/main/resources/application.yml:7` | 실습용 더미 비밀번호 | CLAUDE.md 상 허용되는 더미. 조직판에서는 걸리는 게 맞다 |

남는 구멍: `MSSQL_SA_PASSWORD: "…"` 처럼 **콜론 + 따옴표** 형은 기존 패턴(`=` + 따옴표)도 C4(따옴표 제외)도 놓친다. 기존 패턴을 `/password\s*[:=]\s*['"][^'"]+/i` 로 넓히는 것을 함께 제안한다.

### 2-2. `data-infra`

| # | 제안 | 종류 | 제안 규칙 원문 | 오탐 · 주의 |
|---|---|---|---|---|
| D1 | Terraform state 읽기 금지 | deny | `Read(**/*.tfstate)`, `Read(**/*.tfstate.backup)`, `Read(**/.terraform/**)`, `Bash(terraform state pull *)`, `Bash(terraform show *)` | state 에는 비밀번호가 평문으로 들어간다. `terraform show` 는 plan 파일 확인에도 쓰여 막으면 불편할 수 있다 → `ask` 로 낮출지 결정 필요 |
| D2 | `terraform -chdir` 구멍 막기 | Hook | `block-dangerous.mjs` 에 `/\bterraform\b[^;&\|]*\s(apply\|destroy)\b/` | deny 는 접두어 일치라 `-chdir=…` · `-var-file` 이 앞에 오면 못 막는다. Hook 정규식은 위치와 무관하게 잡는다 |
| D3 | Terraform 규칙 촘촘하게 | deny / ask | deny: `Bash(terraform state rm *)`, `Bash(terraform state mv *)`, `Bash(terraform import *)`, `Bash(terraform taint *)`, `Bash(terraform force-unlock *)`. ask: `Bash(terraform init *)`(백엔드 · 프로바이더 변경), `Bash(terraform workspace select *)` | `terraform plan` · `validate` · `fmt` 는 allow 후보 |
| D4 | kubectl 규칙 | deny / ask | deny: `Bash(kubectl delete *)`, `Bash(kubectl exec *)`, `Bash(kubectl get secret*)`, `Bash(kubectl * --context *prod*)`. ask: `Bash(kubectl apply *)`, `Bash(kubectl scale *)`, `Bash(kubectl rollout *)`, `Bash(kubectl edit *)`, `Bash(kubectl config use-context *)` | `--context` 없이 현재 컨텍스트가 운영이면 deny 를 비켜 간다. 운영 컨텍스트 이름도 Hook 에 넣는 것을 권한다 |
| D5 | aws 규칙 | deny / ask | deny: `Bash(aws * delete-*)`, `Bash(aws * terminate-*)`, `Bash(aws s3 rm *)`, `Bash(aws s3 rb *)`, `Bash(aws iam *)`, `Bash(aws secretsmanager get-secret-value *)`, `Bash(aws ssm get-parameter* --with-decryption*)`. ask: `Bash(aws * create-*)`, `Bash(aws * put-*)`, `Bash(aws * update-*)`, `Bash(aws s3 cp *)`, `Bash(aws s3 sync *)` | `aws iam` 전체 deny 는 조회(`list-*`)도 막는다. 필요하면 `allow: Bash(aws iam list-*)` |
| D6 | `bcp` 차단(ERP 데이터 대량 반출) | deny + Hook | `deny: Bash(bcp *)`, Hook `/\bbcp\b/` | `bcp … in`(적재)도 함께 막힌다. ERP 데이터 반출 위험을 생각하면 감수한다. Hook 을 같이 두는 이유는 `cd x && bcp …` 처럼 앞에 다른 명령이 붙는 경우 |
| D7 | `crontab` 변경 확인 | ask (+ allow) | `ask: Bash(crontab *)`, `allow: Bash(crontab -l *)` | `crontab -r`(전체 삭제)은 `deny` 로 올리는 것을 권한다 |
| D8 | `systemctl` 변경 확인 | ask (+ allow) | `ask: Bash(systemctl *)`, `allow: Bash(systemctl status *)`, `allow: Bash(systemctl list-* *)` | 접두어 일치라 allow 없이 두면 `status` 조회도 매번 묻는다. 대부분 `sudo` 가 붙어 기존 `deny: Bash(sudo *)` 에 먼저 걸린다 |

D2 · C2 · C3 · C1 시험 결과(제안 정규식, scratchpad. C1 은 샘플 이름 `erp-prod\d*` 로 시험):

```
차단 terraform -chdir=infra apply -auto-approve  ← D2
차단 terraform apply                             ← D2
차단 terraform destroy -target=x                 ← D2
통과 terraform plan
통과 terraform -chdir=infra plan
차단 sqlcmd -Q "TRUNCATE TABLE slip"             ← C2
차단 sqlcmd -Q "DELETE FROM slip"                ← C3
통과 sqlcmd -Q "DELETE FROM slip WHERE id=1"
차단 sqlcmd -Q "UPDATE stock SET qty=0"          ← C3
통과 sqlcmd -Q "UPDATE stock SET qty=0 WHERE id=3"
통과 npm update
통과 sudo apt update
차단 sqlcmd -S erp-prod01 -Q "SELECT 1"          ← C1(샘플)
```

## 3. 사람만 확인할 수 있는 것

도구로 강제할 수 없어 운영 절차로 둔다.

| # | 규칙 | 적용 범위 | 강제 수단 | 비고 |
|---|---|---|---|---|
| H1 | ERP 결산 · 마감 기간에는 ERP 관련 변경(배포 · 스키마 · 배치 스케줄)을 하지 않는다 | 공통 | 사람 | 기간 달력을 누가 공지하는지 정해야 한다 |
| H2 | 전표 · 재고 테이블을 바꾸는 SQL 은 승인을 받은 뒤 실행한다 | 공통 | 사람 | 승인자 · 기록 양식이 아직 없다. C3 은 `WHERE` 유무만 보므로 대신하지 못한다 |

## 4. 남은 결정

1. `<ERP-PROD-HOST>` 실제 목록(호스트 · IP · 인스턴스명)과 운영 kube 컨텍스트 이름.
2. `pytest` · `fastapi` 를 `data-infra` 에 둘지, Python 스택 코드를 따로 만들지.
3. D1 의 `terraform show` 를 deny 로 할지 ask 로 할지.
4. C4 오탐 제외 목록(`var.` · `local.` · `[` · `sensitive`)과 `*.md` 검사 제외 여부.
