# 실 공통 규칙 후보 — KimYongMin

작성일: 2026-10-13 · 작성자 주 스택: `data-infra` · 상태: **후보(미적용)**

출처는 이 저장소의 `.claude/settings.json`(permissions · hooks)과 `hooks/block-dangerous.mjs` · `hooks/secret-scan.mjs` 이다. 기존 파일은 고치지 않았다. 2절 규칙은 모두 **제안 · 미설치** 상태이고, 정규식은 scratchpad 에서 샘플 입력으로만 시험했다. 자세한 근거와 시험 기록은 `docs/policy/settings-rule-candidates.md` 에 있다.

## 0. 분류 기준

| 적용 범위 | 뜻 |
|---|---|
| **공통** | 스택과 관계없이 모든 개발자에게 건다. 운영 DB 접근 차단은 ERP 운영 DB 를 포함해 여기에 넣는다. |
| `react` | modern/web 계열(npm, MDN 문서). |
| `spring` | modern/api 계열(gradlew, Spring · Gradle 문서). |
| `data-infra` | Terraform · kubectl · aws, 배치, ERP 도구(MS-SQL 명령줄 도구 `sqlcmd` · `bcp` 등). ERP용 스택 코드가 없어서 여기에 넣었다. |
| 저장소 전용 | 이 실습 저장소에만 있는 서버 · 도구. 조직에 퍼뜨리지 않는다. |

**표 형식:** 규칙 하나가 한 줄이다. 열은 `# · 규칙 원문 · 종류 · 적용 범위 · 출처 · 비고` 이다.
- 종류: `deny` · `ask` · `allow` · `Hook` · `사람`
- 출처: `교안` = 교안 기본 정책, `추가분` = 이 저장소에서 더한 줄, `제안` = 2절 신규 후보

## 1. 기존 규칙

### 1-1. permissions

| # | 규칙 원문 | 종류 | 적용 범위 | 출처 | 비고 |
|---|---|---|---|---|---|
| E01 | `Bash(rm -rf *)` | deny | 공통 | 교안 | `-fr` · `-r -f` 변형은 Hook E61 이 잡는다 |
| E02 | `Bash(sudo *)` | deny | 공통 | 교안 | |
| E03 | `Bash(git push --force *)` | deny | 공통 | 교안 | `git push origin main --force` 처럼 플래그가 뒤에 오면 빠진다. 대신 E30 이 묻는다 |
| E04 | `Bash(git push -f *)` | deny | 공통 | 교안 | E03 과 같은 한계 |
| E05 | `Bash(git reset --hard *)` | deny | 공통 | 교안 | |
| E06 | `Bash(*prod-db*)` | deny | 공통 | 교안 | 이름에 `prod-db` 가 없는 ERP 운영 서버는 못 막는다 → C01 |
| E07 | `Bash(*prod.db*)` | deny | 공통 | 교안 | E06 과 같음 |
| E08 | `Bash(curl *)` | deny | 공통 | 교안 | 정상 조회도 막는다 |
| E09 | `Bash(wget *)` | deny | 공통 | 교안 | |
| E10 | `Read(./.env)` | deny | 공통 | 교안 | |
| E11 | `Read(./.env.*)` | deny | 공통 | 교안 | |
| E12 | `Read(./secrets/**)` | deny | 공통 | 교안 | |
| E13 | `Read(**/*.pem)` | deny | 공통 | 교안 | `.pfx` · `.p12` · `.der` · `.key` 는 빠진다 → C08 |
| E14 | `Read(~/.ssh/**)` | deny | 공통 | 교안 | |
| E15 | `Read(~/.aws/**)` | deny | 공통 | 교안 | 다른 클라우드 로그인 파일은 빠진다 → C07 |
| E16 | `Edit(./.env)` | deny | 공통 | 교안 | |
| E17 | `Edit(./.env.*)` | deny | 공통 | 교안 | |
| E18 | `Edit(./secrets/**)` | deny | 공통 | 교안 | |
| E19 | `mcp__github__delete_file` | deny | 공통 | 교안 | GitHub MCP 사용자에게만 의미가 있다 |
| E20 | `mcp__github__merge_pull_request` | deny | 공통 | 교안 | E19 와 같음 |
| E21 | `Bash(terraform apply *)` | deny | `data-infra` | 추가분 | **구멍:** `terraform -chdir=infra apply` 는 접두어가 달라 비켜 간다 → D02 |
| E22 | `Bash(terraform destroy *)` | deny | `data-infra` | 추가분 | E21 과 같은 구멍 |
| E30 | `Bash(git push *)` | ask | 공통 | 교안 | |
| E31 | `Bash(npm install *)` | ask | `react` | 교안 | |
| E32 | `Bash(npm i *)` | ask | `react` | 교안 | |
| E33 | `Bash(npm ci *)` | ask | `react` | 교안 | |
| E34 | `Bash(docker compose down *)` | ask | 저장소 전용 | 교안 | compose 프로필 운영 방식이 이 저장소 기준 |
| E35 | `Bash(docker compose --profile *)` | ask | 저장소 전용 | 교안 | |
| E36 | `Bash(docker compose up *)` | ask | 저장소 전용 | 교안 | |
| E37 | `Bash(./gradlew bootRun *)` | ask | `spring` | 교안 | |
| E38 | `mcp__github__create_pull_request` | ask | 공통 | 교안 | |
| E39 | `mcp__github__push_files` | ask | 공통 | 교안 | |
| E40 | `mcp__dbhub` | ask | 공통 | 교안 | DB 접근 MCP. 서버 이름은 저장소마다 맞춰 넣는다 |
| E41 | `mcp__dbhub-mssql` | ask | `data-infra` | 교안 | MS-SQL(ERP 도구) 쪽 |
| E42 | `Bash(npm test *)` | allow | `react` | 교안 | |
| E43 | `Bash(npm run test *)` | allow | `react` | 교안 | |
| E44 | `Bash(npm run lint *)` | allow | `react` | 교안 | |
| E45 | `Bash(npm run typecheck *)` | allow | `react` | 교안 | |
| E46 | `Bash(npm run build *)` | allow | `react` | 교안 | |
| E47 | `Bash(./gradlew test *)` | allow | `spring` | 교안 | |
| E48 | `Bash(./gradlew check *)` | allow | `spring` | 교안 | |
| E49 | `Bash(./gradlew build *)` | allow | `spring` | 교안 | |
| E50 | `Bash(git status *)` | allow | 공통 | 교안 | |
| E51 | `Bash(git diff *)` | allow | 공통 | 교안 | |
| E52 | `Bash(git log *)` | allow | 공통 | 교안 | |
| E53 | `Bash(git add *)` | allow | 공통 | 교안 | |
| E54 | `Bash(git commit *)` | allow | 공통 | 교안 | secret-scan Hook(E64)이 먼저 검사한다 |
| E55 | `Bash(pytest *)` | allow | `data-infra` | 교안 | **확인 필요:** react · spring 어디에도 안 맞아 Python 파이프라인(`templates/CLAUDE.data-pipeline.md`) 기준으로 넣었다 |
| E56 | `WebFetch(domain:docs.spring.io)` | allow | `spring` | 교안 | |
| E57 | `WebFetch(domain:docs.gradle.org)` | allow | `spring` | 교안 | settings.json 에 **두 번** 들어 있다. 조직판은 한 줄로 |
| E58 | `WebFetch(domain:developer.mozilla.org)` | allow | `react` | 교안 | |
| E59 | `WebFetch(domain:fastapi.tiangolo.com)` | allow | `data-infra` | 교안 | E55 와 같은 "확인 필요" |
| E5A | `mcp__github__get_file_contents` | allow | 공통 | 교안 | 읽기 전용 |
| E5B | `mcp__github__list_pull_requests` | allow | 공통 | 교안 | 읽기 전용 |
| E5C | `mcp__github__pull_request_read` | allow | 공통 | 교안 | 읽기 전용 |
| E5D | `mcp__github__list_issues` | allow | 공통 | 교안 | 읽기 전용 |
| E5E | `mcp__item-bank__search_items` | allow | 저장소 전용 | 교안 | 실습용 MCP 서버 |

### 1-2. Hook (`PreToolUse`, matcher `Bash`)

| # | 규칙 원문 | 종류 | 적용 범위 | 출처 | 비고 |
|---|---|---|---|---|---|
| E60 | `scripts/hook-node.sh` (실행기, node 못 찾으면 fail-closed) | Hook | 공통 | 추가분 | Hook 을 퍼뜨릴 때 같이 배포해야 한다 |
| E61 | `block-dangerous.mjs` `RULES[0]` — `rm` 재귀 + 강제 | Hook | 공통 | 추가분 | 명령 문자열 어디에 있어도 잡는다(따옴표 안 시험 문자열도 걸린다) |
| E62 | `block-dangerous.mjs` `RULES[1]` — `/DROP\s+(TABLE\|DATABASE)/i` | Hook | 공통 | 추가분 | `TRUNCATE` · `WHERE` 없는 `DELETE`/`UPDATE` 는 못 막는다 → C02 · C03 |
| E63 | `block-dangerous.mjs` `RULES[2]` — `/prod[-.]db/` | Hook | 공통 | 추가분 | |
| E64 | `secret-scan.mjs` `AWS Access Key ID` — `/AKIA[0-9A-Z]{16}/` | Hook | 공통 | 추가분 | `git commit` 으로 시작하는 명령만 검사 |
| E65 | `secret-scan.mjs` `하드코딩된 password` — `/password\s*=\s*['"][^'"]+/i` | Hook | 공통 | 추가분 | 따옴표 없는 값 · `:` 구분 · 연결 문자열을 놓친다 → C04 · C05 · C09 |
| E66 | `secret-scan.mjs` `PEM 개인키` — `/-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----/` | Hook | 공통 | 추가분 | |

## 2. 추가 제안 (제안 · 미설치)

### 2-0. 지금 규칙이 놓치는 것 (2026-10-13 실행)

현재 `hooks/block-dangerous.mjs` 에 아래 명령을 넣으면 모두 통과한다.

```
exit=0 :: terraform -chdir=infra apply -auto-approve
exit=0 :: TRUNCATE TABLE t
exit=0 :: bcp dbo.pay out x.dat -S erp01
```

`erp01` · `erp-prod01` 은 시험용 샘플 이름이고 실제 ERP 운영 서버 이름이 아니다.

### 2-1. 공통

| # | 규칙 원문 | 종류 | 적용 범위 | 출처 | 비고 |
|---|---|---|---|---|---|
| C01a | `Bash(*<ERP-PROD-HOST>*)` (서버마다 한 줄) | deny | 공통 | 제안 | ERP 운영 서버 이름 차단. **`<ERP-PROD-HOST>` 는 사람이 채운다**(호스트명 · IP · `호스트\인스턴스`) |
| C01b | `block-dangerous.mjs` 에 `/<ERP-PROD-HOST>/i` | Hook | 공통 | 제안 | `cd x && sqlcmd -S …` 처럼 앞에 다른 명령이 붙어도 잡는다. MCP 연결 대상은 Hook 이 못 보므로 `.mcp.json` 검토로 따로 막는다 |
| C02 | `block-dangerous.mjs` 에 `/\bTRUNCATE\s+(TABLE\s+)?\S+/i` | Hook | 공통 | 제안 | |
| C03 | `;` 로 나눈 문장이 `/\bDELETE\s+FROM\s+\S+/i` 또는 `/\bUPDATE\s+\S+\s+SET\b/i` 에 맞고 `/\bWHERE\b/i` 가 없으면 차단 | Hook | 공통 | 제안 | `npm update` · `apt update` 는 통과. `WHERE 1=1` · `-i 파일.sql` 은 못 막는다 |
| C04 | secret-scan 에 `/\b\w*(password\|passwd\|pwd)\s*[:=]\s*[^\s'"$<{][^\s;'"]*/i` | Hook | 공통 | 제안 | 따옴표 없는 `password=` · `DB_PASSWORD:` · `jdbc:…;password=` · `Server=…;Password=` 를 잡는다. `${VAR}` · `<masked>` 는 통과. 저장소 오탐: `var.`/`local.` 참조, `sensitive`, `[PASSWORD]` 마스킹 예시 → 제외 목록 필요 |
| C05 | secret-scan 에 `/\b[a-z][a-z0-9+.-]*:\/\/[^\s:@/]+:[^\s@/]+@/i` | Hook | 공통 | 제안 | `postgres://u:pw@h/db` 같은 URL 형 DB 연결 문자열 |
| C06 | secret-scan 에 `/\b\d{2}(0[1-9]\|1[0-2])(0[1-9]\|[12]\d\|3[01])-?[1-4]\d{6}\b/` | Hook | 공통 | 제안 | 주민등록번호. ERP 급여 샘플 커밋 대비. `20261013-1234567` 은 통과. 외국인등록번호까지면 `[1-8]` |
| C07a | `Read(~/.azure/**)` | deny | 공통 | 제안 | 클라우드 로그인 파일 |
| C07b | `Read(~/.config/gcloud/**)` | deny | 공통 | 제안 | 클라우드 로그인 파일 |
| C07c | `Read(~/.kube/config)` | deny | 공통 | 제안 | `kubectl` 을 쓸 때도 Claude 가 파일을 직접 열 필요는 없다 |
| C07d | `Read(~/.docker/config.json)` | deny | 공통 | 제안 | 레지스트리 로그인 토큰 |
| C07e | `Read(**/*credentials*.json)` | deny | 공통 | 제안 | 서비스 계정 키 |
| C08a | `Read(**/*.pfx)` | deny | 공통 | 제안 | 인증서 |
| C08b | `Read(**/*.p12)` | deny | 공통 | 제안 | 인증서 |
| C08c | `Read(**/*.der)` | deny | 공통 | 제안 | 공개 인증서도 막힌다 |
| C08d | `Read(**/*.key)` | deny | 공통 | 제안 | `NPKI/**/signPri.key` 포함 |
| C08e | `Read(**/NPKI/**)` | deny | 공통 | 제안 | 공동 · 공인인증서(전자세금계산서 인증서) 저장 위치 |
| C08f | `Read(**/GPKI/**)` | deny | 공통 | 제안 | 행정전자서명 인증서 저장 위치 |
| C09 | E65 를 `/password\s*[:=]\s*['"][^'"]+/i` 로 넓힘 | Hook | 공통 | 제안 | `MSSQL_SA_PASSWORD: "…"`(콜론 + 따옴표)는 E65 도 C04 도 놓친다 |

### 2-2. `data-infra`

| # | 규칙 원문 | 종류 | 적용 범위 | 출처 | 비고 |
|---|---|---|---|---|---|
| D01a | `Read(**/*.tfstate)` | deny | `data-infra` | 제안 | state 에는 비밀번호가 평문으로 들어간다 |
| D01b | `Read(**/*.tfstate.backup)` | deny | `data-infra` | 제안 | |
| D01c | `Read(**/.terraform/**)` | deny | `data-infra` | 제안 | 로컬 백엔드 캐시 |
| D01d | `Bash(terraform state pull *)` | deny | `data-infra` | 제안 | 원격 state 를 그대로 출력한다 |
| D01e | `Bash(terraform show *)` | deny | `data-infra` | 제안 | plan 파일 확인에도 쓰여 불편할 수 있다 → `ask` 로 낮출지 결정 필요 |
| D02 | `block-dangerous.mjs` 에 `/\bterraform\b[^;&\|]*\s(apply\|destroy)\b/` | Hook | `data-infra` | 제안 | **E21 · E22 구멍 막기.** `-chdir=…` · `-var-file` 이 앞에 와도 잡는다. `terraform -chdir=infra plan` 은 통과 |
| D03a | `Bash(terraform state rm *)` | deny | `data-infra` | 제안 | |
| D03b | `Bash(terraform state mv *)` | deny | `data-infra` | 제안 | |
| D03c | `Bash(terraform import *)` | deny | `data-infra` | 제안 | |
| D03d | `Bash(terraform taint *)` | deny | `data-infra` | 제안 | |
| D03e | `Bash(terraform force-unlock *)` | deny | `data-infra` | 제안 | |
| D03f | `Bash(terraform init *)` | ask | `data-infra` | 제안 | 백엔드 · 프로바이더가 바뀔 수 있다 |
| D03g | `Bash(terraform workspace select *)` | ask | `data-infra` | 제안 | |
| D03h | `Bash(terraform plan *)` · `Bash(terraform validate *)` · `Bash(terraform fmt *)` | allow | `data-infra` | 제안 | 읽기 · 검사 전용(같은 성격 세 줄을 묶음) |
| D04a | `Bash(kubectl delete *)` | deny | `data-infra` | 제안 | |
| D04b | `Bash(kubectl exec *)` | deny | `data-infra` | 제안 | |
| D04c | `Bash(kubectl get secret*)` | deny | `data-infra` | 제안 | |
| D04d | `Bash(kubectl * --context *prod*)` | deny | `data-infra` | 제안 | `--context` 없이 현재 컨텍스트가 운영이면 비켜 간다 → 운영 컨텍스트 이름도 Hook 에 넣기를 권함 |
| D04e | `Bash(kubectl apply *)` | ask | `data-infra` | 제안 | |
| D04f | `Bash(kubectl scale *)` | ask | `data-infra` | 제안 | |
| D04g | `Bash(kubectl rollout *)` | ask | `data-infra` | 제안 | |
| D04h | `Bash(kubectl edit *)` | ask | `data-infra` | 제안 | |
| D04i | `Bash(kubectl config use-context *)` | ask | `data-infra` | 제안 | |
| D05a | `Bash(aws * delete-*)` | deny | `data-infra` | 제안 | |
| D05b | `Bash(aws * terminate-*)` | deny | `data-infra` | 제안 | |
| D05c | `Bash(aws s3 rm *)` | deny | `data-infra` | 제안 | |
| D05d | `Bash(aws s3 rb *)` | deny | `data-infra` | 제안 | |
| D05e | `Bash(aws iam *)` | deny | `data-infra` | 제안 | 조회(`list-*`)도 막힌다. 필요하면 `allow: Bash(aws iam list-*)` |
| D05f | `Bash(aws secretsmanager get-secret-value *)` | deny | `data-infra` | 제안 | |
| D05g | `Bash(aws ssm get-parameter* --with-decryption*)` | deny | `data-infra` | 제안 | |
| D05h | `Bash(aws * create-*)` | ask | `data-infra` | 제안 | |
| D05i | `Bash(aws * put-*)` | ask | `data-infra` | 제안 | |
| D05j | `Bash(aws * update-*)` | ask | `data-infra` | 제안 | |
| D05k | `Bash(aws s3 cp *)` | ask | `data-infra` | 제안 | |
| D05l | `Bash(aws s3 sync *)` | ask | `data-infra` | 제안 | |
| D06a | `Bash(bcp *)` | deny | `data-infra` | 제안 | ERP 데이터 대량 반출 차단. `bcp … in`(적재)도 함께 막힌다 |
| D06b | `block-dangerous.mjs` 에 `/\bbcp\b/` | Hook | `data-infra` | 제안 | `cd x && bcp …` 처럼 앞에 명령이 붙는 경우 |
| D07a | `Bash(crontab *)` | ask | `data-infra` | 제안 | 배치 스케줄 변경 |
| D07b | `Bash(crontab -l *)` | allow | `data-infra` | 제안 | 조회는 묻지 않는다 |
| D07c | `Bash(crontab -r *)` | deny | `data-infra` | 제안 | 전체 삭제 |
| D08a | `Bash(systemctl *)` | ask | `data-infra` | 제안 | 대부분 `sudo` 가 붙어 E02 에 먼저 걸린다 |
| D08b | `Bash(systemctl status *)` | allow | `data-infra` | 제안 | |
| D08c | `Bash(systemctl list-* *)` | allow | `data-infra` | 제안 | |

제안 정규식 시험 결과(scratchpad, 2026-10-13):

```
D2   terraform -chdir=infra apply -auto-approve
-    terraform -chdir=infra plan
C2   TRUNCATE TABLE slip
D6   cd x && bcp a out b
C3   DELETE FROM slip
-    DELETE FROM slip WHERE id=1
C3   UPDATE stock SET qty=0
-    npm update
C4   password=Sa-pass1234!
C4   Server=h;User Id=sa;Password=abc;
-    password=${DB_PASSWORD}
C5   postgres://u:pw@h/db
-    https://example.com/path
C6   주민 900101-1234567
-    주문번호 20261013-1234567
```

## 3. 사람만 확인할 수 있는 것

| # | 규칙 원문 | 종류 | 적용 범위 | 출처 | 비고 |
|---|---|---|---|---|---|
| H01 | ERP 결산 · 마감 기간에는 ERP 관련 변경(배포 · 스키마 · 배치 스케줄)을 하지 않는다 | 사람 | 공통 | 제안 | 기간 달력을 누가 공지할지 정해야 한다 |
| H02 | 전표 · 재고 테이블을 바꾸는 SQL 은 승인을 받은 뒤 실행한다 | 사람 | 공통 | 제안 | 승인자 · 기록 양식이 아직 없다. C03 은 `WHERE` 유무만 봐서 대신하지 못한다 |

## 4. 남은 결정

1. `<ERP-PROD-HOST>` 실제 목록(호스트 · IP · 인스턴스명)과 운영 kube 컨텍스트 이름.
2. E55 `pytest` · E59 `fastapi` 를 `data-infra` 에 둘지, Python 스택 코드를 따로 만들지.
3. D01e `terraform show` 를 deny 로 할지 ask 로 할지.
4. C04 오탐 제외 목록(`var.` · `local.` · `[` · `sensitive`)과 `*.md` 검사 제외 여부.
